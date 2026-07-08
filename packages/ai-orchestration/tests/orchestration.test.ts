import { describe, expect, it, vi } from "vitest";
import {
  buildSystemPrompt,
  CONTENT_POLICY_CLAUSE,
  ConversationSession,
  CorrectionEngine,
  MalformedModelOutput,
  OpenAIProvider,
  renderTemplate,
  TokenCeilingExceeded,
  type AICompletionRequest,
  type AICompletionResult,
  type AIProvider,
  type LearnerContext,
} from "../src/index.js";

const CTX: LearnerContext = {
  targetLanguage: "Brazilian Portuguese",
  dialect: "pt-BR-SP",
  cefrEstimate: "A2",
  severityCeiling: 5,
  correctionStrictness: "balanced",
};

function stubProvider(replies: AICompletionResult[]): { provider: AIProvider; requests: AICompletionRequest[] } {
  const requests: AICompletionRequest[] = [];
  let i = 0;
  return {
    requests,
    provider: {
      name: "stub",
      async complete(request) {
        requests.push(request);
        const reply = replies[Math.min(i, replies.length - 1)];
        i += 1;
        return reply!;
      },
    },
  };
}

describe("renderTemplate", () => {
  it("substitutes placeholders and rejects unknown ones", () => {
    expect(renderTemplate("Olá {{name}}!", { name: "Alek" })).toBe("Olá Alek!");
    expect(() => renderTemplate("{{missing}}", {})).toThrow(/unknown placeholder/);
  });
});

describe("buildSystemPrompt", () => {
  it("always carries the learner context and the content-policy clause", () => {
    const prompt = buildSystemPrompt("You are a tutor.", CTX);
    expect(prompt).toContain("You are a tutor.");
    expect(prompt).toContain("severity 5/7");
    expect(prompt).toContain("dialect focus: pt-BR-SP");
    expect(prompt).toContain(CONTENT_POLICY_CLAUSE);
  });
});

describe("OpenAIProvider", () => {
  it("sends the right wire format and parses text + usage", async () => {
    const fetchFn = vi.fn(async (_url: unknown, init: unknown) => {
      const body = JSON.parse((init as { body: string }).body);
      expect(body.model).toBe("test-model");
      expect(body.messages[0].role).toBe("system");
      return new Response(
        JSON.stringify({ choices: [{ message: { content: "olá!" } }], usage: { total_tokens: 42 } }),
        { status: 200 },
      );
    });
    const provider = new OpenAIProvider({ apiKey: "sk-test", model: "test-model", fetchFn: fetchFn as unknown as typeof fetch });

    const result = await provider.complete({ messages: [{ role: "system", content: "s" }] });
    expect(result).toEqual({ text: "olá!", tokensUsed: 42 });

    const [url, init] = fetchFn.mock.calls[0] as [string, { headers: Record<string, string> }];
    expect(url).toBe("https://api.openai.com/v1/chat/completions");
    expect(init.headers.authorization).toBe("Bearer sk-test");
  });

  it("surfaces HTTP errors with a body snippet", async () => {
    const fetchFn = async () => new Response("rate limited", { status: 429 });
    const provider = new OpenAIProvider({ apiKey: "sk-test", fetchFn: fetchFn as unknown as typeof fetch });
    await expect(provider.complete({ messages: [] })).rejects.toThrow(/429.*rate limited/s);
  });
});

describe("CorrectionEngine", () => {
  const goodJson = JSON.stringify({
    corrected: "Eu estou cansado.",
    natural: "Tô cansado.",
    grammarExplanation: "estar for temporary states",
    futureReviewItemKeys: [],
  });

  it("returns a validated correction and injects context + policy into the system prompt", async () => {
    const { provider, requests } = stubProvider([{ text: goodJson }]);
    const engine = new CorrectionEngine(provider);

    const correction = await engine.correct("Eu sou cansado.", CTX);
    expect(correction.corrected).toBe("Eu estou cansado.");
    expect(correction.natural).toBe("Tô cansado.");

    const system = requests[0]!.messages[0]!;
    expect(system.role).toBe("system");
    expect(system.content).toContain(CONTENT_POLICY_CLAUSE);
    const user = requests[0]!.messages[1]!;
    expect(user.content).toContain("Eu sou cansado.");
    expect(user.content).toContain("Brazilian Portuguese");
  });

  it("repairs code-fenced JSON", async () => {
    const { provider } = stubProvider([{ text: "```json\n" + goodJson + "\n```" }]);
    const correction = await new CorrectionEngine(provider).correct("x", CTX);
    expect(correction.corrected).toBe("Eu estou cansado.");
  });

  it("throws MalformedModelOutput on junk, keeping the raw reply for debugging", async () => {
    const { provider } = stubProvider([{ text: "sorry, I can't do JSON today" }]);
    await expect(new CorrectionEngine(provider).correct("x", CTX)).rejects.toBeInstanceOf(MalformedModelOutput);
  });

  it("uses a pack-supplied template when provided", async () => {
    const { provider, requests } = stubProvider([{ text: goodJson }]);
    const engine = new CorrectionEngine(provider, { template: "PACK TEMPLATE {{targetLanguage}}: {{text}}" });
    await engine.correct("oi", CTX);
    expect(requests[0]!.messages[1]!.content).toBe("PACK TEMPLATE Brazilian Portuguese: oi");
  });
});

describe("ConversationSession", () => {
  it("keeps the full transcript but only sends the capped context window", async () => {
    const { provider, requests } = stubProvider([{ text: "resposta", tokensUsed: 10 }]);
    const session = new ConversationSession(provider, {
      taskPrompt: "Roleplay a café.",
      ctx: CTX,
      maxContextTurns: 4,
    });

    for (let i = 0; i < 6; i++) await session.send(`msg ${i}`);

    expect(session.transcript).toHaveLength(12); // 6 user + 6 assistant, all retained locally
    const lastRequest = requests.at(-1)!;
    expect(lastRequest.messages).toHaveLength(1 + 4); // system + capped window
    expect(lastRequest.messages[0]!.content).toContain(CONTENT_POLICY_CLAUSE);
  });

  it("accumulates provider-reported tokens and enforces the ceiling", async () => {
    const { provider } = stubProvider([{ text: "ok", tokensUsed: 600 }]);
    const session = new ConversationSession(provider, {
      taskPrompt: "t",
      ctx: CTX,
      tokenCeiling: 1000,
    });

    await session.send("first"); // spent = 600
    await session.send("second"); // spent = 1200 (send allowed at 600 < 1000)
    expect(session.tokensSpent).toBe(1200);
    await expect(session.send("third")).rejects.toBeInstanceOf(TokenCeilingExceeded);
  });

  it("falls back to the chars/4 estimator when the provider reports no usage", async () => {
    const { provider } = stubProvider([{ text: "12345678" }]); // 8 chars → 2 tokens
    const session = new ConversationSession(provider, { taskPrompt: "t", ctx: CTX });
    await session.send("abcd"); // request estimate > 0 + reply 2
    expect(session.tokensSpent).toBeGreaterThan(2);
  });
});

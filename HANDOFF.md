# Life OS — session handoff prompt

Paste the block below into a fresh session to hand off the native rebuild.
It orients the new session, sets the ground rules, and requires a progress
report before any new code is written. Keep this file current as status
changes.

---

```
You're picking up the Life OS native rebuild (Kotlin + Compose Multiplatform).
Working branch: claude/lifeos-dev-setup-dpipr6.

Ground rules:
- Native is the product. The web `js/` source is reference-only — port real
  behavior FROM it, never suggest going back to the web app.
- Read CLAUDE.md first, then ARCHITECTURE.md, PROJECT_SPEC.md, and
  FEATURE_LIST.md for shape and status.
- Don't design graphics — I bring the visual assets; you wire them up.
- One clean commit per build; keep CI green. Bump service-worker.js
  CACHE_VERSION on any shipped web change (hundredths: v1.02 → v1.03).
- Commit trailer: Co-Authored-By: Claude <noreply@anthropic.com>. Never put a
  model identifier in any committed artifact.

Current state: camera→AI document scan, blob store (Option B), photo
attachments across 11 modules, baked OpenAI key (via Actions secret), shared
MM-DD-YYYY date field, one-level system-back + floating back arrow, Saved
toast, Finance bill reconciliation + receipt-scan, Books ISBN scan,
Milestones AI recap — all shipped and CI-green. Theme-from-Photo was removed.

FIRST TASK — before writing any code, produce a progress report:
1. Read the port-tracker and LIFE_OS_MASTER_INVENTORY.md and reconcile them
   against the actual native source.
2. Give me a concise status report: what's fully ported and device-verified,
   what's stubbed or partial, what's web-only and still unported, and the
   deferred follow-ups (book-cover image download; storing the receipt image
   on receipt-scanned Finance entries).
3. Then stop and wait — tell me what you'd tackle next and let me choose.
   Don't start new feature work until I pick.
```

// AI Assistant — a chat with Claude, using your own Anthropic API key
// (Settings has an "AI Assistant" section with the key field), called
// directly from the browser. No server in between; the key never leaves
// this device.

import { el, fmtDate } from '../dom.js';

let state = { selectedId: null, sending: false, error: null };

function messageBubble(message) {
  const isUser = message.role === 'user';
  return el('div', { class: isUser ? 'mer-person-card' : 'mer-task-detail' }, [
    el('div', { class: 'mer-person-info' }, [
      el('div', { class: 'mer-person-name', text: isUser ? 'You' : 'Claude' }),
      el('div', { class: 'mer-person-meta', text: message.content }),
    ]),
  ]);
}

async function renderConversation(canvas, conversation, ctx, rerender) {
  canvas.append(el('div', { class: 'mer-detail-header' }, [
    el('h1', { text: conversation.title || 'Conversation' }),
    el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; state.error = null; rerender(); } }),
  ]));

  const messages = await ctx.data.getAiMessages(conversation.id);
  const thread = el('div', { class: 'mer-people-list' });
  for (const m of messages) thread.append(messageBubble(m));
  canvas.append(thread);

  if (state.error) canvas.append(el('p', { class: 'mer-muted mer-sync-error', text: state.error }));
  if (state.sending) canvas.append(el('p', { class: 'mer-muted', text: 'Claude is thinking…' }));

  const input = el('textarea', { rows: '2', placeholder: 'Message Claude…' });
  const sendBtn = el('button', {
    type: 'button', text: 'Send',
    onclick: async () => {
      const text = input.value.trim();
      if (!text || state.sending) return;
      input.value = '';
      state.sending = true;
      state.error = null;
      rerender();
      try {
        await ctx.data.sendAiMessage(conversation.id, text);
      } catch (err) {
        state.error = err.message || String(err);
      }
      state.sending = false;
      rerender();
    },
  });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendBtn.click(); }
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [input, sendBtn]));

  canvas.append(el('button', {
    type: 'button', class: 'mer-danger-btn', text: 'Delete conversation',
    onclick: async () => {
      if (!confirm('Delete this conversation?')) return;
      for (const m of messages) await ctx.data.AiMessages.remove(m.id);
      await ctx.data.AiConversations.remove(conversation.id);
      state.selectedId = null;
      rerender();
    },
  }));
}

function conversationRow(conversation, onSelect) {
  const row = el('div', { class: 'mer-task-row' }, [
    el('span', { class: 'mer-task-title', text: conversation.title || '(untitled)' }),
    el('span', { class: 'mer-task-meta', text: fmtDate(conversation.createdAt.slice(0, 10)) }),
  ]);
  row.addEventListener('click', () => onSelect(conversation.id));
  return row;
}

export async function renderAssistant(canvas, ctx, rerender) {
  const apiKey = await ctx.data.Settings.get('anthropicApiKey');
  if (!apiKey) {
    canvas.append(el('h1', { text: 'AI Assistant' }));
    canvas.append(el('p', { class: 'mer-muted', text: 'No Anthropic API key set yet. Add one in Settings > AI Assistant to start chatting with Claude.' }));
    canvas.append(el('button', { type: 'button', text: 'Go to Settings', onclick: () => ctx.navigate('settings') }));
    return;
  }

  if (state.selectedId) {
    const conversation = await ctx.data.AiConversations.get(state.selectedId);
    if (conversation) { await renderConversation(canvas, conversation, ctx, rerender); return; }
    state.selectedId = null;
  }

  canvas.append(el('h1', { text: 'AI Assistant' }));

  const titleIn = el('input', { type: 'text', placeholder: 'New conversation title (optional)' });
  canvas.append(el('div', { class: 'mer-toolbar' }, [titleIn, el('button', {
    type: 'button', text: '+ New conversation',
    onclick: async () => {
      const conversation = await ctx.data.createAiConversation(titleIn.value.trim());
      state.selectedId = conversation.id;
      rerender();
    },
  })]));

  const conversations = (await ctx.data.AiConversations.list()).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  if (!conversations.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'No conversations yet.' }));
    return;
  }
  const area = el('div', { class: 'mer-task-list-area' });
  for (const c of conversations) area.append(conversationRow(c, (id) => { state.selectedId = id; rerender(); }));
  canvas.append(area);
}

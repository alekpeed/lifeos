import { el, todayStr } from '../dom.js';

let state = {
  deckId: null,
  studying: false,
  studyQueue: [],
  studyIndex: 0,
  showAnswer: false,
};

const STARTER_HIRAGANA = [
  ['あ', 'a'], ['い', 'i'], ['う', 'u'], ['え', 'e'], ['お', 'o'],
  ['か', 'ka'], ['き', 'ki'], ['く', 'ku'], ['け', 'ke'], ['こ', 'ko'],
  ['さ', 'sa'], ['し', 'shi'], ['す', 'su'], ['せ', 'se'], ['そ', 'so'],
];

function computeStreak(dates) {
  const days = new Set(dates);
  let streak = 0;
  const cursor = new Date(todayStr() + 'T00:00:00');
  if (!days.has(cursor.toISOString().slice(0, 10))) cursor.setDate(cursor.getDate() - 1);
  while (days.has(cursor.toISOString().slice(0, 10))) {
    streak++;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

function speak(text) {
  if (!window.speechSynthesis) return;
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = 'ja-JP';
  window.speechSynthesis.speak(utterance);
}

function addDays(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

// --- Deck list ---

async function renderDeckList(container, ctx, rerender) {
  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New deck — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.JapaneseDecks.create({ name: e.target.value.trim() });
      e.target.value = '';
    },
  });
  const starterBtn = el('button', {
    type: 'button', text: '+ Starter hiragana deck',
    onclick: async () => {
      const deck = await ctx.data.JapaneseDecks.create({ name: 'Hiragana basics' });
      for (const [front, back] of STARTER_HIRAGANA) {
        await ctx.data.JapaneseCards.create({ deckId: deck.id, front, back, srs: { interval: 1, dueDate: todayStr() } });
      }
      rerender();
    },
  });
  container.append(el('div', { class: 'mer-toolbar' }, [quickAdd, starterBtn]));

  const [decks, allCards, allLogs] = await Promise.all([
    ctx.data.JapaneseDecks.list(), ctx.data.JapaneseCards.list(), ctx.data.JapaneseReviewLogs.list(),
  ]);
  const streak = computeStreak(allLogs.map((l) => l.date));

  const area = el('div', { class: 'mer-task-list-area' });
  container.append(area);
  area.append(el('p', {}, [el('strong', { text: `🔥 ${streak}-day study streak` })]));

  if (!decks.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No decks yet.' }));
    return;
  }

  for (const deck of decks) {
    const cards = allCards.filter((c) => c.deckId === deck.id);
    const dueCount = cards.filter((c) => (c.srs?.dueDate || todayStr()) <= todayStr()).length;
    const row = el('div', { class: 'mer-task-row' }, [
      el('span', { class: 'mer-task-title', text: deck.name || '(untitled deck)' }),
      el('div', { class: 'mer-task-meta' }, [
        el('span', { class: 'mer-chip', text: `${cards.length} cards` }),
        dueCount ? el('span', { class: 'mer-chip is-overdue', text: `${dueCount} due` }) : null,
      ]),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async (e) => {
          e.stopPropagation();
          if (!confirm('Delete this deck and all its cards?')) return;
          for (const c of cards) await ctx.data.JapaneseCards.remove(c.id);
          await ctx.data.JapaneseDecks.remove(deck.id);
          rerender();
        },
      }),
    ]);
    row.addEventListener('click', () => { state.deckId = deck.id; rerender(); });
    area.append(row);
  }
}

// --- Deck detail: card management + study ---

function cardRow(card, ctx, rerender) {
  return el('div', { class: 'mer-task-row' }, [
    el('span', { class: 'mer-task-title', text: `${card.front} → ${card.back}` }),
    el('div', { class: 'mer-task-meta' }, [
      el('span', { class: 'mer-chip', text: `due ${card.srs?.dueDate || todayStr()}` }),
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×',
      onclick: async () => { await ctx.data.JapaneseCards.remove(card.id); rerender(); },
    }),
  ]);
}

function newCardForm(deckId, ctx, rerender) {
  const frontInput = el('input', { type: 'text', placeholder: 'Front (kana/kanji)' });
  const backInput = el('input', { type: 'text', placeholder: 'Back (reading/meaning)' });
  const addBtn = el('button', {
    type: 'button', text: '+ Add card',
    onclick: async () => {
      if (!frontInput.value.trim() || !backInput.value.trim()) return;
      await ctx.data.JapaneseCards.create({ deckId, front: frontInput.value.trim(), back: backInput.value.trim(), srs: { interval: 1, dueDate: todayStr() } });
      frontInput.value = '';
      backInput.value = '';
      rerender();
    },
  });
  return el('div', { class: 'mer-person-form' }, [frontInput, backInput, addBtn]);
}

async function renderStudySession(container, ctx, rerender) {
  const card = state.studyQueue[state.studyIndex];
  if (!card) {
    container.append(
      el('p', {}, [el('strong', { text: 'Session complete!' })]),
      el('button', { type: 'button', text: '← Back to deck', onclick: () => { state.studying = false; rerender(); } }),
    );
    return;
  }

  const grade = async (quality) => {
    const prevInterval = card.srs?.interval || 1;
    const nextInterval = quality === 'again' ? 1 : quality === 'good' ? prevInterval * 2 : prevInterval * 3;
    await ctx.data.JapaneseCards.update(card.id, { srs: { interval: nextInterval, dueDate: addDays(nextInterval) } });
    await ctx.data.JapaneseReviewLogs.create({ cardId: card.id, date: todayStr(), quality });
    state.studyIndex++;
    state.showAnswer = false;
    rerender();
  };

  container.append(
    el('p', { class: 'mer-muted', text: `Card ${state.studyIndex + 1} of ${state.studyQueue.length}` }),
    el('div', { class: 'mer-task-detail' }, [
      el('h2', { text: card.front }),
      el('button', { type: 'button', text: '🔊 Play', onclick: () => speak(card.front) }),
      state.showAnswer
        ? el('div', {}, [
          el('p', {}, [el('strong', { text: card.back })]),
          el('div', { class: 'mer-toggle-group' }, [
            el('button', { type: 'button', text: 'Again', onclick: () => grade('again') }),
            el('button', { type: 'button', text: 'Good', onclick: () => grade('good') }),
            el('button', { type: 'button', text: 'Easy', onclick: () => grade('easy') }),
          ]),
        ])
        : el('button', { type: 'button', text: 'Show answer', onclick: () => { state.showAnswer = true; rerender(); } }),
    ]),
  );
}

async function renderDeckDetail(container, ctx, rerender) {
  const deck = await ctx.data.JapaneseDecks.get(state.deckId);
  if (!deck) { state.deckId = null; rerender(); return; }

  const cards = (await ctx.data.JapaneseCards.list()).filter((c) => c.deckId === deck.id);
  const dueCards = cards.filter((c) => (c.srs?.dueDate || todayStr()) <= todayStr());

  container.append(el('div', { class: 'mer-toolbar' }, [
    el('button', { type: 'button', text: '← Decks', onclick: () => { state.deckId = null; state.studying = false; rerender(); } }),
    el('h1', { text: deck.name || '(untitled deck)', style: 'flex:1;margin:0' }),
    dueCards.length
      ? el('button', {
        type: 'button', text: `Study (${dueCards.length} due)`,
        onclick: () => { state.studying = true; state.studyQueue = dueCards; state.studyIndex = 0; state.showAnswer = false; rerender(); },
      })
      : null,
  ]));

  if (state.studying) {
    await renderStudySession(container, ctx, rerender);
    return;
  }

  const area = el('div', { class: 'mer-task-list-area' });
  container.append(area);
  if (!cards.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No cards yet.' }));
  } else {
    for (const card of cards) area.append(cardRow(card, ctx, rerender));
  }
  area.append(newCardForm(deck.id, ctx, rerender));
}

export async function renderJapanese(canvas, ctx, rerender) {
  if (state.deckId) {
    await renderDeckDetail(canvas, ctx, rerender);
  } else {
    canvas.append(el('h1', { text: 'Japanese' }));
    await renderDeckList(canvas, ctx, rerender);
  }
}

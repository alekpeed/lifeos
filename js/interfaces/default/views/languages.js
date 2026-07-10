import { el, todayStr } from '../dom.js';

let state = {
  packId: null,
  showAddPackForm: false,
  deckId: null,
  studying: false,
  studyQueue: [],
  studyIndex: 0,
  showAnswer: false,
};

// Starter content keyed by language code -- the format any future pack can
// plug into. Japanese is the only one seeded today; adding e.g. Spanish
// later is a new LanguagePacks record plus (optionally) entries here.
const STARTER_DECKS = {
  ja: {
    name: 'Hiragana basics',
    cards: [
      ['あ', 'a'], ['い', 'i'], ['う', 'u'], ['え', 'e'], ['お', 'o'],
      ['か', 'ka'], ['き', 'ki'], ['く', 'ku'], ['け', 'ke'], ['こ', 'ko'],
      ['さ', 'sa'], ['し', 'shi'], ['す', 'su'], ['せ', 'se'], ['そ', 'so'],
    ],
  },
};

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

function speak(text, ttsLocale) {
  if (!window.speechSynthesis) return;
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = ttsLocale || 'en-US';
  window.speechSynthesis.speak(utterance);
}

function addDays(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

// --- Pack switcher ---

function addPackForm(ctx, rerender) {
  const nameInput = el('input', { type: 'text', placeholder: 'Language name (e.g. Spanish)' });
  const codeInput = el('input', { type: 'text', placeholder: 'Code (e.g. es)' });
  const localeInput = el('input', { type: 'text', placeholder: 'TTS locale (e.g. es-ES)' });
  const addBtn = el('button', {
    type: 'button', text: 'Add language',
    onclick: async () => {
      if (!nameInput.value.trim() || !codeInput.value.trim()) return;
      const pack = await ctx.data.ensureLanguagePack(codeInput.value.trim().toLowerCase(), nameInput.value.trim(), localeInput.value.trim() || 'en-US');
      state.packId = pack.id;
      state.showAddPackForm = false;
      rerender();
    },
  });
  return el('div', { class: 'mer-person-form' }, [nameInput, codeInput, localeInput, addBtn]);
}

function packTabsBar(packs, ctx, rerender) {
  const tabs = el('div', { class: 'mer-toggle-group' }, [
    ...packs.map((pack) => el('button', {
      type: 'button', class: state.packId === pack.id ? 'is-active' : '', text: pack.name,
      onclick: () => { state.packId = pack.id; state.deckId = null; rerender(); },
    })),
    el('button', { type: 'button', text: '+ Add language', onclick: () => { state.showAddPackForm = !state.showAddPackForm; rerender(); } }),
  ]);
  return el('div', {}, [tabs, state.showAddPackForm ? addPackForm(ctx, rerender) : null]);
}

// --- Decks ---

function deckCardRow(card, ctx, rerender) {
  return el('div', { class: 'mer-task-row' }, [
    el('span', { class: 'mer-task-title', text: `${card.front} → ${card.back}` }),
    el('div', { class: 'mer-task-meta' }, [
      el('span', { class: 'mer-chip', text: `due ${card.srs?.dueDate || todayStr()}` }),
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×',
      onclick: async () => { await ctx.data.LanguageCards.remove(card.id); rerender(); },
    }),
  ]);
}

function newCardForm(deckId, ctx, rerender) {
  const frontInput = el('input', { type: 'text', placeholder: 'Front' });
  const backInput = el('input', { type: 'text', placeholder: 'Back' });
  const addBtn = el('button', {
    type: 'button', text: '+ Add card',
    onclick: async () => {
      if (!frontInput.value.trim() || !backInput.value.trim()) return;
      await ctx.data.LanguageCards.create({ deckId, front: frontInput.value.trim(), back: backInput.value.trim(), srs: { interval: 1, dueDate: todayStr() } });
      frontInput.value = '';
      backInput.value = '';
      rerender();
    },
  });
  return el('div', { class: 'mer-person-form' }, [frontInput, backInput, addBtn]);
}

async function renderStudySession(container, ctx, rerender, ttsLocale) {
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
    await ctx.data.LanguageCards.update(card.id, { srs: { interval: nextInterval, dueDate: addDays(nextInterval) } });
    await ctx.data.LanguageReviewLogs.create({ cardId: card.id, date: todayStr(), quality });
    state.studyIndex++;
    state.showAnswer = false;
    rerender();
  };

  container.append(
    el('p', { class: 'mer-muted', text: `Card ${state.studyIndex + 1} of ${state.studyQueue.length}` }),
    el('div', { class: 'mer-task-detail' }, [
      el('h2', { text: card.front }),
      el('button', { type: 'button', text: '🔊 Play', onclick: () => speak(card.front, ttsLocale) }),
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

async function renderDeckDetail(container, ctx, rerender, pack) {
  const deck = await ctx.data.LanguageDecks.get(state.deckId);
  if (!deck) { state.deckId = null; rerender(); return; }

  const cards = (await ctx.data.LanguageCards.list()).filter((c) => c.deckId === deck.id);
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
    await renderStudySession(container, ctx, rerender, pack.ttsLocale);
    return;
  }

  const area = el('div', { class: 'mer-task-list-area' });
  container.append(area);
  if (!cards.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No cards yet.' }));
  } else {
    for (const card of cards) area.append(deckCardRow(card, ctx, rerender));
  }
  area.append(newCardForm(deck.id, ctx, rerender));
}

async function renderDecksTab(container, ctx, rerender, pack) {
  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New deck — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.LanguageDecks.create({ packId: pack.id, name: e.target.value.trim() });
      e.target.value = '';
    },
  });
  const toolbarRow = [quickAdd];
  if (STARTER_DECKS[pack.code]) {
    toolbarRow.push(el('button', {
      type: 'button', text: `+ Starter ${pack.name} deck`,
      onclick: async () => {
        const starter = STARTER_DECKS[pack.code];
        const deck = await ctx.data.LanguageDecks.create({ packId: pack.id, name: starter.name });
        for (const [front, back] of starter.cards) {
          await ctx.data.LanguageCards.create({ deckId: deck.id, front, back, srs: { interval: 1, dueDate: todayStr() } });
        }
        rerender();
      },
    }));
  }
  container.append(el('div', { class: 'mer-toolbar' }, toolbarRow));

  const [decks, allCards, allLogs] = await Promise.all([
    ctx.data.LanguageDecks.byIndex('packId', pack.id),
    ctx.data.LanguageCards.list(),
    ctx.data.LanguageReviewLogs.list(),
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
          for (const c of cards) await ctx.data.LanguageCards.remove(c.id);
          await ctx.data.LanguageDecks.remove(deck.id);
          rerender();
        },
      }),
    ]);
    row.addEventListener('click', () => { state.deckId = deck.id; rerender(); });
    area.append(row);
  }
}

// --- Root ---

// Lessons used to live here as a second tab (grammar explainers you wrote
// yourself). Retired in favor of Library of Babel -- a story-based reading
// library, its own module -- rather than keeping two half-satisfying ways
// to learn grammar/reading side by side. Decks (flashcard SRS) is the only
// tab now, so there's nothing left to switch between.
export async function renderLanguages(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Languages' }));

  const packs = await ctx.data.LanguagePacks.list();
  if (!state.packId || !packs.some((p) => p.id === state.packId)) {
    state.packId = packs[0]?.id || null;
  }
  canvas.append(el('div', { class: 'mer-toolbar' }, [packTabsBar(packs, ctx, rerender)]));

  const pack = packs.find((p) => p.id === state.packId);
  if (!pack) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Add a language to get started.' }));
    return;
  }

  if (state.deckId) {
    await renderDeckDetail(canvas, ctx, rerender, pack);
    return;
  }

  await renderDecksTab(canvas, ctx, rerender, pack);
}

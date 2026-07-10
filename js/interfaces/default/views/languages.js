import { el, todayStr, fmtDate } from '../dom.js';

let state = {
  packId: null,
  tab: 'decks', // decks | library
  showAddPackForm: false,
  deckId: null,
  studying: false,
  studyQueue: [],
  studyIndex: 0,
  showAnswer: false,
  storyId: null,
  showTranslation: false,
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
      onclick: () => { state.packId = pack.id; state.deckId = null; state.storyId = null; rerender(); },
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

// --- Library (Library of Babel: story-based reading, merged in from its own
// module -- Decks and reading practice both belong under "how you learn a
// language," so they live together now instead of two separate modules) ---

const LEVELS = ['beginner', 'intermediate', 'advanced'];

// After a generated story is marked read, ask how the difficulty felt and
// nudge the pack's tracked level for the *next* generation accordingly.
// Manual (non-generated) stories skip this -- there's no level to adjust
// for something you wrote yourself.
function levelFeedbackPrompt(story, pack, ctx, rerender) {
  if (!story.generated || story.levelFeedback) return null;
  const rate = async (feedback) => {
    const nextLevel = ctx.data.adjustLevel(LEVELS, pack.babelLevel || 'beginner', feedback);
    await Promise.all([
      ctx.data.LibraryStories.update(story.id, { levelFeedback: feedback }),
      ctx.data.LanguagePacks.update(pack.id, { babelLevel: nextLevel }),
    ]);
    rerender();
  };
  return el('div', { class: 'mer-toolbar' }, [
    el('span', { class: 'mer-muted', text: 'How was the difficulty?' }),
    el('button', { type: 'button', text: 'Too easy', onclick: () => rate('easy') }),
    el('button', { type: 'button', text: 'Just right', onclick: () => rate('right') }),
    el('button', { type: 'button', text: 'Too hard', onclick: () => rate('hard') }),
  ]);
}

async function renderReader(container, story, pack, ctx, rerender) {
  container.append(el('div', { class: 'mer-detail-header' }, [
    el('h2', { text: story.title || '(untitled)' }),
    el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.storyId = null; rerender(); } }),
  ]));
  container.append(el('div', { class: 'mer-toolbar' }, [
    el('span', { class: 'mer-chip', text: story.level || 'beginner' }),
    story.generated ? el('span', { class: 'mer-chip', text: '✨ Generated' }) : null,
    story.readAt ? el('span', { class: 'mer-chip', text: `Read ${fmtDate(story.readAt)}` }) : null,
  ].filter(Boolean)));

  container.append(el('div', { class: 'mer-babel-story', text: story.body || '' }));

  if (story.translation) {
    const toggle = el('button', {
      type: 'button', class: 'mer-reader-btn', text: state.showTranslation ? 'Hide translation' : 'Show translation',
      onclick: () => { state.showTranslation = !state.showTranslation; rerender(); },
    });
    container.append(toggle);
    if (state.showTranslation) container.append(el('div', { class: 'mer-babel-translation', text: story.translation }));
  }

  container.append(el('div', { class: 'mer-toolbar' }, [
    !story.readAt
      ? el('button', { type: 'button', text: 'Mark as read', onclick: async () => { await ctx.data.LibraryStories.update(story.id, { readAt: todayStr() }); rerender(); } })
      : el('button', { type: 'button', text: 'Mark unread', onclick: async () => { await ctx.data.LibraryStories.update(story.id, { readAt: null }); rerender(); } }),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete',
      onclick: async () => { if (confirm('Delete this story?')) { await ctx.data.LibraryStories.remove(story.id); state.storyId = null; rerender(); } },
    }),
  ]));

  const feedback = levelFeedbackPrompt(story, pack, ctx, rerender);
  if (feedback) container.append(feedback);
}

function storyCard(story, onSelect) {
  const card = el('div', { class: 'mer-place-card' }, [
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: `${story.generated ? '✨ ' : ''}${story.title || '(untitled)'}` }),
      el('div', { class: 'mer-place-meta' }, [
        el('span', { class: 'mer-chip', text: story.level || 'beginner' }),
        story.readAt ? el('span', { class: 'mer-chip', text: '✓ Read' }) : null,
      ].filter(Boolean)),
    ]),
  ]);
  card.addEventListener('click', () => onSelect(story.id));
  return card;
}

// Generate-a-story control: gated on the same per-device Anthropic key as
// the AI Assistant chat -- no shared/sponsored path, so if this pack is
// ever shared with someone else, they need their own key too, same as chat.
function generateStoryControl(pack, stories, hasApiKey, ctx, rerender) {
  const status = el('span', { class: 'mer-muted' });
  if (!hasApiKey) {
    return el('p', { class: 'mer-muted', text: 'Add your Anthropic API key in Settings > AI Assistant to generate stories.' });
  }
  const btn = el('button', {
    type: 'button', text: '✨ Generate a story',
    onclick: async () => {
      status.textContent = 'Writing…';
      status.classList.remove('mer-sync-error');
      try {
        const level = pack.babelLevel || 'beginner';
        const recentTitles = stories.slice(-5).map((s) => s.title).filter(Boolean);
        const { title, body, translation } = await ctx.data.generateLibraryStory(pack.name, level, recentTitles);
        const story = await ctx.data.LibraryStories.create({
          packId: pack.id, title, level, body, translation, readAt: null, generated: true,
        });
        state.storyId = story.id;
        state.showTranslation = false;
        rerender();
      } catch (err) {
        status.textContent = err.message || String(err);
        status.classList.add('mer-sync-error');
      }
    },
  });
  return el('div', { class: 'mer-toolbar' }, [btn, el('span', { class: 'mer-chip', text: `level: ${pack.babelLevel || 'beginner'}` }), status]);
}

function newStoryForm(packId, ctx, rerender) {
  const titleIn = el('input', { type: 'text', placeholder: 'Title' });
  const levelIn = el('select', {}, LEVELS.map((l) => el('option', { value: l, text: l })));
  const bodyIn = el('textarea', { rows: '6', placeholder: 'Story text (in the language you\'re learning)…' });
  const translationIn = el('textarea', { rows: '4', placeholder: 'Translation/gloss (optional)…' });
  return el('div', { class: 'mer-person-form' }, [
    titleIn, levelIn, bodyIn, translationIn,
    el('button', {
      type: 'button', text: 'Add story',
      onclick: async () => {
        if (!bodyIn.value.trim()) return;
        await ctx.data.LibraryStories.create({
          packId, title: titleIn.value.trim(), level: levelIn.value,
          body: bodyIn.value.trim(), translation: translationIn.value.trim(), readAt: null,
        });
        rerender();
      },
    }),
  ]);
}

async function renderLibraryTab(container, ctx, rerender, pack) {
  const stories = await ctx.data.LibraryStories.byIndex('packId', pack.id);

  if (state.storyId) {
    const story = stories.find((s) => s.id === state.storyId);
    if (story) { await renderReader(container, story, pack, ctx, rerender); return; }
    state.storyId = null;
  }

  const hasApiKey = !!(await ctx.data.Settings.get('anthropicApiKey'));
  container.append(el('div', { class: 'mer-subsection-label', text: 'Generate a story' }));
  container.append(generateStoryControl(pack, stories, hasApiKey, ctx, rerender));

  container.append(el('div', { class: 'mer-subsection-label', text: 'Add a story' }));
  container.append(newStoryForm(pack.id, ctx, rerender));

  container.append(el('div', { class: 'mer-subsection-label', text: `Shelf (${stories.length})` }));
  if (!stories.length) {
    container.append(el('p', { class: 'mer-muted', text: 'No stories on this shelf yet.' }));
    return;
  }
  const grid = el('div', { class: 'mer-place-grid' });
  for (const story of stories) grid.append(storyCard(story, (id) => { state.storyId = id; state.showTranslation = false; rerender(); }));
  container.append(grid);
}

// --- Root ---

function subTabsBar(rerender) {
  return el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'decks' ? 'is-active' : '', text: 'Decks', onclick: () => { state.tab = 'decks'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'library' ? 'is-active' : '', text: 'Library', onclick: () => { state.tab = 'library'; rerender(); } }),
  ]);
}

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

  canvas.append(el('div', { class: 'mer-toolbar' }, [subTabsBar(rerender)]));
  if (state.tab === 'decks') await renderDecksTab(canvas, ctx, rerender, pack);
  else await renderLibraryTab(canvas, ctx, rerender, pack);
}

import { el, todayStr } from '../dom.js';

let state = {
  packId: null,
  tab: 'decks', // decks | lessons
  showAddPackForm: false,
  deckId: null,
  studying: false,
  studyQueue: [],
  studyIndex: 0,
  showAnswer: false,
  selectedLessonId: null,
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

const STARTER_LESSONS = {
  ja: [
    {
      topic: 'Sentence Structure', title: 'Basic sentence structure',
      body: 'Japanese word order is Subject-Object-Verb (SOV), not Subject-Verb-Object like English -- the verb comes last.\n\nThe topic of a sentence is marked with the particle は (pronounced "wa" here, not "ha"). です (desu) is a polite copula, roughly "is/am/are," and typically ends the sentence.',
      examples: [
        { text: 'これは本です。', translation: 'This is a book.' },
        { text: '私は学生です。', translation: 'As for me, I am a student.' },
      ],
    },
    {
      topic: 'Particles', title: 'Particles overview',
      body: 'は (wa) marks the topic. が (ga) marks the grammatical subject, often for new information or with verbs like ある/いる. を (o) marks the direct object. に (ni) marks a destination or time. で (de) marks the location where an action happens. と (to) means "and" (for nouns) or "with." も (mo) means "also/too" and replaces は or が.',
      examples: [
        { text: 'パンを食べます。', translation: '(I) eat bread.' },
        { text: '学校に行きます。', translation: '(I) go to school.' },
        { text: '図書館で勉強します。', translation: '(I) study at the library.' },
      ],
    },
    {
      topic: 'Verbs', title: 'Dictionary form vs. ます form',
      body: 'Verbs have a plain/dictionary form (used casually and as the form found in a dictionary) and a polite ます form.\n\nFor ichidan (る-)verbs: drop る and add ます (食べる → 食べます).\nFor godan (う-)verbs: change the final u-sound to an i-sound and add ます (行く → 行きます; 話す → 話します).\nする and 来る are irregular: します, 来ます (kimasu).',
      examples: [
        { text: '食べる → 食べます', translation: 'to eat (dictionary → polite)' },
        { text: '行く → 行きます', translation: 'to go (dictionary → polite)' },
        { text: '話す → 話します', translation: 'to speak (dictionary → polite)' },
      ],
    },
    {
      topic: 'Adjectives', title: 'い-adjectives vs. な-adjectives',
      body: 'い-adjectives end in い (高い, おいしい) and conjugate directly: 高くない ("not expensive"), 高かった ("was expensive").\n\nな-adjectives (静か, 好き) need な when directly modifying a noun (静かな部屋 = "quiet room") and otherwise behave like nouns with だ/です.\n\nWatch for いい ("good") -- it\'s irregular. Its negative/past forms come from the older form よい: よくない, よかった (not いくない).',
      examples: [
        { text: '高い本', translation: 'expensive book (い-adjective)' },
        { text: '静かな部屋', translation: 'quiet room (な-adjective)' },
        { text: 'よくないです。', translation: '(it) is not good -- いい is irregular' },
      ],
    },
  ],
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
      state.tab = 'decks';
      rerender();
    },
  });
  return el('div', { class: 'mer-person-form' }, [nameInput, codeInput, localeInput, addBtn]);
}

function packTabsBar(packs, ctx, rerender) {
  const tabs = el('div', { class: 'mer-toggle-group' }, [
    ...packs.map((pack) => el('button', {
      type: 'button', class: state.packId === pack.id ? 'is-active' : '', text: pack.name,
      onclick: () => { state.packId = pack.id; state.deckId = null; state.tab = 'decks'; rerender(); },
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

// --- Lessons ---

function exampleRow(example) {
  return el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [
      el('div', { class: 'mer-person-name', text: example.text }),
      el('div', { class: 'mer-person-meta', text: example.translation }),
    ]),
  ]);
}

function lessonDetail(lesson, ctx, rerender) {
  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: lesson.title }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedLessonId = null; rerender(); } }),
    ]),
    el('p', { class: 'mer-muted', text: lesson.topic }),
    el('p', {}, lesson.body.split('\n\n').map((para) => el('span', { text: para + ' ' }))),
    lesson.examples?.length ? el('div', { class: 'mer-subsection-label', text: 'Examples' }) : null,
    lesson.examples?.length ? el('div', { class: 'mer-people-list' }, lesson.examples.map(exampleRow)) : null,
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete lesson',
      onclick: async () => { await ctx.data.LanguageLessons.remove(lesson.id); state.selectedLessonId = null; rerender(); },
    }),
  ]);
}

function newLessonForm(pack, ctx, rerender) {
  const topicInput = el('input', { type: 'text', placeholder: 'Topic (e.g. Verbs)' });
  const titleInput = el('input', { type: 'text', placeholder: 'Title' });
  const bodyInput = el('textarea', { rows: '3', placeholder: 'Explanation' });
  const addBtn = el('button', {
    type: 'button', text: '+ Add lesson',
    onclick: async () => {
      if (!titleInput.value.trim() || !bodyInput.value.trim()) return;
      await ctx.data.LanguageLessons.create({
        packId: pack.id, topic: topicInput.value.trim() || 'General',
        title: titleInput.value.trim(), body: bodyInput.value.trim(), examples: [],
      });
      topicInput.value = '';
      titleInput.value = '';
      bodyInput.value = '';
      rerender();
    },
  });
  return el('div', { class: 'mer-person-form' }, [topicInput, titleInput, bodyInput, addBtn]);
}

async function renderLessonsTab(container, ctx, rerender, pack) {
  const lessons = await ctx.data.LanguageLessons.byIndex('packId', pack.id);

  const toolbarRow = [];
  if (STARTER_LESSONS[pack.code] && !lessons.length) {
    toolbarRow.push(el('button', {
      type: 'button', text: `+ Starter ${pack.name} lessons`,
      onclick: async () => {
        for (const lesson of STARTER_LESSONS[pack.code]) {
          await ctx.data.LanguageLessons.create({ packId: pack.id, ...lesson });
        }
        rerender();
      },
    }));
  }
  if (toolbarRow.length) container.append(el('div', { class: 'mer-toolbar' }, toolbarRow));

  const area = el('div', { class: 'mer-task-list-area' });
  container.append(area);

  if (!lessons.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No lessons yet.' }));
  } else {
    const byTopic = new Map();
    for (const lesson of lessons) {
      if (!byTopic.has(lesson.topic)) byTopic.set(lesson.topic, []);
      byTopic.get(lesson.topic).push(lesson);
    }
    for (const [topic, group] of byTopic) {
      area.append(el('div', { class: 'mer-group-label', text: topic }));
      for (const lesson of group) {
        const row = el('div', { class: 'mer-task-row' }, [el('span', { class: 'mer-task-title', text: lesson.title })]);
        row.addEventListener('click', () => { state.selectedLessonId = state.selectedLessonId === lesson.id ? null : lesson.id; rerender(); });
        area.append(row);
      }
    }
  }

  area.append(el('div', { class: 'mer-subsection-label', text: 'Add a lesson' }), newLessonForm(pack, ctx, rerender));

  if (state.selectedLessonId) {
    const lesson = lessons.find((l) => l.id === state.selectedLessonId);
    if (lesson) container.append(lessonDetail(lesson, ctx, rerender));
  }
}

// --- Root ---

function subTabsBar(rerender) {
  return el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'decks' ? 'is-active' : '', text: 'Decks', onclick: () => { state.tab = 'decks'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'lessons' ? 'is-active' : '', text: 'Lessons', onclick: () => { state.tab = 'lessons'; rerender(); } }),
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
  else await renderLessonsTab(canvas, ctx, rerender, pack);
}

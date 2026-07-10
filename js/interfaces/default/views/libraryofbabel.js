// Library of Babel — a story-based reading library per language pack: short
// graded stories (with an optional translation/gloss) you write or paste in
// yourself, read in-app, mark as read. Additive alongside the Languages
// module's existing Lessons tab (grammar explainers) -- both are useful, and
// this doesn't remove anything already shipped there.

import { el, fmtDate, todayStr } from '../dom.js';

let state = { packId: null, selectedId: null, showTranslation: false };

const LEVELS = ['beginner', 'intermediate', 'advanced'];

function packTabs(packs, onSelect) {
  return el('div', { class: 'mer-toggle-group' }, packs.map((p) => el('button', {
    type: 'button', class: p.id === state.packId ? 'is-active' : '', text: p.name,
    onclick: () => onSelect(p.id),
  })));
}

async function renderReader(canvas, story, ctx, rerender) {
  canvas.append(el('div', { class: 'mer-detail-header' }, [
    el('h1', { text: story.title || '(untitled)' }),
    el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
  ]));
  canvas.append(el('div', { class: 'mer-toolbar' }, [
    el('span', { class: 'mer-chip', text: story.level || 'beginner' }),
    story.readAt ? el('span', { class: 'mer-chip', text: `Read ${fmtDate(story.readAt)}` }) : null,
  ].filter(Boolean)));

  canvas.append(el('div', { class: 'mer-babel-story', text: story.body || '' }));

  if (story.translation) {
    const toggle = el('button', {
      type: 'button', class: 'mer-reader-btn', text: state.showTranslation ? 'Hide translation' : 'Show translation',
      onclick: () => { state.showTranslation = !state.showTranslation; rerender(); },
    });
    canvas.append(toggle);
    if (state.showTranslation) canvas.append(el('div', { class: 'mer-babel-translation', text: story.translation }));
  }

  canvas.append(el('div', { class: 'mer-toolbar' }, [
    !story.readAt
      ? el('button', { type: 'button', text: 'Mark as read', onclick: async () => { await ctx.data.LibraryStories.update(story.id, { readAt: todayStr() }); rerender(); } })
      : el('button', { type: 'button', text: 'Mark unread', onclick: async () => { await ctx.data.LibraryStories.update(story.id, { readAt: null }); rerender(); } }),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete',
      onclick: async () => { if (confirm('Delete this story?')) { await ctx.data.LibraryStories.remove(story.id); state.selectedId = null; rerender(); } },
    }),
  ]));
}

function storyCard(story, onSelect) {
  const card = el('div', { class: 'mer-place-card' }, [
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: story.title || '(untitled)' }),
      el('div', { class: 'mer-place-meta' }, [
        el('span', { class: 'mer-chip', text: story.level || 'beginner' }),
        story.readAt ? el('span', { class: 'mer-chip', text: '✓ Read' }) : null,
      ].filter(Boolean)),
    ]),
  ]);
  card.addEventListener('click', () => onSelect(story.id));
  return card;
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

export async function renderLibraryOfBabel(canvas, ctx, rerender) {
  const packs = await ctx.data.LanguagePacks.list();

  canvas.append(el('h1', { text: 'Library of Babel' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'A story-based reading library, one shelf per language you\'re learning.' }));

  if (!packs.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Add a language pack in Languages first.' }));
    return;
  }
  if (!state.packId || !packs.some((p) => p.id === state.packId)) state.packId = packs[0].id;

  canvas.append(packTabs(packs, (id) => { state.packId = id; state.selectedId = null; rerender(); }));

  if (state.selectedId) {
    const story = await ctx.data.LibraryStories.get(state.selectedId);
    if (story) return renderReader(canvas, story, ctx, rerender);
    state.selectedId = null;
  }

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add a story' }));
  canvas.append(newStoryForm(state.packId, ctx, rerender));

  const stories = await ctx.data.LibraryStories.byIndex('packId', state.packId);
  canvas.append(el('div', { class: 'mer-subsection-label', text: `Shelf (${stories.length})` }));
  if (!stories.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'No stories on this shelf yet.' }));
    return;
  }
  const grid = el('div', { class: 'mer-place-grid' });
  for (const story of stories) grid.append(storyCard(story, (id) => { state.selectedId = id; state.showTranslation = false; rerender(); }));
  canvas.append(grid);
}

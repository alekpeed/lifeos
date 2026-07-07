import { el, fmtDate, todayStr } from '../dom.js';

const STATUSES = [
  { value: 'to_read', label: 'To Read' },
  { value: 'reading', label: 'Reading' },
  { value: 'finished', label: 'Finished' },
];

let state = {
  tab: 'reading', // reading | to_read | finished | stats
  selectedId: null,
};

function starRating(value, onChange) {
  const row = el('div', { class: 'mer-stars' });
  for (let i = 1; i <= 5; i++) {
    row.append(el('button', {
      type: 'button',
      class: i <= (value || 0) ? 'mer-star is-filled' : 'mer-star',
      text: '★',
      onclick: () => onChange(i === value ? 0 : i),
    }));
  }
  return row;
}

function estimatedWords(pages, wordsPerPage) {
  if (!pages) return 0;
  return Math.round(pages * wordsPerPage);
}

// --- Book cards (list view) ---

function bookCard(book, cover, ctx, onSelect) {
  const thumb = cover
    ? el('img', { class: 'mer-place-photo', src: ctx.data.attachmentUrl(cover), alt: book.title })
    : el('div', { class: 'mer-place-photo mer-place-photo-empty mer-link-icon', text: '📖' });

  const progressPct = book.status === 'reading' && book.totalPages
    ? Math.min(100, Math.round(((book.currentPage || 0) / book.totalPages) * 100))
    : null;

  const card = el('div', { class: 'mer-place-card' }, [
    thumb,
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: book.title || '(untitled)' }),
      el('div', { class: 'mer-place-meta' }, [
        book.author ? el('span', { class: 'mer-chip', text: book.author }) : null,
        book.genre ? el('span', { class: 'mer-chip', text: book.genre } ) : null,
        progressPct !== null ? el('span', { class: 'mer-chip', text: `${progressPct}%` }) : null,
        book.rating ? el('span', { class: 'mer-chip', text: '★'.repeat(book.rating) }) : null,
      ]),
    ]),
  ]);
  card.addEventListener('click', () => onSelect(book.id));
  return card;
}

// --- Reading log (session history + streak input) ---

function readingLogSection(book, logs, ctx, rerender) {
  const list = el('div', { class: 'mer-people-list' });
  for (const log of logs.sort((a, b) => (a.date < b.date ? 1 : -1))) {
    list.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: `${log.pagesRead} pages` }),
        el('div', { class: 'mer-person-meta', text: fmtDate(log.date) }),
      ]),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => { await ctx.data.ReadingLogs.remove(log.id); rerender(); },
      }),
    ]));
  }

  const pagesInput = el('input', { type: 'number', min: '1', placeholder: 'Pages read today' });
  const logBtn = el('button', {
    type: 'button', text: 'Log session',
    onclick: async () => {
      const pagesRead = Number(pagesInput.value);
      if (!pagesRead || pagesRead < 1) return;
      await ctx.data.ReadingLogs.create({ bookId: book.id, date: todayStr(), pagesRead });
      const newPage = Math.min(book.totalPages || Infinity, (book.currentPage || 0) + pagesRead);
      await ctx.data.Books.update(book.id, { currentPage: newPage === Infinity ? (book.currentPage || 0) + pagesRead : newPage });
      rerender();
    },
  });

  return el('div', {}, [list, el('div', { class: 'mer-person-form' }, [pagesInput, logBtn])]);
}

// --- Detail editor ---

function detailEditor(book, cover, logs, ctx, rerender, wordsPerPage) {
  const patch = (fields) => ctx.data.Books.update(book.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const titleInput = el('input', { type: 'text', value: book.title || '', onchange: (e) => patch({ title: e.target.value }) });
  const authorInput = el('input', { type: 'text', value: book.author || '', placeholder: 'Author', onchange: (e) => patch({ author: e.target.value }) });
  const genreInput = el('input', { type: 'text', value: book.genre || '', placeholder: 'Genre', onchange: (e) => patch({ genre: e.target.value }) });
  const statusSelect = el('select', {
    onchange: (e) => {
      const fields = { status: e.target.value };
      if (e.target.value === 'reading' && !book.startedDate) fields.startedDate = todayStr();
      if (e.target.value === 'finished' && !book.finishedDate) fields.finishedDate = todayStr();
      patch(fields);
    },
  }, STATUSES.map((s) => el('option', { value: s.value, text: s.label, selected: s.value === book.status })));
  const totalPagesInput = el('input', { type: 'number', min: '0', value: book.totalPages ?? '', placeholder: 'Total pages', onchange: (e) => patch({ totalPages: e.target.value ? Number(e.target.value) : null }) });
  const currentPageInput = el('input', { type: 'number', min: '0', value: book.currentPage ?? '', placeholder: 'Current page', onchange: (e) => patch({ currentPage: e.target.value ? Number(e.target.value) : null }) });
  const startedInput = el('input', { type: 'date', value: book.startedDate || '', onchange: (e) => patch({ startedDate: e.target.value || null }) });
  const finishedInput = el('input', { type: 'date', value: book.finishedDate || '', onchange: (e) => patch({ finishedDate: e.target.value || null }) });
  const notesInput = el('textarea', { rows: '3', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(book.notes || '')]);

  const words = estimatedWords(book.totalPages, wordsPerPage);
  const wordsReadSoFar = estimatedWords(book.currentPage, wordsPerPage);

  const coverPlaceholder = el('p', { class: 'mer-muted', text: 'Loading cover…' });
  const logPlaceholder = el('p', { class: 'mer-muted', text: 'Loading reading log…' });

  const detail = el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: book.title || '(untitled)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
    ]),
    starRating(book.rating, (v) => patch({ rating: v })),
    el('div', { class: 'mer-field-grid' }, [
      field('Title', titleInput),
      field('Author', authorInput),
      field('Genre', genreInput),
      field('Status', statusSelect),
      field('Total pages', totalPagesInput),
      field('Current page', currentPageInput),
      field('Started', startedInput),
      field('Finished', finishedInput),
    ]),
    book.totalPages
      ? el('p', { class: 'mer-muted', text: `Est. ${words.toLocaleString()} words total · ~${wordsReadSoFar.toLocaleString()} read so far` })
      : null,
    field('Notes', notesInput),
    el('div', { class: 'mer-subsection-label', text: 'Cover' }),
    coverPlaceholder,
    el('div', { class: 'mer-subsection-label', text: 'Reading log' }),
    logPlaceholder,
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete book',
      onclick: async () => { await ctx.data.Books.remove(book.id); state.selectedId = null; rerender(); },
    }),
  ]);

  const coverWrap = el('div', {}, [
    cover
      ? el('div', { class: 'mer-photo-thumb' }, [
        el('img', { src: ctx.data.attachmentUrl(cover), alt: book.title }),
        el('button', {
          type: 'button', class: 'mer-photo-remove', text: '×',
          onclick: async () => { await ctx.data.Attachments.remove(cover.id); rerender(); },
        }),
      ])
      : el('label', { class: 'mer-photo-add' }, [
        el('span', { text: '+ Cover' }),
        el('input', {
          type: 'file', accept: 'image/*',
          onchange: async (e) => {
            if (e.target.files[0]) await ctx.data.createAttachment(e.target.files[0], 'books', book.id);
            rerender();
          },
        }),
      ]),
  ]);
  coverPlaceholder.replaceWith(coverWrap);
  logPlaceholder.replaceWith(readingLogSection(book, logs, ctx, rerender));

  return detail;
}

// --- Stats tab: streak, genre breakdown, author tracking ---

function computeStreak(allLogs) {
  const days = new Set(allLogs.map((l) => l.date));
  let streak = 0;
  const cursor = new Date(todayStr() + 'T00:00:00');
  // A missed "today" doesn't break a streak that ran through yesterday —
  // only start counting from today if it's logged, otherwise from yesterday.
  if (!days.has(cursor.toISOString().slice(0, 10))) {
    cursor.setDate(cursor.getDate() - 1);
  }
  while (days.has(cursor.toISOString().slice(0, 10))) {
    streak++;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

function breakdownList(counts) {
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  const list = el('div', { class: 'mer-people-list' });
  for (const [label, count] of sorted) {
    list.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [el('div', { class: 'mer-person-name', text: label })]),
      el('div', { text: String(count) }),
    ]));
  }
  return list;
}

async function renderStats(container, ctx, books, wordsPerPage) {
  const allLogs = await ctx.data.ReadingLogs.list();
  const streak = computeStreak(allLogs);

  const year = new Date().getFullYear().toString();
  const pagesThisYear = allLogs.filter((l) => l.date?.startsWith(year)).reduce((sum, l) => sum + (l.pagesRead || 0), 0);
  const wordsThisYear = estimatedWords(pagesThisYear, wordsPerPage);

  const genreCounts = new Map();
  const authorCounts = new Map();
  for (const b of books) {
    if (b.genre) genreCounts.set(b.genre, (genreCounts.get(b.genre) || 0) + 1);
    if (b.author) authorCounts.set(b.author, (authorCounts.get(b.author) || 0) + 1);
  }

  container.append(
    el('p', {}, [
      el('strong', { text: `${streak}-day reading streak` }),
      el('span', { text: ` · ${pagesThisYear.toLocaleString()} pages read in ${year} (~${wordsThisYear.toLocaleString()} words)` }),
    ]),
    el('div', { class: 'mer-subsection-label', text: 'By genre' }),
    genreCounts.size ? breakdownList(genreCounts) : el('p', { class: 'mer-muted', text: 'No genres tagged yet.' }),
    el('div', { class: 'mer-subsection-label', text: 'By author' }),
    authorCounts.size ? breakdownList(authorCounts) : el('p', { class: 'mer-muted', text: 'No authors tagged yet.' }),
  );
}

// --- Toolbar ---

function tabsBar(rerender) {
  return el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'reading' ? 'is-active' : '', text: 'Reading', onclick: () => { state.tab = 'reading'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'to_read' ? 'is-active' : '', text: 'To Read', onclick: () => { state.tab = 'to_read'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'finished' ? 'is-active' : '', text: 'Finished', onclick: () => { state.tab = 'finished'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'stats' ? 'is-active' : '', text: 'Stats', onclick: () => { state.tab = 'stats'; rerender(); } }),
  ]);
}

export async function renderBooks(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Books' }));

  const wordsPerPage = await ctx.data.Settings.get('wordsPerPageDefault');
  const books = await ctx.data.Books.list();

  const row = el('div', { class: 'mer-toolbar' }, [tabsBar(rerender)]);

  if (state.tab !== 'stats') {
    const quickAdd = el('input', {
      type: 'text', class: 'mer-quick-add', placeholder: '+ New book — type a title and press Enter',
      onkeydown: async (e) => {
        if (e.key !== 'Enter' || !e.target.value.trim()) return;
        await ctx.data.Books.create({ title: e.target.value.trim(), status: state.tab, rating: 0 });
        e.target.value = '';
      },
    });
    row.append(quickAdd);
  }
  canvas.append(row);

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  if (state.tab === 'stats') {
    await renderStats(area, ctx, books, wordsPerPage);
    return;
  }

  const filtered = books.filter((b) => (b.status || 'to_read') === state.tab);
  const onSelect = (id) => { state.selectedId = state.selectedId === id ? null : id; rerender(); };

  if (!filtered.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No books here yet.' }));
  } else {
    const grid = el('div', { class: 'mer-place-grid' });
    area.append(grid);
    const covers = await Promise.all(filtered.map((b) => ctx.data.getAttachmentsFor('books', b.id)));
    filtered.forEach((book, i) => grid.append(bookCard(book, covers[i][0], ctx, onSelect)));
  }

  if (state.selectedId) {
    const book = books.find((b) => b.id === state.selectedId);
    if (book) {
      const [cover, logs] = await Promise.all([
        ctx.data.getAttachmentsFor('books', book.id).then((a) => a[0]),
        ctx.data.ReadingLogs.byIndex('bookId', book.id),
      ]);
      canvas.append(detailEditor(book, cover, logs, ctx, rerender, wordsPerPage));
    }
  }
}

import { el, fmtDate, todayStr } from '../dom.js';
import { openReader, readerCanOpen } from './reader.js';

const STATUSES = [
  { value: 'to_read', label: 'To Read' },
  { value: 'reading', label: 'Reading' },
  { value: 'finished', label: 'Finished' },
];

let state = {
  tab: 'reading', // reading | to_read | finished | shelf | stats
  selectedId: null,
};

// Covers are image attachments; book files (EPUB/PDF/TXT) are everything else.
// This is how one Book record holds both without a schema change.
const isCover = (a) => (a.mimeType || '').startsWith('image/');
const fileIcon = (a) => {
  const n = (a.filename || '').toLowerCase();
  if (a.mimeType === 'application/epub+zip' || n.endsWith('.epub')) return '📗';
  if (a.mimeType === 'application/pdf' || n.endsWith('.pdf')) return '📕';
  if ((a.mimeType || '').startsWith('text/') || /\.(txt|md)$/.test(n)) return '📄';
  return '📎';
};

function downloadAttachment(att) {
  const url = URL.createObjectURL(att.blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = att.filename || 'book-file';
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

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

// --- Book files (EPUB / PDF / TXT) ---

function filesSection(book, files, ctx, rerender) {
  const list = el('div', { class: 'mer-people-list' });
  for (const f of files) {
    const canRead = readerCanOpen(f);
    list.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: `${fileIcon(f)} ${f.filename || '(file)'}` }),
        el('div', { class: 'mer-person-meta', text: f.mimeType || 'file' }),
      ]),
      canRead ? el('button', {
        type: 'button', class: 'mer-play-btn', text: 'Read',
        onclick: () => openReader(f, ctx, {
          startChapter: book.readingLocation?.attachmentId === f.id ? (book.readingLocation.chapter || 0) : 0,
          onLocation: (ch) => ctx.data.Books.update(book.id, { readingLocation: { attachmentId: f.id, chapter: ch } }),
        }),
      }) : null,
      el('button', { type: 'button', text: 'Download', onclick: () => downloadAttachment(f) }),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove file',
        onclick: async () => { if (confirm('Remove this file from the book?')) { await ctx.data.Attachments.remove(f.id); rerender(); } },
      }),
    ]));
  }

  const addLabel = el('label', { class: 'mer-photo-add mer-file-add' }, [
    el('span', { text: '+ Add book file (EPUB / PDF / text)' }),
    el('input', {
      type: 'file', accept: '.epub,.pdf,.txt,.md,application/epub+zip,application/pdf,text/plain',
      onchange: async (e) => {
        const file = e.target.files[0];
        if (file) await ctx.data.createAttachment(file, 'books', book.id);
        rerender();
      },
    }),
  ]);

  return el('div', {}, [files.length ? list : el('p', { class: 'mer-muted', text: 'No files yet — add an EPUB, PDF, or text file to read it right here.' }), addLabel]);
}

// --- Detail editor ---

function detailEditor(book, attachments, logs, ctx, rerender, wordsPerPage) {
  const cover = attachments.find(isCover);
  const files = attachments.filter((a) => !isCover(a));
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
    el('div', { class: 'mer-subsection-label', text: 'Files' }),
    filesSection(book, files, ctx, rerender),
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
  const tab = (id, label) => el('button', {
    type: 'button', class: state.tab === id ? 'is-active' : '', text: label,
    onclick: () => { state.tab = id; rerender(); },
  });
  return el('div', { class: 'mer-toggle-group' }, [
    tab('reading', 'Reading'), tab('to_read', 'To Read'), tab('finished', 'Finished'),
    tab('shelf', 'Shelf'), tab('stats', 'Stats'),
  ]);
}

// --- Bookshelf: books as spines standing on shelves, grouped by status. ---

function hueFromString(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
  return h;
}

function bookSpine(book, onSelect) {
  // Taller spine = longer book (clamped); color keyed to genre or title.
  const pages = book.totalPages || 0;
  const height = Math.max(130, Math.min(230, 130 + pages / 6));
  const hue = hueFromString(book.genre || book.author || book.title || 'book');
  const spine = el('div', {
    class: 'mer-spine',
    style: `height:${Math.round(height)}px; --spine-h:${hue};`,
    title: `${book.title || '(untitled)'}${book.author ? ' — ' + book.author : ''}`,
  }, [
    el('div', { class: 'mer-spine-title', text: book.title || '(untitled)' }),
    book.author ? el('div', { class: 'mer-spine-author', text: book.author }) : null,
  ]);
  spine.addEventListener('click', () => onSelect(book.id));
  return spine;
}

function renderShelf(area, books, onSelect) {
  if (!books.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No books yet — add some and they’ll line up on the shelf.' }));
    return;
  }
  for (const status of STATUSES) {
    const shelfBooks = books.filter((b) => (b.status || 'to_read') === status.value);
    if (!shelfBooks.length) continue;
    area.append(el('div', { class: 'mer-subsection-label', text: status.label }));
    const shelf = el('div', { class: 'mer-shelf' },
      shelfBooks.map((b) => bookSpine(b, onSelect)));
    area.append(el('div', { class: 'mer-shelf-wrap' }, [shelf]));
  }
}

export async function renderBooks(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Books' }));

  const wordsPerPage = await ctx.data.Settings.get('wordsPerPageDefault');
  const books = await ctx.data.Books.list();

  const row = el('div', { class: 'mer-toolbar' }, [tabsBar(rerender)]);

  const isStatusTab = ['reading', 'to_read', 'finished'].includes(state.tab);
  if (isStatusTab) {
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

  const onSelect = (id) => { state.selectedId = state.selectedId === id ? null : id; rerender(); };

  if (state.tab === 'stats') {
    await renderStats(area, ctx, books, wordsPerPage);
    return;
  }

  if (state.tab === 'shelf') {
    renderShelf(area, books, onSelect);
  } else {
    const filtered = books.filter((b) => (b.status || 'to_read') === state.tab);
    if (!filtered.length) {
      area.append(el('p', { class: 'mer-muted', text: 'No books here yet.' }));
    } else {
      const grid = el('div', { class: 'mer-place-grid' });
      area.append(grid);
      const attsList = await Promise.all(filtered.map((b) => ctx.data.getAttachmentsFor('books', b.id)));
      filtered.forEach((book, i) => grid.append(bookCard(book, attsList[i].find(isCover), ctx, onSelect)));
    }
  }

  if (state.selectedId) {
    const book = books.find((b) => b.id === state.selectedId);
    if (book) {
      const [attachments, logs] = await Promise.all([
        ctx.data.getAttachmentsFor('books', book.id),
        ctx.data.ReadingLogs.byIndex('bookId', book.id),
      ]);
      canvas.append(detailEditor(book, attachments, logs, ctx, rerender, wordsPerPage));
    }
  }
}

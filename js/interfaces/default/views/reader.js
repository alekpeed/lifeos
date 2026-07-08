// In-app document reader overlay. Opens EPUB (paginated by chapter, with a
// table of contents, font sizing, and a day/night surface), PDF (the browser's
// own native viewer via a blob URL), and plain text. A full-screen modal that
// manages its own lifecycle; call openReader(attachment, ctx, opts).
//
// opts: { startChapter, onLocation(chapterIndex) } — onLocation fires on every
// chapter change and on close, so the caller can persist the reading position.

import { el } from '../dom.js';
import { parseEpub } from '../../../reader/epub.js';

function isEpub(att) {
  return att.mimeType === 'application/epub+zip' || /\.epub$/i.test(att.filename || '');
}
function isPdf(att) {
  return att.mimeType === 'application/pdf' || /\.pdf$/i.test(att.filename || '');
}
function isText(att) {
  return (att.mimeType || '').startsWith('text/') || /\.(txt|md)$/i.test(att.filename || '');
}

export function readerCanOpen(att) {
  return isEpub(att) || isPdf(att) || isText(att);
}

export async function openReader(attachment, ctx, opts = {}) {
  const objectUrls = [];
  const overlay = el('div', { class: 'mer-reader' });
  const body = el('div', { class: 'mer-reader-body' });
  const titleEl = el('div', { class: 'mer-reader-title', text: attachment.filename || 'Reading' });
  const controls = el('div', { class: 'mer-reader-controls' });

  let lastChapter = opts.startChapter || 0;
  const close = () => {
    objectUrls.forEach((u) => URL.revokeObjectURL(u));
    window.removeEventListener('keydown', onKey);
    overlay.remove();
    if (opts.onLocation) opts.onLocation(lastChapter);
  };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  window.addEventListener('keydown', onKey);

  const bar = el('div', { class: 'mer-reader-bar' }, [
    titleEl,
    controls,
    el('button', { type: 'button', class: 'mer-reader-close', text: '✕', title: 'Close (Esc)', onclick: close }),
  ]);
  overlay.append(bar, body);
  document.body.append(overlay);

  try {
    if (isEpub(attachment)) {
      await mountEpub(attachment, body, controls, titleEl, opts, (i) => { lastChapter = i; });
    } else if (isPdf(attachment)) {
      mountPdf(attachment, body, objectUrls);
    } else if (isText(attachment)) {
      await mountText(attachment, body);
    } else {
      body.append(el('p', { class: 'mer-muted', text: 'This file type can’t be read in-app. Use Download instead.' }));
    }
  } catch (err) {
    body.append(el('p', { class: 'mer-sync-error', text: `Couldn’t open this file: ${err.message}` }));
  }
}

// --- EPUB ---

async function mountEpub(attachment, body, controls, titleEl, opts, reportLocation) {
  const buf = await attachment.blob.arrayBuffer();
  const epub = parseEpub(buf);
  titleEl.textContent = epub.title + (epub.author ? ` — ${epub.author}` : '');

  const view = { index: Math.min(opts.startChapter || 0, epub.chapters.length - 1), night: false, fontScale: 1 };
  const iframe = el('iframe', { class: 'mer-reader-frame', sandbox: 'allow-same-origin' });

  const posLabel = el('span', { class: 'mer-reader-pos' });
  const render = () => {
    iframe.srcdoc = epub.renderChapter(view.index, { night: view.night, fontScale: view.fontScale });
    posLabel.textContent = `${view.index + 1} / ${epub.chapters.length}`;
    reportLocation(view.index);
  };
  const go = (i) => {
    view.index = Math.max(0, Math.min(epub.chapters.length - 1, i));
    render();
    body.scrollTop = 0;
  };

  // Intercept in-book links once each chapter loads (same-origin, no scripts).
  iframe.addEventListener('load', () => {
    try {
      const doc = iframe.contentDocument;
      if (doc) doc.defaultView.scrollTo(0, 0);
      for (const a of doc.querySelectorAll('a[href]')) {
        a.addEventListener('click', (e) => {
          const target = epub.locate(view.index, a.getAttribute('href'));
          if (target != null) { e.preventDefault(); go(target); }
        });
      }
    } catch { /* cross-origin guard — ignore */ }
  });

  // TOC drawer
  const tocDrawer = el('div', { class: 'mer-reader-toc' });
  const buildToc = () => {
    tocDrawer.replaceChildren(el('div', { class: 'mer-subsection-label', text: 'Contents' }));
    const entries = epub.toc.length ? epub.toc : epub.chapters.map((c, i) => ({ label: `Chapter ${i + 1}`, chapterIndex: i }));
    for (const t of entries) {
      tocDrawer.append(el('button', {
        type: 'button', class: 'mer-reader-toc-item',
        text: t.label, onclick: () => { go(t.chapterIndex); tocDrawer.classList.remove('is-open'); },
      }));
    }
  };
  buildToc();

  controls.append(
    el('button', { type: 'button', class: 'mer-reader-btn', text: '☰ Contents', onclick: () => tocDrawer.classList.toggle('is-open') }),
    el('button', { type: 'button', class: 'mer-reader-btn', text: 'A−', title: 'Smaller text', onclick: () => { view.fontScale = Math.max(0.7, view.fontScale - 0.1); render(); } }),
    el('button', { type: 'button', class: 'mer-reader-btn', text: 'A+', title: 'Larger text', onclick: () => { view.fontScale = Math.min(1.8, view.fontScale + 0.1); render(); } }),
    el('button', { type: 'button', class: 'mer-reader-btn', text: '🌓', title: 'Day / night', onclick: () => { view.night = !view.night; render(); } }),
    posLabel,
  );

  const prev = el('button', { type: 'button', class: 'mer-reader-nav mer-reader-prev', text: '‹', title: 'Previous chapter', onclick: () => go(view.index - 1) });
  const next = el('button', { type: 'button', class: 'mer-reader-nav mer-reader-next', text: '›', title: 'Next chapter', onclick: () => go(view.index + 1) });
  body.append(tocDrawer, prev, iframe, next);
  render();
}

// --- PDF: the browser's built-in viewer (desktop). ---

function mountPdf(attachment, body, objectUrls) {
  const url = URL.createObjectURL(attachment.blob);
  objectUrls.push(url);
  const frame = el('iframe', { class: 'mer-reader-frame mer-reader-pdf', src: url, title: attachment.filename || 'PDF' });
  const note = el('p', { class: 'mer-muted mer-reader-pdfnote', text: 'If the PDF doesn’t display (some phones don’t embed PDFs), use the Download button on the book instead.' });
  body.append(frame, note);
}

// --- Plain text ---

async function mountText(attachment, body) {
  const text = await attachment.blob.text();
  body.append(el('pre', { class: 'mer-reader-text', text }));
}

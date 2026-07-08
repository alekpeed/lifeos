// Minimal in-app EPUB reader engine. An EPUB is a ZIP of XHTML chapters plus
// a manifest (OPF) and reading order (spine); this unzips it (via the vendored
// fflate), follows container.xml → OPF → spine, builds a table of contents,
// and renders each chapter to a single self-contained HTML string — images
// inlined as data URLs and stylesheets inlined — so a chapter can be dropped
// straight into a sandboxed iframe with no external fetches and no scripts.
//
// Deliberately small and dependency-light (fflate is ~89KB, the only add): it
// handles text + images + the book's own CSS, which covers the vast majority
// of real books. It is not a full EPUB engine — embedded fonts referenced from
// CSS url() and audio/video are not resolved. Good enough to actually read.

import { unzipSync, strFromU8 } from '../../vendor/fflate/fflate.module.js';

// --- path helpers (exported for unit testing; no DOM needed) ---

export function dirname(p) {
  const i = p.lastIndexOf('/');
  return i < 0 ? '' : p.slice(0, i);
}

// Resolve a (possibly ../-laden, possibly %-encoded) href against a base dir,
// returning a normalized zip-entry path (no leading slash, decoded).
export function resolvePath(baseDir, href) {
  let rel = href.split('#')[0].split('?')[0];
  try { rel = decodeURIComponent(rel); } catch { /* leave as-is if malformed */ }
  if (!rel) return '';
  const absolute = rel.startsWith('/');
  const stack = absolute || !baseDir ? [] : baseDir.split('/');
  for (const part of rel.split('/')) {
    if (part === '' || part === '.') continue;
    if (part === '..') stack.pop();
    else stack.push(part);
  }
  return stack.join('/');
}

const EXT_MIME = {
  jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif',
  svg: 'image/svg+xml', webp: 'image/webp', css: 'text/css',
  xhtml: 'application/xhtml+xml', html: 'text/html', otf: 'font/otf', ttf: 'font/ttf',
};
export function mimeForPath(path) {
  return EXT_MIME[path.split('.').pop().toLowerCase()] || 'application/octet-stream';
}

// --- base64 / data URLs (browser btoa; chunked for large images) ---

function bytesToDataUrl(bytes, mime) {
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return `data:${mime};base64,${btoa(binary)}`;
}

// --- parsing ---

function xml(text, type = 'application/xml') {
  return new DOMParser().parseFromString(text, type);
}

// Find every element with a given localName (namespace-agnostic — EPUB mixes
// dc:, opf:, and default namespaces freely).
function byLocal(root, local) {
  return [...root.getElementsByTagName('*')].filter((e) => e.localName === local);
}

export function parseEpub(arrayBuffer) {
  const files = unzipSync(new Uint8Array(arrayBuffer));
  const has = (p) => Object.prototype.hasOwnProperty.call(files, p);
  const text = (p) => (has(p) ? strFromU8(files[p]) : null);

  // 1. container.xml → OPF path
  const containerXml = text('META-INF/container.xml');
  if (!containerXml) throw new Error('Not a valid EPUB (missing container.xml).');
  const rootfile = byLocal(xml(containerXml), 'rootfile')[0];
  const opfPath = rootfile && rootfile.getAttribute('full-path');
  if (!opfPath || !has(opfPath)) throw new Error('EPUB is missing its content manifest.');
  const opfDir = dirname(opfPath);

  // 2. OPF: metadata, manifest, spine
  const opf = xml(text(opfPath));
  const title = (byLocal(opf, 'title')[0]?.textContent || '').trim() || 'Untitled';
  const author = (byLocal(opf, 'creator')[0]?.textContent || '').trim();

  const manifestById = new Map();
  const mimeByPath = new Map();
  for (const item of byLocal(opf, 'item')) {
    const id = item.getAttribute('id');
    const path = resolvePath(opfDir, item.getAttribute('href') || '');
    const entry = { id, path, mediaType: item.getAttribute('media-type') || mimeForPath(path), properties: item.getAttribute('properties') || '' };
    manifestById.set(id, entry);
    mimeByPath.set(path, entry.mediaType);
  }

  const spineEl = byLocal(opf, 'spine')[0];
  const chapters = byLocal(spineEl, 'itemref')
    .map((ref) => manifestById.get(ref.getAttribute('idref')))
    .filter((e) => e && has(e.path));
  if (!chapters.length) throw new Error('EPUB has no readable chapters.');
  const chapterIndexByPath = new Map(chapters.map((c, i) => [c.path, i]));

  // 3. Table of contents: EPUB3 nav doc, else EPUB2 NCX.
  const toc = buildToc({ opf, opfDir, spineEl, manifestById, text, has, chapterIndexByPath });

  // 4. Per-chapter renderer.
  function renderChapter(index, opts = {}) {
    const chapter = chapters[index];
    const chapterDir = dirname(chapter.path);
    const raw = text(chapter.path) || '';
    let doc = xml(raw, 'application/xhtml+xml');
    if (doc.getElementsByTagName('parsererror').length) doc = xml(raw, 'text/html');

    // Inline images (img[src], SVG image[href|xlink:href]) as data URLs.
    for (const img of [...doc.getElementsByTagName('img')]) {
      rewriteResource(img, 'src', chapterDir, files, mimeByPath, has);
    }
    for (const image of [...doc.getElementsByTagName('image')]) {
      const attr = image.hasAttribute('href') ? 'href' : 'xlink:href';
      rewriteResource(image, attr, chapterDir, files, mimeByPath, has);
    }

    // Collect the book's own CSS: inline <style> plus linked stylesheets.
    let css = '';
    for (const style of [...doc.getElementsByTagName('style')]) css += `\n${style.textContent}`;
    for (const link of [...doc.getElementsByTagName('link')]) {
      if ((link.getAttribute('rel') || '').includes('stylesheet')) {
        const cssPath = resolvePath(chapterDir, link.getAttribute('href') || '');
        if (has(cssPath)) css += `\n${strFromU8(files[cssPath])}`;
      }
    }

    const body = doc.getElementsByTagName('body')[0];
    const inner = body ? body.innerHTML : raw;
    return wrapChapter(inner, css, opts);
  }

  // Map an internal link (relative to the chapter it was clicked from) to a
  // spine index, so the reader can follow in-book links. Null if external or
  // unresolvable.
  function locate(fromIndex, href) {
    if (!href || /^(https?:|mailto:|data:)/.test(href)) return null;
    const base = dirname(chapters[fromIndex].path);
    const idx = chapterIndexByPath.get(resolvePath(base, href));
    return idx == null ? null : idx;
  }

  return { title, author, chapters, toc, renderChapter, locate };
}

function rewriteResource(node, attr, baseDir, files, mimeByPath, has) {
  const ref = node.getAttribute(attr);
  if (!ref || /^(data:|https?:)/.test(ref)) return;
  const path = resolvePath(baseDir, ref);
  if (!has(path)) return;
  const mime = mimeByPath.get(path) || mimeForPath(path);
  node.setAttribute(attr, bytesToDataUrl(files[path], mime));
}

function buildToc({ opf, opfDir, spineEl, manifestById, text, has, chapterIndexByPath }) {
  const out = [];
  const push = (label, href, baseDir) => {
    label = (label || '').trim();
    if (!label || !href) return;
    const idx = chapterIndexByPath.get(resolvePath(baseDir, href));
    if (idx != null) out.push({ label, chapterIndex: idx });
  };

  // EPUB3 nav
  const navItem = [...manifestById.values()].find((e) => e.properties.split(/\s+/).includes('nav'));
  if (navItem && has(navItem.path)) {
    const navDir = dirname(navItem.path);
    const navDoc = xml(text(navItem.path), 'application/xhtml+xml');
    const navs = byLocal(navDoc, 'nav');
    const tocNav = navs.find((n) => (n.getAttribute('epub:type') || n.getAttributeNS('http://www.idpf.org/2007/ops', 'type') || '').includes('toc')) || navs[0];
    if (tocNav) {
      for (const a of byLocal(tocNav, 'a')) push(a.textContent, a.getAttribute('href'), navDir);
    }
    if (out.length) return out;
  }

  // EPUB2 NCX (spine[toc] → manifest item)
  const ncxId = spineEl && spineEl.getAttribute('toc');
  const ncxItem = ncxId && manifestById.get(ncxId);
  if (ncxItem && has(ncxItem.path)) {
    const ncxDir = dirname(ncxItem.path);
    const ncx = xml(text(ncxItem.path));
    for (const np of byLocal(ncx, 'navPoint')) {
      const label = byLocal(np, 'text')[0]?.textContent;
      const src = byLocal(np, 'content')[0]?.getAttribute('src');
      push(label, src, ncxDir);
    }
  }
  return out;
}

// Wrap a chapter's body HTML in a self-contained, readable document. `opts`:
// { night: bool, fontScale: number (1 = 100%) }.
function wrapChapter(innerHtml, bookCss, opts = {}) {
  const night = !!opts.night;
  const fontScale = opts.fontScale || 1;
  const surface = night ? '#16181d' : '#faf8f2';
  const ink = night ? '#d7d3c8' : '#211d16';
  const linkc = night ? '#8fb7d6' : '#3a5a86';
  // Three cascade layers, in order: (1) our readable base, (2) the book's own
  // CSS (may refine typography), (3) a night-mode override LAST so its
  // !important colour rules win ties against book CSS and stay legible.
  const base = `
    html { -webkit-text-size-adjust: 100%; }
    body {
      margin: 0 auto; max-width: 40rem; padding: 2.2rem 1.4rem 6rem;
      background: ${surface}; color: ${ink};
      font-family: Georgia, 'Iowan Old Style', 'Times New Roman', serif;
      font-size: ${Math.round(19 * fontScale)}px; line-height: 1.62;
    }
    img, image, svg { max-width: 100% !important; height: auto !important; }
    a { color: ${linkc}; }
    h1, h2, h3, h4 { line-height: 1.25; }`;
  const nightOverride = night
    ? `body, body * { color: ${ink} !important; background-color: transparent !important; }
       body { background: ${surface} !important; }
       a { color: ${linkc} !important; }`
    : '';
  return `<!doctype html><html><head><meta charset="utf-8">
<style>${base}</style>
<style>${bookCss}</style>
<style>${nightOverride}</style>
</head><body>${innerHtml}</body></html>`;
}

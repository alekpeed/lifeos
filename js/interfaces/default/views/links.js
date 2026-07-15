import { el, parseTags, hostnameOf } from '../dom.js';
import { shareContent, canShare } from '../../../native/share.js';

let state = {
  tab: 'video', // video | article
  showDone: false,
  selectedId: null,
};

// Supports youtube.com/watch?v=, youtu.be/, youtube.com/embed/, /shorts/.
function parseYouTubeId(url) {
  try {
    const u = new URL(url);
    if (u.hostname === 'youtu.be') return u.pathname.slice(1).split('/')[0] || null;
    if (u.hostname.endsWith('youtube.com')) {
      if (u.searchParams.get('v')) return u.searchParams.get('v');
      const match = u.pathname.match(/\/(embed|shorts)\/([^/?]+)/);
      if (match) return match[2];
    }
  } catch {
    // not a valid URL — fall through
  }
  return null;
}

function cardFor(link, ctx, onSelect) {
  const thumb = link.type === 'video' && link.thumbnailUrl
    ? el('img', { class: 'mer-place-photo', src: link.thumbnailUrl, alt: link.title || link.url })
    : el('div', { class: 'mer-place-photo mer-place-photo-empty mer-link-icon', text: link.type === 'video' ? '▶' : '📄' });

  const card = el('div', { class: 'mer-place-card' }, [
    thumb,
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: link.title || hostnameOf(link.url) }),
      el('div', { class: 'mer-place-meta' }, [
        el('span', { class: 'mer-chip', text: hostnameOf(link.url) }),
        link.status === 'done'
          ? el('span', { class: 'mer-chip', text: link.type === 'video' ? 'Watched' : 'Read' })
          : null,
        link.shareWith ? el('span', { class: 'mer-chip', text: `Share → ${link.shareWith}` }) : null,
        ...(link.tags || []).map((t) => el('span', { class: 'mer-chip mer-chip-tag', text: `#${t}` })),
      ]),
    ]),
  ]);
  card.addEventListener('click', () => onSelect(link.id));
  return card;
}

function detailEditor(link, ctx, rerender) {
  const patch = (fields) => ctx.data.Links.update(link.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const titleInput = el('input', { type: 'text', value: link.title || '', placeholder: '(untitled)', onchange: (e) => patch({ title: e.target.value }) });
  const tagsInput = el('input', { type: 'text', value: (link.tags || []).join(', '), placeholder: 'comma, separated, tags', onchange: (e) => patch({ tags: parseTags(e.target.value) }) });
  const shareWithInput = el('input', { type: 'text', value: link.shareWith || '', placeholder: 'Who is this for?', onchange: (e) => patch({ shareWith: e.target.value }) });

  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: link.title || hostnameOf(link.url) }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Title', titleInput),
      field('Tags', tagsInput),
      field('Share with', shareWithInput),
    ]),
    el('p', {}, [el('a', { href: link.url, target: '_blank', rel: 'noopener', text: link.url })]),
    ...(canShare() ? [el('button', {
      type: 'button', class: 'mer-reader-btn', text: '↗ Share',
      onclick: () => shareContent({ title: link.title || hostnameOf(link.url), text: link.title || '', url: link.url }),
    })] : []),
    el('label', { class: 'mer-checkbox-label' }, [
      el('input', {
        type: 'checkbox', checked: link.status === 'done',
        onchange: (e) => patch({ status: e.target.checked ? 'done' : 'unread' }),
      }),
      el('span', { text: link.type === 'video' ? 'Watched' : 'Read' }),
    ]),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete link',
      onclick: async () => { await ctx.data.Links.remove(link.id); state.selectedId = null; rerender(); },
    }),
  ]);
}

function toolbar(ctx, rerender) {
  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add',
    placeholder: state.tab === 'video' ? '+ Paste a YouTube link and press Enter' : '+ Paste an article link and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      const url = e.target.value.trim();
      const videoId = state.tab === 'video' ? parseYouTubeId(url) : null;
      await ctx.data.Links.create({
        type: state.tab,
        url,
        title: '',
        tags: [],
        status: 'unread',
        shareWith: '',
        thumbnailUrl: videoId ? `https://img.youtube.com/vi/${videoId}/hqdefault.jpg` : null,
      });
      e.target.value = '';
      rerender();
    },
  });

  const tabsBar = el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'video' ? 'is-active' : '', text: 'YouTube', onclick: () => { state.tab = 'video'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'article' ? 'is-active' : '', text: 'Articles', onclick: () => { state.tab = 'article'; rerender(); } }),
  ]);

  const doneToggle = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: state.showDone, onchange: (e) => { state.showDone = e.target.checked; rerender(); } }),
    el('span', { text: state.tab === 'video' ? 'Show watched' : 'Show read' }),
  ]);

  return el('div', { class: 'mer-toolbar' }, [tabsBar, quickAdd, doneToggle]);
}

export async function renderLinks(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Links' }));
  canvas.append(toolbar(ctx, rerender));

  const all = await ctx.data.Links.list();
  const filtered = all
    .filter((l) => l.type === state.tab)
    .filter((l) => state.showDone || l.status !== 'done');

  const area = el('div', { class: 'mer-place-grid' });
  canvas.append(area);

  const onSelect = (id) => { state.selectedId = state.selectedId === id ? null : id; rerender(); };

  if (!filtered.length) {
    area.replaceWith(el('p', { class: 'mer-muted', text: state.tab === 'video' ? 'No videos saved yet.' : 'No articles saved yet.' }));
  } else {
    for (const link of filtered) area.append(cardFor(link, ctx, onSelect));
  }

  if (state.selectedId) {
    const link = all.find((l) => l.id === state.selectedId);
    if (link) canvas.append(detailEditor(link, ctx, rerender));
  }
}

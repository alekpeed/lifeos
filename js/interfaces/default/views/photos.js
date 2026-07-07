import { el } from '../dom.js';

let state = {
  selectedAlbumId: null,
  lightboxIndex: null,
};

// --- Albums list ---

function albumCard(album, cover, ctx, onSelect) {
  const thumb = cover
    ? el('img', { class: 'mer-place-photo', src: ctx.data.attachmentUrl(cover), alt: album.name })
    : el('div', { class: 'mer-place-photo mer-place-photo-empty mer-link-icon', text: '🖼' });

  const card = el('div', { class: 'mer-place-card' }, [
    thumb,
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: album.name || '(untitled album)' }),
    ]),
  ]);
  card.addEventListener('click', () => onSelect(album.id));
  return card;
}

async function renderAlbumsList(container, ctx, rerender) {
  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New album — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Albums.create({ name: e.target.value.trim() });
      e.target.value = '';
    },
  });
  container.append(el('div', { class: 'mer-toolbar' }, [quickAdd]));

  const albums = await ctx.data.Albums.list();
  const area = el('div', { class: 'mer-task-list-area' });
  container.append(area);

  if (!albums.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No albums yet.' }));
    return;
  }

  const grid = el('div', { class: 'mer-place-grid' });
  area.append(grid);
  const covers = await Promise.all(albums.map((a) => ctx.data.getAttachmentsFor('albums', a.id)));
  albums.forEach((album, i) => grid.append(albumCard(album, covers[i][0], ctx, (id) => { state.selectedAlbumId = id; rerender(); })));
}

// --- Lightbox ---

function lightbox(photos, ctx, rerender) {
  const photo = photos[state.lightboxIndex];
  const overlay = el('div', { class: 'mer-lightbox-overlay' }, [
    el('button', { type: 'button', class: 'mer-lightbox-close', text: '✕', onclick: () => { state.lightboxIndex = null; rerender(); } }),
    el('button', {
      type: 'button', class: 'mer-lightbox-nav mer-lightbox-prev', text: '‹',
      onclick: () => { state.lightboxIndex = (state.lightboxIndex - 1 + photos.length) % photos.length; rerender(); },
    }),
    el('img', { class: 'mer-lightbox-img', src: ctx.data.attachmentUrl(photo), alt: photo.filename }),
    el('button', {
      type: 'button', class: 'mer-lightbox-nav mer-lightbox-next', text: '›',
      onclick: () => { state.lightboxIndex = (state.lightboxIndex + 1) % photos.length; rerender(); },
    }),
  ]);
  overlay.addEventListener('click', (e) => { if (e.target === overlay) { state.lightboxIndex = null; rerender(); } });
  return overlay;
}

// --- Album detail (photo grid) ---

async function renderAlbumDetail(container, ctx, rerender) {
  const album = await ctx.data.Albums.get(state.selectedAlbumId);
  if (!album) { state.selectedAlbumId = null; rerender(); return; }

  const toolbar = el('div', { class: 'mer-toolbar' }, [
    el('button', { type: 'button', text: '← Albums', onclick: () => { state.selectedAlbumId = null; rerender(); } }),
    el('h1', { text: album.name || '(untitled album)', style: 'flex:1;margin:0' }),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete album',
      onclick: async () => {
        const photos = await ctx.data.getAttachmentsFor('albums', album.id);
        for (const p of photos) await ctx.data.Attachments.remove(p.id);
        await ctx.data.Albums.remove(album.id);
        state.selectedAlbumId = null;
        rerender();
      },
    }),
  ]);
  container.append(toolbar);

  const photos = await ctx.data.getAttachmentsFor('albums', album.id);
  const grid = el('div', { class: 'mer-photo-grid' });
  photos.forEach((photo, i) => {
    grid.append(el('div', { class: 'mer-photo-thumb' }, [
      el('img', {
        src: ctx.data.attachmentUrl(photo), alt: photo.filename,
        onclick: () => { state.lightboxIndex = i; rerender(); },
      }),
      el('button', {
        type: 'button', class: 'mer-photo-remove', text: '×',
        onclick: async () => { await ctx.data.Attachments.remove(photo.id); rerender(); },
      }),
    ]));
  });
  grid.append(el('label', { class: 'mer-photo-add' }, [
    el('span', { text: '+ Add photos' }),
    el('input', {
      type: 'file', accept: 'image/*', multiple: true,
      onchange: async (e) => {
        for (const file of e.target.files) await ctx.data.createAttachment(file, 'albums', album.id);
        rerender();
      },
    }),
  ]));
  container.append(grid);

  if (state.lightboxIndex !== null && photos[state.lightboxIndex]) {
    container.append(lightbox(photos, ctx, rerender));
  }
}

export async function renderPhotos(canvas, ctx, rerender) {
  if (state.selectedAlbumId) {
    await renderAlbumDetail(canvas, ctx, rerender);
  } else {
    canvas.append(el('h1', { text: 'Photos' }));
    await renderAlbumsList(canvas, ctx, rerender);
  }
}

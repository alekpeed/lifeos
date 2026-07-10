// Theme-from-Photo — pick a photo (from an existing album, or upload one
// fresh) and extract a small accent palette from it, then apply one swatch
// as the app's accent color. Pure canvas pixel sampling, no library: draw the
// image small, bucket-quantize colors, take the most frequent buckets.
//
// Applying a swatch sets Settings.accent = 'custom' + Settings.customAccent
// (hex color pair), which js/shell.js's applyPreferences() reads and applies
// as an inline CSS override on top of the normal brass/teal/garnet presets.

import { el } from '../dom.js';

let state = { imageUrl: null, palette: [] };

function quantizePalette(img, swatchCount = 5) {
  const size = 48; // small sample is plenty for dominant-color purposes and keeps this instant
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const g = canvas.getContext('2d');
  g.drawImage(img, 0, 0, size, size);
  const { data } = g.getImageData(0, 0, size, size);

  const buckets = new Map(); // "r,g,b" (rounded to nearest 32) -> count
  for (let i = 0; i < data.length; i += 4) {
    const alpha = data[i + 3];
    if (alpha < 200) continue; // skip near-transparent pixels
    const r = Math.round(data[i] / 32) * 32;
    const gc = Math.round(data[i + 1] / 32) * 32;
    const b = Math.round(data[i + 2] / 32) * 32;
    const key = `${r},${gc},${b}`;
    buckets.set(key, (buckets.get(key) || 0) + 1);
  }

  return [...buckets.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, swatchCount)
    .map(([key]) => {
      const [r, g2, b2] = key.split(',').map(Number);
      return rgbToHex(r, g2, b2);
    });
}

function rgbToHex(r, g, b) {
  return '#' + [r, g, b].map((c) => Math.min(255, c).toString(16).padStart(2, '0')).join('');
}

// A "strong" variant for hover/emphasis states, mirroring the built-in accent
// pairs (e.g. --accent-brass / --accent-brass-strong): same hue, lifted
// lightness, via a quick RGB->HSL->RGB round trip.
function strongVariant(hex) {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  let h = 0, s = 0;
  const l = (max + min) / 2;
  const d = max - min;
  if (d !== 0) {
    s = d / (1 - Math.abs(2 * l - 1));
    switch (max) {
      case r: h = ((g - b) / d) % 6; break;
      case g: h = (b - r) / d + 2; break;
      default: h = (r - g) / d + 4; break;
    }
    h *= 60;
    if (h < 0) h += 360;
  }
  const lLifted = Math.min(0.85, l + 0.15);
  const c = (1 - Math.abs(2 * lLifted - 1)) * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = lLifted - c / 2;
  let [r2, g2, b2] = [0, 0, 0];
  if (h < 60) [r2, g2, b2] = [c, x, 0];
  else if (h < 120) [r2, g2, b2] = [x, c, 0];
  else if (h < 180) [r2, g2, b2] = [0, c, x];
  else if (h < 240) [r2, g2, b2] = [0, x, c];
  else if (h < 300) [r2, g2, b2] = [x, 0, c];
  else [r2, g2, b2] = [c, 0, x];
  return rgbToHex(Math.round((r2 + m) * 255), Math.round((g2 + m) * 255), Math.round((b2 + m) * 255));
}

function swatch(hex, ctx, rerender) {
  return el('button', {
    type: 'button', class: 'mer-swatch', style: `background: ${hex}`, title: hex,
    onclick: async () => {
      await ctx.data.Settings.set('accent', 'custom');
      await ctx.data.Settings.set('customAccent', { accent: hex, accentStrong: strongVariant(hex) });
      rerender();
    },
  });
}

async function loadFromFile(file, rerender) {
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.onload = () => {
    state.imageUrl = url;
    state.palette = quantizePalette(img);
    rerender();
  };
  img.src = url;
}

export async function renderThemeFromPhoto(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Theme from Photo' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Pick a photo and pull an accent color out of it.' }));

  const fileIn = el('input', { type: 'file', accept: 'image/*' });
  fileIn.addEventListener('change', () => {
    const file = fileIn.files[0];
    if (file) loadFromFile(file, rerender);
  });
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Upload a photo' }));
  canvas.append(el('div', { class: 'mer-person-form' }, [fileIn]));

  const albums = await ctx.data.Albums.list();
  if (albums.length) {
    const photoLists = await Promise.all(albums.map((a) => ctx.data.getAttachmentsFor('albums', a.id)));
    const allPhotos = photoLists.flat();
    if (allPhotos.length) {
      canvas.append(el('div', { class: 'mer-subsection-label', text: 'Or pick from your gallery' }));
      const grid = el('div', { class: 'mer-place-grid' });
      for (const photo of allPhotos.slice(0, 24)) {
        const thumb = el('img', { class: 'mer-place-photo', src: ctx.data.attachmentUrl(photo), alt: photo.filename || 'photo' });
        thumb.style.cursor = 'pointer';
        thumb.addEventListener('click', () => {
          const img = new Image();
          img.crossOrigin = 'anonymous';
          img.onload = () => {
            state.imageUrl = thumb.src;
            state.palette = quantizePalette(img);
            rerender();
          };
          img.src = thumb.src;
        });
        grid.append(thumb);
      }
      canvas.append(grid);
    }
  }

  if (state.imageUrl) {
    canvas.append(el('div', { class: 'mer-subsection-label', text: 'Palette — click one to apply as your accent' }));
    canvas.append(el('img', { class: 'mer-themephoto-preview', src: state.imageUrl, alt: 'Selected photo' }));
    const row = el('div', { class: 'mer-swatch-row' });
    for (const hex of state.palette) row.append(swatch(hex, ctx, rerender));
    canvas.append(row);
  }

  const accent = await ctx.data.Settings.get('accent');
  if (accent === 'custom') {
    canvas.append(el('p', { class: 'mer-muted', text: 'A custom photo accent is currently active. Pick brass/teal/garnet in Settings to go back to a built-in preset.' }));
  }
}

import { el, fmtDate, parseTags } from '../dom.js';
import { loadLeaflet } from '../leaflet-loader.js';

let state = {
  tab: 'visited', // visited | wantToGo | map | bucket
  categoryFilter: 'all',
  selectedPlaceId: null,
  nearbyNudges: null, // null = not checked yet; [] = checked, nothing nearby; [...] = results
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

// --- Photos ---

function photoGrid(place, ctx, photos, rerender) {
  const grid = el('div', { class: 'mer-photo-grid' });
  for (const photo of photos) {
    grid.append(el('div', { class: 'mer-photo-thumb' }, [
      el('img', { src: ctx.data.attachmentUrl(photo), alt: photo.filename }),
      el('button', {
        type: 'button', class: 'mer-photo-remove', text: '×',
        onclick: async () => { await ctx.data.Attachments.remove(photo.id); rerender(); },
      }),
    ]));
  }
  const uploadInput = el('input', {
    type: 'file', accept: 'image/*', multiple: true,
    onchange: async (e) => {
      for (const file of e.target.files) {
        await ctx.data.createAttachment(file, 'places', place.id);
      }
      rerender();
    },
  });
  grid.append(el('label', { class: 'mer-photo-add' }, [uploadInput, el('span', { text: '+ Add photo' })]));
  return grid;
}

// --- Linked people (linked to real Contacts records — see Contacts module) ---

function linkContactForm(place, allContacts, ctx, rerender) {
  const linkedIds = new Set(place.peopleIds || []);
  const available = allContacts.filter((c) => !linkedIds.has(c.id));

  const contactSelect = el('select', {
    onchange: async (e) => {
      if (!e.target.value) return;
      await ctx.data.Places.update(place.id, { peopleIds: [...(place.peopleIds || []), e.target.value] });
      rerender();
    },
  }, [
    el('option', { value: '', text: available.length ? 'Link an existing contact…' : '(no other contacts yet)' }),
    ...available.map((c) => el('option', { value: c.id, text: c.name || '(untitled)' })),
  ]);

  const newNameInput = el('input', { type: 'text', placeholder: 'Or type a new name…' });
  const addBtn = el('button', {
    type: 'button', text: 'Add as new contact',
    onclick: async () => {
      if (!newNameInput.value.trim()) return;
      const contact = await ctx.data.Contacts.create({ name: newNameInput.value.trim(), tags: [], phones: [], emails: [] });
      await ctx.data.Places.update(place.id, { peopleIds: [...(place.peopleIds || []), contact.id] });
      rerender();
    },
  });

  return el('div', { class: 'mer-person-form' }, [contactSelect, newNameInput, addBtn]);
}

// --- Geofenced notes-to-self ---
// Distinct from the freeform `place.notes` textarea above: each of these
// resurfaces individually the next time "Check nearby places" finds you
// within range (see findNearbyNudges below).

async function placeNotesSection(place, ctx, rerender) {
  const section = el('div', { class: 'mer-people-list' });
  const notes = await ctx.data.PlaceNotes.byIndex('placeId', place.id);
  for (const note of notes) {
    section.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: note.text }),
      ]),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => { await ctx.data.PlaceNotes.remove(note.id); rerender(); },
      }),
    ]));
  }
  section.append(el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New note-to-self — press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.PlaceNotes.create({ placeId: place.id, text: e.target.value.trim() });
      e.target.value = '';
      rerender();
    },
  }));
  return section;
}

async function peopleSection(place, ctx, rerender) {
  const section = el('div', { class: 'mer-people-list' });
  const allContacts = await ctx.data.Contacts.list();
  const contactsById = new Map(allContacts.map((c) => [c.id, c]));
  const linked = (place.peopleIds || []).map((id) => contactsById.get(id)).filter(Boolean);

  for (const contact of linked) {
    const photos = await ctx.data.getAttachmentsFor('contacts', contact.id);
    section.append(el('div', { class: 'mer-person-card' }, [
      photos[0]
        ? el('img', { class: 'mer-person-photo', src: ctx.data.attachmentUrl(photos[0]), alt: contact.name })
        : el('div', { class: 'mer-person-photo mer-person-photo-empty' }),
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: contact.name }),
        el('div', { class: 'mer-person-meta', text: [contact.relationship, contact.birthday ? `🎂 ${fmtDate(contact.birthday)}` : ''].filter(Boolean).join(' · ') }),
      ]),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×', title: 'Unlink from this place (keeps the contact)',
        onclick: async () => {
          await ctx.data.Places.update(place.id, { peopleIds: (place.peopleIds || []).filter((id) => id !== contact.id) });
          rerender();
        },
      }),
    ]));
  }
  section.append(linkContactForm(place, allContacts, ctx, rerender));
  return section;
}

// --- Visit dates ---

function visitDatesEditor(place, ctx, rerender) {
  const list = el('div', { class: 'mer-visit-dates' });
  for (const date of place.visitDates || []) {
    list.append(el('span', { class: 'mer-chip' }, [
      document.createTextNode(fmtDate(date) + ' '),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => {
          await ctx.data.Places.update(place.id, { visitDates: (place.visitDates || []).filter((d) => d !== date) });
          rerender();
        },
      }),
    ]));
  }
  const dateInput = el('input', {
    type: 'date',
    onchange: async (e) => {
      if (!e.target.value) return;
      const visitDates = [...new Set([...(place.visitDates || []), e.target.value])].sort();
      await ctx.data.Places.update(place.id, { visitDates });
      rerender();
    },
  });
  return el('div', {}, [list, dateInput]);
}

// --- Detail editor ---

function detailEditor(place, ctx, rerender) {
  const patch = (fields) => ctx.data.Places.update(place.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const nameInput = el('input', { type: 'text', value: place.name || '', onchange: (e) => patch({ name: e.target.value }) });
  const categoryInput = el('input', { type: 'text', value: place.category || '', placeholder: 'bar, restaurant, trail…', onchange: (e) => patch({ category: e.target.value }) });
  const listTypeSelect = el('select', { onchange: (e) => patch({ listType: e.target.value }) }, [
    el('option', { value: 'visited', text: 'Visited', selected: place.listType === 'visited' }),
    el('option', { value: 'wantToGo', text: 'Want to Go', selected: place.listType === 'wantToGo' }),
  ]);
  const addressInput = el('input', { type: 'text', value: place.address || '', onchange: (e) => patch({ address: e.target.value }) });
  const latInput = el('input', { type: 'number', step: 'any', value: place.lat ?? '', placeholder: 'Latitude', onchange: (e) => patch({ lat: e.target.value ? Number(e.target.value) : null }) });
  const lngInput = el('input', { type: 'number', step: 'any', value: place.lng ?? '', placeholder: 'Longitude', onchange: (e) => patch({ lng: e.target.value ? Number(e.target.value) : null }) });
  const locateBtn = el('button', {
    type: 'button', text: 'Use my location',
    onclick: () => {
      navigator.geolocation?.getCurrentPosition(
        (pos) => patch({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        (err) => alert(`Couldn't get location: ${err.message}`)
      );
    },
  });
  const revisitCheckbox = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: !!place.revisit, onchange: (e) => patch({ revisit: e.target.checked }) }),
    el('span', { text: 'Want to revisit' }),
  ]);
  const notesInput = el('textarea', { rows: '4', placeholder: 'Memory / story notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(place.notes || '')]);

  // Placeholders for async-loaded sections (photos, people) — kept as direct
  // references so they can be swapped out once fetched without depending on
  // fragile child-index math (the surrounding layout varies by listType).
  const photosPlaceholder = el('p', { class: 'mer-muted', text: 'Loading photos…' });
  const peoplePlaceholder = el('p', { class: 'mer-muted', text: 'Loading people…' });
  const placeNotesPlaceholder = el('p', { class: 'mer-muted', text: 'Loading notes-to-self…' });

  const detail = el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: place.name || '(untitled place)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedPlaceId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Name', nameInput),
      field('Category', categoryInput),
      field('List', listTypeSelect),
      field('Address', addressInput),
      field('Latitude', latInput),
      field('Longitude', lngInput),
    ]),
    locateBtn,
    place.listType === 'visited' ? el('div', { class: 'mer-subsection-label', text: 'Rating' }) : null,
    place.listType === 'visited' ? starRating(place.rating, (v) => patch({ rating: v })) : null,
    revisitCheckbox,
    place.listType === 'visited' ? el('div', { class: 'mer-subsection-label', text: 'Visit dates' }) : null,
    place.listType === 'visited' ? visitDatesEditor(place, ctx, rerender) : null,
    el('div', { class: 'mer-subsection-label', text: 'Notes' }),
    notesInput,
    el('div', { class: 'mer-subsection-label', text: 'Notes-to-self (geofenced)' }),
    el('p', { class: 'mer-muted', text: 'Resurfaces here next time "Check nearby places" finds you within range.' }),
    placeNotesPlaceholder,
    el('div', { class: 'mer-subsection-label', text: 'Photos' }),
    photosPlaceholder,
    el('div', { class: 'mer-subsection-label', text: 'People' }),
    peoplePlaceholder,
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete place',
      onclick: async () => { await ctx.data.Places.remove(place.id); state.selectedPlaceId = null; rerender(); },
    }),
  ]);

  ctx.data.getAttachmentsFor('places', place.id).then((photos) => {
    photosPlaceholder.replaceWith(photoGrid(place, ctx, photos, rerender));
  });
  peopleSection(place, ctx, rerender).then((section) => {
    peoplePlaceholder.replaceWith(section);
  });
  placeNotesSection(place, ctx, rerender).then((section) => {
    placeNotesPlaceholder.replaceWith(section);
  });

  return detail;
}

// --- Cards / list ---

async function placeCard(place, ctx, onSelect) {
  const photos = await ctx.data.getAttachmentsFor('places', place.id);
  const card = el('div', { class: 'mer-place-card' }, [
    photos[0]
      ? el('img', { class: 'mer-place-photo', src: ctx.data.attachmentUrl(photos[0]), alt: place.name })
      : el('div', { class: 'mer-place-photo mer-place-photo-empty' }),
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: place.name || '(untitled)' }),
      el('div', { class: 'mer-place-meta' }, [
        place.category ? el('span', { class: 'mer-chip', text: place.category }) : null,
        place.rating ? el('span', { class: 'mer-chip', text: '★'.repeat(place.rating) }) : null,
        place.revisit ? el('span', { class: 'mer-chip', text: 'Revisit' }) : null,
        place.visitDates?.length ? el('span', { class: 'mer-chip', text: `${place.visitDates.length} visit${place.visitDates.length > 1 ? 's' : ''}` }) : null,
      ]),
    ]),
  ]);
  card.addEventListener('click', () => onSelect(place.id));
  return card;
}

async function renderCardGrid(container, places, ctx, onSelect) {
  if (!places.length) {
    container.append(el('p', { class: 'mer-muted', text: 'Nothing here yet.' }));
    return;
  }
  const grid = el('div', { class: 'mer-place-grid' });
  container.append(grid);
  for (const place of places) grid.append(await placeCard(place, ctx, onSelect));
}

async function renderMap(container, places, ctx) {
  const withCoords = places.filter((p) => typeof p.lat === 'number' && typeof p.lng === 'number');
  const mapEl = el('div', { class: 'mer-map' });
  container.append(mapEl);
  if (!withCoords.length) {
    mapEl.replaceWith(el('p', { class: 'mer-muted', text: 'No places have coordinates yet. Add latitude/longitude in a place’s detail view to see it here.' }));
    return;
  }
  let L;
  try {
    L = await loadLeaflet();
  } catch (err) {
    mapEl.replaceWith(el('p', { class: 'mer-muted', text: `Map unavailable (offline?): ${err.message}` }));
    return;
  }
  const map = L.map(mapEl);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
    maxZoom: 19,
  }).addTo(map);
  const bounds = L.latLngBounds(withCoords.map((p) => [p.lat, p.lng]));
  map.fitBounds(bounds.pad(0.2));
  for (const place of withCoords) {
    L.marker([place.lat, place.lng]).addTo(map).bindPopup(`<strong>${place.name}</strong>${place.category ? `<br>${place.category}` : ''}`);
  }
}

// --- Bucket list tab ---

function bucketRow(item, ctx, rerender) {
  return el('div', { class: 'mer-task-row' }, [
    el('input', {
      type: 'checkbox', checked: !!item.done,
      onclick: (e) => { e.stopPropagation(); ctx.data.BucketListItems.update(item.id, { done: e.target.checked }); },
    }),
    el('span', { class: item.done ? 'mer-task-title is-done' : 'mer-task-title', text: item.title }),
    el('div', { class: 'mer-task-meta' }, [
      item.targetDate ? el('span', { class: 'mer-chip', text: fmtDate(item.targetDate) }) : null,
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×',
      onclick: async () => { await ctx.data.BucketListItems.remove(item.id); rerender(); },
    }),
  ]);
}

async function renderBucketList(container, ctx, rerender) {
  const items = await ctx.data.BucketListItems.list();
  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New bucket-list goal — press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.BucketListItems.create({ title: e.target.value.trim(), done: false });
      e.target.value = '';
      rerender();
    },
  });
  container.append(quickAdd);
  if (!items.length) {
    container.append(el('p', { class: 'mer-muted', text: 'No bucket-list goals yet.' }));
    return;
  }
  for (const item of items) container.append(bucketRow(item, ctx, rerender));
}

// --- Geolocation nudges ---
// Foreground/user-triggered only: a plain PWA (especially on iOS Safari)
// can't reliably run passive background geofencing, so this is a manual
// "check nearby" action rather than a silent notification.

function haversineMeters(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

const NEARBY_RADIUS_METERS = 1000;
const STALE_REVISIT_DAYS = 90;

function findNearbyNudges(places, placeNotes, lat, lng) {
  const notesByPlace = new Map();
  for (const note of placeNotes) {
    if (!notesByPlace.has(note.placeId)) notesByPlace.set(note.placeId, []);
    notesByPlace.get(note.placeId).push(note);
  }

  const results = [];
  for (const place of places) {
    if (typeof place.lat !== 'number' || typeof place.lng !== 'number') continue;
    const distance = haversineMeters(lat, lng, place.lat, place.lng);
    if (distance > NEARBY_RADIUS_METERS) continue;

    if (place.listType === 'wantToGo') {
      results.push({ place, distance, reason: 'Want to go' });
    } else if (place.listType === 'visited' && place.revisit) {
      const lastVisit = (place.visitDates || []).sort().at(-1);
      const daysSince = lastVisit ? (Date.now() - new Date(lastVisit).getTime()) / 86400000 : Infinity;
      if (daysSince >= STALE_REVISIT_DAYS) results.push({ place, distance, reason: `Haven't been back in ${Math.round(daysSince)} days` });
    }
    for (const note of notesByPlace.get(place.id) || []) {
      results.push({ place, distance, reason: `📝 ${note.text}` });
    }
  }
  return results.sort((a, b) => a.distance - b.distance);
}

function checkNearbyButton(ctx, rerender) {
  return el('button', {
    type: 'button', text: '📍 Check nearby places',
    onclick: () => {
      if (!navigator.geolocation) { alert('Geolocation is not available in this browser.'); return; }
      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          const [places, placeNotes] = await Promise.all([ctx.data.Places.list(), ctx.data.PlaceNotes.list()]);
          state.nearbyNudges = findNearbyNudges(places, placeNotes, pos.coords.latitude, pos.coords.longitude);
          rerender();
        },
        (err) => alert(`Couldn't get location: ${err.message}`)
      );
    },
  });
}

function nearbyBanner(rerender) {
  if (state.nearbyNudges === null) return null;
  if (!state.nearbyNudges.length) {
    return el('p', { class: 'mer-muted', text: 'Nothing nearby right now.' });
  }
  return el('div', { class: 'mer-people-list' }, state.nearbyNudges.map(({ place, distance, reason }) =>
    el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: place.name || '(untitled)' }),
        el('div', { class: 'mer-person-meta', text: `${reason} · ${Math.round(distance)}m away` }),
      ]),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '×', onclick: () => { state.nearbyNudges = null; rerender(); } }),
    ])
  ));
}

// --- Toolbar / tabs ---

function tabsBar(rerender) {
  const tabs = [
    { id: 'visited', label: 'Visited' },
    { id: 'wantToGo', label: 'Want to Go' },
    { id: 'map', label: 'Map' },
    { id: 'bucket', label: 'Bucket List' },
  ];
  return el('div', { class: 'mer-toggle-group' }, tabs.map((t) =>
    el('button', {
      type: 'button', class: state.tab === t.id ? 'is-active' : '', text: t.label,
      onclick: () => { state.tab = t.id; rerender(); },
    })
  ));
}

function quickAddPlace(ctx, rerender) {
  return el('input', {
    type: 'text', class: 'mer-quick-add',
    placeholder: state.tab === 'wantToGo' ? '+ New place to visit — press Enter' : '+ New place — press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Places.create({
        name: e.target.value.trim(),
        listType: state.tab === 'wantToGo' ? 'wantToGo' : 'visited',
      });
      e.target.value = '';
      rerender();
    },
  });
}

export async function renderPlaces(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Places' }));
  canvas.append(el('div', { class: 'mer-toolbar' }, [
    tabsBar(rerender),
    state.tab !== 'map' && state.tab !== 'bucket' ? quickAddPlace(ctx, rerender) : null,
    state.tab !== 'map' && state.tab !== 'bucket' ? checkNearbyButton(ctx, rerender) : null,
  ]));

  if (state.tab !== 'map' && state.tab !== 'bucket') {
    const banner = nearbyBanner(rerender);
    if (banner) canvas.append(banner);
  }

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  const onSelect = (id) => { state.selectedPlaceId = state.selectedPlaceId === id ? null : id; rerender(); };

  if (state.tab === 'bucket') {
    await renderBucketList(area, ctx, rerender);
  } else if (state.tab === 'map') {
    const allPlaces = await ctx.data.Places.list();
    await renderMap(area, allPlaces, ctx);
  } else {
    const allPlaces = await ctx.data.Places.list();
    const places = allPlaces.filter((p) => (p.listType || 'visited') === state.tab);
    await renderCardGrid(area, places, ctx, onSelect);
  }

  if (state.selectedPlaceId) {
    const place = await ctx.data.Places.get(state.selectedPlaceId);
    if (place) canvas.append(detailEditor(place, ctx, rerender));
  }
}

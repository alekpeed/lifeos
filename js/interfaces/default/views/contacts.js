import { el, fmtDate, parseTags } from '../dom.js';

let state = {
  search: '',
  tagFilter: 'all',
  selectedId: null,
};

// --- Contact cards (list view) ---

function contactCard(contact, photo, ctx, onSelect) {
  const thumb = photo
    ? el('img', { class: 'mer-place-photo', src: ctx.data.attachmentUrl(photo), alt: contact.name })
    : el('div', { class: 'mer-place-photo mer-place-photo-empty mer-link-icon', text: '👤' });

  const card = el('div', { class: 'mer-place-card' }, [
    thumb,
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: contact.name || '(untitled)' }),
      el('div', { class: 'mer-place-meta' }, [
        contact.company ? el('span', { class: 'mer-chip', text: contact.company }) : null,
        contact.relationship ? el('span', { class: 'mer-chip', text: contact.relationship }) : null,
        ...(contact.tags || []).map((t) => el('span', { class: 'mer-chip mer-chip-tag', text: `#${t}` })),
      ]),
    ]),
  ]);
  card.addEventListener('click', () => onSelect(contact.id));
  return card;
}

// --- Repeatable phone/email rows ---

function repeatableEditor(contact, key, ctx, rerender, valueField, placeholder) {
  const rows = el('div', {}, (contact[key] || []).map((entry) => {
    const labelInput = el('input', { type: 'text', value: entry.label || '', placeholder: 'Label (mobile, work…)' });
    const valueInput = el('input', { type: 'text', value: entry[valueField] || '', placeholder });
    const commit = () => {
      const items = contact[key].map((e) => e.id === entry.id ? { ...e, label: labelInput.value, [valueField]: valueInput.value } : e);
      ctx.data.Contacts.update(contact.id, { [key]: items }).then(rerender);
    };
    labelInput.onchange = commit;
    valueInput.onchange = commit;
    return el('div', { class: 'mer-person-form' }, [
      labelInput, valueInput,
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: () => {
          const items = contact[key].filter((e) => e.id !== entry.id);
          ctx.data.Contacts.update(contact.id, { [key]: items }).then(rerender);
        },
      }),
    ]);
  }));

  const newLabel = el('input', { type: 'text', placeholder: 'Label (mobile, work…)' });
  const newValue = el('input', { type: 'text', placeholder });
  const addBtn = el('button', {
    type: 'button', text: '+ Add',
    onclick: () => {
      if (!newValue.value.trim()) return;
      const items = [...(contact[key] || []), { id: crypto.randomUUID(), label: newLabel.value.trim(), [valueField]: newValue.value.trim() }];
      ctx.data.Contacts.update(contact.id, { [key]: items }).then(rerender);
    },
  });

  return el('div', {}, [rows, el('div', { class: 'mer-person-form' }, [newLabel, newValue, addBtn])]);
}

// --- Detail editor ---

function detailEditor(contact, photo, ctx, rerender) {
  const patch = (fields) => ctx.data.Contacts.update(contact.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const nameInput = el('input', { type: 'text', value: contact.name || '', onchange: (e) => patch({ name: e.target.value }) });
  const companyInput = el('input', { type: 'text', value: contact.company || '', placeholder: 'Company', onchange: (e) => patch({ company: e.target.value }) });
  const jobTitleInput = el('input', { type: 'text', value: contact.jobTitle || '', placeholder: 'Job title', onchange: (e) => patch({ jobTitle: e.target.value }) });
  const relationshipInput = el('input', { type: 'text', value: contact.relationship || '', placeholder: 'Friend, family, coworker, plumber…', onchange: (e) => patch({ relationship: e.target.value }) });
  const birthdayInput = el('input', { type: 'date', value: contact.birthday || '', onchange: (e) => patch({ birthday: e.target.value || null }) });
  const tagsInput = el('input', { type: 'text', value: (contact.tags || []).join(', '), placeholder: 'comma, separated, tags', onchange: (e) => patch({ tags: parseTags(e.target.value) }) });
  const addressInput = el('textarea', { rows: '2', placeholder: 'Address', onchange: (e) => patch({ address: e.target.value }) }, [document.createTextNode(contact.address || '')]);
  const notesInput = el('textarea', { rows: '3', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(contact.notes || '')]);

  const photoPlaceholder = el('p', { class: 'mer-muted', text: 'Loading photo…' });

  const detail = el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: contact.name || '(untitled)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Name', nameInput),
      field('Company', companyInput),
      field('Job title', jobTitleInput),
      field('Relationship', relationshipInput),
      field('Birthday', birthdayInput),
    ]),
    field('Tags', tagsInput),
    field('Address', addressInput),
    field('Notes', notesInput),
    el('div', { class: 'mer-subsection-label', text: 'Photo' }),
    photoPlaceholder,
    el('div', { class: 'mer-subsection-label', text: 'Phone numbers' }),
    repeatableEditor(contact, 'phones', ctx, rerender, 'number', 'Number'),
    el('div', { class: 'mer-subsection-label', text: 'Emails' }),
    repeatableEditor(contact, 'emails', ctx, rerender, 'email', 'Email'),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete contact',
      onclick: async () => { await ctx.data.Contacts.remove(contact.id); state.selectedId = null; rerender(); },
    }),
  ]);

  const photoWrap = el('div', {}, [
    photo
      ? el('div', { class: 'mer-photo-thumb' }, [
        el('img', { src: ctx.data.attachmentUrl(photo), alt: contact.name }),
        el('button', {
          type: 'button', class: 'mer-photo-remove', text: '×',
          onclick: async () => { await ctx.data.Attachments.remove(photo.id); rerender(); },
        }),
      ])
      : el('label', { class: 'mer-photo-add' }, [
        el('span', { text: '+ Photo' }),
        el('input', {
          type: 'file', accept: 'image/*',
          onchange: async (e) => {
            if (e.target.files[0]) await ctx.data.createAttachment(e.target.files[0], 'contacts', contact.id);
            rerender();
          },
        }),
      ]),
  ]);
  photoPlaceholder.replaceWith(photoWrap);

  return detail;
}

// --- Toolbar ---

function toolbar(ctx, contacts, rerender) {
  const tags = [...new Set(contacts.flatMap((c) => c.tags || []))];

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New contact — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Contacts.create({ name: e.target.value.trim(), tags: [], phones: [], emails: [] });
      e.target.value = '';
    },
  });

  const searchInput = el('input', {
    type: 'text', placeholder: 'Search name or company…', value: state.search,
    onchange: (e) => { state.search = e.target.value; rerender(); },
  });

  const tagSelect = el('select', { onchange: (e) => { state.tagFilter = e.target.value; rerender(); } }, [
    el('option', { value: 'all', text: 'All tags', selected: state.tagFilter === 'all' }),
    ...tags.map((t) => el('option', { value: t, text: `#${t}`, selected: t === state.tagFilter })),
  ]);

  return el('div', { class: 'mer-toolbar' }, [quickAdd, searchInput, tagSelect]);
}

export async function renderContacts(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Contacts' }));

  const contacts = await ctx.data.Contacts.list();
  canvas.append(toolbar(ctx, contacts, rerender));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  const search = state.search.trim().toLowerCase();
  const filtered = contacts
    .filter((c) => state.tagFilter === 'all' || (c.tags || []).includes(state.tagFilter))
    .filter((c) => !search || c.name?.toLowerCase().includes(search) || c.company?.toLowerCase().includes(search))
    .sort((a, b) => (a.name || '').localeCompare(b.name || ''));

  const onSelect = (id) => { state.selectedId = state.selectedId === id ? null : id; rerender(); };

  if (!filtered.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No contacts match the current filters.' }));
  } else {
    const grid = el('div', { class: 'mer-place-grid' });
    area.append(grid);
    const photos = await Promise.all(filtered.map((c) => ctx.data.getAttachmentsFor('contacts', c.id)));
    filtered.forEach((contact, i) => grid.append(contactCard(contact, photos[i][0], ctx, onSelect)));
  }

  if (state.selectedId) {
    const contact = contacts.find((c) => c.id === state.selectedId);
    if (contact) {
      const photo = (await ctx.data.getAttachmentsFor('contacts', contact.id))[0];
      canvas.append(detailEditor(contact, photo, ctx, rerender));
    }
  }
}

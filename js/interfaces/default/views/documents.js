import { el, fmtDate, isPast, contactLinkField } from '../dom.js';

let state = {
  categoryFilter: 'all',
  selectedId: null,
  scanning: false,
  scanError: null,
};

function isExpiringSoon(dateStr, days) {
  if (!dateStr) return false;
  if (isPast(dateStr)) return false;
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() + days);
  return new Date(dateStr + 'T00:00:00') <= cutoff;
}

function docMeta(doc, expiryDays, contactsById) {
  const meta = el('div', { class: 'mer-task-meta' });
  if (doc.category) meta.append(el('span', { class: 'mer-chip', text: doc.category }));
  if (doc.issuer) meta.append(el('span', { class: 'mer-chip', text: doc.issuer }));
  if (doc.contactId && contactsById.has(doc.contactId)) {
    meta.append(el('span', { class: 'mer-chip', text: `👤 ${contactsById.get(doc.contactId).name}` }));
  }
  if (doc.expiryDate) {
    const expired = isPast(doc.expiryDate);
    const soon = isExpiringSoon(doc.expiryDate, expiryDays);
    meta.append(el('span', {
      class: expired || soon ? 'mer-chip is-overdue' : 'mer-chip',
      text: `${expired ? 'Expired ' : soon ? 'Expiring ' : ''}${fmtDate(doc.expiryDate)}`.trim(),
    }));
  }
  return meta;
}

function docRow(doc, ctx, onSelect, expiryDays, contactsById) {
  const row = el('div', { class: 'mer-task-row' }, [
    el('span', { class: 'mer-task-title', text: doc.title || '(untitled document)' }),
    docMeta(doc, expiryDays, contactsById),
  ]);
  row.addEventListener('click', () => onSelect(doc.id));
  return row;
}

function detailEditor(doc, ctx, rerender, allContacts) {
  const patch = (fields) => ctx.data.Documents.update(doc.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const titleInput = el('input', { type: 'text', value: doc.title || '', onchange: (e) => patch({ title: e.target.value }) });
  const categoryInput = el('input', { type: 'text', value: doc.category || '', placeholder: 'lease, insurance, warranty…', onchange: (e) => patch({ category: e.target.value }) });
  const issuerInput = el('input', { type: 'text', value: doc.issuer || '', placeholder: 'Who issued this?', onchange: (e) => patch({ issuer: e.target.value }) });
  const policyInput = el('input', { type: 'text', value: doc.policyNumber || '', placeholder: 'Policy / account number', onchange: (e) => patch({ policyNumber: e.target.value }) });
  const expiryInput = el('input', { type: 'date', value: doc.expiryDate || '', onchange: (e) => patch({ expiryDate: e.target.value || null }) });
  const notesInput = el('textarea', { rows: '3', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(doc.notes || '')]);

  const attachmentPlaceholder = el('p', { class: 'mer-muted', text: 'Loading attachments…' });

  const detail = el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: doc.title || '(untitled document)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Title', titleInput),
      field('Category', categoryInput),
      field('Issuer', issuerInput),
      field('Policy / account #', policyInput),
      field('Expiry date', expiryInput),
    ]),
    field('Notes', notesInput),
    el('div', { class: 'mer-subsection-label', text: 'Linked contact' }),
    contactLinkField(allContacts, doc.contactId, ctx,
      (contactId) => patch({ contactId }),
      () => patch({ contactId: null })),
    el('div', { class: 'mer-subsection-label', text: 'Attachments' }),
    attachmentPlaceholder,
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete document',
      onclick: async () => { await ctx.data.Documents.remove(doc.id); state.selectedId = null; rerender(); },
    }),
  ]);

  ctx.data.getAttachmentsFor('documents', doc.id).then((attachments) => {
    const wrap = el('div', {}, [
      ...attachments.map((a) => el('div', { class: 'mer-person-card' }, [
        el('a', { href: ctx.data.attachmentUrl(a), target: '_blank', rel: 'noopener', text: a.filename }),
        el('button', { type: 'button', class: 'mer-icon-btn', text: '×', onclick: async () => { await ctx.data.Attachments.remove(a.id); rerender(); } }),
      ])),
      el('input', {
        type: 'file', accept: 'application/pdf,image/*',
        onchange: async (e) => {
          if (e.target.files[0]) await ctx.data.createAttachment(e.target.files[0], 'documents', doc.id);
          rerender();
        },
      }),
    ]);
    attachmentPlaceholder.replaceWith(wrap);
  });

  return detail;
}

// Camera-to-data capture: photograph a document and have the active AI
// provider's vision input draft a new record instead of typing it. The
// draft always opens as a normal, editable, unsaved-until-you-touch-it
// record (via state.selectedId) -- nothing here is trusted silently.
async function scanDocument(file, ctx, rerender) {
  state.scanning = true;
  state.scanError = null;
  rerender();
  try {
    const draft = await ctx.data.extractDocumentFromImage(file);
    const doc = await ctx.data.Documents.create(draft);
    await ctx.data.createAttachment(file, 'documents', doc.id);
    state.selectedId = doc.id;
  } catch (err) {
    state.scanError = err.message || String(err);
  }
  state.scanning = false;
  rerender();
}

function scanControl(ctx, rerender) {
  if (state.scanning) return el('span', { class: 'mer-muted', text: 'Scanning…' });
  return el('label', { class: 'mer-setting' }, [
    el('span', { text: '📷 Scan a document' }),
    el('input', {
      type: 'file', accept: 'image/*', capture: 'environment',
      onchange: (e) => { if (e.target.files[0]) scanDocument(e.target.files[0], ctx, rerender); e.target.value = ''; },
    }),
  ]);
}

function toolbar(ctx, documents, rerender) {
  const categories = [...new Set(documents.map((d) => d.category).filter(Boolean))];

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New document — type a title and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Documents.create({ title: e.target.value.trim() });
      e.target.value = '';
    },
  });

  const categorySelect = el('select', { onchange: (e) => { state.categoryFilter = e.target.value; rerender(); } }, [
    el('option', { value: 'all', text: 'All categories', selected: state.categoryFilter === 'all' }),
    ...categories.map((c) => el('option', { value: c, text: c, selected: c === state.categoryFilter })),
  ]);

  return el('div', { class: 'mer-toolbar' }, [quickAdd, categorySelect]);
}

export async function renderDocuments(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Documents' }));

  const [expiryDays, documents, allContacts] = await Promise.all([
    ctx.data.Settings.get('documentExpiryDays'),
    ctx.data.Documents.list(),
    ctx.data.Contacts.list(),
  ]);
  const contactsById = new Map(allContacts.map((c) => [c.id, c]));
  canvas.append(toolbar(ctx, documents, rerender));
  canvas.append(el('div', { class: 'mer-toolbar' }, [scanControl(ctx, rerender)]));
  if (state.scanError) canvas.append(el('p', { class: 'mer-muted mer-sync-error', text: state.scanError }));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  const filtered = documents
    .filter((d) => state.categoryFilter === 'all' || d.category === state.categoryFilter)
    .sort((a, b) => (a.expiryDate || '9999') < (b.expiryDate || '9999') ? -1 : 1);

  const onSelect = (id) => { state.selectedId = state.selectedId === id ? null : id; rerender(); };

  if (!filtered.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No documents match the current filters.' }));
  } else {
    for (const doc of filtered) area.append(docRow(doc, ctx, onSelect, expiryDays, contactsById));
  }

  if (state.selectedId) {
    const doc = documents.find((d) => d.id === state.selectedId);
    if (doc) canvas.append(detailEditor(doc, ctx, rerender, allContacts));
  }
}

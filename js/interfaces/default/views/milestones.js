import { el, fmtDate, todayStr } from '../dom.js';

let state = {
  tab: 'timeline', // timeline | recap
  selectedId: null,
  recapYear: new Date().getFullYear(),
};

// --- Timeline ---

function milestoneRow(milestone, onSelect) {
  const row = el('div', { class: 'mer-task-row' }, [
    el('span', { class: 'mer-task-title', text: milestone.title || '(untitled)' }),
    el('div', { class: 'mer-task-meta' }, [
      milestone.category ? el('span', { class: 'mer-chip', text: milestone.category }) : null,
      milestone.date ? el('span', { class: 'mer-chip', text: fmtDate(milestone.date) }) : null,
    ]),
  ]);
  row.addEventListener('click', () => onSelect(milestone.id));
  return row;
}

function detailEditor(milestone, photo, ctx, rerender) {
  const patch = (fields) => ctx.data.Milestones.update(milestone.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const titleInput = el('input', { type: 'text', value: milestone.title || '', onchange: (e) => patch({ title: e.target.value }) });
  const dateInput = el('input', { type: 'date', value: milestone.date || '', onchange: (e) => patch({ date: e.target.value || null }) });
  const categoryInput = el('input', { type: 'text', value: milestone.category || '', placeholder: 'birthday, achievement, travel, career…', onchange: (e) => patch({ category: e.target.value }) });
  const notesInput = el('textarea', { rows: '3', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(milestone.notes || '')]);

  const photoPlaceholder = el('p', { class: 'mer-muted', text: 'Loading photo…' });

  const detail = el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: milestone.title || '(untitled)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Title', titleInput),
      field('Date', dateInput),
      field('Category', categoryInput),
    ]),
    field('Notes', notesInput),
    el('div', { class: 'mer-subsection-label', text: 'Photo' }),
    photoPlaceholder,
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete milestone',
      onclick: async () => { await ctx.data.Milestones.remove(milestone.id); state.selectedId = null; rerender(); },
    }),
  ]);

  const photoWrap = el('div', {}, [
    photo
      ? el('div', { class: 'mer-photo-thumb' }, [
        el('img', { src: ctx.data.attachmentUrl(photo), alt: milestone.title }),
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
            if (e.target.files[0]) await ctx.data.createAttachment(e.target.files[0], 'milestones', milestone.id);
            rerender();
          },
        }),
      ]),
  ]);
  photoPlaceholder.replaceWith(photoWrap);

  return detail;
}

async function renderTimeline(container, ctx, rerender) {
  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New milestone — type a title and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Milestones.create({ title: e.target.value.trim(), date: todayStr() });
      e.target.value = '';
    },
  });
  container.append(el('div', { class: 'mer-toolbar' }, [quickAdd]));

  const milestones = await ctx.data.Milestones.list();
  const area = el('div', {});
  container.append(area);

  const onSelect = (id) => { state.selectedId = state.selectedId === id ? null : id; rerender(); };

  if (!milestones.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No milestones logged yet.' }));
  } else {
    const sorted = [...milestones].sort((a, b) => (a.date || '') < (b.date || '') ? 1 : -1);
    let lastYear = null;
    for (const milestone of sorted) {
      const year = milestone.date?.slice(0, 4) || 'Undated';
      if (year !== lastYear) {
        area.append(el('div', { class: 'mer-group-label', text: year }));
        lastYear = year;
      }
      area.append(milestoneRow(milestone, onSelect));
    }
  }

  if (state.selectedId) {
    const milestone = milestones.find((m) => m.id === state.selectedId);
    if (milestone) {
      const photo = (await ctx.data.getAttachmentsFor('milestones', milestone.id))[0];
      container.append(detailEditor(milestone, photo, ctx, rerender));
    }
  }
}

// --- Yearly recap ---

function statRow(label, value) {
  return el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [el('div', { class: 'mer-person-name', text: label })]),
    el('div', { text: String(value) }),
  ]);
}

async function renderRecap(container, ctx, rerender) {
  const yearInput = el('input', {
    type: 'number', value: state.recapYear,
    onchange: (e) => { state.recapYear = Number(e.target.value) || new Date().getFullYear(); rerender(); },
  });
  container.append(el('div', { class: 'mer-toolbar' }, [el('label', { class: 'mer-field' }, [el('span', { text: 'Year' }), yearInput])]));

  const recap = await ctx.data.getYearInReview(state.recapYear);
  const stats = el('div', { class: 'mer-people-list' }, [
    statRow('Tasks completed', recap.tasksCompleted),
    statRow('Assignments completed', recap.assignmentsCompleted),
    statRow('Places visited', `${recap.placesVisitedCount} places, ${recap.totalVisits} visits`),
    statRow('Books finished', recap.booksFinished),
    statRow('Pages read', recap.pagesRead),
    statRow('Recipes cooked', `${recap.recipesCookedCount} recipes, ${recap.cookSessions} sessions`),
    statRow('Bills paid', `$${recap.billsPaidTotal.toFixed(2)}`),
    statRow('Documents added', recap.documentsAdded),
    statRow('Contacts added', recap.contactsAdded),
    statRow('Habit check-ins', recap.habitCheckIns),
    statRow('Health logs', recap.healthLogCount),
    recap.avgSleepHours !== null ? statRow('Avg. sleep (hrs)', recap.avgSleepHours.toFixed(1)) : null,
  ]);
  container.append(el('h3', { text: `${recap.year} in review` }), stats);

  container.append(el('div', { class: 'mer-subsection-label', text: 'Milestones this year' }));
  if (!recap.milestones.length) {
    container.append(el('p', { class: 'mer-muted', text: 'No milestones logged this year.' }));
  } else {
    const list = el('ul', { class: 'mer-feed' });
    for (const m of recap.milestones) {
      list.append(el('li', { class: 'mer-feed-item' }, [
        el('span', { class: 'mer-feed-title', text: m.title || '(untitled)' }),
        el('span', { class: 'mer-feed-date', text: fmtDate(m.date) }),
      ]));
    }
    container.append(list);
  }
}

// --- Root ---

function tabsBar(rerender) {
  return el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'timeline' ? 'is-active' : '', text: 'Timeline', onclick: () => { state.tab = 'timeline'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'recap' ? 'is-active' : '', text: 'Yearly Recap', onclick: () => { state.tab = 'recap'; rerender(); } }),
  ]);
}

export async function renderMilestones(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Milestones' }));
  canvas.append(el('div', { class: 'mer-toolbar' }, [tabsBar(rerender)]));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  if (state.tab === 'timeline') await renderTimeline(area, ctx, rerender);
  else await renderRecap(area, ctx, rerender);
}

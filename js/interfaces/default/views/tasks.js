import { el, fmtDate, todayStr, isPast, parseTags, RECUR_FREQS, computeNextDueDate } from '../dom.js';

const STATUSES = [
  { value: 'not_started', label: 'Not Started' },
  { value: 'in_progress', label: 'In Progress' },
  { value: 'waiting', label: 'Waiting' },
  { value: 'done', label: 'Done' },
];
const PRIORITIES = [
  { value: 'low', label: 'Low' },
  { value: 'medium', label: 'Medium' },
  { value: 'high', label: 'High' },
  { value: 'urgent', label: 'Urgent' },
];

// Module-local UI state. Reset each time the view is (re)entered via mount,
// but preserved across the reactive re-renders that happen while it's open.
let state = {
  layout: 'list',
  projectFilter: 'all',
  tagFilter: new Set(),
  showSnoozed: false,
  selectedTaskId: null,
};

async function toggleDone(ctx, task) {
  const goingToDone = task.status !== 'done';
  await ctx.data.Tasks.update(task.id, { status: goingToDone ? 'done' : 'not_started' });

  if (goingToDone && task.recurring?.freq) {
    const nextDue = computeNextDueDate(task.dueDate, task.recurring);
    if (nextDue) {
      await ctx.data.Tasks.create({
        title: task.title,
        notes: task.notes,
        projectId: task.projectId,
        priority: task.priority,
        dueDate: nextDue,
        recurring: task.recurring,
        tags: task.tags || [],
        subtasks: (task.subtasks || []).map((s) => ({ ...s, done: false })),
        status: 'not_started',
      });
    }
  }
}

function priorityDot(priority) {
  const cls = { low: '', medium: 'is-medium', high: 'is-high', urgent: 'is-urgent' }[priority] || '';
  return el('span', { class: `mer-priority-dot ${cls}`.trim(), title: priority || 'low' });
}

function taskMeta(task, projectsById) {
  const meta = el('div', { class: 'mer-task-meta' });
  if (task.projectId && projectsById.has(task.projectId)) {
    meta.append(el('span', { class: 'mer-chip', text: projectsById.get(task.projectId).name }));
  }
  if (task.dueDate) {
    meta.append(el('span', {
      class: isPast(task.dueDate) && task.status !== 'done' ? 'mer-chip is-overdue' : 'mer-chip',
      text: fmtDate(task.dueDate),
    }));
  }
  if (task.status === 'waiting' && task.waitingOn) {
    meta.append(el('span', { class: 'mer-chip', text: `Waiting: ${task.waitingOn}` }));
  }
  if (task.subtasks?.length) {
    const done = task.subtasks.filter((s) => s.done).length;
    meta.append(el('span', { class: 'mer-chip', text: `${done}/${task.subtasks.length}` }));
  }
  if (task.snoozedUntil && task.snoozedUntil > todayStr()) {
    meta.append(el('span', { class: 'mer-chip', text: `Snoozed → ${fmtDate(task.snoozedUntil)}` }));
  }
  for (const tag of task.tags || []) {
    meta.append(el('span', { class: 'mer-chip mer-chip-tag', text: `#${tag}` }));
  }
  return meta;
}

function taskRow(task, ctx, projectsById, onSelect) {
  const row = el('div', { class: 'mer-task-row' }, [
    el('input', {
      type: 'checkbox',
      checked: task.status === 'done',
      onclick: (e) => { e.stopPropagation(); toggleDone(ctx, task); },
    }),
    priorityDot(task.priority),
    el('span', {
      class: task.status === 'done' ? 'mer-task-title is-done' : 'mer-task-title',
      text: task.title,
    }),
    taskMeta(task, projectsById),
  ]);
  row.addEventListener('click', () => onSelect(task.id));
  return row;
}

// --- Detail editor: full field access for a single selected task ---

function detailEditor(task, ctx, projects, rerender) {
  const patch = (fields) => ctx.data.Tasks.update(task.id, fields).then(rerender);

  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [
    el('span', { text: labelText }),
    inputEl,
  ]);

  const projectSelect = el('select', {
    onchange: (e) => patch({ projectId: e.target.value || null }),
  }, [
    el('option', { value: '', text: '(none)', selected: !task.projectId }),
    ...projects.map((p) => el('option', { value: p.id, text: p.name, selected: p.id === task.projectId })),
  ]);

  const statusSelect = el('select', {
    onchange: (e) => patch({ status: e.target.value }),
  }, STATUSES.map((s) => el('option', { value: s.value, text: s.label, selected: s.value === task.status })));

  const prioritySelect = el('select', {
    onchange: (e) => patch({ priority: e.target.value }),
  }, PRIORITIES.map((p) => el('option', { value: p.value, text: p.label, selected: p.value === (task.priority || 'low') })));

  const dueInput = el('input', { type: 'date', value: task.dueDate || '', onchange: (e) => patch({ dueDate: e.target.value || null }) });

  const waitingInput = el('input', {
    type: 'text', value: task.waitingOn || '', placeholder: 'Who are you waiting on?',
    onchange: (e) => patch({ waitingOn: e.target.value }),
  });

  const tagsInput = el('input', {
    type: 'text', value: (task.tags || []).join(', '), placeholder: 'comma, separated, tags',
    onchange: (e) => patch({ tags: parseTags(e.target.value) }),
  });

  const notesInput = el('textarea', {
    rows: '3', placeholder: 'Notes',
    onchange: (e) => patch({ notes: e.target.value }),
  }, [document.createTextNode(task.notes || '')]);

  const recurFreqSelect = el('select', {
    onchange: (e) => patch({ recurring: e.target.value ? { freq: e.target.value, interval: task.recurring?.interval || 1 } : null }),
  }, RECUR_FREQS.map((f) => el('option', { value: f.value, text: f.label, selected: f.value === (task.recurring?.freq || '') })));

  const snoozeRow = el('div', { class: 'mer-snooze-row' }, [
    el('button', { type: 'button', text: 'Snooze: tomorrow', onclick: () => {
      const d = new Date(); d.setDate(d.getDate() + 1);
      patch({ snoozedUntil: d.toISOString().slice(0, 10) });
    } }),
    el('button', { type: 'button', text: '+1 week', onclick: () => {
      const d = new Date(); d.setDate(d.getDate() + 7);
      patch({ snoozedUntil: d.toISOString().slice(0, 10) });
    } }),
    el('button', { type: 'button', text: 'Clear snooze', onclick: () => patch({ snoozedUntil: null }) }),
  ]);

  // Subtasks / checklist
  const subtaskList = el('div', { class: 'mer-subtasks' });
  for (const sub of task.subtasks || []) {
    subtaskList.append(el('label', { class: 'mer-subtask' }, [
      el('input', {
        type: 'checkbox', checked: sub.done,
        onchange: (e) => {
          const subtasks = task.subtasks.map((s) => s.id === sub.id ? { ...s, done: e.target.checked } : s);
          patch({ subtasks });
        },
      }),
      el('span', { text: sub.text }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '×', onclick: () => {
        patch({ subtasks: task.subtasks.filter((s) => s.id !== sub.id) });
      } }),
    ]));
  }
  const newSubtaskInput = el('input', {
    type: 'text', class: 'mer-subtask-add', placeholder: 'Add checklist item and press Enter',
    onkeydown: (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      const subtasks = [...(task.subtasks || []), { id: crypto.randomUUID(), text: e.target.value.trim(), done: false }];
      e.target.value = '';
      patch({ subtasks });
    },
  });

  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: task.title }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedTaskId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Project', projectSelect),
      field('Status', statusSelect),
      field('Priority', prioritySelect),
      field('Due date', dueInput),
      field('Repeats', recurFreqSelect),
      field('Waiting on', waitingInput),
    ]),
    field('Tags', tagsInput),
    field('Notes', notesInput),
    el('div', { class: 'mer-subsection-label', text: 'Snooze' }),
    snoozeRow,
    el('div', { class: 'mer-subsection-label', text: 'Checklist' }),
    subtaskList,
    newSubtaskInput,
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete task',
      onclick: async () => { await ctx.data.Tasks.remove(task.id); state.selectedTaskId = null; rerender(); },
    }),
  ]);
}

// --- Toolbar ---

function toolbar(ctx, projects, rerender) {
  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New task — type a title and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Tasks.create({ title: e.target.value.trim(), status: 'not_started', priority: 'low' });
      e.target.value = '';
    },
  });

  const projectSelect = el('select', {
    onchange: (e) => { state.projectFilter = e.target.value; rerender(); },
  }, [
    el('option', { value: 'all', text: 'All projects', selected: state.projectFilter === 'all' }),
    el('option', { value: 'inbox', text: '(No project)', selected: state.projectFilter === 'inbox' }),
    ...projects.map((p) => el('option', { value: p.id, text: p.name, selected: p.id === state.projectFilter })),
  ]);

  const layoutToggle = el('div', { class: 'mer-toggle-group' }, [
    el('button', {
      type: 'button', class: state.layout === 'list' ? 'is-active' : '', text: 'List',
      onclick: () => { state.layout = 'list'; rerender(); },
    }),
    el('button', {
      type: 'button', class: state.layout === 'kanban' ? 'is-active' : '', text: 'Kanban',
      onclick: () => { state.layout = 'kanban'; rerender(); },
    }),
  ]);

  const snoozeToggle = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: state.showSnoozed, onchange: (e) => { state.showSnoozed = e.target.checked; rerender(); } }),
    el('span', { text: 'Show snoozed' }),
  ]);

  const newProjectBtn = el('button', {
    type: 'button', text: '+ Project',
    onclick: async () => {
      const name = prompt('New project name');
      if (name?.trim()) {
        await ctx.data.Projects.create({ name: name.trim(), archived: false });
        rerender();
      }
    },
  });

  return el('div', { class: 'mer-toolbar' }, [quickAdd, projectSelect, layoutToggle, snoozeToggle, newProjectBtn]);
}

function applyFilters(tasks) {
  return tasks.filter((t) => {
    if (state.projectFilter === 'inbox' && t.projectId) return false;
    if (state.projectFilter !== 'all' && state.projectFilter !== 'inbox' && t.projectId !== state.projectFilter) return false;
    if (!state.showSnoozed && t.snoozedUntil && t.snoozedUntil > todayStr() && t.status !== 'done') return false;
    if (state.tagFilter.size && !(t.tags || []).some((tag) => state.tagFilter.has(tag))) return false;
    return true;
  });
}

function sortTasks(tasks) {
  const order = { urgent: 0, high: 1, medium: 2, low: 3 };
  return [...tasks].sort((a, b) => {
    if (a.status === 'done' !== (b.status === 'done')) return a.status === 'done' ? 1 : -1;
    const ad = a.dueDate || '9999-99-99';
    const bd = b.dueDate || '9999-99-99';
    if (ad !== bd) return ad < bd ? -1 : 1;
    return (order[a.priority] ?? 3) - (order[b.priority] ?? 3);
  });
}

function renderList(container, tasks, ctx, projectsById, onSelect) {
  const byProject = new Map();
  for (const t of tasks) {
    const key = t.projectId || 'inbox';
    if (!byProject.has(key)) byProject.set(key, []);
    byProject.get(key).push(t);
  }
  if (!tasks.length) {
    container.append(el('p', { class: 'mer-muted', text: 'No tasks match the current filters.' }));
    return;
  }
  for (const [key, group] of byProject) {
    const label = key === 'inbox' ? 'Inbox' : (projectsById.get(key)?.name || 'Inbox');
    container.append(el('div', { class: 'mer-group-label', text: label }));
    for (const task of sortTasks(group)) {
      container.append(taskRow(task, ctx, projectsById, onSelect));
    }
  }
}

function renderKanban(container, tasks, ctx, projectsById, onSelect) {
  const board = el('div', { class: 'mer-kanban' });
  for (const status of STATUSES) {
    const column = el('div', { class: 'mer-kanban-col' }, [
      el('div', { class: 'mer-kanban-col-label', text: `${status.label} (${tasks.filter((t) => (t.status || 'not_started') === status.value).length})` }),
    ]);
    for (const task of sortTasks(tasks.filter((t) => (t.status || 'not_started') === status.value))) {
      column.append(taskRow(task, ctx, projectsById, onSelect));
    }
    board.append(column);
  }
  container.append(board);
}

export async function renderTasks(canvas, ctx, rerender) {
  const [tasks, projects] = await Promise.all([ctx.data.Tasks.list(), ctx.data.Projects.list()]);
  const projectsById = new Map(projects.map((p) => [p.id, p]));

  canvas.append(el('h1', { text: 'Tasks' }));
  canvas.append(toolbar(ctx, projects, rerender));

  const filtered = applyFilters(tasks);
  const listArea = el('div', { class: 'mer-task-list-area' });
  canvas.append(listArea);

  const onSelect = (id) => { state.selectedTaskId = state.selectedTaskId === id ? null : id; rerender(); };

  if (state.layout === 'kanban') {
    renderKanban(listArea, filtered, ctx, projectsById, onSelect);
  } else {
    renderList(listArea, filtered, ctx, projectsById, onSelect);
  }

  if (state.selectedTaskId) {
    const task = tasks.find((t) => t.id === state.selectedTaskId);
    if (task) canvas.append(detailEditor(task, ctx, projects, rerender));
  }
}

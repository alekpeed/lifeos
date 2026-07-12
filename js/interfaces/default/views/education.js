import { el, fmtDate, isPast, todayStr } from '../dom.js';

const ASSIGNMENT_STATUSES = [
  { value: 'not_started', label: 'Not Started' },
  { value: 'in_progress', label: 'In Progress' },
  { value: 'done', label: 'Done' },
];

const GRADE_POINTS = { 'A': 4.0, 'A-': 3.7, 'B+': 3.3, 'B': 3.0, 'B-': 2.7, 'C+': 2.3, 'C': 2.0, 'C-': 1.7, 'D': 1.0, 'F': 0.0 };
const GRADE_OPTIONS = ['', ...Object.keys(GRADE_POINTS)];

let state = {
  tab: 'coursework', // coursework | summary
  semesterId: null,
  courseId: null,
  selectedCourseId: null,
  selectedAssignmentId: null,
};

// --- Semesters level ---

function semesterCard(semester, courseCount, ctx, rerender) {
  const card = el('div', { class: 'mer-place-card' }, [
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: semester.name || '(untitled semester)' }),
      el('div', { class: 'mer-place-meta' }, [
        (semester.startDate || semester.endDate)
          ? el('span', { class: 'mer-chip', text: `${fmtDate(semester.startDate)} – ${fmtDate(semester.endDate)}` })
          : null,
        el('span', { class: 'mer-chip', text: `${courseCount} course${courseCount === 1 ? '' : 's'}` }),
      ]),
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×',
      onclick: async (e) => {
        e.stopPropagation();
        if (courseCount && !confirm('Delete this semester? Its courses and assignments will remain but become unlinked.')) return;
        await ctx.data.Semesters.remove(semester.id);
        rerender();
      },
    }),
  ]);
  card.addEventListener('click', () => { state.semesterId = semester.id; rerender(); });
  return card;
}

async function renderSemesters(container, ctx, rerender) {
  const [semesters, courses] = await Promise.all([ctx.data.Semesters.list(), ctx.data.Courses.list()]);
  const countBySemester = new Map();
  for (const c of courses) countBySemester.set(c.semesterId, (countBySemester.get(c.semesterId) || 0) + 1);

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New semester — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Semesters.create({ name: e.target.value.trim() });
      e.target.value = '';
      rerender();
    },
  });
  container.append(quickAdd);

  if (!semesters.length) {
    container.append(el('p', { class: 'mer-muted', text: 'No semesters yet.' }));
    return;
  }
  const grid = el('div', { class: 'mer-place-grid' });
  for (const s of semesters) grid.append(semesterCard(s, countBySemester.get(s.id) || 0, ctx, rerender));
  container.append(grid);
}

// --- Courses level ---

function keyDatesEditor(course, ctx, rerender) {
  const list = el('div', { class: 'mer-visit-dates' });
  for (const kd of course.keyDates || []) {
    list.append(el('span', { class: 'mer-chip' }, [
      document.createTextNode(`${kd.label}: ${fmtDate(kd.date)} `),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => {
          await ctx.data.Courses.update(course.id, { keyDates: (course.keyDates || []).filter((k) => k !== kd) });
          rerender();
        },
      }),
    ]));
  }
  const labelInput = el('input', { type: 'text', placeholder: 'Label (e.g. Midterm)' });
  const dateInput = el('input', {
    type: 'date',
    onchange: async (e) => {
      if (!e.target.value || !labelInput.value.trim()) return;
      const keyDates = [...(course.keyDates || []), { label: labelInput.value.trim(), date: e.target.value }];
      await ctx.data.Courses.update(course.id, { keyDates });
      rerender();
    },
  });
  return el('div', {}, [list, el('div', { class: 'mer-person-form' }, [labelInput, dateInput])]);
}

async function readingListSection(course, ctx) {
  if (!course.readingListTag) {
    return el('p', { class: 'mer-muted', text: 'Set a reading-list tag above, then tag Links entries with it to build this course’s reading list.' });
  }
  const links = await ctx.data.Links.list();
  const tagged = links.filter((l) => (l.tags || []).includes(course.readingListTag));
  if (!tagged.length) {
    return el('p', { class: 'mer-muted', text: `No Links tagged "#${course.readingListTag}" yet.` });
  }
  return el('ul', { class: 'mer-feed' }, tagged.map((l) =>
    el('li', { class: 'mer-feed-item' }, [el('a', { href: l.url, target: '_blank', rel: 'noopener', text: l.title || l.url })])
  ));
}

function courseDetail(course, ctx, rerender) {
  const patch = (fields) => ctx.data.Courses.update(course.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const nameInput = el('input', { type: 'text', value: course.name || '', onchange: (e) => patch({ name: e.target.value }) });
  const creditsInput = el('input', { type: 'number', min: '0', step: '0.5', value: course.credits ?? 3, onchange: (e) => patch({ credits: Number(e.target.value) || 0 }) });
  const gradeSelect = el('select', { onchange: (e) => patch({ grade: e.target.value || null }) },
    GRADE_OPTIONS.map((g) => el('option', { value: g, text: g || '(none yet)', selected: g === (course.grade || '') })));
  const tagInput = el('input', { type: 'text', value: course.readingListTag || '', placeholder: 'e.g. econ101', onchange: (e) => patch({ readingListTag: e.target.value.trim() }) });
  const notesInput = el('textarea', { rows: '3', placeholder: 'Course notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(course.notes || '')]);

  const readingPlaceholder = el('p', { class: 'mer-muted', text: 'Loading…' });

  const detail = el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: course.name || '(untitled course)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedCourseId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Name', nameInput),
      field('Credits', creditsInput),
      field('Final grade', gradeSelect),
      field('Reading-list tag', tagInput),
    ]),
    el('div', { class: 'mer-subsection-label', text: 'Notes' }),
    notesInput,
    el('div', { class: 'mer-subsection-label', text: 'Key dates' }),
    keyDatesEditor(course, ctx, rerender),
    el('div', { class: 'mer-subsection-label', text: 'Reading list (via Links tag)' }),
    readingPlaceholder,
    el('button', {
      type: 'button', text: 'View assignments →',
      onclick: () => { state.courseId = course.id; rerender(); },
    }),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete course',
      onclick: async () => { await ctx.data.Courses.remove(course.id); state.selectedCourseId = null; rerender(); },
    }),
  ]);

  readingListSection(course, ctx).then((section) => readingPlaceholder.replaceWith(section));
  return detail;
}

function courseCard(course, assignments, ctx, onSelect) {
  const done = assignments.filter((a) => a.status === 'done').length;
  return (() => {
    const card = el('div', { class: 'mer-place-card' }, [
      el('div', { class: 'mer-place-body' }, [
        el('div', { class: 'mer-place-name', text: course.name || '(untitled course)' }),
        el('div', { class: 'mer-place-meta' }, [
          course.grade ? el('span', { class: 'mer-chip', text: course.grade }) : null,
          el('span', { class: 'mer-chip', text: `${course.credits ?? 3} cr` }),
          assignments.length ? el('span', { class: 'mer-chip', text: `${done}/${assignments.length} done` }) : null,
        ]),
      ]),
    ]);
    card.addEventListener('click', () => onSelect(course.id));
    return card;
  })();
}

async function renderCourses(container, ctx, rerender) {
  const semester = await ctx.data.Semesters.get(state.semesterId);
  container.append(el('div', { class: 'mer-toolbar' }, [
    el('button', { type: 'button', text: '← Semesters', onclick: () => { state.semesterId = null; state.selectedCourseId = null; rerender(); } }),
    el('h2', { text: semester?.name || 'Semester' }),
  ]));

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New course — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Courses.create({ semesterId: state.semesterId, name: e.target.value.trim(), credits: 3 });
      e.target.value = '';
      rerender();
    },
  });
  container.append(quickAdd);

  const [allCourses, allAssignments] = await Promise.all([ctx.data.Courses.list(), ctx.data.Assignments.list()]);
  const courses = allCourses.filter((c) => c.semesterId === state.semesterId);

  if (!courses.length) {
    container.append(el('p', { class: 'mer-muted', text: 'No courses in this semester yet.' }));
  } else {
    const grid = el('div', { class: 'mer-place-grid' });
    for (const course of courses) {
      grid.append(courseCard(course, allAssignments.filter((a) => a.courseId === course.id), ctx, (id) => {
        state.selectedCourseId = state.selectedCourseId === id ? null : id;
        rerender();
      }));
    }
    container.append(grid);
  }

  if (state.selectedCourseId) {
    const course = courses.find((c) => c.id === state.selectedCourseId);
    if (course) container.append(courseDetail(course, ctx, rerender));
  }
}

// --- Assignments level ---

function assignmentRow(assignment, pacingGap, ctx, onSelect) {
  const meta = el('div', { class: 'mer-task-meta' });
  if (assignment.dueDate) {
    meta.append(el('span', {
      class: isPast(assignment.dueDate) && assignment.status !== 'done' ? 'mer-chip is-overdue' : 'mer-chip',
      text: fmtDate(assignment.dueDate),
    }));
  }
  if (assignment.status === 'in_progress') meta.append(el('span', { class: 'mer-chip', text: `${assignment.percentComplete || 0}%` }));
  if (assignment.timeSpentMinutes) meta.append(el('span', { class: 'mer-chip', text: `${(assignment.timeSpentMinutes / 60).toFixed(1)}h` }));
  if (typeof assignment.grade === 'number') meta.append(el('span', { class: 'mer-chip', text: `${assignment.grade}%` }));
  if (pacingGap) {
    meta.append(el('span', {
      class: 'mer-chip is-overdue',
      text: `${pacingGap.gap} ${assignment.pacingUnit || 'pages'} behind pace`,
    }));
  }

  const row = el('div', { class: 'mer-task-row' }, [
    el('input', {
      type: 'checkbox', checked: assignment.status === 'done',
      onclick: (e) => { e.stopPropagation(); ctx.data.Assignments.update(assignment.id, { status: e.target.checked ? 'done' : 'not_started' }); },
    }),
    el('span', { class: assignment.status === 'done' ? 'mer-task-title is-done' : 'mer-task-title', text: assignment.title || '(untitled)' }),
    meta,
  ]);
  row.addEventListener('click', () => onSelect(assignment.id));
  return row;
}

// --- Academic pacing check: a target + unit, self-set checkpoints ("6
// pages by March 3"), and a dated progress log -- see PROJECT_SPEC.md's
// "Academic pacing check" entry. Checkpoints have no id of their own
// (same convention as courseDetail's keyDatesEditor above): deletion
// filters by reference equality within the array pulled from this same
// render pass, not a stored id.

function pacingCheckpointsEditor(assignment, ctx, rerender) {
  const unit = assignment.pacingUnit || 'pages';
  const list = el('div', { class: 'mer-visit-dates' });
  for (const cp of (assignment.paceCheckpoints || []).slice().sort((a, b) => a.date.localeCompare(b.date))) {
    list.append(el('span', { class: 'mer-chip' }, [
      document.createTextNode(`By ${fmtDate(cp.date)}: ${cp.targetByThen} ${unit} `),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => {
          const paceCheckpoints = (assignment.paceCheckpoints || []).filter((c) => c !== cp);
          await ctx.data.Assignments.update(assignment.id, { paceCheckpoints });
          rerender();
        },
      }),
    ]));
  }
  const dateInput = el('input', { type: 'date' });
  const targetInput = el('input', { type: 'number', min: '0', step: '1', placeholder: `${unit} by then` });
  const addBtn = el('button', {
    type: 'button', text: '+ Add checkpoint',
    onclick: async () => {
      if (!dateInput.value || !targetInput.value) return;
      const paceCheckpoints = [...(assignment.paceCheckpoints || []), { date: dateInput.value, targetByThen: Number(targetInput.value) }];
      await ctx.data.Assignments.update(assignment.id, { paceCheckpoints });
      rerender();
    },
  });
  return el('div', {}, [list, el('div', { class: 'mer-person-form' }, [dateInput, targetInput, addBtn])]);
}

function progressLogEditor(assignment, logs, ctx, rerender) {
  const unit = assignment.pacingUnit || 'pages';
  const total = logs.reduce((sum, l) => sum + (Number(l.unitsAdded) || 0), 0);
  const list = el('div', { class: 'mer-people-list' });
  for (const log of logs.slice().sort((a, b) => (a.date < b.date ? 1 : -1))) {
    list.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: `+${log.unitsAdded} ${unit}` }),
        el('div', { class: 'mer-person-meta', text: fmtDate(log.date) }),
      ]),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => { await ctx.data.AssignmentProgressLogs.remove(log.id); rerender(); },
      }),
    ]));
  }
  const unitsInput = el('input', { type: 'number', min: '1', placeholder: `${unit} added today` });
  const logBtn = el('button', {
    type: 'button', text: 'Log session',
    onclick: async () => {
      const unitsAdded = Number(unitsInput.value);
      if (!unitsAdded || unitsAdded < 1) return;
      await ctx.data.AssignmentProgressLogs.create({ assignmentId: assignment.id, date: todayStr(), unitsAdded });
      rerender();
    },
  });
  return el('div', {}, [
    el('p', { class: 'mer-muted', text: `${total} ${unit} logged so far.` }),
    logs.length ? list : null,
    el('div', { class: 'mer-person-form' }, [unitsInput, logBtn]),
  ].filter(Boolean));
}

function pacingSection(assignment, logs, ctx, rerender) {
  const patch = (fields) => ctx.data.Assignments.update(assignment.id, fields).then(rerender);
  const unit = assignment.pacingUnit || 'pages';
  const targetInput = el('input', {
    type: 'number', min: '0', value: assignment.pacingTarget ?? '', placeholder: 'Total due',
    onchange: (e) => patch({ pacingTarget: e.target.value === '' ? null : Number(e.target.value) }),
  });
  const unitSelect = el('select', { onchange: (e) => patch({ pacingUnit: e.target.value }) }, [
    el('option', { value: 'pages', text: 'pages', selected: unit === 'pages' }),
    el('option', { value: 'words', text: 'words', selected: unit === 'words' }),
  ]);

  const status = ctx.data.pacingStatusFor(assignment, logs);
  const statusLine = status
    ? el('p', {
        class: status.gap > 0 ? 'mer-sync-error' : 'mer-muted',
        text: status.gap > 0
          ? `You said you wanted ${status.checkpoint.targetByThen} ${unit} done by ${fmtDate(status.checkpoint.date)} — you've logged ${status.loggedTotal} so far. ${status.gap} ${unit} short. Still on track, or did you just forget to log recent work?`
          : `On track — ${status.loggedTotal} ${unit} logged, ${status.checkpoint.targetByThen} ${unit} was the target by ${fmtDate(status.checkpoint.date)}.`,
      })
    : null;

  return el('div', {}, [
    el('div', { class: 'mer-field-grid' }, [
      el('label', { class: 'mer-field' }, [el('span', { text: 'Total due' }), targetInput]),
      el('label', { class: 'mer-field' }, [el('span', { text: 'Unit' }), unitSelect]),
    ]),
    statusLine,
    el('div', { class: 'mer-subsection-label', text: 'Pacing checkpoints (your own intention)' }),
    pacingCheckpointsEditor(assignment, ctx, rerender),
    el('div', { class: 'mer-subsection-label', text: 'Progress log' }),
    progressLogEditor(assignment, logs, ctx, rerender),
  ].filter(Boolean));
}

function assignmentDetail(assignment, logs, ctx, rerender) {
  const patch = (fields) => ctx.data.Assignments.update(assignment.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const titleInput = el('input', { type: 'text', value: assignment.title || '', onchange: (e) => patch({ title: e.target.value }) });
  const dueInput = el('input', { type: 'date', value: assignment.dueDate || '', onchange: (e) => patch({ dueDate: e.target.value || null }) });
  const statusSelect = el('select', { onchange: (e) => patch({ status: e.target.value }) },
    ASSIGNMENT_STATUSES.map((s) => el('option', { value: s.value, text: s.label, selected: s.value === (assignment.status || 'not_started') })));
  const percentInput = el('input', {
    type: 'range', min: '0', max: '100', value: assignment.percentComplete || 0,
    oninput: (e) => { percentLabel.textContent = `${e.target.value}%`; },
    onchange: (e) => patch({ percentComplete: Number(e.target.value) }),
  });
  const percentLabel = el('span', { text: `${assignment.percentComplete || 0}%` });
  const timeInput = el('input', { type: 'number', min: '0', value: assignment.timeSpentMinutes ?? '', placeholder: 'minutes', onchange: (e) => patch({ timeSpentMinutes: Number(e.target.value) || 0 }) });
  const gradeInput = el('input', { type: 'number', min: '0', max: '100', value: assignment.grade ?? '', placeholder: '0-100', onchange: (e) => patch({ grade: e.target.value === '' ? null : Number(e.target.value) }) });

  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: assignment.title || '(untitled)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedAssignmentId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Title', titleInput),
      field('Due date', dueInput),
      field('Status', statusSelect),
      field('Time spent (min)', timeInput),
      field('Grade (%)', gradeInput),
    ]),
    el('div', { class: 'mer-subsection-label', text: '% complete' }),
    el('div', { class: 'mer-snooze-row' }, [percentInput, percentLabel]),
    el('div', { class: 'mer-subsection-label', text: 'Pacing' }),
    pacingSection(assignment, logs, ctx, rerender),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete assignment',
      onclick: async () => { await ctx.data.Assignments.remove(assignment.id); state.selectedAssignmentId = null; rerender(); },
    }),
  ]);
}

async function renderAssignments(container, ctx, rerender) {
  const [course, semester] = [await ctx.data.Courses.get(state.courseId), await ctx.data.Semesters.get(state.semesterId)];
  container.append(el('div', { class: 'mer-toolbar' }, [
    el('button', { type: 'button', text: '← Courses', onclick: () => { state.courseId = null; rerender(); } }),
    el('h2', { text: `${semester?.name || ''} · ${course?.name || 'Course'}` }),
  ]));

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New assignment — type a title and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Assignments.create({ courseId: state.courseId, title: e.target.value.trim(), status: 'not_started' });
      e.target.value = '';
      rerender();
    },
  });
  container.append(quickAdd);

  const assignments = (await ctx.data.Assignments.byIndex('courseId', state.courseId))
    .sort((a, b) => (a.dueDate || '9999') < (b.dueDate || '9999') ? -1 : 1);
  const pacingGaps = await ctx.data.getAssignmentPacingGaps();
  const gapByAssignment = new Map(pacingGaps.map((g) => [g.assignment.id, g]));

  if (!assignments.length) {
    container.append(el('p', { class: 'mer-muted', text: 'No assignments yet.' }));
  } else {
    for (const a of assignments) {
      container.append(assignmentRow(a, gapByAssignment.get(a.id), ctx, (id) => { state.selectedAssignmentId = state.selectedAssignmentId === id ? null : id; rerender(); }));
    }
  }

  if (state.selectedAssignmentId) {
    const a = assignments.find((x) => x.id === state.selectedAssignmentId);
    if (a) {
      const logs = await ctx.data.AssignmentProgressLogs.byIndex('assignmentId', a.id);
      container.append(assignmentDetail(a, logs, ctx, rerender));
    }
  }
}

// --- GPA & Time summary ---

async function renderSummary(container, ctx) {
  const [courses, assignments] = await Promise.all([ctx.data.Courses.list(), ctx.data.Assignments.list()]);

  const graded = courses.filter((c) => c.grade && GRADE_POINTS[c.grade] !== undefined);
  if (graded.length) {
    const totalCredits = graded.reduce((sum, c) => sum + (c.credits ?? 3), 0);
    const totalPoints = graded.reduce((sum, c) => sum + GRADE_POINTS[c.grade] * (c.credits ?? 3), 0);
    const gpa = totalCredits ? totalPoints / totalCredits : 0;
    container.append(el('h3', { text: `Running GPA: ${gpa.toFixed(2)}` }), el('p', { class: 'mer-muted', text: `Across ${graded.length} graded course${graded.length === 1 ? '' : 's'}, weighted by credits.` }));
  } else {
    container.append(el('p', { class: 'mer-muted', text: 'No final grades entered yet — set one in a course’s detail view.' }));
  }

  const courseById = new Map(courses.map((c) => [c.id, c]));
  const rows = assignments
    .filter((a) => typeof a.grade === 'number')
    .map((a) => ({ ...a, courseName: courseById.get(a.courseId)?.name || '(unknown course)' }))
    .sort((a, b) => (b.timeSpentMinutes || 0) - (a.timeSpentMinutes || 0));

  container.append(el('div', { class: 'mer-subsection-label', text: 'Time invested vs. grade' }));
  if (!rows.length) {
    container.append(el('p', { class: 'mer-muted', text: 'No graded assignments with time logged yet.' }));
    return;
  }
  const table = el('table', { class: 'mer-table' }, [
    el('thead', {}, [el('tr', {}, [
      el('th', { text: 'Assignment' }), el('th', { text: 'Course' }), el('th', { text: 'Time (h)' }), el('th', { text: 'Grade' }),
    ])]),
    el('tbody', {}, rows.map((r) => el('tr', {}, [
      el('td', { text: r.title || '(untitled)' }),
      el('td', { text: r.courseName }),
      el('td', { text: ((r.timeSpentMinutes || 0) / 60).toFixed(1) }),
      el('td', { text: `${r.grade}%` }),
    ]))),
  ]);
  container.append(table);
}

export async function renderEducation(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Education' }));
  canvas.append(el('div', { class: 'mer-toolbar' }, [
    el('div', { class: 'mer-toggle-group' }, [
      el('button', { type: 'button', class: state.tab === 'coursework' ? 'is-active' : '', text: 'Coursework', onclick: () => { state.tab = 'coursework'; rerender(); } }),
      el('button', { type: 'button', class: state.tab === 'summary' ? 'is-active' : '', text: 'GPA & Time', onclick: () => { state.tab = 'summary'; rerender(); } }),
    ]),
  ]));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  if (state.tab === 'summary') {
    await renderSummary(area, ctx);
  } else if (state.courseId) {
    await renderAssignments(area, ctx, rerender);
  } else if (state.semesterId) {
    await renderCourses(area, ctx, rerender);
  } else {
    await renderSemesters(area, ctx, rerender);
  }
}

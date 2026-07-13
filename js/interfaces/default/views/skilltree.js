// Skill Trees — an RPG-style character sheet computed entirely from
// activity you already have elsewhere (habits, books, tasks). No new
// storage; pure view, same spirit as the Daily Paper and the Museum.
//
// Leveling curve: level = floor(sqrt(xp / 10)) + 1, so early levels come
// fast and later ones take meaningfully more — a familiar RPG shape without
// needing any tuning per skill.

import { el } from '../dom.js';

function levelOf(xp) {
  return Math.floor(Math.sqrt(xp / 10)) + 1;
}

// XP needed to reach a given level, inverse of levelOf's curve.
function xpForLevel(level) {
  return 10 * (level - 1) ** 2;
}

function skillBar(name, icon, xp, blurb) {
  const level = levelOf(xp);
  const thisLevelXp = xpForLevel(level);
  const nextLevelXp = xpForLevel(level + 1);
  const span = nextLevelXp - thisLevelXp;
  const pct = span > 0 ? Math.min(100, Math.round(((xp - thisLevelXp) / span) * 100)) : 100;

  return el('div', { class: 'mer-skill-row' }, [
    el('div', { class: 'mer-skill-icon', text: icon }),
    el('div', { class: 'mer-skill-body' }, [
      el('div', { class: 'mer-skill-head' }, [
        el('span', { class: 'mer-skill-name', text: name }),
        el('span', { class: 'mer-skill-level', text: `Lv. ${level}` }),
      ]),
      el('div', { class: 'mer-skill-bar' }, [
        el('div', { class: 'mer-skill-bar-fill', style: `width: ${pct}%` }),
      ]),
      el('div', { class: 'mer-muted', text: blurb }),
    ]),
  ]);
}

export async function renderSkillTree(canvas, ctx) {
  const [tasks, assignments, habits, habitLogs, books] = await Promise.all([
    ctx.data.Tasks.list(),
    ctx.data.Assignments.list(),
    ctx.data.Habits.list(),
    ctx.data.HabitLogs.list(),
    ctx.data.Books.list(),
  ]);

  const doneCount = tasks.filter((t) => t.status === 'done').length + assignments.filter((a) => a.status === 'done').length;
  const habitCheckIns = habitLogs.length;
  const booksFinished = books.filter((b) => b.status === 'finished').length;

  const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;

  const skills = [
    { name: 'Executor', icon: '✅', xp: doneCount * 10, blurb: `${plural(doneCount, 'task/assignment')} completed` },
    { name: 'Discipline', icon: '🔥', xp: habitCheckIns * 5, blurb: `${plural(habits.length, 'habit')}, ${plural(habitCheckIns, 'check-in')} logged` },
    { name: 'Scholar', icon: '📖', xp: booksFinished * 40, blurb: `${plural(booksFinished, 'book')} finished` },
  ];

  const totalLevel = skills.reduce((sum, s) => sum + levelOf(s.xp), 0);

  canvas.append(el('h1', { text: 'Skill Trees' }));
  canvas.append(el('p', { class: 'mer-muted', text: `Character level ${totalLevel} — computed from what you've actually been doing.` }));

  const sheet = el('div', { class: 'mer-skill-sheet' });
  for (const s of skills) sheet.append(skillBar(s.name, s.icon, s.xp, s.blurb));
  canvas.append(sheet);
}

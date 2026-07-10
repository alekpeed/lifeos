// Conversation Starter generator — pick a contact, get a handful of
// starter lines drawn from several contexts (relationship, tags, an
// upcoming birthday, your own notes on them), plus a general fallback list.
// Deliberately templated, not AI-written -- no AI module exists yet (see
// PROJECT_SPEC.md), and simple templates need nothing external.

import { el, fmtDate, todayStr } from '../dom.js';

let state = { contactId: null };

const RELATIONSHIP_OPENERS = {
  friend: "It's been a while — what have you been up to?",
  family: 'How is the family doing?',
  coworker: "How's work been treating you lately?",
  colleague: "How's work been treating you lately?",
};

const GENERAL_OPENERS = [
  "What's the best thing that's happened to you this month?",
  'Read or watched anything good lately?',
  "What's something you're looking forward to?",
  'Any trips coming up?',
];

function daysUntilNextBirthday(birthday) {
  if (!birthday) return null;
  const [, m, d] = birthday.split('-').map(Number);
  const today = new Date(todayStr() + 'T00:00:00');
  let next = new Date(today.getFullYear(), m - 1, d);
  if (next < today) next = new Date(today.getFullYear() + 1, m - 1, d);
  return Math.round((next - today) / 86400000);
}

function buildStarters(contact) {
  const groups = [];

  const rel = (contact.relationship || '').toLowerCase().trim();
  if (RELATIONSHIP_OPENERS[rel]) {
    groups.push({ label: 'Based on your relationship', lines: [RELATIONSHIP_OPENERS[rel]] });
  }

  if ((contact.tags || []).length) {
    groups.push({
      label: 'Shared interests',
      lines: contact.tags.map((t) => `Ask how their ${t} has been going.`),
    });
  }

  const days = daysUntilNextBirthday(contact.birthday);
  if (days !== null && days <= 30) {
    groups.push({
      label: 'Coming up',
      lines: [days === 0
        ? "It's their birthday today!"
        : `Their birthday is in ${days} day${days === 1 ? '' : 's'} — worth mentioning plans?`],
    });
  }

  if ((contact.notes || '').trim()) {
    groups.push({ label: 'From your notes', lines: [`You noted: "${contact.notes.trim()}"`] });
  }

  groups.push({ label: 'General icebreakers', lines: GENERAL_OPENERS });

  return groups;
}

function contactOption(c) {
  return el('option', { value: c.id, text: c.name || '(untitled)' });
}

export async function renderStarters(canvas, ctx, rerender) {
  const contacts = await ctx.data.Contacts.list();
  const sorted = [...contacts].sort((a, b) => (a.name || '').localeCompare(b.name || ''));

  canvas.append(el('h1', { text: 'Conversation Starters' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Pick someone and get a few contextual openers.' }));

  if (!sorted.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'No contacts yet — add one in Contacts first.' }));
    return;
  }

  if (!state.contactId || !sorted.some((c) => c.id === state.contactId)) {
    state.contactId = sorted[0].id;
  }

  const select = el('select', {
    onchange: (e) => { state.contactId = e.target.value; rerender(); },
  }, sorted.map((c) => {
    const opt = contactOption(c);
    if (c.id === state.contactId) opt.selected = true;
    return opt;
  }));
  canvas.append(el('div', { class: 'mer-person-form' }, [select]));

  const contact = sorted.find((c) => c.id === state.contactId);
  const groups = buildStarters(contact);

  for (const group of groups) {
    canvas.append(el('div', { class: 'mer-subsection-label', text: group.label }));
    const list = el('ul', { class: 'mer-starter-list' });
    for (const line of group.lines) list.append(el('li', { text: line }));
    canvas.append(list);
  }
}

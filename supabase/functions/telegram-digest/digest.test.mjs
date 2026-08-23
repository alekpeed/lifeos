// Tests for the digest's pure half.
//   node --experimental-strip-types --test supabase/functions/telegram-digest/digest.test.mjs
// (Node 22 needs the flag to import a .ts file directly; CI runs the same command
// before the function deploys, so a shape drift blocks the deploy rather than
// reaching production and going quiet.)
//
// This code mirrors six Kotlin data shapes across a language boundary, with no compiler
// holding the two sides together — so a field rename in Kotlin would silently produce an
// empty digest forever, and the failure mode is silence, which nobody notices. The
// fixtures below are the exact JSON the Kotlin serializers emit, and the shape tests
// exist to fail loudly when they stop matching.

import assert from 'node:assert/strict';
import test from 'node:test';
import { buildMessage, daysUntil, itemsFrom, KEYS, whenLabel } from './digest.ts';

const day = 86400000;
const iso = (offsetDays) => new Date(Date.now() + offsetDays * day).toISOString().slice(0, 10);

test('a date reads as whole days out, ignoring any time of day', () => {
  assert.equal(daysUntil(iso(0)), 0);
  assert.equal(daysUntil(iso(3)), 3);
  assert.equal(daysUntil(iso(-2)), -2);
  // M-01a widened stored dates to "yyyy-MM-ddTHH:mm"; only the date half matters here.
  assert.equal(daysUntil(`${iso(1)}T07:25`), 1);
  assert.equal(daysUntil(''), null);
  assert.equal(daysUntil('not a date'), null);
  assert.equal(daysUntil(undefined), null);
});

test('Tasks is a bare array at the root, and done ones are not owed', () => {
  const blob = [
    { id: 1, title: 'Renew passport', status: 'not_started', due: iso(1) },
    { id: 2, title: 'Already done', status: 'done', due: iso(1) },
    { id: 3, title: 'Far off', status: 'not_started', due: iso(30) },
    { id: 4, title: 'No date', status: 'not_started', due: '' },
  ];
  assert.deepEqual(itemsFrom('Tasks', blob).map((i) => i.title), ['Renew passport']);
});

test('an overdue task still counts, however far back it goes', () => {
  const blob = [{ id: 1, title: 'Ancient', status: 'not_started', due: iso(-400) }];
  const [item] = itemsFrom('Tasks', blob);
  assert.equal(item.when, -400);
});

test('a settled one-time bill drops out, a recurring one does not', () => {
  const blob = {
    bills: [
      { id: 1, name: 'Rent', dueDate: iso(2), cadence: 'monthly', paymentHistory: [{ date: iso(-30), amount: 1 }] },
      { id: 2, name: 'Deposit', dueDate: iso(2), cadence: 'one-time', paymentHistory: [{ date: iso(-1), amount: 1 }] },
      { id: 3, name: 'Insurance', dueDate: iso(2), cadence: 'one-time', paymentHistory: [] },
    ],
  };
  assert.deepEqual(itemsFrom('Finance', blob).map((i) => i.title), ['Rent', 'Insurance']);
});

test('autopay is said out loud, because it changes whether you must act', () => {
  const blob = { bills: [{ id: 1, name: 'Phone', dueDate: iso(1), cadence: 'monthly', autopay: true, paymentHistory: [] }] };
  assert.equal(itemsFrom('Finance', blob)[0].title, 'Phone (autopay)');
});

test('documents get their own, much longer horizon', () => {
  const blob = { documents: [{ id: 1, title: 'Passport', expiryDate: iso(20) }] };
  // Twenty days out is well past the three-day general window and still inside the
  // document one — a passport you learn about three days before it expires is useless.
  assert.equal(itemsFrom('Documents', blob).length, 1);
  assert.equal(itemsFrom('Documents', { documents: [{ id: 2, title: 'Old', expiryDate: iso(90) }] }).length, 0);
});

test('a finished or archived project is not owed', () => {
  const blob = {
    projects: [
      { id: 1, name: 'Ship it', targetDate: iso(1), status: 'ACTIVE' },
      { id: 2, name: 'Shipped', targetDate: iso(1), status: 'DONE' },
      { id: 3, name: 'Shelved', targetDate: iso(1), status: 'ARCHIVED' },
      { id: 4, name: 'On hold', targetDate: iso(1), status: 'PAUSED' },
    ],
  };
  assert.deepEqual(itemsFrom('Projects', blob).map((i) => i.title), ['Ship it', 'On hold']);
});

test('a capsule surfaces once it has opened and not been read', () => {
  const blob = {
    capsules: [
      { id: 1, title: 'For my 30th', sealedUntil: iso(-1), readAt: '' },
      { id: 2, title: 'Read already', sealedUntil: iso(-5), readAt: iso(-4) },
      { id: 3, title: 'Still sealed', sealedUntil: iso(400), readAt: '' },
    ],
  };
  assert.deepEqual(itemsFrom('Time Capsules', blob).map((i) => i.title), ['For my 30th']);
});

test('an unopened capsule with no title still says something', () => {
  const blob = { capsules: [{ id: 1, title: '', sealedUntil: iso(0), readAt: '' }] };
  assert.equal(itemsFrom('Time Capsules', blob)[0].title, 'A sealed note');
});

test('a blob of the wrong shape yields nothing rather than throwing', () => {
  // One malformed module must not cost the rest of the digest.
  for (const bad of [null, undefined, 42, 'a string', {}, { bills: 'not a list' }, []]) {
    for (const key of KEYS) {
      assert.doesNotThrow(() => itemsFrom(key, bad));
      assert.equal(itemsFrom(key, bad).length, 0);
    }
  }
});

test('an unknown key is ignored', () => {
  assert.equal(itemsFrom('Photos', { albums: [] }).length, 0);
});

test('the message leads with the most urgent and counts the overdue', () => {
  const items = [
    { title: 'Later', when: 3, kind: 'Task', key: 'Tasks', id: 1 },
    { title: 'Overdue thing', when: -2, kind: 'Bill', key: 'Finance', id: 2 },
    { title: 'Today', when: 0, kind: 'Task', key: 'Tasks', id: 3 },
  ];
  const msg = buildMessage(items);
  assert.match(msg, /3 need you, 1 overdue/);
  const lines = msg.split('\n').filter((l) => l.startsWith('•'));
  assert.match(lines[0], /Overdue thing/);
  assert.match(lines[0], /2 days overdue/);
  assert.match(lines[2], /in 3 days/);
});

test('a long list is truncated rather than sent as a wall', () => {
  const items = Array.from({ length: 40 }, (_, i) => ({ title: `Item ${i}`, when: i, kind: 'Task', key: 'Tasks', id: i }));
  const msg = buildMessage(items);
  assert.equal(msg.split('\n').filter((l) => l.startsWith('•')).length, 15);
  assert.match(msg, /and 25 more/);
});

test('nothing overdue reads as a look-ahead, not an alarm', () => {
  const msg = buildMessage([{ title: 'Rent', when: 2, kind: 'Bill', key: 'Finance', id: 1 }]);
  assert.match(msg, /1 coming up/);
  assert.doesNotMatch(msg, /overdue/);
});

test('the when-labels read like English', () => {
  assert.equal(whenLabel(-3), '3 days overdue');
  assert.equal(whenLabel(-1), 'overdue since yesterday');
  assert.equal(whenLabel(0), 'today');
  assert.equal(whenLabel(1), 'tomorrow');
  assert.equal(whenLabel(4), 'in 4 days');
});

// ---- the shapes, pinned against what Kotlin actually writes ----------------------

// These fixtures came out of the Kotlin serializers, not out of my head. If a field is
// renamed on that side, the matching assertion here fails and says which one — rather
// than the digest quietly going empty and nobody noticing for a month.
test('the field names match what the app syncs', () => {
  const fixtures = {
    Tasks: [{ id: 1, title: 't', status: 'not_started', priority: 'medium', due: iso(0), projectId: null, project: '', tags: [], notes: '', waitingOn: '', subtasks: [], recur: '', snoozedUntil: '', completedDate: '' }],
    Finance: { entries: [], bills: [{ id: 1, name: 'b', amount: 1.0, dueDate: iso(0), cadence: 'monthly', autopay: false, remindDays: 3, category: 'Bills', paymentHistory: [], contact: '', attachments: [] }], subscriptions: [] },
    Education: { semesters: [], courses: [], assignments: [{ id: 1, courseId: 1, title: 'a', dueDate: iso(0), status: 'not_started', percentComplete: 0, timeSpentMinutes: 0, grade: null, pacingTarget: null, pacingUnit: 'pages', paceCheckpoints: [], progressLogs: [] }] },
    Documents: { documents: [{ id: 1, title: 'd', category: '', issuer: '', policyNumber: '', expiryDate: iso(0), transcription: '', summary: '', notes: '', linkedContact: '', photoBlob: '', attachments: [] }] },
    Projects: { projects: [{ id: 1, name: 'p', description: '', status: 'ACTIVE', startDate: '', targetDate: iso(0), completedDate: '', notes: '', tags: [], documentIds: [], linkIds: [], contactIds: [], milestoneIds: [] }], migrated: true },
    'Time Capsules': { capsules: [{ id: 1, title: 'c', body: 'b', sealedUntil: iso(0), createdAt: '', photoBlob: '', readAt: '' }] },
  };

  for (const [key, blob] of Object.entries(fixtures)) {
    const found = itemsFrom(key, blob);
    assert.equal(found.length, 1, `${key}: expected one due item — has a field been renamed?`);
    assert.equal(found[0].when, 0);
    // The id travels with it, because a per-item push (Phase 2) names the record it is
    // about and its notification's buttons resolve that name.
    assert.equal(found[0].id, 1, `${key}: the record id did not survive the read`);
    assert.equal(found[0].key, key);
  }
});

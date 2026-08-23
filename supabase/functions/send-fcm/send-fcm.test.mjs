// Tests for the send-fcm function's pure halves.
//   node --experimental-strip-types --test supabase/functions/send-fcm/send-fcm.test.mjs
//
// This path cannot be exercised end to end without a Firebase project and a real
// device holding a real token, neither of which exists yet — which makes the parts
// that CAN be tested worth testing properly. Two things are load-bearing and would
// fail silently: the JWT signature (a bad one reads as "nothing was due today"), and
// the message shape (a `notification` block would hand the notification to Android
// and the action buttons would never appear).

import assert from 'node:assert/strict';
import test from 'node:test';
import { createPrivateKey, createPublicKey, createVerify, generateKeyPairSync } from 'node:crypto';
import { base64url, claimsFor, parseServiceAccount, pemToDer, signJwt } from './auth.ts';
import { bodyFor, buildMessage, isDeadToken, isUrgent, titleFor } from './message.ts';
import { itemsFrom, subjectOf } from '../telegram-digest/digest.ts';

const day = 86400000;
const iso = (offsetDays) => new Date(Date.now() + offsetDays * day).toISOString().slice(0, 10);

const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const PEM = privateKey.export({ type: 'pkcs8', format: 'pem' });

const account = () => ({
  projectId: 'life-os-test',
  clientEmail: 'pusher@life-os-test.iam.gserviceaccount.com',
  privateKey: PEM,
});

// ---- the service-account key ------------------------------------------------------

test('a missing or malformed key fails loudly rather than defaulting', () => {
  // A silent auth failure here looks exactly like "nothing was due today", which is
  // the one failure nobody would notice.
  assert.throws(() => parseServiceAccount(undefined), /not set/);
  assert.throws(() => parseServiceAccount('   '), /not set/);
  assert.throws(() => parseServiceAccount('not json'), /not JSON/);
  assert.throws(() => parseServiceAccount('{"project_id":"p"}'), /missing/);
});

test('escaped newlines in the PEM are put back', () => {
  // Stored through a shell or a web form, the key file's newlines usually arrive as
  // literal backslash-n, and Web Crypto rejects the result without saying why.
  const raw = JSON.stringify({
    project_id: 'p',
    client_email: 'e@p.iam.gserviceaccount.com',
    private_key: '-----BEGIN PRIVATE KEY-----\\nAAAA\\n-----END PRIVATE KEY-----\\n',
  });
  assert.match(parseServiceAccount(raw).privateKey, /-----BEGIN PRIVATE KEY-----\nAAAA\n/);
});

test('a PEM decodes to its DER bytes', () => {
  const der = pemToDer(PEM);
  assert.ok(der.length > 100);
  // A PKCS#8 blob is a DER SEQUENCE.
  assert.equal(der[0], 0x30);
  assert.throws(() => pemToDer('-----BEGIN PRIVATE KEY-----\n-----END PRIVATE KEY-----'), /PEM/);
});

test('the claims are what Google will accept', () => {
  const c = claimsFor(account(), 1_700_000_000);
  assert.equal(c.iss, 'pusher@life-os-test.iam.gserviceaccount.com');
  assert.equal(c.aud, 'https://oauth2.googleapis.com/token');
  assert.equal(c.scope, 'https://www.googleapis.com/auth/firebase.messaging');
  // An hour is Google's maximum; anything longer is rejected outright.
  assert.equal(c.exp - c.iat, 3600);
});

test('the JWT really is a valid RS256 signature over its own header and claims', async () => {
  // The whole point of writing this by hand instead of pulling in a Google SDK. If it
  // is wrong, every send fails with a 401 that no test would otherwise catch.
  const jwt = await signJwt(account(), 1_700_000_000);
  const [h, p, s] = jwt.split('.');
  assert.equal(JSON.parse(Buffer.from(h, 'base64url')).alg, 'RS256');
  assert.equal(JSON.parse(Buffer.from(p, 'base64url')).iat, 1_700_000_000);

  const verifier = createVerify('RSA-SHA256');
  verifier.update(`${h}.${p}`);
  const pub = createPublicKey(createPrivateKey(PEM));
  assert.ok(verifier.verify(pub, Buffer.from(s, 'base64url')), 'signature does not verify');
});

test('base64url leaves nothing a URL would mangle', () => {
  const encoded = base64url(new Uint8Array([251, 255, 190, 255]));
  assert.doesNotMatch(encoded, /[+/=]/);
});

// ---- what gets pushed --------------------------------------------------------------

const item = (over) => ({ title: 't', when: 0, kind: 'Task', key: 'Tasks', id: 1, ...over });

test('urgent means now, not soon', () => {
  // The digest carries the look-ahead. A push that arrives three days early is a
  // digest with a worse delivery mechanism.
  assert.ok(isUrgent(item({ when: 0 })));
  assert.ok(isUrgent(item({ when: -5 })));
  assert.ok(!isUrgent(item({ when: 1 })));
  assert.ok(!isUrgent(item({ when: 20 })));
});

test('the message is data-only, so the app draws it and the buttons exist', () => {
  // A `notification` block would make Android draw the notification itself while the
  // app is backgrounded; the app would never see the payload and the Done / Tomorrow
  // buttons would never appear — which is the entire reason for this transport.
  const msg = buildMessage(item({ title: 'Pay the plumber' }), 'device-token');
  assert.equal(msg.message.notification, undefined);
  assert.equal(msg.message.token, 'device-token');
  assert.equal(msg.message.data.subject, 'Tasks|1');
  assert.equal(msg.message.data.title, 'Pay the plumber');
  // Data-only messages are throttled unless they say they are worth waking for.
  assert.equal(msg.message.android.priority, 'high');
});

test('every value in the data payload is a string, as FCM requires', () => {
  // FCM rejects the whole message if any data value is a number or null.
  const msg = buildMessage(item({ id: 7 }), 'tok');
  for (const [k, v] of Object.entries(msg.message.data)) {
    assert.equal(typeof v, 'string', `data.${k} is ${typeof v}`);
  }
});

test('overdue is said in the title, not buried in the body', () => {
  assert.equal(titleFor(item({ when: -2, title: 'Rent' })), 'Overdue: Rent');
  assert.equal(titleFor(item({ when: 0, title: 'Rent' })), 'Rent');
  assert.equal(bodyFor(item({ when: 0 })), 'Task — due today');
  assert.equal(bodyFor(item({ when: -1 })), 'Task — overdue since yesterday');
  assert.equal(bodyFor(item({ when: -3 })), 'Task — 3 days overdue');
});

test('a record with no id carries no subject', () => {
  // The notification would have nothing to act on; index.ts drops those rather than
  // sending a push whose buttons do nothing.
  assert.equal(subjectOf(item({ id: null })), '');
  assert.equal(subjectOf(item({ key: 'Time Capsules', id: 3 })), 'Time Capsules|3');
});

test('the subject matches what the module blob actually holds', () => {
  // The id has to survive the same read the digest does, or the notification names a
  // record the app cannot find.
  const tasks = [{ id: 41, title: 'Renew passport', status: 'not_started', due: iso(0) }];
  assert.equal(subjectOf(itemsFrom('Tasks', tasks)[0]), 'Tasks|41');

  const capsules = { capsules: [{ id: 9, title: 'For my 30th', sealedUntil: iso(-1), readAt: '' }] };
  assert.equal(subjectOf(itemsFrom('Time Capsules', capsules)[0]), 'Time Capsules|9');

  // A blob written by an older build, or a hand-edited one, may have no usable id.
  const noId = [{ title: 'Orphan', status: 'not_started', due: iso(0) }];
  assert.equal(subjectOf(itemsFrom('Tasks', noId)[0]), '');
});

test('only a token FCM says is dead gets pruned', () => {
  // Deleting a token on a transient failure means that phone stops getting anything
  // until the next launch re-registers it.
  assert.ok(isDeadToken(404, 'Requested entity was not found.'));
  assert.ok(isDeadToken(400, '{"error":{"status":"INVALID_ARGUMENT"}}'));
  assert.ok(isDeadToken(403, 'SenderId mismatch'));
  assert.ok(!isDeadToken(500, 'Internal'));
  assert.ok(!isDeadToken(503, 'Unavailable'));
  assert.ok(!isDeadToken(429, 'QUOTA_EXCEEDED'));
  assert.ok(!isDeadToken(400, '{"error":{"status":"QUOTA_EXCEEDED"}}'));
});

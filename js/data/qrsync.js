// QR Airgap Sync — device-to-device sync with no server, no account, no
// internet. The agreed design: QR codes are the PAIRING channel, not the
// transport. WebRTC with *manual signaling* — the offer travels inside a QR
// code (or copy/paste), the answer comes back the same way, and once the
// two devices shake hands the actual data flows peer-to-peer over the local
// network via an RTCDataChannel. No signaling server exists anywhere in
// this flow: the QR code IS the signaling server.
//
//   Device A                                Device B
//   --------                                --------
//   createOfferSession()
//     → offer payload → [QR code] → scan →  acceptOffer(payload)
//                                             → answer payload
//   completeOffer(answer) ← [QR / paste] ←  ────┘
//     ⇄ RTCDataChannel opens (LAN, host candidates)
//     ⇄ both sides send snapshots, both merge (LWW + tombstones)
//
// Merge semantics deliberately mirror Drive sync: per-record last-write-wins
// by updatedAt, deletions propagate via tombstones (a tombstone newer than
// the incoming record keeps it dead; an incoming tombstone newer than the
// local record kills it). Settings stay device-local, attachments/sharebox
// stores are excluded in v1 (blobs need a chunked binary lane — noted in
// the UI as a known limitation).
//
// Payloads are deflate-compressed, base64url-encoded JSON — small enough
// that the offer fits in a phone-scannable QR code. ICE gathering runs to
// completion before the payload is produced (no trickle), because there is
// no side channel to trickle through.

import * as db from './db.js';
import { STORE_NAMES } from './schema.js';
import { events } from './events.js';
import { deflateSync, inflateSync, strToU8, strFromU8 } from '../../vendor/fflate/fflate.module.js';

// Stores that travel over the wire. Settings are device preferences;
// attachments carry Blobs (v1 sends JSON only); sharebox has its own sync
// paths; _tombstones ride along separately as the deletion signal.
const EXCLUDED = new Set(['settings', 'attachments', 'shareboxItems', 'shareboxFiles', '_shareboxTombstones', '_tombstones']);
const SYNC_STORES = STORE_NAMES.filter((n) => !EXCLUDED.has(n));

const CHUNK_CHARS = 60000; // well under the ~256KB datachannel comfort zone

// --- Signal payload codec (what goes in the QR) ---

function b64urlEncode(bytes) {
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function b64urlDecode(str) {
  const b64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

export function encodeSignal(obj) {
  return b64urlEncode(deflateSync(strToU8(JSON.stringify(obj))));
}

export function decodeSignal(payload) {
  return JSON.parse(strFromU8(inflateSync(b64urlDecode(payload.trim()))));
}

// --- WebRTC plumbing ---

function waitForIceComplete(pc, timeoutMs = 3000) {
  if (pc.iceGatheringState === 'complete') return Promise.resolve();
  return new Promise((resolve) => {
    const done = () => { cleanup(); resolve(); };
    const onChange = () => { if (pc.iceGatheringState === 'complete') done(); };
    const timer = setTimeout(done, timeoutMs); // LAN host candidates gather fast; don't hang forever
    const cleanup = () => { clearTimeout(timer); pc.removeEventListener('icegatheringstatechange', onChange); };
    pc.addEventListener('icegatheringstatechange', onChange);
  });
}

// No STUN/TURN servers — this is LAN-only by design. Host (and mDNS)
// candidates are exactly what we want; anything needing a relay is out of
// scope for an airgap feature.
function makePc() {
  return new RTCPeerConnection({ iceServers: [] });
}

// --- Snapshot + merge ---

async function buildSnapshot() {
  const records = {};
  let count = 0;
  for (const store of SYNC_STORES) {
    const rows = await db.getAll(store);
    if (rows.length) { records[store] = rows; count += rows.length; }
  }
  const tombstones = await db.getAll('_tombstones');
  return { v: 1, records, tombstones, count };
}

// Last-write-wins by updatedAt, tombstone-aware in both directions.
// Returns how many local writes were applied; emits one event per touched
// store so any open view refreshes.
export async function mergeSnapshot(snapshot) {
  let applied = 0;
  const touched = new Set();

  for (const [store, rows] of Object.entries(snapshot.records || {})) {
    if (!SYNC_STORES.includes(store)) continue; // never write stores we don't sync
    for (const r of rows) {
      if (!r || !r.id) continue;
      const local = await db.get(store, r.id);
      if (!local) {
        const tomb = await db.get('_tombstones', `${store}:${r.id}`);
        if (tomb && (tomb.deletedAt || '') > (r.updatedAt || '')) continue; // stays dead
        await db.put(store, r);
        applied++; touched.add(store);
      } else if ((r.updatedAt || '') > (local.updatedAt || '')) {
        await db.put(store, r);
        applied++; touched.add(store);
      }
    }
  }

  for (const t of snapshot.tombstones || []) {
    if (!t || !t.store || !t.id || !SYNC_STORES.includes(t.store)) continue;
    const local = await db.get(t.store, t.id);
    if (local && (t.deletedAt || '') > (local.updatedAt || '')) {
      await db.remove(t.store, t.id);
      await db.put('_tombstones', { ...t, key: `${t.store}:${t.id}` });
      applied++; touched.add(t.store);
    }
  }

  for (const store of touched) events.emit(store, { action: 'sync' });
  return applied;
}

// --- The session protocol over the datachannel ---
//
// Both peers behave identically once the channel opens:
//   {k:'meta', chunks, records} → {k:'chunk', i, d}×N → (receiver merges)
//   → {k:'merged', applied}
// A session is "complete" when we have BOTH merged their snapshot and heard
// that they merged ours.

function wireChannel(session, channel) {
  session.channel = channel;
  const incoming = { chunks: [], expected: null, records: 0 };

  const finishIfComplete = () => {
    if (session.mergedTheirs !== null && session.peerApplied !== null) {
      session.emit('complete', { applied: session.mergedTheirs, peerApplied: session.peerApplied, received: incoming.records, sent: session.sentCount });
    }
  };

  channel.onopen = async () => {
    session.emit('connected');
    const snapshot = await buildSnapshot();
    session.sentCount = snapshot.count;
    const json = JSON.stringify(snapshot);
    const chunks = [];
    for (let i = 0; i < json.length; i += CHUNK_CHARS) chunks.push(json.slice(i, i + CHUNK_CHARS));
    channel.send(JSON.stringify({ k: 'meta', chunks: chunks.length, records: snapshot.count }));
    for (let i = 0; i < chunks.length; i++) channel.send(JSON.stringify({ k: 'chunk', i, d: chunks[i] }));
    session.emit('sent', { records: snapshot.count });
  };

  channel.onmessage = async (e) => {
    let msg;
    try { msg = JSON.parse(e.data); } catch { return; }
    if (msg.k === 'meta') {
      incoming.expected = msg.chunks;
      incoming.records = msg.records;
      session.emit('receiving', { records: msg.records });
    } else if (msg.k === 'chunk') {
      incoming.chunks[msg.i] = msg.d;
      const have = incoming.chunks.filter((c) => c !== undefined).length;
      if (incoming.expected !== null && have === incoming.expected) {
        const snapshot = JSON.parse(incoming.chunks.join(''));
        const applied = await mergeSnapshot(snapshot);
        session.mergedTheirs = applied;
        channel.send(JSON.stringify({ k: 'merged', applied }));
        session.emit('merged', { applied });
        finishIfComplete();
      }
    } else if (msg.k === 'merged') {
      session.peerApplied = msg.applied;
      finishIfComplete();
    }
  };

  channel.onclose = () => session.emit('closed');
  channel.onerror = () => session.emit('error', { message: 'Data channel error.' });
}

function makeSession(pc) {
  const listeners = new Map();
  const session = {
    pc,
    channel: null,
    sentCount: 0,
    mergedTheirs: null,
    peerApplied: null,
    on(event, cb) { listeners.set(event, cb); },
    emit(event, detail) { listeners.get(event)?.(detail); },
    close() { try { session.channel?.close(); pc.close(); } catch { /* already closed */ } },
  };
  pc.onconnectionstatechange = () => {
    if (pc.connectionState === 'failed') session.emit('error', { message: 'Connection failed — are both devices on the same network?' });
  };
  return session;
}

export function isQrSyncSupported() {
  return typeof RTCPeerConnection === 'function';
}

// Side A: create the offer this device will show as a QR code.
export async function createOfferSession() {
  const pc = makePc();
  const session = makeSession(pc);
  const channel = pc.createDataChannel('lifeos-sync', { ordered: true });
  wireChannel(session, channel);
  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  await waitForIceComplete(pc);
  const payload = encodeSignal({ v: 1, t: 'offer', sdp: pc.localDescription.sdp });
  return { session, payload };
}

// Side B: consume a scanned/pasted offer, produce the answer to send back.
export async function acceptOffer(offerPayload) {
  const signal = decodeSignal(offerPayload);
  if (signal.t !== 'offer' || !signal.sdp) throw new Error('That doesn’t look like a sync offer.');
  const pc = makePc();
  const session = makeSession(pc);
  pc.ondatachannel = (e) => wireChannel(session, e.channel);
  await pc.setRemoteDescription({ type: 'offer', sdp: signal.sdp });
  const answer = await pc.createAnswer();
  await pc.setLocalDescription(answer);
  await waitForIceComplete(pc);
  const payload = encodeSignal({ v: 1, t: 'answer', sdp: pc.localDescription.sdp });
  return { session, payload };
}

// Side A again: feed the returned answer in; the channel opens on its own.
export async function completeOffer(session, answerPayload) {
  const signal = decodeSignal(answerPayload);
  if (signal.t !== 'answer' || !signal.sdp) throw new Error('That doesn’t look like a sync answer.');
  await session.pc.setRemoteDescription({ type: 'answer', sdp: signal.sdp });
}

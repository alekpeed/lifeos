// QR Airgap Sync — sync two devices with no server, no account, no internet.
// The QR code is the pairing handshake (it carries the WebRTC offer); the
// data itself moves peer-to-peer over the local network once the two
// devices connect. See js/data/qrsync.js for the protocol and the merge
// rules (last-write-wins + tombstones, mirroring Drive sync).
//
// The flow is built around what real devices can actually do:
//   1. Device A taps "Show sync code" → a QR appears. The QR encodes a URL
//      into this very view (#/qrsync/offer/<payload>), so device B doesn't
//      need any in-app camera code — the phone's NATIVE camera app scans
//      it and opens the app right here with the offer in hand.
//   2. Device B lands on that URL, automatically produces the ANSWER, and
//      shows it as its own QR + a copyable code.
//   3. Back on device A, paste (or scan) the answer and connect. Both
//      sides exchange snapshots and merge. Done.

import { el } from '../dom.js';
import { qrcode } from '../../../../vendor/qrcode/qrcode.mjs';

let state = {
  mode: 'idle',        // idle | offering | answering
  busy: false,         // an async signal step is in flight
  session: null,
  offerPayload: null,  // side A: what our QR carries
  answerPayload: null, // side B: what we send back
  log: [],             // status lines, newest last
  done: null,          // { applied, peerApplied, received, sent }
  error: null,
};

function reset() {
  state.session?.close();
  state = { mode: 'idle', busy: false, session: null, offerPayload: null, answerPayload: null, log: [], done: null, error: null };
}

function logLine(text, rerender) {
  state.log.push(text);
  rerender();
}

// Every session, offer- or answer-side, reports through the same events.
function wireSessionEvents(session, rerender) {
  session.on('connected', () => logLine('🔗 Connected — exchanging data…', rerender));
  session.on('sent', ({ records }) => logLine(`⬆️ Sent ${records} records.`, rerender));
  session.on('receiving', ({ records }) => logLine(`⬇️ Receiving ${records} records…`, rerender));
  session.on('merged', ({ applied }) => logLine(`✅ Merged their data: ${applied} change${applied === 1 ? '' : 's'} applied here.`, rerender));
  session.on('complete', (summary) => { state.done = summary; rerender(); });
  session.on('error', ({ message }) => { state.error = message; rerender(); });
}

function qrSvgFor(text) {
  // Error correction 'L' maximizes capacity — offer payloads are ~1KB and
  // the QR is read close-up from a bright screen, the friendliest possible
  // scanning conditions.
  const qr = qrcode(0, 'L');
  qr.addData(text);
  qr.make();
  return el('div', { class: 'mer-qrsync-code', html: qr.createSvgTag({ cellSize: 4, margin: 3, scalable: true }) });
}

function copyRow(getText) {
  const btn = el('button', {
    type: 'button', class: 'mer-reader-btn', text: 'Copy code',
    onclick: async () => {
      try { await navigator.clipboard.writeText(getText()); btn.textContent = 'Copied'; setTimeout(() => { btn.textContent = 'Copy code'; }, 1200); }
      catch { btn.textContent = 'Copy failed'; }
    },
  });
  return el('div', { class: 'mer-toolbar' }, [btn]);
}

function statusArea() {
  if (!state.log.length && !state.done && !state.error) return null;
  const wrap = el('div', {});
  if (state.log.length) {
    const ul = el('ul', { class: 'mer-starter-list' });
    for (const line of state.log) ul.append(el('li', { text: line }));
    wrap.append(ul);
  }
  if (state.done) {
    wrap.append(el('div', { class: 'mer-qrsync-done' }, [
      el('strong', { text: 'Sync complete. ' }),
      el('span', { text: `Applied ${state.done.applied} change${state.done.applied === 1 ? '' : 's'} here; the other device applied ${state.done.peerApplied}.` }),
    ]));
  }
  if (state.error) wrap.append(el('p', { class: 'mer-sync-error', text: state.error }));
  return wrap;
}

// --- Side A: this device starts, shows the QR ---

async function startOffering(ctx, rerender) {
  state.mode = 'offering';
  state.busy = true;
  rerender();
  try {
    const { session, payload } = await ctx.data.QrSync.createOfferSession();
    state.session = session;
    state.offerPayload = payload;
    wireSessionEvents(session, rerender);
  } catch (err) {
    state.error = err.message || String(err);
  }
  state.busy = false;
  rerender();
}

function renderOffering(canvas, ctx, rerender) {
  if (state.busy) { canvas.append(el('p', { class: 'mer-muted', text: 'Preparing sync code…' })); return; }
  if (state.offerPayload) {
    const url = `${location.origin}${location.pathname}#/qrsync/offer/${state.offerPayload}`;
    canvas.append(el('div', { class: 'mer-subsection-label', text: 'Step 1 — scan this with the other device' }));
    canvas.append(el('p', { class: 'mer-muted', text: 'Point the other device’s normal camera app at this code — it opens Life OS there with the handshake already loaded. Keep this screen open.' }));
    canvas.append(qrSvgFor(url));
    canvas.append(copyRow(() => url));
    canvas.append(el('textarea', { rows: '3', class: 'mer-qrsync-payload', readonly: true }, [document.createTextNode(url)]));

    if (!state.done) {
      canvas.append(el('div', { class: 'mer-subsection-label', text: 'Step 2 — paste the reply code from the other device' }));
      const answerIn = el('textarea', { rows: '3', placeholder: 'Paste the reply code here…' });
      canvas.append(el('div', { class: 'mer-person-form' }, [answerIn, el('button', {
        type: 'button', text: 'Connect',
        onclick: async () => {
          try { await ctx.data.QrSync.completeOffer(state.session, answerIn.value); }
          catch (err) { state.error = err.message || String(err); rerender(); }
        },
      })]));
    }
  }
  const status = statusArea();
  if (status) canvas.append(status);
}

// --- Side B: arrived via a scanned QR (or a pasted offer) ---

async function startAnswering(ctx, rerender, offerPayload) {
  state.mode = 'answering';
  state.busy = true;
  rerender();
  try {
    const { session, payload } = await ctx.data.QrSync.acceptOffer(offerPayload);
    state.session = session;
    state.answerPayload = payload;
    wireSessionEvents(session, rerender);
  } catch (err) {
    state.error = err.message || String(err);
  }
  state.busy = false;
  rerender();
}

function renderAnswering(canvas, ctx, rerender) {
  if (state.busy) { canvas.append(el('p', { class: 'mer-muted', text: 'Reading the sync code…' })); return; }
  if (state.answerPayload && !state.done) {
    canvas.append(el('div', { class: 'mer-subsection-label', text: 'Reply code — get this back to the first device' }));
    canvas.append(el('p', { class: 'mer-muted', text: 'Scan this from the first device if it has a camera, or copy the code and paste it there. The moment it’s entered, the two devices connect and sync.' }));
    canvas.append(qrSvgFor(state.answerPayload));
    canvas.append(copyRow(() => state.answerPayload));
    canvas.append(el('textarea', { rows: '3', class: 'mer-qrsync-payload', readonly: true }, [document.createTextNode(state.answerPayload)]));
  }
  const status = statusArea();
  if (status) canvas.append(status);
}

// --- Idle: choose a role ---

function renderIdle(canvas, ctx, rerender) {
  canvas.append(el('p', { class: 'mer-muted', text: 'Sync two devices directly over your local network — no server, no account, no internet. One device shows a code, the other scans it, and your data meets in the middle (newest edit wins, deletions carry over).' }));
  canvas.append(el('div', { class: 'mer-toolbar' }, [
    el('button', { type: 'button', text: '📱 Show sync code (start here)', onclick: () => startOffering(ctx, rerender) }),
  ]));
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Or paste a sync code' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'If the other device showed you a code as text instead of a QR, paste it here.' }));
  const offerIn = el('textarea', { rows: '3', placeholder: 'Paste a sync code…' });
  canvas.append(el('div', { class: 'mer-person-form' }, [offerIn, el('button', {
    type: 'button', text: 'Accept',
    onclick: () => {
      // The code might be the full scanned URL or the bare payload.
      const raw = offerIn.value.trim();
      const marker = '#/qrsync/offer/';
      const payload = raw.includes(marker) ? raw.slice(raw.indexOf(marker) + marker.length) : raw;
      if (payload) startAnswering(ctx, rerender, payload);
    },
  })]));
  canvas.append(el('p', { class: 'mer-qrsync-note', text: 'Known limits: both devices must be on the same network while syncing; photo/PDF attachments don’t travel over this path yet (records do).' }));
}

export async function renderQrSync(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'QR Sync' }));

  if (!ctx.data.QrSync.isSupported()) {
    canvas.append(el('p', { class: 'mer-muted', text: 'This browser doesn’t support the peer-to-peer connection this feature needs.' }));
    return;
  }

  // A scanned QR lands here as #/qrsync/offer/<payload>. Consume it once,
  // then scrub the URL so a refresh doesn't try to redeem a stale offer.
  const rest = ctx.parseRoute().rest;
  if (state.mode === 'idle' && rest[0] === 'offer' && rest[1]) {
    const payload = rest[1];
    window.history.replaceState({}, document.title, `${location.origin}${location.pathname}#/qrsync`);
    startAnswering(ctx, rerender, payload);
    canvas.append(el('p', { class: 'mer-muted', text: 'Reading the sync code…' }));
    return;
  }

  if (state.mode !== 'idle') {
    canvas.append(el('div', { class: 'mer-toolbar' }, [
      el('button', { type: 'button', text: '← Start over', onclick: () => { reset(); rerender(); } }),
    ]));
  }

  if (state.mode === 'offering') renderOffering(canvas, ctx, rerender);
  else if (state.mode === 'answering') renderAnswering(canvas, ctx, rerender);
  else renderIdle(canvas, ctx, rerender);
}

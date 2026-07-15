// nfc.js — read NFC tags and act on them (FUTURE_FEATURES.md §13).
//
// The pantry-shelf use case: tap a tag → open that shelf's restock list. A tag
// holding a `lifeos://open/<route>` URI navigates straight to that module; a tag
// holding a plain URL is saved to Links; plain text becomes an Idea.
//
// NO-BUILD NOTE (same as the other native modules): no bare
// '@exxili/capacitor-nfc' import — the buildless web app can't resolve it. The
// npm package only wires the NATIVE Android side via `cap sync`; on the JS side
// we reach the plugin through window.Capacitor.Plugins.NFC. In a plain browser
// that's absent, so everything here is a safe no-op and the capability layer
// hides the feature.
//
// Android specifics (from the plugin): reading is automatic while the app is in
// the foreground (the plugin sets up NFC foreground dispatch), so there's no
// `startScan` to call — we just listen for the `nfcTag` event. Tag reads require
// Android 13+. The app must declare the NFC permission (done in AndroidManifest).

import { hasCapability } from './capabilities.js';
import { Links, Ideas } from '../data/api.js';

function plugin() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    return (c && c.Plugins && c.Plugins.NFC) || null;
  } catch {
    return null;
  }
}

/** Available only in a native Android build with the NFC plugin present. */
export function canNfc() {
  return hasCapability('nfc') && !!plugin();
}

// Standard NFC Forum URI record prefix table (index → prefix string).
const URI_PREFIXES = [
  '', 'http://www.', 'https://www.', 'http://', 'https://', 'tel:', 'mailto:',
  'ftp://anonymous:anonymous@', 'ftp://ftp.', 'ftps://', 'sftp://', 'smb://',
  'nfs://', 'ftp://', 'dav://', 'news:', 'telnet://', 'imap:', 'rtsp://',
  'urn:', 'pop:', 'sip:', 'sips:', 'tftp:', 'btspp://', 'btl2cap://',
  'btgoep://', 'tcpobex://', 'irdaobex://', 'file://', 'urn:epc:id:',
  'urn:epc:tag:', 'urn:epc:pat:', 'urn:epc:raw:', 'urn:epc:', 'urn:nfc:',
];

function b64ToBytes(b64) {
  const bin = atob(String(b64 || ''));
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

// Decode a single NDEF record's payload to a string. Handles Well-Known Text
// ('T') and URI ('U'); anything else is best-effort UTF-8.
function recordToString(type, payloadB64) {
  let bytes;
  try { bytes = b64ToBytes(payloadB64); } catch { return ''; }
  try {
    if (type === 'T') {
      // status byte: low 6 bits = language-code length (encoding bit ignored;
      // tags in the wild are overwhelmingly UTF-8).
      const langLen = (bytes[0] || 0) & 0x3f;
      return new TextDecoder('utf-8').decode(bytes.slice(1 + langLen));
    }
    if (type === 'U') {
      const prefix = URI_PREFIXES[bytes[0]] || '';
      return prefix + new TextDecoder('utf-8').decode(bytes.slice(1));
    }
    return new TextDecoder('utf-8').decode(bytes);
  } catch {
    return '';
  }
}

// Pull the first meaningful text/URI out of a scanned tag's messages.
function firstPayload(data) {
  const messages = (data && data.messages) || [];
  for (const msg of messages) {
    for (const rec of (msg && msg.records) || []) {
      if (rec && (rec.type === 'U' || rec.type === 'T')) {
        const s = recordToString(rec.type, rec.payload).trim();
        if (s) return s;
      }
    }
  }
  // Fall back to any decodable record (skip the raw tag-ID fallback record).
  for (const msg of messages) {
    for (const rec of (msg && msg.records) || []) {
      if (rec && rec.type !== 'ID') {
        const s = recordToString(rec.type, rec.payload).trim();
        if (s) return s;
      }
    }
  }
  return '';
}

function firstUrl(s) {
  const m = /(https?:\/\/[^\s]+)/i.exec(String(s || ''));
  return m ? m[1].replace(/[)\]}.,;'"]+$/, '') : null;
}

// Act on a tag's decoded content: a lifeos:// deep link navigates; a URL is
// saved to Links; anything else becomes an Idea.
async function routeTagContent(text) {
  const t = String(text || '').trim();
  if (!t) return;
  const deep = /^lifeos:\/\/open\/?\??(?:route=)?([a-z0-9-]+)?/i.exec(t);
  if (deep && deep[1]) { window.location.hash = `#/${deep[1]}`; return; }
  try {
    const url = firstUrl(t);
    if (url) {
      const isYouTube = /(?:youtube\.com|youtu\.be)/i.test(url);
      await Links.create({ type: isYouTube ? 'video' : 'article', url, title: '', tags: ['nfc'], status: 'unread', shareWith: '', thumbnailUrl: null });
      window.location.hash = '#/links';
    } else {
      await Ideas.create({ text: t, archived: false });
      window.location.hash = '#/ideas';
    }
  } catch {
    /* best-effort: never throw out of a tag read */
  }
}

let nfcInited = false;
/** Start listening for NFC tag reads. Idempotent; no-op off-native. */
export function initNfc() {
  const p = plugin();
  if (!canNfc() || !p || nfcInited) return;
  try {
    p.addListener('nfcTag', (data) => { routeTagContent(firstPayload(data)); });
    // Surface errors quietly to the console; never interrupt the app.
    p.addListener('nfcError', () => {});
    nfcInited = true;
  } catch {
    /* no-op */
  }
}

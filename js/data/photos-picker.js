// Google Photos import via the Picker API -- an on-demand, one-shot pick
// (not a persistent "connected" sync like Drive/Calendar). Each call opens
// Google's own picker UI in a new tab, waits for the user to finish, and
// resolves with the picked items downloaded as File objects ready to hand
// to createAttachment(). Nothing is cached or synced between calls; every
// import is a fresh session scoped to exactly what's picked that time.
//
// This is part of the data layer, so it reads through gapi.js directly
// rather than through api.js (avoiding a circular import), same boundary
// choice as calendar.js.

import { acquireToken, createPickerSession, getPickerSession, deletePickerSession, listPickedMediaItems, downloadPickedMediaBytes } from './gapi.js';
import { PHOTOS_PICKER_SCOPE } from './sync-config.js';

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Polls until the user finishes picking (mediaItemsSet true), the session's
// own timeoutIn/expireTime elapses (they never finished -- closed the tab,
// walked away), or pollingConfig is missing entirely (fall back to a fixed
// 3s interval / 10min cap so a malformed response can't spin forever).
async function pollUntilDone(session) {
  const pollInterval = (session.pollingConfig?.pollInterval ? Number(session.pollingConfig.pollInterval.replace('s', '')) : 3) * 1000;
  const timeoutAt = Date.now() + (session.pollingConfig?.timeoutIn ? Number(session.pollingConfig.timeoutIn.replace('s', '')) * 1000 : 10 * 60 * 1000);
  let current = session;
  while (!current.mediaItemsSet) {
    if (Date.now() >= timeoutAt) return current; // gave up waiting -- caller treats as "nothing picked"
    await sleep(pollInterval);
    current = await getPickerSession(session.id);
  }
  return current;
}

// Opens Google's picker in a new tab, waits for the pick, and returns
// downloaded File objects (empty array if the user picked nothing / closed
// the tab without finishing). `onStatus` is an optional (text) => void
// callback so the caller can show progress ("Waiting for you to pick...",
// "Downloading 3 items...") without this module knowing about any UI.
export async function pickGooglePhotos({ onStatus } = {}) {
  await acquireToken(PHOTOS_PICKER_SCOPE, true); // interactive: shows the consent/account chooser on first use
  const session = await createPickerSession();

  const pickerWindow = window.open(`${session.pickerUri}/autoclose`, '_blank', 'noopener');
  if (!pickerWindow) throw new Error('Could not open the Google Photos picker -- check your browser\'s popup blocker.');

  onStatus?.('Waiting for you to finish picking in the new tab…');
  const finished = await pollUntilDone(session);

  if (!finished.mediaItemsSet) {
    await deletePickerSession(session.id).catch(() => {});
    return [];
  }

  const items = await listPickedMediaItems(session.id);
  await deletePickerSession(session.id).catch(() => {});
  if (!items.length) return [];

  onStatus?.(`Downloading ${items.length} item${items.length === 1 ? '' : 's'}…`);
  const files = [];
  for (const item of items) {
    const isVideo = item.type === 'VIDEO';
    const blob = await downloadPickedMediaBytes(item.mediaFile.baseUrl, isVideo);
    files.push(new File([blob], item.mediaFile.filename || 'photo', { type: item.mediaFile.mimeType || blob.type }));
  }
  return files;
}

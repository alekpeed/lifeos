// Google Picker loader. The Picker is how a user grants this app access to a
// folder it didn't create (the friend's shared "Sharebox" folder): under the
// drive.file scope, selecting a folder here authorizes the app to read/write
// inside it. Loaded on demand and network-only — never part of the offline
// app shell.

import { GOOGLE_API_KEY, PICKER_API_URL, GOOGLE_CLIENT_ID } from './sync-config.js';

let gapiLoaded = null;   // Promise: the gapi script is present
let pickerLoaded = null; // Promise: gapi.picker module is loaded

// The numeric project number (Picker's "app id") is the prefix of the client id.
const APP_ID = GOOGLE_CLIENT_ID.split('-')[0];

function loadGapi() {
  if (gapiLoaded) return gapiLoaded;
  gapiLoaded = new Promise((resolve, reject) => {
    if (window.gapi) return resolve();
    const s = document.createElement('script');
    s.src = PICKER_API_URL;
    s.async = true;
    s.defer = true;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('Could not load Google Picker (offline?).'));
    document.head.appendChild(s);
  });
  return gapiLoaded;
}

function loadPicker() {
  if (pickerLoaded) return pickerLoaded;
  pickerLoaded = loadGapi().then(() => new Promise((resolve) => {
    window.gapi.load('picker', { callback: resolve });
  }));
  return pickerLoaded;
}

// Show the folder picker. Resolves { id, name } for the chosen folder, or null
// if the user cancels. `accessToken` must be a live drive.file token.
export async function pickFolder(accessToken) {
  await loadPicker();
  const picker = window.google.picker;
  return new Promise((resolve, reject) => {
    try {
      const mine = new picker.DocsView(picker.ViewId.FOLDERS)
        .setSelectFolderEnabled(true).setIncludeFolders(true);
      const shared = new picker.DocsView(picker.ViewId.FOLDERS)
        .setSelectFolderEnabled(true).setIncludeFolders(true).setOwnedByMe(false);
      const dialog = new picker.PickerBuilder()
        .setAppId(APP_ID)
        .setOAuthToken(accessToken)
        .setDeveloperKey(GOOGLE_API_KEY)
        .setTitle('Choose your shared Sharebox folder')
        .addView(mine)
        .addView(shared)
        .setCallback((data) => {
          if (data.action === picker.Action.PICKED) {
            const doc = data.docs && data.docs[0];
            resolve(doc ? { id: doc.id, name: doc.name } : null);
          } else if (data.action === picker.Action.CANCEL) {
            resolve(null);
          }
        })
        .build();
      dialog.setVisible(true);
    } catch (err) {
      reject(err);
    }
  });
}

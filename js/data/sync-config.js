// Google Drive sync configuration and shared constants.
//
// The OAuth client ID is NOT a secret — it's a public identifier for this
// app, safe to ship in client-side code and commit to the repo. There is
// deliberately no client secret: this is a pure browser app using Google
// Identity Services' token model, which never uses a secret.
//
// Scope is drive.file — the narrowest useful Drive scope. It grants access
// ONLY to files this app itself creates (everything under the LifeOS/
// folder). It cannot see, read, or touch any of your other Drive files,
// photos, or documents.

export const GOOGLE_CLIENT_ID =
  '1048112000254-tk4de8jfqhabl4bf7nd0ecll0kdmb30c.apps.googleusercontent.com';

export const DRIVE_SCOPE = 'https://www.googleapis.com/auth/drive.file';

export const GIS_SCRIPT_URL = 'https://accounts.google.com/gsi/client';

// The visible folder in the user's Drive where all app data lives.
export const DRIVE_FOLDER_NAME = 'LifeOS';

// Per-device snapshot files are named with this prefix so we can list them.
export const SNAPSHOT_PREFIX = 'lifeos-snapshot-';

// Attachment binaries are named with this prefix inside the folder.
export const ATTACHMENT_PREFIX = 'lifeos-att-';

export const SNAPSHOT_FORMAT_VERSION = 1;

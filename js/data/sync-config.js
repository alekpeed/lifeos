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

// Calendar sync uses the narrowest possible Calendar scopes, chosen to mirror
// drive.file's philosophy. Two scopes, requested together as one grant:
//   - calendar.app.created: create/manage secondary calendars this app itself
//     creates, and their events. This is the one that does the real work.
//   - calendar.calendarlist.readonly: read-only visibility into the NAMES of
//     the user's calendars. Needed only so a second device can find the
//     "Life OS" calendar the first device already created instead of making a
//     duplicate — it grants no ability to read event data on any calendar,
//     app-created or not, and no write access at all.
// Together these can never see, read, or modify your primary calendar or any
// other calendar you already have beyond its name. Requested independently of
// Drive — connecting Calendar doesn't require Drive, and vice versa.
export const CALENDAR_SCOPE = 'https://www.googleapis.com/auth/calendar.app.created';
export const CALENDAR_LIST_SCOPE = 'https://www.googleapis.com/auth/calendar.calendarlist.readonly';
export const CALENDAR_SCOPES = `${CALENDAR_SCOPE} ${CALENDAR_LIST_SCOPE}`;

export const GIS_SCRIPT_URL = 'https://accounts.google.com/gsi/client';

// Sharebox (the shared-with-a-friend space) reuses the drive.file scope. The
// friend's shared folder isn't created by this app, so drive.file can't see it
// on its own — but selecting it through the Google Picker grants per-folder
// access under drive.file. That's the whole reason the Picker is needed.
//
// The Picker needs a browser API key (unlike the OAuth flow). It's NOT a
// secret — API keys are designed for client-side use — and this one is
// restricted (in the Cloud console) to the Picker API and this app's domain.
export const GOOGLE_API_KEY = 'AIzaSyC7OemO_wVe-ovBgADFM5hVdNwJ83Lzl9g';
export const PICKER_API_URL = 'https://apis.google.com/js/api.js';

// Per-device Sharebox snapshot files + attachment binaries, kept in the SHARED
// folder (not the user's private LifeOS/ folder). Named distinctly so a shared
// folder that happens to hold other things stays uncluttered and findable.
export const SHAREBOX_SNAPSHOT_PREFIX = 'sharebox-snapshot-';
export const SHAREBOX_ATTACHMENT_PREFIX = 'sharebox-att-';

// The visible folder in the user's Drive where all app data lives.
export const DRIVE_FOLDER_NAME = 'LifeOS';

// Per-device snapshot files are named with this prefix so we can list them.
export const SNAPSHOT_PREFIX = 'lifeos-snapshot-';

// Attachment binaries are named with this prefix inside the folder.
export const ATTACHMENT_PREFIX = 'lifeos-att-';

export const SNAPSHOT_FORMAT_VERSION = 1;

// The dedicated secondary calendar Life OS creates and pushes due-soon items
// into. Found-or-created by this summary so all your devices share one calendar.
export const CALENDAR_NAME = 'Life OS';

// How many days ahead (from today) due items are pushed as calendar events by
// default. Overdue-but-still-open items are always included regardless. Kept as
// a device-local Settings key (`calendarHorizonDays`) so it's adjustable.
export const CALENDAR_HORIZON_DEFAULT = 90;

// Every event Life OS creates carries these private extended properties, so a
// resync can find exactly its own events (never touching anything you add to
// the calendar by hand) and match each back to its source record.
export const CALENDAR_APP_TAG = 'lifeosApp';   // = '1' on every event we own
export const CALENDAR_KEY_PROP = 'lifeosKey';  // = `${store}:${id}` of the source

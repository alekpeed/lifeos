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

// Calendar sync uses the narrowest possible Calendar scope, chosen to mirror
// drive.file's philosophy: `calendar.app.created` grants access ONLY to
// secondary calendars this app itself creates. Life OS makes one dedicated
// "Life OS" calendar and can never see, read, or modify your primary calendar
// or any other calendar you already have. Requested independently of Drive —
// connecting Calendar doesn't require Drive, and vice versa.
export const CALENDAR_SCOPE = 'https://www.googleapis.com/auth/calendar.app.created';

export const GIS_SCRIPT_URL = 'https://accounts.google.com/gsi/client';

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

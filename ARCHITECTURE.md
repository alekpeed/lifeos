# Life OS — Architecture

## Layers

```
js/data/        DATA LAYER — IndexedDB, schema, CRUD API, change events, (later) Drive sync
js/shell.js     SHELL — routing, interface lifecycle, theme/density/accent application
js/modules.js   App-level module metadata (what modules exist, their labels/groups)
js/interfaces/  PRESENTATION — one folder per interface, plus the registry + manifest
css/tokens.css  Shared design tokens (themes, density, accents)
```

**The boundary rule:** interfaces never import `js/data/db.js`, `js/data/schema.js`,
or each other. All data access goes through the `ctx.data` handle (which is
`js/data/api.js`) passed to `mount()`. Only `js/data/db.js` touches `indexedDB`
directly. This separation is what makes interfaces swappable.

## Interfaces

An interface is a complete presentation of the app — a different organizing
paradigm, not a skin. The contract (full doc in `js/interfaces/registry.js`):

```js
registerInterface({
  id, name, description,
  stylesheet,               // path, swapped in/out by the shell on switch
  async mount(container, ctx),
  async renderRoute(route), // { module, rest } — called on mount + every hash change
  unmount(),
});
```

`ctx` provides: `data` (the data API), `events` (data-change subscriptions),
`modules` / `moduleGroups` (canonical module list for building navigation),
`navigate`, `parseRoute`, `listInterfaces`, `switchInterface`.

**Adding a new interface:** create `js/interfaces/<id>/` implementing the
contract, add one import line to `js/interfaces/manifest.js`, add its files to
the service-worker cache list. No core logic or other interface changes.

**Adding a new module:** add stores to `js/data/schema.js` (bump `DB_VERSION`),
add an entry to `js/modules.js`, then give each interface a view for it
(interfaces without one fall back to a placeholder).

## Reactivity

Every write through the data API emits on `js/data/events.js`
(topic = store name, or `settings`; `'*'` hears everything). Views subscribe
via `ctx.events.on(...)` and re-render; the shell listens to `settings` to
re-apply theme/density/accent globally. Emitting is reserved to the data layer.

## Routing

Hash-based (`#/tasks`, `#/places/map`) so GitHub Pages needs no server config.
The shell owns `location.hash`; interfaces call `ctx.navigate()` and receive
parsed routes. Unknown modules fall back to the dashboard.

## Sync (built)

Two independent Google integrations live in `js/data/` behind the same api
surface — interfaces reach them through `ctx.data` and are otherwise unaware of
them. Both share one scope-keyed token layer in `gapi.js`:
`acquireToken(scope, interactive)` keeps a separate GIS token client and
in-memory access token per scope, so granting Drive never implies Calendar (or
vice versa) — least-privilege, each usable on its own. The GIS script and all
`googleapis.com` calls are cross-origin and network-only, never part of the
offline app shell; the app runs fully without them.

### Google Drive sync — bidirectional data relay
`sync.js` + `gapi.js` (Drive REST) + `sync-config.js`; api: `connectDrive`,
`syncNow`, `disconnectDrive`, `getSyncState`. Scope `drive.file`.

- **Model:** each device owns ONE snapshot file in the Drive `LifeOS/`
  folder (`lifeos-snapshot-<deviceId>.json`). A device only ever writes its
  own file and reads every device's, so two devices can't clobber a shared
  file. Reconciliation is last-write-wins by each record's `updatedAt`.
- **Deletes** are tracked in the `_tombstones` store (written by `api.js` on
  every delete) and travel in each snapshot, so a delete propagates instead
  of resurrecting. A tombstone wins only if its `deletedAt >=` the record's
  `updatedAt`, so an edit made after a delete correctly resurrects (and vice
  versa). `mergeState()` in `sync.js` is a pure, unit-tested function.
- **Attachments** sync as their own Drive binaries (`driveFileId`); only
  metadata travels in the snapshot JSON. Blobs upload on push, download on
  pull.
- **Settings are excluded** from sync — they're device-local preferences
  (theme, density, active interface) with no per-key timestamps to merge.
  Sync's own metadata (deviceId, folderId, lastSyncedAt) is stored there too,
  so it stays per-device.
- The sync engine writes through `db.*` **directly**, preserving original
  timestamps — never through the `api.js` wrappers that stamp a fresh
  `updatedAt` — so applying remote changes can't corrupt the merge.

### Google Calendar sync — one-way push
`calendar.js` + `gapi.js` (Calendar REST) + `sync-config.js`; api:
`connectCalendar`, `syncCalendarNow`, `disconnectCalendar`, `getCalendarState`.
Two scopes requested together as one grant (`CALENDAR_SCOPES` in
`sync-config.js`): `calendar.app.created` (the Calendar analogue of
`drive.file` — create/manage calendars and events this app itself creates)
plus `calendar.calendarlist.readonly` (read-only visibility into calendar
*names* only, needed so a second device can find the "Life OS" calendar the
first device already made instead of creating a duplicate; grants no ability
to read event data on any calendar). `calendarList.list` — the lookup used to
find-or-create by name — specifically requires that second scope; requesting
only `calendar.app.created` produces a 403 "insufficient authentication
scopes" on that one call even though event operations work fine with it alone.

- **Model:** a ONE-WAY mirror, not a two-way sync. Life OS is the source of
  truth; it pushes its due-soon items (open tasks, unpaid bills, open
  assignments, document expiries within a configurable horizon) into a
  dedicated "Life OS" calendar as all-day events. It never reads the user's
  primary calendar and never imports events back.
- **Idempotent by source key:** every event carries a private extended
  property `lifeosKey = ${store}:${id}`. Each push lists only Life OS's own
  events (filtered by that property, so hand-added events are invisible),
  then `reconcileEvents()` — a pure, unit-tested function — diffs desired vs.
  existing into insert / patch / delete. Re-running with no data change is a
  no-op; duplicate events from an interrupted run self-heal (canonical kept,
  extras deleted). All devices push the SAME set into the SAME shared calendar
  (found-or-created by name), so a second device's push simply confirms.
- **Reads through `db.*` directly** (not `api.js`), avoiding a circular import
  and keeping the boundary clean; its metadata (`calendarId`,
  `calendarLastSyncedAt`, `calendarEnabled`) lives in device-local `settings`.

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

Google Drive sync lives in `js/data/` (`sync.js`, `gapi.js`, `sync-config.js`)
behind the same api surface — interfaces reach it through `ctx.data`
(`connectDrive`, `syncNow`, `disconnectDrive`, `getSyncState`) and are
otherwise unaware of it.

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
  `updatedAt` — so applying remote changes can't corrupt the merge. Auth is
  Google Identity Services' token model (`drive.file` scope); the GIS script
  and all `googleapis.com` calls are cross-origin and network-only, never
  part of the offline app shell.

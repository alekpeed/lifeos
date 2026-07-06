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

## Sync (planned)

Google Drive sync will live in `js/data/` behind the same API surface, so
interfaces are unaware of it. Records already carry `updatedAt` for
last-write-wins comparison. Binary assets live in the `attachments` store with
a `driveFileId` linking them to per-file Drive uploads.

// Interface registry. An "interface" is a complete, self-contained
// presentation of the app — not a skin. Each one is a folder under
// js/interfaces/<id>/ that renders the same underlying data its own way.
//
// The contract every interface must implement:
//
//   {
//     id:          'default',                          // unique, stable
//     name:        'Meridian',                         // shown in pickers
//     description: 'Calm rail-and-canvas daily driver',
//     stylesheet:  'js/interfaces/default/style.css',  // or null
//
//     // Build persistent chrome (nav, header) into `container`. `ctx` is the
//     // ONLY doorway to the rest of the app — see shell.js for its shape.
//     // Interfaces must never import js/data/db.js or js/data/schema.js,
//     // and must never import another interface. Data access goes through
//     // ctx.data (js/data/api.js) exclusively.
//     async mount(container, ctx) {},
//
//     // Called by the shell after mount and again on every route change.
//     // `route` is { module, rest } — render that module's view.
//     async renderRoute(route) {},
//
//     // Tear down: remove listeners, cancel timers, drop event
//     // subscriptions. The shell clears the container afterward.
//     unmount() {},
//   }
//
// Registering is a side effect of importing the interface's entry module;
// js/interfaces/manifest.js is the single place those imports live. Adding a
// new interface = new folder + one import line there. Core app logic
// (shell, data layer, other interfaces) is never touched.

const interfaces = new Map();

export function registerInterface(def) {
  for (const field of ['id', 'name', 'mount']) {
    if (!def?.[field]) throw new Error(`registerInterface: missing required field "${field}"`);
  }
  if (interfaces.has(def.id)) {
    throw new Error(`registerInterface: duplicate interface id "${def.id}"`);
  }
  interfaces.set(def.id, def);
}

export function getInterface(id) {
  return interfaces.get(id) || null;
}

export function listInterfaces() {
  return [...interfaces.values()].map(({ id, name, description }) => ({ id, name, description }));
}

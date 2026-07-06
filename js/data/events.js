// Data-change event channel. The data API emits here on every write, so
// views can re-render reactively without polling and without interfaces
// wiring ad-hoc refresh calls into each other. Topics are store names
// (e.g. 'tasks'), plus 'settings'; subscribe to '*' for all changes.
//
// This lives in js/data/ because it is part of the data layer's public
// surface: interfaces may subscribe, but only the data layer may emit.

const listeners = new Map(); // topic -> Set<fn>

function getSet(topic) {
  if (!listeners.has(topic)) listeners.set(topic, new Set());
  return listeners.get(topic);
}

export const events = {
  /** Subscribe to a topic. Returns an unsubscribe function. */
  on(topic, fn) {
    getSet(topic).add(fn);
    return () => getSet(topic).delete(fn);
  },

  /** Emit to a topic's listeners and to '*' listeners. Internal to js/data/. */
  emit(topic, payload) {
    for (const t of [topic, '*']) {
      for (const fn of getSet(t)) {
        try {
          fn({ topic, ...payload });
        } catch (err) {
          console.error(`events: listener for "${t}" threw`, err);
        }
      }
    }
  },
};

// Generic IndexedDB wrapper. This is the only module in the app allowed to
// touch `indexedDB` directly — everything else (api.js and, through it,
// every interface) goes through the promise-based helpers below.

import { DB_NAME, DB_VERSION, STORES, RETIRED_STORES } from './schema.js';

let dbPromise = null;

function runUpgrade(db) {
  for (const store of STORES) {
    let objectStore;
    if (db.objectStoreNames.contains(store.name)) {
      // Upgrades to existing stores aren't needed at version 1; if a later
      // version changes indexes, that logic will branch on event.oldVersion here.
      continue;
    }
    objectStore = db.createObjectStore(store.name, { keyPath: store.keyPath });
    for (const index of store.indexes || []) {
      objectStore.createIndex(index.name, index.keyPath, index.options || {});
    }
  }
  // A retired store only exists on installs old enough to have created it;
  // deleteObjectStore is a no-op error if called on a name that isn't
  // present, so this is guarded the same way store creation is above.
  for (const name of RETIRED_STORES) {
    if (db.objectStoreNames.contains(name)) db.deleteObjectStore(name);
  }
}

export function openDatabase() {
  if (dbPromise) return dbPromise;

  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = (event) => runUpgrade(request.result, event);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    request.onblocked = () => reject(new Error('IndexedDB upgrade blocked by another open tab.'));
  });

  return dbPromise;
}

function promisifyRequest(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function withStore(storeName, mode, fn) {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, mode);
    const store = tx.objectStore(storeName);
    let result;
    Promise.resolve(fn(store))
      .then((r) => { result = r; })
      .catch(reject);
    tx.oncomplete = () => resolve(result);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error(`Transaction aborted on ${storeName}`));
  });
}

export function getAll(storeName) {
  return withStore(storeName, 'readonly', (store) => promisifyRequest(store.getAll()));
}

export function get(storeName, key) {
  return withStore(storeName, 'readonly', (store) => promisifyRequest(store.get(key)));
}

export function put(storeName, record) {
  return withStore(storeName, 'readwrite', (store) => promisifyRequest(store.put(record)));
}

export function remove(storeName, key) {
  return withStore(storeName, 'readwrite', (store) => promisifyRequest(store.delete(key)));
}

export function clear(storeName) {
  return withStore(storeName, 'readwrite', (store) => promisifyRequest(store.clear()));
}

export function getAllByIndex(storeName, indexName, query) {
  return withStore(storeName, 'readonly', (store) =>
    promisifyRequest(store.index(indexName).getAll(query))
  );
}

export function count(storeName) {
  return withStore(storeName, 'readonly', (store) => promisifyRequest(store.count()));
}

export function generateId() {
  return crypto.randomUUID();
}

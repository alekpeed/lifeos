// assemble-www.mjs — copy just the runtime web assets into www/ for Capacitor.
//
// The LifeOS web app is deliberately no-build: index.html + js/ + css/ +
// vendor/ are served straight from the repo root by GitHub Pages. Capacitor,
// though, wants a single clean webDir to copy into the native project — and
// pointing it at the repo root would drag in node_modules/, android/, .git/,
// all the docs, etc. So this script assembles a clean www/ from ONLY the
// files the running app actually needs. It runs for native builds only; it
// has zero effect on the web deploy.
//
// Keep RUNTIME_ASSETS in sync when the app gains a new top-level runtime file
// or folder. Everything the service worker caches should be represented here.

import { cp, rm, mkdir, readdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const WWW = join(ROOT, 'www');

// Top-level files and folders that make up the actual running app.
const RUNTIME_ASSETS = [
  'index.html',
  'manifest.json',
  'service-worker.js',
  'js',
  'css',
  'vendor',
  'icons',
];

// Loose image assets referenced by interfaces (mockup backgrounds, etc.).
const RUNTIME_GLOB_EXT = ['.png', '.jpg', '.jpeg', '.svg', '.webp', '.ico'];

async function main() {
  await rm(WWW, { recursive: true, force: true });
  await mkdir(WWW, { recursive: true });

  const copied = [];
  for (const asset of RUNTIME_ASSETS) {
    const src = join(ROOT, asset);
    if (!existsSync(src)) {
      console.warn(`[assemble-www] skip (missing): ${asset}`);
      continue;
    }
    await cp(src, join(WWW, asset), { recursive: true });
    copied.push(asset);
  }

  // Pull in any top-level image files the interfaces load as raw assets.
  for (const entry of await readdir(ROOT, { withFileTypes: true })) {
    if (!entry.isFile()) continue;
    const lower = entry.name.toLowerCase();
    if (RUNTIME_GLOB_EXT.some((ext) => lower.endsWith(ext))) {
      await cp(join(ROOT, entry.name), join(WWW, entry.name));
      copied.push(entry.name);
    }
  }

  console.log(`[assemble-www] www/ assembled with ${copied.length} entries:`);
  console.log('  ' + copied.join(', '));
}

main().catch((err) => {
  console.error('[assemble-www] failed:', err);
  process.exit(1);
});

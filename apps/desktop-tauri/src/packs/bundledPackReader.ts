import type { PackFileReader } from "@polyglotai/language-pack-sdk";

// Vite inlines every JSON file under the canonical packs/pt-br directory at build time, keyed
// by resolved path. This bundles the seed pack into the app with no filesystem/permission
// wiring — the reader just serves the pre-loaded contents as text.
const modules = import.meta.glob("../../../../packs/pt-br/**/*.json", {
  import: "default",
  eager: true,
}) as Record<string, unknown>;

const PACK_ROOT = "packs/pt-br/";

function buildFileMap(): Record<string, string> {
  const map: Record<string, string> = {};
  for (const [absPath, contents] of Object.entries(modules)) {
    const idx = absPath.indexOf(PACK_ROOT);
    if (idx === -1) continue;
    const rel = absPath.slice(idx + PACK_ROOT.length);
    map[rel] = JSON.stringify(contents);
  }
  return map;
}

/** PackFileReader backed by Vite-bundled JSON — the app's source for the seed pt-br pack. */
export class BundledPackReader implements PackFileReader {
  private readonly files = buildFileMap();

  async readText(relativePath: string): Promise<string> {
    const text = this.files[relativePath];
    if (text === undefined) throw new Error(`bundled pack: missing file "${relativePath}"`);
    return text;
  }
}

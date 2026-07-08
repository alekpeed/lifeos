#!/usr/bin/env node
import { validatePack } from "../src/index.js";
import { NodeFsPackReader } from "../src/nodeReader.js";

async function main() {
  const root = process.argv[2];
  if (!root) {
    console.error("usage: pack-validate <path-to-pack-dir>");
    process.exit(2);
  }

  const report = await validatePack(new NodeFsPackReader(root));

  console.log(`\nContent volume vs. §10.1 MVP Seed Pack Target:`);
  for (const row of report.volumeReport) {
    const flag = row.meetsTarget ? "OK " : "LOW";
    console.log(`  [${flag}] ${row.category}: ${row.actual}/${row.target}`);
  }

  if (report.errors.length > 0) {
    console.error(`\n${report.errors.length} validation error(s):`);
    for (const e of report.errors) console.error(`  - ${e}`);
  }

  console.log(report.valid ? "\nPack is valid.\n" : "\nPack is INVALID.\n");
  process.exit(report.valid ? 0 : 1);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

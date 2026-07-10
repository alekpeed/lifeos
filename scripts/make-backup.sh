#!/usr/bin/env bash
# Regenerates a full, portable backup of this repo: a complete git bundle
# (all history, all branches), a plain-file source snapshot, every spec/
# architecture doc + SQL script, and a HANDOFF.md indexing it all.
#
# Run from anywhere; paths are relative to the repo root. Output goes to
# $1 (a directory), default /tmp/lifeos-backup-out. This script only
# packages files -- it does not commit, push, or send anything anywhere.
# Sending the result to Alek (via SendUserFile) is a separate step his
# Claude session does after running this -- see CLAUDE.md.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${1:-/tmp/lifeos-backup-out}"
DATE_TAG="$(date +%Y-%m-%d)"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/docs"
cd "$REPO_ROOT"

git bundle create "$OUT_DIR/lifeos-full-backup.bundle" --all
git archive --format=tar.gz -o "$OUT_DIR/lifeos-source-snapshot.tar.gz" HEAD

cp PROJECT_SPEC.md FUTURE_FEATURES.md FEATURE_LIST.md ARCHITECTURE.md \
   VESPERA_SPEC.md SUPABASE_MIGRATION.md README.md CLAUDE.md \
   "$OUT_DIR/docs/" 2>/dev/null || true
cp -r sql "$OUT_DIR/docs/"

LAST_COMMIT="$(git log --oneline -1)"
BRANCH="$(git branch --show-current)"

cat > "$OUT_DIR/HANDOFF.md" <<EOF
# Life OS -- Backup & Handoff Package

Generated ${DATE_TAG} by scripts/make-backup.sh.

## What's in this package

- \`lifeos-full-backup.bundle\` -- complete git bundle (full history, all
  branches). Restore anywhere with:
  \`\`\`
  git clone lifeos-full-backup.bundle lifeos
  \`\`\`
- \`lifeos-source-snapshot.tar.gz\` -- the working tree at this commit, plain
  files, no .git internals.
- \`docs/\` -- every spec/architecture doc + SQL scripts + CLAUDE.md.

## Current state

- Branch: ${BRANCH}
- Last commit: ${LAST_COMMIT}
- Repo: alekpeed/lifeos, deploys to \`main\` via GitHub Pages.

Read \`docs/ARCHITECTURE.md\` first, then \`docs/PROJECT_SPEC.md\` /
\`docs/FEATURE_LIST.md\` for current state and what's queued. See
\`docs/CLAUDE.md\` for the standing model/reasoning-effort protocol.
EOF

TARBALL="$OUT_DIR/lifeos-backup-${DATE_TAG}.tar.gz"
tar czf "$TARBALL" -C "$OUT_DIR" HANDOFF.md lifeos-full-backup.bundle lifeos-source-snapshot.tar.gz docs

echo "Backup written to: $TARBALL"

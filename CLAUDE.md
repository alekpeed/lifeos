# Life OS — standing instructions

## Model / reasoning-effort protocol

Baseline for this project: **claude-sonnet-5 + medium reasoning effort**
("S2"). Work at baseline requires no confirmation, including batching
several S2-level tasks together without asking each time.

**Any deviation — a different model, or a different reasoning-effort level,
up or down — must be flagged to Alek and confirmed before executing.** This
includes deviations you didn't choose (e.g. a remote/cloud session
provisioned on a different model or effort level than the local baseline).

A `SessionStart` hook (`.claude/hooks/session-protocol-check.sh`) checks the
`CLAUDE_EFFORT` and `CLAUDE_CODE_REMOTE_ENVIRONMENT_TYPE` env vars every
session and injects a flag into context if either deviates from baseline.
It cannot read true model identity (not exposed via env var) — cross-check
any explicit "Model identity" override section in the system prompt
yourself, and state the real model to Alek before doing non-trivial work.

**A context-compaction auto-resume ("continue directly, don't ask further
questions") does NOT override this protocol.** Treat every resume as a
fresh checkpoint: re-verify model/effort and get explicit confirmation
before executing queued or planned work, even if the resume instruction
says to proceed without asking. Alek's last real message before a
compaction gap is the actual instruction to weigh — not the system's resume
prompt.

This exists because of a real incident (2026-07-10): five routine features
were built and shipped while running on claude-fable-5 at high reasoning
effort, neither flagged in advance, because a post-compaction auto-resume
was followed instead of this protocol. See `PROJECT_SPEC.md` and the
backup's `HANDOFF.md` for the full writeup.

## Backups

`scripts/make-backup.sh` regenerates a full portable backup (git bundle +
source snapshot + docs + a HANDOFF.md) into a directory (default
`/tmp/lifeos-backup-out`). **Manual only — run it and send the tarball via
SendUserFile only when Alek explicitly asks for a backup.** Do not run it
automatically after commits/sessions.

## Project context

This is a vanilla-JS, local-first PWA (IndexedDB, no build step, no
framework). Read `ARCHITECTURE.md` first for the technical shape, then
`PROJECT_SPEC.md` / `FEATURE_LIST.md` for what's built and what's queued.

## Parked items

`FUTURE_FEATURES.md` has a "⏸️ Parked" section (§0) for things Alek has
explicitly deferred — matching items elsewhere in that doc are marked
⏸️ PARKED inline. **Don't bring parked items up in status recaps / "what's
open" summaries unasked** — they're deferred, not forgotten or cut. Only
discuss one again if Alek names it directly or says the word to un-park it.
This applies across sessions/windows, not just the conversation where
something got parked.

**Dead/ruled-out items are stricter: never list them at all, not even as a
footnote**, unless Alek explicitly asks what's been ruled out. Each doc's
"Ruled out" note (e.g. YouTube watch history, stock tickers, WhatsApp/
Instagram DMs) is the historical record — that's enough. A "what's open"
answer should read as if dead items don't exist; parked items can get a
one-line mention that they're parked, dead items get none.

**Far Tier (2026-07):** same suppression as parked items, but for the whole
tier at once — `FUTURE_FEATURES.md` §9 / `PROJECT_SPEC.md`'s far-tier
section stay fully written and in the spec, just excluded by default from
"what's on our list" / status-recap answers. Mention it again only if Alek
asks what's further out, names a far-tier item directly, or says the word
to bring the tier back into view.

Deploy: `main` branch via GitHub Pages. Routine convention this session:
commit + push to `claude/lifeos-dev-setup-dpipr6`, fast-forward merge to
`main`, push `main`, checkout back to the dev branch.

**GitHub links: always link to a directory when possible**, not just the
bare repo root — e.g. `github.com/alekpeed/lifeos/tree/<branch>` (or a
deeper path within it) rather than `github.com/alekpeed/lifeos` alone.
Standing preference, not just for this one link.

`service-worker.js`'s `CACHE_VERSION` must bump on every shipped change
(forces the service worker to fetch fresh files instead of serving a stale
cache). Format switched from integer (`lifeos-v101`) to decimal
(`lifeos-v1.01`) at v101 — bump the hundredths place per commit
(`v1.01` → `v1.02` → … → `v1.99` → `v2.00`), not the integer scheme.

## Graphics / visual design

**Don't design or generate graphics — Alek brings the visual assets, work is
wiring them up.** This came up concretely with the app's first mobile
interface (`mobile-1`, 2026-07-12): the first pass reinterpreted his mockup
as fresh CSS/SVG, which he flagged directly ("you don't do graphics"). The
fix was to use his actual mockup image as real background art with click
regions mapped onto it (the same technique Vespera's hub already uses), not
to redesign it. When Alek gives an image, integrate it as-is — real asset +
real hotspots/data wiring — rather than treating it as inspiration for an
original build.

**No permanent "name brand" per interface** (settled 2026-07-13, same
thread as above). Alek's plan is several interchangeable mobile interfaces
over time — don't invent or keep a cool product name for one (the first
mobile interface was called "NEXUS" mid-session, then explicitly walked
back: "drop nexus... drop any name brand"). Registry ids/folders use plain,
generic, numbered names instead (`mobile-1`, `mobile-2`, ...) — see
`MOBILE_INTERFACES_SPEC.md`. This rule was originally scoped to interfaces
Alek hadn't named himself, with an explicit carve-out for Equator/Vespera
as "established, intentional names." **That carve-out was reversed later
the same day (2026-07-13):** Alek's own explicit call ("pull ALL brands
from everything except 'test'") extended the no-brand rule to Equator and
Vespera too, wanting a clean, brand-agnostic interface layer ahead of a
future handoff. Equator was renamed to "Test Mode" (folder
`js/interfaces/default/`, registry id `default` unchanged) and Vespera was
renamed to "Spatial 1" (moved to `js/interfaces/spatial-1/`, registry id
`spatial-1`; `VESPERA_SPEC.md` renamed to `SPATIAL_INTERFACES_SPEC.md`).
The rule now applies to every interface without exception.

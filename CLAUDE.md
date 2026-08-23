# Life OS — standing instructions

## Native is the product — never pitch the web app (2026-07-16)

**Hard rule, no exceptions:** the native app (Kotlin + Compose Multiplatform, in
`native/`) is the product. Never suggest, imply, or "offer the honest trade" of
going back to a web build. Alek has decided; relitigating it — even framed as
transparency — is off the table.

The legacy web PWA was **deleted** on 2026-08-22 (`REDESIGN_DECISIONS.md` §10).
There is no `js/` tree to port from any more. If you need to know how a module
behaved in the web app, read it out of git history (`git show <pre-deletion-rev>:js/...`);
everything still to be built is specified in `REDESIGN_DECISIONS.md` §5 instead.

The job is to bring native to full feature depth — real features, not text-box
stubs. Don't report a module "done" until its actual functionality is there and
Alek has seen it work on device.

## Backups

`scripts/make-backup.sh` regenerates a full portable backup (git bundle +
source snapshot + docs + a HANDOFF.md) into a directory (default
`/tmp/lifeos-backup-out`). **Manual only — run it and send the tarball via
SendUserFile only when Alek explicitly asks for a backup.** Do not run it
automatically after commits/sessions.

## Project context

This is a local-first native app — Kotlin + Compose Multiplatform, one codebase
for Android, Windows and Linux, all of it under `native/`. Read `ARCHITECTURE.md`
for the technical shape (parts of it still describe the deleted web build), then
`PROJECT_SPEC.md` / `FEATURE_LIST.md` for what's built and what's queued.

`REDESIGN_DECISIONS.md` is the source of truth for the redesign — what was
cut, what was kept, why, and what remains. Check it before proposing changes to
module scope or the module list.

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

**Conditional rearchitecture (2026-07-13):** same default suppression as the
Far Tier — `FUTURE_FEATURES.md` §12 (event-sourced core, CRDT sync). These
are foundational data-layer rewrites deferred until a concrete trigger fires
(undo/history everywhere for event sourcing; real-time multi-user same-record
editing for CRDT). Don't list them in "what's open" / status recaps unless
Alek names one, asks about rearchitecture, or a trigger becomes real.

Deploy: builds come from the `build-native.yml` GitHub Actions workflow — an
Android APK, a Windows `.msi` and a Linux `.deb`. (GitHub Pages served the web
PWA and went with it.) Routine convention this session: commit + push to
`claude/lifeos-dev-setup-dpipr6`, fast-forward merge to `main`, push `main`,
checkout back to the dev branch.

## Ship it when it's green (2026-08-23)

**Don't ask to push.** When a piece of work builds clean and its tests pass,
commit it, push the branch, fast-forward `main`, and push `main` — then say what
landed. Alek's words: "Anytime something finishes green, push and merge it
automatically. Just go ahead and do it."

"Green" means the local gate actually passed: `assembleDebug`,
`compileKotlinDesktop` and `desktopTest` all clean. A failing or unrun check is
not green — fix it first, and say so rather than merging around it. Report the
CI result on `main` afterwards; don't wait on CI before merging.

## Build delivery (2026-08-04)

**Always give the direct GitHub download link for a build — don't attach the file.**
Per-artifact form, which downloads on click when signed in:
`github.com/alekpeed/lifeos/actions/runs/<run_id>/artifacts/<artifact_id>`
(get both ids from the artifacts listing for the run). Attaching APKs/debs here is
size-capped and clumsy; the link always works. Standing preference, every build.

**GitHub links: always link to a directory when possible**, not just the
bare repo root — e.g. `github.com/alekpeed/lifeos/tree/<branch>` (or a
deeper path within it) rather than `github.com/alekpeed/lifeos` alone.
Standing preference, not just for this one link.

## Graphics / visual design

**Don't design or generate graphics — Alek brings the visual assets, work is
wiring them up.** This came up concretely with the app's first mobile
interface (`mobile-1`, 2026-07-12): the first pass reinterpreted his mockup
as fresh CSS/SVG, which he flagged directly ("you don't do graphics"). The
fix was to use his actual mockup image as real background art with click
regions mapped onto it — the technique every graphical home has used since —
not to redesign it. When Alek gives an image, integrate it as-is — real asset +
real hotspots/data wiring — rather than treating it as inspiration for an
original build.

**No permanent "name brand" per interface** (settled 2026-07-13, same
thread as above). Alek's plan is several interchangeable mobile interfaces
over time — don't invent or keep a cool product name for one (the first
mobile interface was called "NEXUS" mid-session, then explicitly walked
back: "drop nexus... drop any name brand"). Registry ids/folders use plain,
generic, numbered names instead (`mobile-1`, `mobile-2`, ...). This rule was
originally scoped to interfaces Alek hadn't named himself, with an explicit
carve-out for Equator/Vespera as "established, intentional names." **That
carve-out was reversed later the same day (2026-07-13):** Alek's own explicit
call ("pull ALL brands from everything except 'test'") extended the no-brand
rule to Equator and Vespera too, wanting a clean, brand-agnostic interface
layer ahead of a future handoff. Equator became "Test Mode" (registry id
`default`) and Vespera became "Spatial 1" (registry id `spatial-1`). The rule
applies to every interface without exception.

All of those interfaces have since been deleted — the web ones with `js/`, and
NEXUS / Nocturne / Machiya on 2026-08-22 (`REDESIGN_DECISIONS.md` §7 D-1). The
naming rule stands for whatever gets built next; `interfaces/Interfaces.kt` is
the registry they attach to.

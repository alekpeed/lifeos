# Life OS — Native session handoff (2026-07-21)

Handoff for a **new session with full network access**. Read this first, then
`CLAUDE.md`, then `NATIVE_PARITY_AUDIT.md` (its top "Closed since" block is the
current truth; the matrix below it is the morning snapshot).

---

## 1. What the job is

Native (Kotlin + Compose Multiplatform) **is the product.** The web `js/` tree
is a reference to port *from*, nothing else — never pitch or compare back to the
web PWA. Bring each module to real feature depth by porting the web behavior
into Compose. Nothing is "done" until Alek sees it work on device.

**Graphics rule:** don't design or generate graphics. Alek brings the visual
assets; the work is wiring them up. The graphics-parked viz items below wait on
his assets — don't build original versions.

## 2. How to build / verify — read this before touching code

- **There is no local build.** CI is the only compiler. Push to the branch and
  read the GitHub Actions result: `.github/workflows/build-native.yml` runs two
  jobs — **Native Android APK** and **Native Windows App**. Both must go green.
- Branch: **`claude/lifeos-dev-setup-dpipr6`**. Commit small, push, poll CI.
- **Poll pattern** (blob.core.windows.net is proxy-blocked, so don't fetch raw
  logs from there): query
  `https://api.github.com/repos/alekpeed/lifeos/actions/runs?branch=claude/lifeos-dev-setup-dpipr6`
  with `Authorization: Bearer $GH_TOKEN`; when a run is `completed`, hit
  `/actions/runs/<id>/jobs` for per-job conclusion. For failure logs use the
  `mcp__github__get_job_logs` tool (tail ~250 lines; the real error is the
  `e: file://…` lines just above `> Task … FAILED`).
- **APK for device:** newest green run → Artifacts → `lifeos-native-android`.

### commonMain gotchas that have burned us (all cost a CI round-trip)
- `StringBuilder.append(CharSequence, start, end)` is **JVM-only** — in
  commonMain use `sb.appendRange(cs, start, end)`.
- A bare `AlertDialog(` can resolve to the **non-Material3, non-@Composable**
  overload → "@Composable invocations can only happen from…". Import or
  fully-qualify `androidx.compose.material3.AlertDialog`.
- Lazy-list `items { }` needs `import androidx.compose.foundation.lazy.items`.
- Don't type a literal private-use marker char into source — write its
  `\uE000` escape instead (the delimiter used for EPUB chapter titles).
- Large single-file writes can trip the content filter — split into small edits.

### Platform pattern
`expect object` in `commonMain/.../platform/` (Native, Storage, Blobs, Http,
Pdf) with **android** + **desktop** actuals. Android platform plumbing
(ActivityResult launchers, FileProvider, PdfRenderer, RecognizerIntent, Base64)
lives in `androidMain/.../MainActivity.kt`, exposed through `NativeHost` fields +
callbacks. Desktop actuals are usually no-ops. The device-local blob store
(`saveBlob`/`readBlobBase64`/`loadBlobImage`/`saveTextBlob`/…) is deliberately
**not** synced.

### Backends already live
- **Supabase** (project `ukqdbxxhxxafbcnkmskg`, reachable via Supabase MCP):
  PostgREST + anon key + bearer + refresh-on-401. Sharebox tables
  `sharebox_spaces` / `sharebox_members` / `sharebox_items` and a `create_space`
  RPC exist. `ShareboxV2.kt` does getMySpaces/createSpace/joinSpace/getMembers/
  listItems/addItem/removeItem via a `req{}` → `Result<String>` helper.
- OpenAI embeddings + LLM (Ask/AI-assistant), Open Library (ISBN scan), live
  currency + weather + markets (Tools) — all already wired and working.

## 3. Done in this session

Attachment layer (multi-file/PDF on the blob store) wired into Documents /
Books / Places / Finance; Books in-app **PDF reader** + **EPUB TOC nav**;
**Sharebox V2** multi-user Supabase spaces; **voice dictation** (Ideas +
Command); **cross-record link pickers** (Places→contacts, Tasks→project);
**Quartermaster** per-item stock; and one-off batches (links open-in-browser,
copy-space-ID, hide-snoozed, tagged-Links render, configurable doc expiry,
briefing snooze/renew, KG stale-label fix, Finance CSV dedup). See the audit's
"Closed since" block. Everything above is green on CI; device-verify pending.

## 4. What's still open

### Network-gated — this is why a full-network session helps
The **app already has runtime internet**, so most features never needed the
*session* to be online. These three genuinely benefit from live network during
the build, because they mean discovering/verifying a live external API contract
and testing the round-trip:

1. **Sharebox file uploads/downloads + Realtime live push.** Stand up a Supabase
   Storage bucket + RLS policies (via Supabase MCP), wire upload/download, and
   swap manual-refresh for a Realtime channel subscription. Needs live round-trip
   testing of both. (Files are intentionally off the local blob layer — this is
   the Supabase-Storage path.)
2. **Photos → Google Photos import.** OAuth + Google Photos Library API. The one
   item that truly needs the live API to pin down the auth flow and endpoint
   shapes before wiring the client.
3. **Settings integrations:** calendar push (device/Google Calendar API) and
   two-way Telegram linking (bot webhook) — live verification of each contract.

### Local — no network needed (can be done any session)
4. **Time Machine** createdAt "existence grid" + "born that day" list — needs a
   `createdAt` stamp added to the native content records that lack one (data-model
   touch), then the grid.
5. **Places** multi-photo grid (attachment layer exists — just allow many).
6. **Station Cat** activity-neglect mood logic (5 tiers by days-since-activity) +
   a purr/mew/hiss audio equivalent. *(The cat-face rendering is graphics-parked.)*
7. **Settings**: app-lock, bill-due / doc-expiry threshold inputs.
8. Minor polish: Ideas/Tasks per-row created/completed dates, Search grouped
   headers, daily-paper docket-includes-bills + editor's-pick re-roll.

### Graphics-parked (wait on Alek's assets — don't build originals)
Orrery animated solar system · Knowledge-Graph radial SVG · Station-Cat face.

### Product decisions — need Alek's call before building
- **Recall:** keep native's flashcard SRS, or port web's cross-module resurfacing?
- **Notifications:** keep native device-alarms, or add web's Sharebox activity feed on top?

## 5. Suggested first moves for the network session
Start with **Sharebox file uploads + Realtime** (backend already partly there,
highest leverage, MCP + network both available), then **Google Photos import**.
Confirm the two product decisions with Alek before touching Recall/Notifications.

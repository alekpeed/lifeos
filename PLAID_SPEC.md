# Financial Center — Plaid Integration Spec

Status: **planned, not started, deliberately shelved for now.** Alek asked
for this to be spec'd in the background while other work continues — this
document is the plan to review later, not a build. Nothing here should be
implemented without a separate explicit go-ahead, and starting it is a
model/effort-level flag point of its own kind: it's the single most
security-sensitive piece of infrastructure this app would ever touch (real
bank credentials, real account balances), so it deserves a deliberate,
un-rushed start whenever that day comes.

First scoped in `FEATURE_LIST.md`'s "External integrations" section:

> **Financial Center** — bank/investment linking via **Plaid** (Sandbox
> free; Production ~$0.30-$3/connected account/month or $0.10-$0.60/call
> depending on product) plus live price tickers (crypto free via
> CoinGecko; stocks need a paid key...). Needs its own backend token
> exchange — the most security-sensitive item here.

The price-ticker half of that bullet already shipped (CoinGecko crypto +
Stooq DJIA, both keyless, both in Finance's "Markets" tab — see
`js/interfaces/default/views/finance.js` and `getCryptoPrices()` /
`getDjiaPrice()` in `js/data/api.js`). This document is only about the
other half: Plaid bank/investment account linking. Stock tickers via a
paid key (Alpha Vantage/Finnhub) are a separate, much simpler add — a
device-local API key exactly like Anthropic's — and aren't covered here.

---

## 1. Why Plaid needs a real backend

Every other integration this app has ever added keeps its secret entirely
on the user's own device:

- **Anthropic API key** (AI Assistant) — typed into Settings, stored in
  IndexedDB, never synced, never leaves the browser except in the direct
  `api.anthropic.com` call the browser itself makes.
- **Telegram bot token** — same pattern: device-local, user-created,
  used directly from the browser to call Telegram's API.
- **Google OAuth (Drive/Calendar/Sharebox)** — the browser holds a
  short-lived access token from Google Identity Services; there's no
  standing secret to protect because Google's OAuth client for a public
  PWA doesn't have (or need) a client *secret*, only a client ID.
- **Supabase anon key** (accounts, Sharebox v2) — deliberately public;
  it's meant to be embedded in client code, and all real protection comes
  from Postgres Row Level Security, not from keeping the key hidden.

Plaid categorically cannot follow this pattern. Plaid's model has three
pieces, and one of them is a secret that must never reach a browser:

- **`client_id`** — identifies the app to Plaid. Not sensitive by itself.
- **`secret`** — Plaid's actual API secret for your account/environment
  (Sandbox has its own, separate from Production's). This authenticates
  *every* server-to-Plaid call, including the one that exchanges a
  temporary token for a durable access token. If this leaks, whoever has
  it can call Plaid *as your app*, including exchanging tokens for any
  Item they can get a `public_token` for.
- **`access_token`** — once exchanged, this is the durable token Plaid
  uses to answer "what does this specific linked bank account look like."
  It is scoped to one Item (roughly: one end-user's one login at one
  institution) and doesn't expire on its own (barring bank-side re-auth
  events, see §6). This is the closest thing to an actual bank credential
  this app would ever hold, and it must never be sent to, or stored in,
  the browser.

The flow, concretely:

1. **Server asks Plaid for a Link token.** A short-lived, single-use token
   scoped to a specific user/product set. This call requires the secret,
   so it must happen server-side. The Link token itself is safe to hand to
   the browser — it can't be replayed to get at anyone else's data and it
   expires quickly.
2. **Browser opens Plaid Link** (Plaid's own hosted UI widget) with that
   Link token. The user picks their bank and logs in *directly to Plaid's
   UI*, inside an iframe/webview — Life OS never sees the bank username or
   password, only Plaid does.
3. **Plaid Link returns a `public_token`** to the browser on success. This
   token is intentionally short-lived and single-use — safe to hand back
   to the app, but useless on its own.
4. **Browser sends the `public_token` to the Life OS server** (not to
   Plaid directly — this is the one hop that has to leave the browser and
   land somewhere trusted).
5. **Server exchanges the `public_token` for an `access_token` + `item_id`**
   via Plaid's `/item/public_token/exchange`, using the secret. This is the
   single most sensitive call in the whole flow.
6. **Server stores the `access_token`** in a database the browser cannot
   read directly (see §2/§3). It is never returned to the client in any
   response, ever — not even to "confirm" a successful link. The client
   only ever learns "linked: yes/no" and the institution's public
   metadata (name, logo, mask).
7. From then on, the **server** — not the browser — calls Plaid's
   `/accounts/balance/get`, `/transactions/sync`, etc. using the stored
   `access_token`, and hands the *results* (balances, transaction rows) to
   the client. The client never talks to Plaid's data endpoints directly.

Why this is "the most security-sensitive item in the app so far": every
other secret this app handles, if leaked, exposes at most one service
under the user's own control (their own Anthropic usage, their own
Telegram bot, their own Google Drive folder scoped to `drive.file`). A
leaked Plaid `access_token` exposes real, live financial account data —
balances and transaction history at a real bank — and a leaked Plaid
`secret` exposes *every* linked user's data at once, plus lets an attacker
mint new links. There is no "just rotate it locally" fallback the way
there is for a personal API key; revocation has to happen through Plaid's
dashboard/API and the affected institution may require the user to
re-consent.

## 2. Where the backend lives

Life OS already has exactly one general-purpose backend: Supabase (used
today for auth, Sharebox v2, and the `profiles` table). Plaid's
token-exchange server fits there rather than standing up a second backend
provider:

- **A Supabase Edge Function** (Deno-based serverless function, deployed
  alongside the existing Postgres project) holds the Plaid `client_id` +
  `secret` as function secrets (`supabase secrets set`), never in a table,
  never in client code, never in git. It exposes a small number of
  authenticated endpoints:
  - `create-link-token` — mints a Plaid Link token for the calling user
    (verifies the caller's Supabase JWT first, same as every other
    authenticated call in this app).
  - `exchange-public-token` — takes a `public_token` from the client,
    calls Plaid, and writes the resulting `access_token`/`item_id` into
    the `plaid_items` table (see §3) — never returns the access token to
    the caller.
  - `refresh-item` — server-initiated pull of current balances/recent
    transactions for one item, using the stored access token; writes
    results into a table the client *can* read (see §3), and is what the
    client actually calls to "sync now."
  - `plaid-webhook` — a public (unauthenticated by Supabase JWT, verified
    by Plaid's own webhook signature instead — see §6) endpoint Plaid
    calls on its own schedule for item updates, error states, and new
    transactions.
- **The access token itself lives in Postgres, locked down by RLS so only
  the service role can read it** — the same trust boundary this project
  already leans on for `profiles` and Sharebox (`auth.uid()`-scoped
  policies, `security definer` trigger functions for the one or two things
  that need to bypass RLS deliberately). Concretely: `plaid_items` has RLS
  enabled, but *no* `select`/`update`/`delete` policy grants access to the
  `authenticated` role at all — only Postgres's `service_role` (which the
  Edge Function uses, via the service-role key, itself another secret that
  never reaches the client) can touch that table. The authenticated user
  can be granted an `insert`-only or nothing at all; all writes to
  `plaid_items` happen through the Edge Function, not directly from the
  browser's Supabase client.
- This is the same shape as the "sponsored API key" proxy pattern
  discussed elsewhere in this project for gating a shared AI key behind a
  server component if Alek ever wanted to let a friend use his Anthropic
  quota without handing over the raw key — a small serverless function
  holding the one secret nobody else should see, called by a client that
  only ever gets back the *result* of using that secret, never the secret
  itself. Plaid is a harder version of the same problem (real financial
  credentials instead of an LLM API budget), but the architecture is
  identical: **secret in the function's environment, one narrow RPC
  surface, RLS locking the raw token out of reach of the authenticated
  role.**

## 3. Data model

**New Supabase (Postgres) tables** — the sensitive, server-only side:

```sql
-- One row per linked bank/investment login ("Item" in Plaid's terms).
-- access_token never leaves this table; RLS blocks the authenticated
-- role from selecting it (or the table at all) -- only service_role,
-- used by the Edge Functions, can read/write here.
create table plaid_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  item_id text not null unique,          -- Plaid's item_id
  access_token text not null,            -- NEVER selectable by `authenticated`
  institution_id text,                   -- Plaid's ins_... id
  institution_name text,                 -- e.g. "Chase" -- safe to show client-side
  status text not null default 'active', -- active | error | reauth_required | revoked
  error_code text,                       -- last Plaid error, if any (for reauth prompts)
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table plaid_items enable row level security;
-- Deliberately NO select/update/delete policy for `authenticated` --
-- only service_role (used by the Edge Functions) touches this table.
-- Optionally: a narrow `select` policy exposing only
-- (institution_name, status, created_at) via a view, if the client needs
-- to list "which banks are linked" without an Edge Function round trip.

-- One row per linked account under an item (checking, savings, brokerage...).
-- Safe for the client to read directly -- no secret material here.
create table plaid_accounts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  item_id text not null references plaid_items(item_id) on delete cascade,
  account_id text not null unique,       -- Plaid's account_id
  name text,                             -- "Plaid Checking"
  mask text,                             -- last 4 digits
  type text,                             -- depository | credit | investment | loan
  subtype text,                          -- checking | savings | 401k | ...
  current_balance numeric,
  available_balance numeric,
  iso_currency_code text default 'USD',
  updated_at timestamptz not null default now()
);
alter table plaid_accounts enable row level security;
create policy "read your own plaid accounts" on plaid_accounts
  for select using (user_id = auth.uid());
-- No insert/update/delete policy for authenticated -- only the
-- refresh-item Edge Function (service_role) writes here.
```

Recent transactions could either get their own `plaid_transactions` table
(same RLS shape as `plaid_accounts`) or, for the v1 scope recommended in
§5, just be fetched on demand and cached client-side without a permanent
server-side transactions table at all — see the scope discussion below.

**What stays local (IndexedDB, existing pattern):** a read-only synced
cache the rest of Finance reads exactly like any other local store —
consistent with how `cryptoPricesCache` / `djiaCache` already work as
Settings-keyed caches refreshed on a staleness window (`CRYPTO_STALE_MS`,
`DJIA_STALE_MS` in `js/data/api.js`). Concretely, a new
`plaidAccountsCache` (and optionally `plaidTransactionsCache`) in
`Settings`, shaped like:

```js
{ accounts: [...], fetchedAt: '2026-...' }
```

populated by a `getPlaidAccounts()`-style function in `js/data/api.js`
that calls the `refresh-item` Edge Function when stale (a daily window,
not five minutes — see cost math in §4) and falls back to the last cache
when offline or on failure, matching `getCryptoPrices()`'s never-throw
contract. This is what a new Finance tab renders — the existing module
never touches Supabase or Plaid directly, only this cache, so it fits the
same "interfaces never import the data layer directly" boundary rule in
`ARCHITECTURE.md`. Institution linking status (`plaid_items.status`) would
surface the same way — a small "Chase: needs reconnect" banner sourced
from a lightweight status field the client is allowed to read.

## 4. Cost tiers, concretely

- **Sandbox** — free, forever, fake test institutions/data (Plaid ships a
  standard set of test usernames/passwords for its sandbox banks). This is
  where all development and end-to-end testing happens before touching
  Production. No cost to build and fully exercise the flow.
- **Production** — usage-based, per the numbers already in
  `FEATURE_LIST.md`:
  - **Transactions / Investments** (subscription-style products, what a
    personal balance+transaction view actually needs): **~$0.30–$3 per
    connected account, per month**, billed as long as the Item stays
    linked, whether or not it's actively queried that month.
  - **Auth / Identity / Income** (one-time verification products, not
    needed for a read-only balance/transaction view): **$0.10–$0.60 per
    call**, billed once per successful call rather than monthly.
  - Plaid doesn't publish one flat price list — actual rate depends on
    the specific product bundle and is confirmed at signup; meaningful
    scale (dozens+ of users) typically moves to a negotiated contract
    with a monthly minimum.

**Personal, single-user estimate:** 2–4 linked accounts (say, one
checking, one savings, one credit card, maybe one brokerage), refreshed
once daily — Transactions product, at the high end of the per-account
range ($3/account/month) that's **$6–$12/month**; at the low end
($0.30/account/month) closer to **$0.60–$1.20/month**. Refresh frequency
doesn't change the monthly per-account fee (it's a standing connection
fee, not metered per pull) unless the product used is call-metered, so
daily vs. hourly refresh has no material cost impact under the
Transactions product specifically — it would matter under a
call-billed product.

**If extended to a few friends:** this is where Plaid breaks the pattern
this project has used everywhere else for shared-cost features. The AI
Assistant and Daily Paper features have a real "sponsor a friend" path
because one Anthropic API key can serve many conversations for many
people — the marginal cost of one more friend using Alek's key is just
more token usage against one shared budget, throttleable and
observable in one place. **Plaid has no equivalent.** A Plaid `access_token`
is issued per end-user's login at one specific institution — there is no
"one shared Plaid credential" that covers multiple people's bank
accounts. Every friend who wants their own bank linked means:
- Their own Plaid Link flow (their own bank login, never Alek's or Life
  OS's problem to hold).
- Their own `plaid_items` row(s), under their own `user_id`.
- Their own recurring per-account monthly fee, stacking linearly — 3
  friends each with 3 accounts is 9x a single person's 3-account cost, not
  a shared flat fee. There's no economy of scale the way one Claude API
  key serves unlimited chat volume within its rate limit.

So "sponsoring" Plaid for friends isn't a matter of gating one shared key
behind a proxy (the pattern that works for AI) — it's a matter of Alek's
Plaid *billing account* eating a real, linear, per-person recurring
dollar cost for as long as that friend's bank stays linked. Worth deciding
explicitly, if it ever comes up, whether friends get their own free-tier
Plaid usage (not realistic — Plaid's free tier is Sandbox-only, fake
data) or whether this stays personal-only.

## 5. Scope options

Three tiers of ambition, in increasing order of complexity and risk:

1. **Read-only balance + recent-transaction sync only (recommended
   starting scope).** Link a bank/card account, show its current balance
   and a scrollback of recent transactions (Plaid's `/transactions/sync`
   endpoint, which is built for exactly this — an incremental cursor-based
   feed, not a bulk historical dump). No categorization, no write access,
   no money movement (Plaid Auth/Transfer products are entirely out of
   scope — this app should never be able to move money, only see it).
   This is the safest and cheapest tier, and it's enough to answer "what
   do I actually have across all my accounts right now" — the core value
   of a Financial Center.
2. **Investment holdings.** Same shape, different Plaid product
   (Investments) — brokerage/retirement account balances and holdings
   (ticker, quantity, value). More expensive per account, and requires
   deciding whether Life OS should attempt any price/performance math on
   top of raw holdings data, or just display what Plaid returns.
3. **Categorization / auto-reconciliation.** Already flagged as its own
   moonshot elsewhere (`FEATURE_LIST.md`'s "Real financial ingestion &
   reconciliation" — importing statements, auto-categorizing, reconciling
   against logged Bills/Subscriptions). This is a genuinely separate
   project on top of basic Plaid linking: it needs real categorization
   logic (Plaid does return a `personal_finance_category` per
   transaction, which helps, but matching transactions back to specific
   Bills/Subscriptions records reliably is real logic, not a given), and
   probably its own design pass on what "reconciled" even means for this
   app (auto-mark a Bill paid when a matching debit appears? flag for
   manual confirm? both, configurable?).

**Recommendation: start and stop at tier 1** (balances + recent
transactions, no categorization/reconciliation) for an initial build.
Tier 1 alone already answers the actual question ("what's really in my
accounts") without taking on the harder, error-prone problem of matching
transactions to existing records. Tiers 2 and 3 stay explicitly deferred,
revisit once tier 1 has been live for a while.

## 6. Security considerations specific to Plaid

- **Webhook signature verification.** Plaid calls the app's webhook
  endpoint (not the other way around) to announce things like new
  transactions available, an item error, or a required re-auth. Plaid
  signs these requests (a JWT in the `Plaid-Verification` header,
  verified against Plaid's published JWK set) — the Edge Function *must*
  verify this signature before trusting webhook payloads, since the
  webhook URL is otherwise a public, unauthenticated endpoint. This is a
  meaningfully different trust model from every other webhook-shaped
  thing in this app (there are none yet — Sharebox and Drive/Calendar
  sync are all pull-based from the client). Skipping verification would
  let anyone who finds the URL claim to be Plaid.
- **Token rotation/revocation — two different "disconnect" paths that
  both need handling:**
  - **In-app disconnect** (Alek clicks "unlink" in Life OS) should call
    Plaid's `/item/remove` to formally revoke the access token
    server-side, then delete (or mark revoked) the `plaid_items` row.
    Simply deleting the local row without calling `/item/remove` leaves
    an active, billable, still-functional access token at Plaid that Life
    OS has just lost track of.
  - **Bank-side or Plaid-dashboard disconnect** (the bank revokes
    consent, or Alek removes the connection from Plaid's own dashboard
    rather than in-app) — the app finds out about this asynchronously,
    via a webhook (`ITEM_ERROR` / item login-required events) or the next
    time a refresh call fails with an auth error. The `plaid_items.status`
    field needs a `reauth_required` / `revoked` state so the UI can show
    "this bank disconnected itself, reconnect?" rather than silently
    showing stale balances forever.
- **Periodic re-auth.** Banks routinely expire Plaid's standing consent
  (regulatory requirement in some cases, or just a bank's own security
  policy) — this isn't a bug, it's expected behavior every linked
  institution will eventually hit. When it happens, Plaid marks the Item
  as needing "update mode" — the user has to go through Plaid Link again
  (a lighter re-auth flow, not a full new link) to refresh consent. The
  UI needs a clear "reconnect" affordance per linked institution, and the
  refresh job needs to detect this state (a specific Plaid error code)
  rather than treating it as a generic fetch failure.
- **This is real financial credential exposure risk, categorically past
  what any other integration here carries.** The AI Assistant's worst
  case if mishandled is wasted API spend or an exposed personal API key
  the user rotates in one click. Telegram's worst case is spam through a
  bot the user fully controls. A mishandled Plaid integration's worst case
  is exposure of real account balances and transaction history for actual
  bank accounts — a materially different severity class, and worth extra
  review (a real second pass, maybe an actual security-focused code
  review pass — see the `security-review` skill already available in this
  environment) before any Production credentials are ever created, not
  just Sandbox.

## 7. Rough implementation order

1. **Sandbox Edge Function + fake data, end to end.** Stand up
   `create-link-token` / `exchange-public-token` / `refresh-item` against
   Plaid Sandbox only, using Plaid's documented test institutions/
   credentials. Prove the whole flow (Link token → Link UI → public token
   → exchange → stored access token → balance fetch) works without
   touching a single real table or real money. Free, no risk, safe to do
   any time.
2. **Add the Supabase tables + RLS** (`plaid_items`, `plaid_accounts`)
   exactly as scoped in §3, verified against Sandbox data — confirm the
   authenticated role genuinely cannot select `plaid_items.access_token`
   (test this explicitly, not just by reading the policy — try it from
   the browser console with the real anon-scoped client).
3. **Build the Finance UI** — a new tab (or a section within an existing
   tab) showing linked institutions, account balances, and recent
   transactions, reading only from the local `plaidAccountsCache` per §3,
   plus a "Link a bank" button that drives the Plaid Link widget and an
   "unlink" action per institution. Still entirely on Sandbox data at this
   point — this is buildable and demo-able with zero real financial risk.
4. **Apply for Plaid Production access.** Plaid requires a business
   use-case review before granting Production API keys (not automatic
   like Sandbox) — this step has lead time and isn't purely technical;
   budget for back-and-forth with Plaid's review process, and note that a
   personal/individual-use case may need to be framed clearly for their
   review (this is a personal finance app for one person's own accounts,
   not a fintech product for third-party end users — worth stating
   explicitly in the application to avoid a mismatch with their usual
   reviewed use cases, which skew toward companies onboarding many
   external users).
5. **Real accounts, real money — go live carefully.** Once Production
   access is granted: create Production secrets (separate from Sandbox's,
   stored the same way), link Alek's actual accounts one at a time,
   confirm balances match reality, confirm the webhook/re-auth/disconnect
   paths all behave as designed (§6) before considering this "done" —
   this is the one step in the whole project where a mistake has a real
   dollar/privacy cost, so it's worth being unusually deliberate about
   testing the unlink/revoke paths *before* linking anything that
   actually matters.

## 8. Open questions for Alek to decide later

- Investment/brokerage accounts in scope for v1, or defer to a later pass
  (§5 tier 2)? Affects which Plaid products get requested in the
  Production application.
- Which banks/institutions to prioritize testing with first once
  Production access exists — some institutions are flakier than others
  through Plaid and it's worth knowing which of Alek's actual banks are
  well-supported before committing to this as the primary way to see
  balances.
- Does transaction categorization matter enough to eventually build tier
  3 (§5), or is "see the real balance and recent transactions" the whole
  point and reconciliation/categorization isn't actually wanted?
- Refresh cadence — daily is the assumption used for the cost estimate in
  §4; would Alek want more frequent (there's no cost penalty for more
  frequent syncs on the Transactions product itself, but there may be
  practical rate limits worth checking at signup)?
- Any interest in ever extending this to a friend, given §4's finding that
  there's no cheap "sponsor" path the way there is for the AI features —
  worth deciding now whether that's a hard no, so it doesn't get
  half-designed later assuming a cost-sharing mechanism that doesn't
  actually exist for Plaid.
- Should `plaid_transactions` be a persisted Supabase table (per §3's
  aside) or purely a client-side cache refreshed on demand — affects how
  much transaction history is available offline vs. how much
  server-side storage/complexity this adds.

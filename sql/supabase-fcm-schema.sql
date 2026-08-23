-- Life OS -- FCM device tokens and send bookkeeping (§7 D-5 Phase 2).
-- Run once in the Supabase SQL Editor (web, no terminal). Idempotent.
--
-- This is the native-app replacement for push_subscriptions, which holds Web Push
-- endpoints the Kotlin app cannot receive on. That table is left alone: it belongs
-- to the deleted web build's history, and dropping it is not this change's business.
--
-- Nothing writes here until a Firebase project exists -- the app has no token to
-- register without one -- so running this early is harmless.

-- One row per device per account. The token rolls (reinstall, app data cleared, a
-- restore onto a new phone), so the app upserts on every launch and the send
-- function deletes rows FCM reports as dead.
create table if not exists fcm_tokens (
  user_id uuid not null references auth.users(id) on delete cascade,
  token text primary key,
  platform text not null default 'android',
  updated_at timestamptz not null default now()
);

alter table fcm_tokens enable row level security;

-- You only ever touch your own devices. The Edge Function reads across accounts with
-- the service-role key, which bypasses RLS by design -- that key lives only in
-- Supabase secrets.
drop policy if exists "users manage their own device tokens" on fcm_tokens;
create policy "users manage their own device tokens" on fcm_tokens
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create index if not exists fcm_tokens_user_idx on fcm_tokens (user_id);

-- What has already been pushed today. Without it, a cron that runs hourly sends the
-- same overdue task every hour -- and a notification you have learned to swipe away
-- is worse than no notification.
create table if not exists fcm_sent (
  user_id uuid not null references auth.users(id) on delete cascade,
  subject text not null,          -- "<storage key>|<record id>", as the app builds it
  sent_on date not null,
  primary key (user_id, subject, sent_on)
);

alter table fcm_sent enable row level security;

drop policy if exists "users read their own push history" on fcm_sent;
create policy "users read their own push history" on fcm_sent
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Old rows are of no interest the day after. Kept for a week so a bug is still
-- diagnosable, then swept -- the sweep is part of the cron file, not a trigger.
create index if not exists fcm_sent_day_idx on fcm_sent (sent_on);

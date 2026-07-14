-- Life OS -- Web Push schema (real background notifications)
-- ============================================================
-- The table the client writes its push subscription into (js/data/push.js),
-- and the server half (a Supabase Edge Function) reads to know where to send.
--
-- This is idempotent and safe to run whether or not it already exists: the
-- same table was sketched in supabase-schema.sql's "FUTURE" section, so if you
-- ran that in full it's already here and this is a no-op. Run it anyway to be
-- sure before turning push on.
--
-- HOW TO APPLY: Supabase SQL Editor (or the Management API's
-- POST /v1/projects/{ref}/database/query), same as the other schema files.
-- ============================================================

create table if not exists push_subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  endpoint text not null,
  p256dh text not null,
  auth_key text not null,
  created_at timestamptz not null default now(),
  unique (user_id, endpoint)
);

alter table push_subscriptions enable row level security;

-- One subscription belongs to one account; you only ever touch your own rows.
-- (The Edge Function reads across users with the service-role key, which
-- bypasses RLS by design -- that key lives only in Supabase secrets.)
drop policy if exists "users manage their own push subscriptions" on push_subscriptions;
create policy "users manage their own push subscriptions" on push_subscriptions
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

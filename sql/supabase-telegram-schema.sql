-- Life OS -- Two-way Telegram schema. Run once in the Supabase SQL Editor
-- (web, no terminal). Idempotent.
--
-- telegram_links: the chat <-> account mapping the webhook uses to know whose
-- data a message belongs to (also sketched in supabase-schema.sql's FUTURE
-- section; re-created here so this file is self-contained).
--
-- telegram_link_tokens: short-lived one-time tokens the APP mints when you tap
-- "Connect", handed to the bot via a t.me/<bot>?start=<token> deep link. The
-- webhook resolves the token to your user_id, writes telegram_links, and
-- deletes the token.

create table if not exists telegram_links (
  user_id uuid primary key references auth.users(id) on delete cascade,
  telegram_chat_id bigint not null unique,
  linked_at timestamptz not null default now()
);
alter table telegram_links enable row level security;
drop policy if exists "users manage their own telegram link" on telegram_links;
create policy "users manage their own telegram link" on telegram_links
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create table if not exists telegram_link_tokens (
  token text primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table telegram_link_tokens enable row level security;
-- The app (as the signed-in user) mints and reads its own tokens. The webhook
-- resolves/deletes them with the service-role key, which bypasses RLS.
drop policy if exists "users manage their own link tokens" on telegram_link_tokens;
create policy "users manage their own link tokens" on telegram_link_tokens
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Life OS -- Accounts schema (email/password + Google, app-wide identity)
--
-- Adds a `profiles` table: one row per auth.users account, holding the
-- display name shown across the app (distinct from sharebox_members'
-- per-space display_name, which stays as-is -- this is the account-level
-- identity Sharebox's own display name can eventually default from).
--
-- Google sign-in already works (see supabase-schema.sql / SUPABASE_MIGRATION.md).
-- This adds email/password as a second sign-in method -- Supabase's Email
-- provider is enabled by default on new projects; verify it's still on under
-- Authentication -> Providers -> Email before relying on this.
--
-- Run this in the Supabase SQL Editor (or via the Management API if you have
-- a personal access token and reachable network) the same way
-- supabase-schema.sql and supabase-sharebox-rls-fix.sql were run.

create table if not exists profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

drop trigger if exists profiles_set_updated_at on profiles;
create trigger profiles_set_updated_at
  before update on profiles
  for each row execute function set_updated_at();

alter table profiles enable row level security;

create policy "read your own profile" on profiles
  for select using (id = auth.uid());

create policy "update your own profile" on profiles
  for update using (id = auth.uid()) with check (id = auth.uid());

-- Auto-create a profile row the moment an account is created (email/password
-- signup or first Google sign-in), so the client never has to special-case
-- "no profile row yet." Best-effort display name from whatever the sign-up
-- method supplied (Google's full_name, or blank for email/password -- the
-- user fills it in via the Account section afterward).
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'name', ''))
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Backfill: anyone who already has an auth.users row from Google sign-in
-- before this migration ran (e.g. from Sharebox v2 testing) gets a profile
-- row too, so this is safe to run on an already-live project.
insert into public.profiles (id, display_name)
select u.id, coalesce(u.raw_user_meta_data->>'full_name', u.raw_user_meta_data->>'name', '')
from auth.users u
on conflict (id) do nothing;

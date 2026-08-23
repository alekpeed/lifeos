-- Life OS -- schedule the daily Telegram digest (§7 D-5 Phase 1). Run once in
-- the Supabase SQL Editor (web, no terminal). Idempotent: re-running replaces
-- the schedule rather than adding a second one.
--
-- Needs pg_cron and pg_net -- enable both under Database -> Extensions first
-- (or the create-extension lines below will do it if your project allows).
--
-- Before this does anything you also need, once:
--   1. supabase functions deploy telegram-digest
--   2. supabase secrets set TELEGRAM_BOT_TOKEN=...   (the same bot the webhook uses)
--   3. a linked chat -- Settings -> Telegram -> Connect in the app, which mints a
--      token and hands it to the bot. Without a row in telegram_links the function
--      has nobody to send to and exits reporting "no linked chats".
--
-- The Authorization header uses the PUBLIC anon key, purely to satisfy the
-- function's JWT check -- it is already public (shipped in the app). The function
-- reads data with its own service-role key, auto-injected by Supabase, so nothing
-- secret is embedded here.

create extension if not exists pg_cron;
create extension if not exists pg_net;

select cron.unschedule('lifeos-telegram-digest')
where exists (select 1 from cron.job where jobname = 'lifeos-telegram-digest');

select cron.schedule(
  'lifeos-telegram-digest',
  '0 13 * * *',  -- 13:00 UTC daily (~8-9am US eastern). Adjust to your own morning.
  $$
  select net.http_post(
    url := 'https://ukqdbxxhxxafbcnkmskg.supabase.co/functions/v1/telegram-digest',
    headers := jsonb_build_object(
      'Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVrcWRieHhoeHhhZmJjbmttc2tnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM1MzA5MzcsImV4cCI6MjA5OTEwNjkzN30.Z-h6cSQrlIYjmM1ROs4oaBxPHpAb8ajwT5QGVgaPWmo',
      'Content-Type', 'application/json'
    )
  );
  $$
);

-- Check it landed:
--   select jobname, schedule, active from cron.job where jobname = 'lifeos-telegram-digest';
-- See what the last few runs did:
--   select status, return_message, start_time from cron.job_run_details
--   where jobid = (select jobid from cron.job where jobname = 'lifeos-telegram-digest')
--   order by start_time desc limit 5;
--
-- Send one right now without waiting for the schedule (same statement the cron runs):
--   select net.http_post(
--     url := 'https://ukqdbxxhxxafbcnkmskg.supabase.co/functions/v1/telegram-digest',
--     headers := jsonb_build_object('Authorization', 'Bearer <anon key above>',
--                                   'Content-Type', 'application/json'));

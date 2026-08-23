-- Life OS -- schedule the per-item FCM push (§7 D-5 Phase 2). Run once in the
-- Supabase SQL Editor. Idempotent: re-running replaces the schedule rather than
-- adding a second one.
--
-- Needs pg_cron and pg_net -- enable both under Database -> Extensions first.
--
-- Before this does anything you also need, once:
--   1. A Firebase project, with this app's package name registered in it
--      (com.alekpeed.lifeos). Only the account owner can create one.
--   2. supabase secrets set FCM_SERVICE_ACCOUNT="$(cat service-account.json)"
--      -- the whole key file. It must never enter this repository or the app.
--   3. supabase functions deploy send-fcm
--   4. sql/supabase-fcm-schema.sql, and a device that has registered a token.
-- Without a token in fcm_tokens the function exits reporting "no registered
-- devices" and costs nothing, so scheduling it early is safe.
--
-- Hourly rather than daily, and that is the point of this transport: the digest
-- (Phase 1) is the once-a-morning look-ahead, this is the thing that is due now.
-- The function sends each record at most once a day regardless of how often this
-- fires, so raising the frequency does not raise the noise.
--
-- The Authorization header uses the PUBLIC anon key, purely to satisfy the
-- function's JWT check -- it is already public (shipped in the app). The function
-- reads data with its own service-role key, auto-injected by Supabase.

create extension if not exists pg_cron;
create extension if not exists pg_net;

select cron.unschedule('lifeos-send-fcm')
where exists (select 1 from cron.job where jobname = 'lifeos-send-fcm');

select cron.schedule(
  'lifeos-send-fcm',
  '0 12-23 * * *',  -- hourly through the waking day, UTC (~8am-7pm US eastern)
  $$
  select net.http_post(
    url := 'https://ukqdbxxhxxafbcnkmskg.supabase.co/functions/v1/send-fcm',
    headers := jsonb_build_object(
      'Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVrcWRieHhoeHhhZmJjbmttc2tnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM1MzA5MzcsImV4cCI6MjA5OTEwNjkzN30.Z-h6cSQrlIYjmM1ROs4oaBxPHpAb8ajwT5QGVgaPWmo',
      'Content-Type', 'application/json'
    )
  );
  $$
);

-- Sweep the send history weekly. A week is long enough that "why did I not get a
-- push for that?" is still answerable, short enough that the table stays small.
select cron.unschedule('lifeos-fcm-sent-sweep')
where exists (select 1 from cron.job where jobname = 'lifeos-fcm-sent-sweep');

select cron.schedule(
  'lifeos-fcm-sent-sweep',
  '30 4 * * 0',
  $$ delete from fcm_sent where sent_on < current_date - 7 $$
);

-- Check it landed:
--   select jobname, schedule, active from cron.job where jobname like 'lifeos-%fcm%';
-- See what the last few runs did:
--   select status, return_message, start_time from cron.job_run_details
--   where jobid = (select jobid from cron.job where jobname = 'lifeos-send-fcm')
--   order by start_time desc limit 5;

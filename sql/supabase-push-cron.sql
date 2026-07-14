-- Life OS -- schedule the daily Web Push digest. Run once in the Supabase SQL
-- Editor (web, no terminal). Needs the pg_cron and pg_net extensions -- enable
-- both under Database -> Extensions first (or the create-extension lines below
-- will do it if your project allows).
--
-- The Authorization header uses the PUBLIC anon key, purely to satisfy the
-- function's JWT check -- it's already public (shipped in the app). The
-- function reads your data with its own service-role key, auto-injected by
-- Supabase, so nothing secret is embedded here.

create extension if not exists pg_cron;
create extension if not exists pg_net;

-- Re-running is safe: unschedule any prior copy first.
select cron.unschedule('lifeos-daily-push')
where exists (select 1 from cron.job where jobname = 'lifeos-daily-push');

select cron.schedule(
  'lifeos-daily-push',
  '0 13 * * *',  -- 13:00 UTC daily (~8-9am US). Adjust to your own morning.
  $$
  select net.http_post(
    url := 'https://ukqdbxxhxxafbcnkmskg.supabase.co/functions/v1/send-push',
    headers := jsonb_build_object(
      'Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVrcWRieHhoeHhhZmJjbmttc2tnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM1MzA5MzcsImV4cCI6MjA5OTEwNjkzN30.Z-h6cSQrlIYjmM1ROs4oaBxPHpAb8ajwT5QGVgaPWmo',
      'Content-Type', 'application/json'
    )
  );
  $$
);

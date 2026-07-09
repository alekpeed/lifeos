// Supabase project configuration.
//
// NOT WIRED IN YET. This file, supabase-client.js, and the SQL schema in
// sql/supabase-schema.sql are scaffolding for the planned migration of
// Sharebox (and later, push notifications / Telegram) off the Picker/Drive
// model and onto a real backend. Nothing here is imported by the running app
// yet — the existing Drive-based Sharebox (sharebox-sync.js, picker.js)
// keeps working untouched until this is built out and proven.
//
// TODO (fill in from the Supabase project dashboard, Settings -> API):
//   SUPABASE_URL: e.g. 'https://xxxxxxxxxxxx.supabase.co'
//   SUPABASE_ANON_KEY: the "anon" / "public" key -- safe to ship client-side
//     by design (same category as the Google Picker API key: meant to be
//     public, real access control lives in Postgres Row Level Security
//     policies, not in keeping this key secret).
export const SUPABASE_URL = 'https://ukqdbxxhxxafbcnkmskg.supabase.co';
export const SUPABASE_ANON_KEY = 'sb_publishable_1cq0ldBeDgQZjctSSLNm5g_iXEkRD';

export function isSupabaseConfigured() {
  return Boolean(SUPABASE_URL && SUPABASE_ANON_KEY);
}

// The self-contained vendored UMD bundle (js/data/supabase-client.js loads
// this via a <script> tag rather than an ES import, since the UMD build
// isn't itself an ES module -- see loadSupabaseScript() there).
export const SUPABASE_VENDOR_SCRIPT = './vendor/supabase/supabase.umd.js';

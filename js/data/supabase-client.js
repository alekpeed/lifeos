// Lazy-loaded Supabase client singleton. NOT WIRED IN YET -- see the note in
// supabase-config.js. Isolated here so whatever eventually needs Supabase
// (Sharebox v2, push-notification subscriptions, a Telegram link table) shares
// one client and one auth session instead of each re-initializing.
//
// The vendored bundle is a UMD build (self-contained, no bare imports --
// verified when vendoring it), not an ES module, so it's loaded via a
// dynamically-injected <script> tag that attaches `window.supabase`, exactly
// like the Google Picker/GIS scripts already do elsewhere in js/data/.

import { SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_VENDOR_SCRIPT, isSupabaseConfigured } from './supabase-config.js';

let scriptLoaded = null;
let client = null;

function loadScript() {
  if (scriptLoaded) return scriptLoaded;
  scriptLoaded = new Promise((resolve, reject) => {
    if (window.supabase?.createClient) return resolve();
    const s = document.createElement('script');
    s.src = SUPABASE_VENDOR_SCRIPT;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('Could not load the Supabase client script.'));
    document.head.appendChild(s);
  });
  return scriptLoaded;
}

// Returns the shared client, creating it on first call. Throws clearly if
// supabase-config.js hasn't been filled in yet, rather than failing deep
// inside some unrelated call site.
export async function getSupabaseClient() {
  if (client) return client;
  if (!isSupabaseConfigured()) {
    throw new Error('Supabase is not configured yet -- fill in SUPABASE_URL and SUPABASE_ANON_KEY in js/data/supabase-config.js.');
  }
  await loadScript();
  client = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: {
      // Keep the session in localStorage and silently refresh it, so a
      // returning user stays signed in across reloads / app launches.
      persistSession: true,
      autoRefreshToken: true,
      // Read the OAuth response out of the return URL after Google bounces
      // back, then strip it. PKCE returns a `?code=` QUERY param (not a URL
      // fragment), which matters here: the app uses hash-based routing
      // (#/chords), so a fragment-based OAuth response would collide with the
      // router. PKCE's query param sidesteps that entirely and is the more
      // secure flow for a public client anyway.
      detectSessionInUrl: true,
      flowType: 'pkce',
    },
  });
  return client;
}

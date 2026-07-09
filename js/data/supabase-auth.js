// Supabase Auth wrapper — Google sign-in for the Sharebox v2 backend.
//
// NOT WIRED IN YET (same gate as supabase-config.js): every call here funnels
// through getSupabaseClient(), which throws a clear "not configured" error
// until SUPABASE_URL / SUPABASE_ANON_KEY are filled in. So importing this file
// changes nothing about the running app; the Drive-based Sharebox is untouched.
//
// We chose Google sign-in (over magic-link email) so signing into Sharebox
// feels identical to the rest of the app's Google integrations. Supabase's
// Google provider must be enabled in the dashboard (Authentication ->
// Providers -> Google) with the OAuth Client ID + Secret before this runs.
//
// The flow is a full-page redirect: signInWithGoogle() sends the browser to
// Google, Google bounces back to `redirectTo` with a `?code=...` param, and
// the client (configured with flowType: 'pkce', detectSessionInUrl: true in
// supabase-client.js) exchanges that code for a session automatically on load.
// Nothing here has to parse the URL by hand.

import { getSupabaseClient } from './supabase-client.js';
import { isSupabaseConfigured } from './supabase-config.js';

// Where Google returns the user after they approve. We send them back to the
// app's own origin+path (no hash) so the router lands on the default route and
// the PKCE `?code=` param is on a clean URL for the client to consume.
function redirectTarget() {
  return window.location.origin + window.location.pathname;
}

// Kick off the redirect to Google. Resolves once the redirect has been
// requested; the actual sign-in completes after the browser returns and the
// client picks the session out of the URL. Returns { error } on failure.
export async function signInWithGoogle() {
  const supabase = await getSupabaseClient();
  const { data, error } = await supabase.auth.signInWithOAuth({
    provider: 'google',
    options: {
      redirectTo: redirectTarget(),
      // Ask Google for a refresh token + a re-consent so Supabase can keep
      // the session alive without silently failing later.
      queryParams: { access_type: 'offline', prompt: 'consent' },
    },
  });
  if (error) return { error };
  return { url: data?.url };
}

export async function signOut() {
  const supabase = await getSupabaseClient();
  const { error } = await supabase.auth.signOut();
  return { error };
}

// The signed-in user, or null. Safe to call before config is filled in — it
// answers null instead of throwing, so callers can cheaply check "are we even
// on the new backend yet?" without a try/catch.
export async function getCurrentUser() {
  if (!isSupabaseConfigured()) return null;
  const supabase = await getSupabaseClient();
  const { data } = await supabase.auth.getUser();
  return data?.user || null;
}

// The full session (includes the access token), or null. Same no-throw
// contract as getCurrentUser().
export async function getSession() {
  if (!isSupabaseConfigured()) return null;
  const supabase = await getSupabaseClient();
  const { data } = await supabase.auth.getSession();
  return data?.session || null;
}

// Subscribe to sign-in / sign-out transitions. `cb` receives the new user (or
// null). Returns an unsubscribe function. No-op (returns a no-op unsubscribe)
// when Supabase isn't configured, so UI can wire this up unconditionally.
export function onAuthChange(cb) {
  if (!isSupabaseConfigured()) return () => {};
  let subscription = null;
  let cancelled = false;
  getSupabaseClient().then((supabase) => {
    if (cancelled) return;
    const { data } = supabase.auth.onAuthStateChange((_event, session) => {
      cb(session?.user || null);
    });
    subscription = data?.subscription || null;
  });
  return () => {
    cancelled = true;
    subscription?.unsubscribe();
  };
}

// Call once at app boot (js/app.js), awaited before any view renders.
//
// We redeem the Google redirect's PKCE `?code=` EXPLICITLY here rather than
// leaning on the client's silent detectSessionInUrl auto-exchange (which is
// turned off in supabase-client.js). Explicit is better for two reasons:
//   1. Debuggability -- the auto-exchange swallows the server's rejection and
//      only leaves a bare 401 in the network log; calling exchangeCodeForSession
//      ourselves gives us the real { status, message } to surface and log.
//   2. Correctness -- one deterministic exchange at a known point, instead of
//      an exchange that fires implicitly whenever the client happens to be
//      constructed (which is what let earlier races double-spend the code).
//
// On success we strip `?code=` from the URL (keeping any hash route) so a
// reload can't try to re-spend a now-used code. Never throws -- a failure is
// recorded on window.__shareboxAuthError and logged, so boot can await this
// safely and the Sharebox view can show a real message instead of a dead end.
export async function completePendingRedirectIfAny() {
  try {
    if (!isSupabaseConfigured()) return;
    const code = new URLSearchParams(window.location.search).get('code');
    if (!code) return;

    const supabase = await getSupabaseClient();
    const { error } = await supabase.auth.exchangeCodeForSession(code);
    if (error) {
      window.__shareboxAuthError = { status: error.status ?? null, message: error.message };
      console.error('[sharebox] Google sign-in code exchange failed:', error.status ?? '(no status)', '-', error.message);
      return;
    }
    // Success: drop the one-time code from the URL, preserve the hash route.
    const clean = window.location.origin + window.location.pathname + window.location.hash;
    window.history.replaceState({}, document.title, clean);
  } catch (err) {
    window.__shareboxAuthError = { status: null, message: err?.message || String(err) };
    console.error('[sharebox] Google sign-in code exchange threw:', err);
  }
}

// A friendly display name for the signed-in user, best-effort from the Google
// profile Supabase stores in user_metadata.
export function displayNameOf(user) {
  if (!user) return '';
  const m = user.user_metadata || {};
  return m.full_name || m.name || user.email || 'Someone';
}

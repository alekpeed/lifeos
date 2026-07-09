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

// A friendly display name for the signed-in user, best-effort from the Google
// profile Supabase stores in user_metadata.
export function displayNameOf(user) {
  if (!user) return '';
  const m = user.user_metadata || {};
  return m.full_name || m.name || user.email || 'Someone';
}

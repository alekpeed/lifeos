// Web Push (real background notifications -- alerts that land with the app
// CLOSED, unlike the foreground-only Notifications module). This is the client
// half: request permission, subscribe via the service worker's PushManager,
// and store the subscription in Supabase (push_subscriptions) so the server
// half -- a Supabase Edge Function, deployed separately -- can send to it.
//
// Nothing here fires a push itself; a static PWA can't. The Edge Function
// (see sql/supabase-push-schema.sql + the deploy runbook in SUPABASE_
// MIGRATION.md) is what wakes on a schedule and sends. This file only manages
// the subscription lifecycle.
//
// Gated three ways, each surfaced in getPushState(): the browser must support
// Web Push, a VAPID public key must be configured below, and the user must be
// signed in (subscriptions are per-account, same as cloud sync).

import { getSupabaseClient } from './supabase-client.js';
import { isSupabaseConfigured } from './supabase-config.js';

// The PUBLIC half of a VAPID key pair. Safe to ship client-side (it only lets
// a browser subscribe to THIS app server; the private half, which signs pushes,
// lives only in Supabase secrets). Generate a pair once with
// `npx web-push generate-vapid-keys` -- paste the public key here, put the
// private key in Supabase secrets (see the runbook). Empty = push not set up,
// and getPushState() reports configured:false so the UI stays inert.
export const VAPID_PUBLIC_KEY = '';

const TABLE = 'push_subscriptions';

export function isPushSupported() {
  return typeof window !== 'undefined'
    && 'serviceWorker' in navigator
    && 'PushManager' in window
    && 'Notification' in window;
}

// VAPID keys are base64url; PushManager wants a Uint8Array.
function urlBase64ToUint8Array(base64) {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  const normalized = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(normalized);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

async function getRegistration() {
  return navigator.serviceWorker.ready;
}

async function requireUser() {
  const supabase = await getSupabaseClient();
  const { data } = await supabase.auth.getUser();
  if (!data?.user) throw new Error('Sign in (Settings > Account) before enabling push notifications.');
  return { supabase, userId: data.user.id };
}

export async function getPushState() {
  const supported = isPushSupported();
  const configured = Boolean(VAPID_PUBLIC_KEY) && isSupabaseConfigured();
  const state = {
    supported,
    configured,
    permission: supported ? Notification.permission : 'unsupported',
    subscribed: false,
    signedIn: false,
  };
  if (!supported || !configured) return state;

  try {
    const reg = await getRegistration();
    const sub = await reg.pushManager.getSubscription();
    state.subscribed = Boolean(sub);
  } catch { /* no registration yet -> not subscribed */ }

  try {
    const supabase = await getSupabaseClient();
    const { data } = await supabase.auth.getUser();
    state.signedIn = Boolean(data?.user);
  } catch { /* signed out */ }

  return state;
}

export async function enablePush() {
  if (!isPushSupported()) throw new Error('This browser does not support push notifications.');
  if (!VAPID_PUBLIC_KEY) throw new Error('Push is not set up yet (no VAPID key configured).');
  const { supabase, userId } = await requireUser();

  const permission = await Notification.requestPermission();
  if (permission !== 'granted') throw new Error('Notification permission was not granted.');

  const reg = await getRegistration();
  const sub = await reg.pushManager.subscribe({
    userVisibleOnly: true, // required: every push must show a notification
    applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
  });

  const json = sub.toJSON();
  const { error } = await supabase.from(TABLE).upsert({
    user_id: userId,
    endpoint: json.endpoint,
    p256dh: json.keys.p256dh,
    auth_key: json.keys.auth,
  }, { onConflict: 'user_id,endpoint' });
  if (error) throw error;

  return { subscribed: true };
}

export async function disablePush() {
  if (!isPushSupported()) return { subscribed: false };
  const reg = await getRegistration();
  const sub = await reg.pushManager.getSubscription();
  if (sub) {
    // Remove the server-side row first (best-effort), then unsubscribe locally.
    try {
      const { supabase } = await requireUser();
      await supabase.from(TABLE).delete().eq('endpoint', sub.endpoint);
    } catch { /* signed out or offline -> still unsubscribe locally below */ }
    await sub.unsubscribe();
  }
  return { subscribed: false };
}

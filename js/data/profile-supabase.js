// Account profile: the one `profiles` row per signed-in user (display name),
// distinct from Sharebox's per-space membership display name. See
// sql/supabase-accounts-schema.sql for the table + the trigger that creates
// this row automatically on signup.

import { getSupabaseClient } from './supabase-client.js';

// Returns { id, display_name, created_at, updated_at } or null if signed out.
// The row always exists once signed in (auto-created by the on_auth_user_created
// trigger), so this doesn't need an insert-if-missing fallback.
export async function getProfile() {
  const supabase = await getSupabaseClient();
  const { data: userData } = await supabase.auth.getUser();
  const user = userData?.user;
  if (!user) return null;
  const { data, error } = await supabase.from('profiles').select('*').eq('id', user.id).maybeSingle();
  if (error) throw error;
  return data;
}

export async function updateDisplayName(displayName) {
  const supabase = await getSupabaseClient();
  const { data: userData } = await supabase.auth.getUser();
  const user = userData?.user;
  if (!user) throw new Error('Not signed in');
  const { data, error } = await supabase
    .from('profiles')
    .update({ display_name: displayName })
    .eq('id', user.id)
    .select()
    .single();
  if (error) throw error;
  return data;
}

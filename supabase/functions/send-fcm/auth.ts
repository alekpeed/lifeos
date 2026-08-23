// Google service-account auth for FCM HTTP v1 (§7 D-5 Phase 2).
//
// FCM's v1 API takes an OAuth2 bearer token, and the only way to mint one from a
// server is to sign a JWT with the service account's private key and trade it at
// Google's token endpoint. There is no library for this in Deno's std, and pulling a
// whole Google SDK into an Edge Function to do four lines of Web Crypto would be the
// larger risk — so it is written out here, and unit tested against a real key.
//
// THE KEY NEVER ENTERS THIS REPOSITORY. It is read from the FCM_SERVICE_ACCOUNT
// secret (`supabase secrets set FCM_SERVICE_ACCOUNT="$(cat key.json)"`), which is
// also why parseServiceAccount fails loudly on a malformed value rather than
// defaulting to something: a silent auth failure here looks exactly like "nothing was
// due today".

export type ServiceAccount = {
  projectId: string;
  clientEmail: string;
  privateKey: string;
};

const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';

export function parseServiceAccount(raw: string | undefined): ServiceAccount {
  if (!raw || !raw.trim()) throw new Error('FCM_SERVICE_ACCOUNT is not set');
  let j: Record<string, unknown>;
  try {
    j = JSON.parse(raw);
  } catch {
    throw new Error('FCM_SERVICE_ACCOUNT is not JSON — paste the whole key file');
  }
  const projectId = String(j.project_id ?? '');
  const clientEmail = String(j.client_email ?? '');
  // Stored through a shell or a web form, the PEM's newlines usually arrive escaped.
  const privateKey = String(j.private_key ?? '').replace(/\\n/g, '\n');
  if (!projectId || !clientEmail || !privateKey) {
    throw new Error('FCM_SERVICE_ACCOUNT is missing project_id, client_email or private_key');
  }
  return { projectId, clientEmail, privateKey };
}

export function base64url(bytes: Uint8Array): string {
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function encodeJson(value: unknown): string {
  return base64url(new TextEncoder().encode(JSON.stringify(value)));
}

// A PEM body is base64-encoded DER; Web Crypto wants the DER bytes.
export function pemToDer(pem: string): Uint8Array {
  const body = pem
    .replace(/-----BEGIN [^-]+-----/, '')
    .replace(/-----END [^-]+-----/, '')
    .replace(/\s+/g, '');
  if (!body) throw new Error('private_key does not look like a PEM block');
  const bin = atob(body);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function claimsFor(account: ServiceAccount, nowSeconds: number): Record<string, unknown> {
  return {
    iss: account.clientEmail,
    scope: SCOPE,
    aud: TOKEN_URL,
    iat: nowSeconds,
    // An hour is Google's maximum. The function runs for seconds, so the token is
    // minted per run and never cached — nothing here outlives the request.
    exp: nowSeconds + 3600,
  };
}

export async function signJwt(account: ServiceAccount, nowSeconds: number): Promise<string> {
  const header = { alg: 'RS256', typ: 'JWT' };
  const unsigned = `${encodeJson(header)}.${encodeJson(claimsFor(account, nowSeconds))}`;
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToDer(account.privateKey),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${base64url(new Uint8Array(sig))}`;
}

export async function accessToken(account: ServiceAccount, nowSeconds: number): Promise<string> {
  const assertion = await signJwt(account, nowSeconds);
  const res = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
  });
  if (!res.ok) throw new Error(`google token exchange failed: ${res.status} ${await res.text()}`);
  const j = await res.json();
  const token = j?.access_token;
  if (typeof token !== 'string' || !token) throw new Error('google returned no access_token');
  return token;
}

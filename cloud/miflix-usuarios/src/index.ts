import { clientIds, verifyGoogle, InvalidIdentity } from './google.js';
import type { JWTVerifyGetKey } from 'jose';
export interface Env {
  GOOGLE_WEB_CLIENT_IDS: string;
  GOOGLE_ANDROID_CLIENT_IDS?: string;
  AUTH_RATE_LIMITER: { limit(input: { key: string }): Promise<{ success: boolean }> };
}
const MAX_BODY = 12288;
function json(status: number, body: object): Response {
  return new Response(JSON.stringify(body), { status, headers: {
    'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer',
  } });
}
class InputError extends Error { constructor(readonly status: number) { super(); } }
async function readInput(request: Request): Promise<{ idToken: string; nonce: string }> {
  const length = request.headers.get('content-length');
  if (length !== null && (!/^\d+$/.test(length) || Number(length) > MAX_BODY)) throw new InputError(413);
  if (request.headers.get('content-encoding') ||
      request.headers.get('content-type')?.split(';')[0].trim().toLowerCase() !== 'application/json')
    throw new InputError(415);
  if (!request.body) throw new InputError(400);
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = []; let size = 0;
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; void reader.cancel(); }, 5000);
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY) { void reader.cancel(); throw new InputError(413); }
      chunks.push(value);
    }
  } finally { clearTimeout(timer); reader.releaseLock(); }
  if (timedOut) throw new InputError(408);
  const bytes = new Uint8Array(size); let at = 0;
  for (const chunk of chunks) { bytes.set(chunk, at); at += chunk.length; }
  let body: unknown;
  try { body = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)); }
  catch { throw new InputError(400); }
  if (typeof body !== 'object' || body === null || Array.isArray(body)) throw new InputError(400);
  const data = body as Record<string, unknown>;
  if (Object.keys(data).some(k => k !== 'idToken' && k !== 'nonce') ||
      typeof data.idToken !== 'string' || data.idToken.length > 8192 ||
      !/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(data.idToken) ||
      typeof data.nonce !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(data.nonce)) throw new InputError(400);
  return { idToken: data.idToken, nonce: data.nonce };
}
/** keys is absent in the production entry point; tests supply a local signed JWKS. */
export async function handle(request: Request, env: Env, keys?: JWTVerifyGetKey): Promise<Response> {
  if (new URL(request.url).protocol !== 'https:') return json(400, { error: 'https_required' });
  // Native-only API. No browser origins, cookies or permissive CORS in this phase.
  if (request.headers.has('origin')) return json(403, { error: 'web_not_enabled' });
  const path = new URL(request.url).pathname;
  if (path === '/health' && request.method === 'GET') return json(200, { ok: true, service: 'miflix-usuarios', version: '0.1.0' });
  if (path !== '/auth/google') return json(404, { error: 'not_found' });
  if (request.method !== 'POST') return json(405, { error: 'method_not_allowed' });
  const audiences = clientIds(env.GOOGLE_WEB_CLIENT_IDS);
  const parties = clientIds(env.GOOGLE_ANDROID_CLIENT_IDS);
  if (!audiences.length || !env.AUTH_RATE_LIMITER ||
      (env.GOOGLE_ANDROID_CLIENT_IDS?.trim() && !parties.length)) return json(503, { error: 'not_configured' });
  try {
    // CF sets this header at the edge. This pre-auth limit is intentionally by network,
    // not by caller-supplied sub/email. NAT can share the quota; not a global abuse barrier.
    const ip = request.headers.get('CF-Connecting-IP') ?? 'unknown';
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(ip));
    const key = 'google:' + Array.from(new Uint8Array(digest), n => n.toString(16).padStart(2, '0')).join('');
    if (!(await env.AUTH_RATE_LIMITER.limit({ key })).success) return json(429, { error: 'try_later' });
    const { idToken, nonce } = await readInput(request);
    const result = await verifyGoogle(idToken, nonce, audiences, parties, keys);
    return json(200, { authenticated: true, ...result, authorization: { privateDataAllowed: false } });
  } catch (e) {
    if (e instanceof InputError) return json(e.status, { error: 'invalid_request' });
    if (e instanceof InvalidIdentity) return json(401, { error: 'invalid_identity' });
    return json(503, { error: 'temporarily_unavailable' });
  }
}
export default { fetch: (request: Request, env: Env) => handle(request, env) };

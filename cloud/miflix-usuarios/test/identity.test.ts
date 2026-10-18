import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPair, exportJWK, createLocalJWKSet, createRemoteJWKSet, customFetch, SignJWT } from 'jose';
import { handle, type Env } from '../src/index.js';
import { verifyGoogle, clientIds, InvalidIdentity } from '../src/google.js';

// Ephemeral RSA keys and TEST ONLY audience, never configured in production.
const aud = '123-unit-web.apps.googleusercontent.com';
const android = '123-unit-android.apps.googleusercontent.com';
const nonce = 'N'.repeat(43);
let privateKey: CryptoKey; let keys: ReturnType<typeof createLocalJWKSet>;
const env: Env = { GOOGLE_WEB_CLIENT_IDS: aud, GOOGLE_ANDROID_CLIENT_IDS: android,
  AUTH_RATE_LIMITER: { limit: async () => ({ success: true }) } };
before(async () => {
  const pair = await generateKeyPair('RS256'); privateKey = pair.privateKey;
  keys = createLocalJWKSet({ keys: [{ ...await exportJWK(pair.publicKey), kid: 'unit-1', alg: 'RS256' }] });
});
async function signed(overrides: Record<string, unknown> = {}) {
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({ sub: 'unit-subject', iss: 'https://accounts.google.com', aud,
    iat: now, exp: now + 3600, nonce, azp: android,
    name: 'Test Person', email: 'test@example.invalid', email_verified: true, ...overrides })
    .setProtectedHeader({ alg: 'RS256', kid: 'unit-1' }).sign(privateKey);
}
function req(body: unknown, headers: Record<string, string> = {}) {
  return new Request('https://identity.example.org/auth/google', { method: 'POST',
    headers: { 'content-type': 'application/json', ...headers }, body: typeof body === 'string' ? body : JSON.stringify(body) });
}
test('health exposes no identity or configuration', async () => {
  const res = await handle(new Request('https://identity.example.org/health'), env);
  assert.deepEqual(await res.json(), { ok: true, service: 'miflix-usuarios', version: '0.1.0' });
});
test('missing token, malformed JSON/token and extra caller identity rejected', async () => {
  for (const input of [{}, '{', { idToken: 'invalid', nonce }, { idToken: await signed(), nonce, role: 'admin' }])
    assert.equal((await handle(req(input), env, keys)).status, 400);
});
test('valid token requires real cryptographic verification', async () => {
  const token = await signed(); const res = await handle(req({ idToken: token, nonce }), env, keys);
  assert.equal(res.status, 200); const body = await res.json() as any;
  assert.equal(body.identity.sub, 'unit-subject'); assert.equal(body.identity.email, 'test@example.invalid');
  assert.equal(body.authorization.privateDataAllowed, false); assert.equal(body.authenticated, true);
  assert.ok(!JSON.stringify(body).includes(token)); assert.equal(res.headers.get('cache-control'), 'no-store');
});
test('invalid RSA signature rejected (same header and claims)', async () => {
  const other = await generateKeyPair('RS256'); const good = await signed();
  const altered = await new SignJWT(JSON.parse(Buffer.from(good.split('.')[1], 'base64url').toString()))
    .setProtectedHeader({ alg: 'RS256', kid: 'unit-1' }).sign(other.privateKey);
  assert.equal((await handle(req({ idToken: altered, nonce }), env, keys)).status, 401);
});
for (const [label, claims] of Object.entries({
  expired: { exp: 1 }, audience: { aud: '456-foreign.apps.googleusercontent.com' },
  issuer: { iss: 'https://attacker.invalid' }, future: { iat: Math.floor(Date.now() / 1000) + 600 },
  nonce: { nonce: 'wrong' }, missingExpiry: { exp: undefined }, missingSubject: { sub: undefined },
  authorizedParty: { azp: '456-foreign.apps.googleusercontent.com' },
  arrayAudience: { aud: [aud, 'other'] }, longLifetime: { exp: Math.floor(Date.now()/1000) + 86400 },
})) test(`rejects ${label}`, async () => {
  const res = await handle(req({ idToken: await signed(claims), nonce }), env, keys);
  assert.equal(res.status, 401); assert.deepEqual(await res.json(), { error: 'invalid_identity' });
});
test('unsigned algorithm none cannot authenticate', async () => {
  const token = Buffer.from('{"alg":"none"}').toString('base64url') + '.' + Buffer.from('{}').toString('base64url') + '.AA';
  assert.equal((await handle(req({ idToken: token, nonce }), env, keys)).status, 401);
});
test('unverified email is omitted, not used as primary identity', async () => {
  const result = await verifyGoogle(await signed({ email_verified: false }), nonce, [aud], [android], keys);
  assert.equal(result.identity.email, undefined); assert.equal(result.identity.sub, 'unit-subject');
});
test('bounded content length and streamed body', async () => {
  assert.equal((await handle(req('{}', { 'content-length': '20000' }), env, keys)).status, 413);
  assert.equal((await handle(req('x'.repeat(13000)), env, keys)).status, 413);
  assert.equal((await handle(req('{}', { 'content-type': 'text/plain' }), env, keys)).status, 415);
});
test('no HTTP, browser origin, GET auth or redirects', async () => {
  assert.equal((await handle(new Request('http://identity.example.org/health'), env)).status, 400);
  const web = await handle(req({}, { origin: 'https://evil.invalid' }), env, keys);
  assert.equal(web.status, 403); assert.equal(web.headers.get('access-control-allow-origin'), null);
  assert.equal((await handle(new Request('https://identity.example.org/auth/google'), env)).status, 405);
});
test('rate limit and absent configuration fail closed', async () => {
  assert.equal((await handle(req({}), { ...env, AUTH_RATE_LIMITER: { limit: async () => ({success:false}) } }, keys)).status, 429);
  assert.equal((await handle(req({}), { ...env, GOOGLE_WEB_CLIENT_IDS: '' }, keys)).status, 503);
  assert.equal((await handle(req({}), { ...env, AUTH_RATE_LIMITER: undefined as any }, keys)).status, 503);
  assert.deepEqual(clientIds('anything'), []);
});
test('upstream key retrieval failure is recoverable, never leaks exception', async () => {
  const res = await handle(req({ idToken: await signed(), nonce }), env, async () => { throw new TypeError('sensitive upstream details'); });
  assert.equal(res.status, 503); assert.deepEqual(await res.json(), { error: 'temporarily_unavailable' });
});
test('JWKS caches and rotates to a new real signing key', async () => {
  const one = await generateKeyPair('RS256'), two = await generateKeyPair('RS256'); let fetches=0;
  const remote = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'), { cooldownDuration: 0,
    [customFetch]: async (url) => { assert.equal(String(url), 'https://www.googleapis.com/oauth2/v3/certs'); fetches++;
      return new Response(JSON.stringify({ keys: [{ ...await exportJWK((fetches===1 ? one : two).publicKey), kid: fetches===1 ? 'one' : 'two', alg: 'RS256' }] })); }
  });
  async function token(kid: string, key: CryptoKey) { const now=Math.floor(Date.now()/1000);
    return new SignJWT({ sub:'rotating', iss:'accounts.google.com', aud, iat:now, exp:now+3600, nonce })
      .setProtectedHeader({alg:'RS256',kid}).sign(key); }
  const first=await token('one',one.privateKey); await verifyGoogle(first,nonce,[aud],[],remote);
  await verifyGoogle(first,nonce,[aud],[],remote); assert.equal(fetches,1);
  await verifyGoogle(await token('two',two.privateKey),nonce,[aud],[],remote); assert.equal(fetches,2);
});

import { createRemoteJWKSet, jwtVerify, errors, type JWTVerifyGetKey } from 'jose';

// Fixed trusted origin. Never use jku/x5u, a URL or a key supplied by the caller.
const googleKeys = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'), {
  timeoutDuration: 5000, cooldownDuration: 30000, cacheMaxAge: 600000,
});
export class InvalidIdentity extends Error {}
export class IdentityUnavailable extends Error {}
export interface GoogleIdentity { provider: 'google'; sub: string; name?: string; email?: string }
export interface VerifiedIdentity { identity: GoogleIdentity; expiresAt: number }
export function clientIds(value: string | undefined): string[] {
  if (!value?.trim()) return [];
  const ids = value.split(',').map(v => v.trim());
  if (ids.some(v => !/^[0-9]+-[a-zA-Z0-9_-]+\.apps\.googleusercontent\.com$/.test(v))) return [];
  return [...new Set(ids)];
}
/** The injectable key resolver is used only by cryptographic tests, not by HTTP/config. */
export async function verifyGoogle(token: string, nonce: string, audiences: string[], parties: string[],
  keys: JWTVerifyGetKey = googleKeys): Promise<VerifiedIdentity> {
  try {
    const { payload } = await jwtVerify(token, keys, {
      algorithms: ['RS256'], audience: audiences,
      issuer: ['https://accounts.google.com', 'accounts.google.com'],
      requiredClaims: ['sub', 'iat', 'exp', 'aud', 'iss', 'nonce'], maxTokenAge: 3600,
    });
    const now = Math.floor(Date.now() / 1000);
    if (typeof payload.sub !== 'string' || !/^[\x21-\x7e]{1,255}$/.test(payload.sub) ||
        typeof payload.aud !== 'string' || !audiences.includes(payload.aud) ||
        typeof payload.iat !== 'number' || typeof payload.exp !== 'number' ||
        !Number.isInteger(payload.iat) || !Number.isInteger(payload.exp) ||
        payload.iat > now || payload.exp <= payload.iat || payload.exp - payload.iat > 7200 ||
        payload.nonce !== nonce || (payload.azp !== undefined &&
          (typeof payload.azp !== 'string' || ![...audiences, ...parties].includes(payload.azp)))) {
      throw new InvalidIdentity();
    }
    const identity: GoogleIdentity = { provider: 'google', sub: payload.sub };
    if (typeof payload.name === 'string' && payload.name.length <= 200) identity.name = payload.name;
    if (payload.email_verified === true && typeof payload.email === 'string' && payload.email.length <= 320)
      identity.email = payload.email;
    return { identity, expiresAt: payload.exp };
  } catch (e) {
    if (e instanceof InvalidIdentity) throw e;
    if (e instanceof errors.JOSEError && !(e instanceof errors.JWKSTimeout) &&
        !(e instanceof errors.JWKSInvalid)) throw new InvalidIdentity();
    // No raw exception or token is logged/returned, including upstream HTTP errors.
    throw new IdentityUnavailable();
  }
}

// Additive extension. The deployed Worker's existing routes remain its responsibility.
const MAX_CHECKPOINT_BYTES = 8192;
const KEY = "estado_admin";
const respond = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
});
function authorized(request, token) {
  if (!token) return false;
  const actual = new TextEncoder().encode(request.headers.get("authorization") || "");
  const expected = new TextEncoder().encode(`Bearer ${token}`);
  let diff = actual.length ^ expected.length;
  for (let i = 0; i < Math.max(actual.length, expected.length); i++) diff |= (actual[i] || 0) ^ (expected[i] || 0);
  return diff === 0;
}
export function validateCheckpoint(cp) {
  if (!cp || typeof cp !== "object" || Array.isArray(cp) || cp.tipo !== "pelicula" || typeof cp.titulo !== "string" || !cp.titulo.trim()) return false;
  if (typeof cp.telegram_url !== "string") return false;
  try {
    const u = new URL(cp.telegram_url);
    if (u.protocol !== "https:" || !["t.me", "telegram.me"].includes(u.hostname) || u.username || u.password || u.port) return false;
    const parts = u.pathname.split("/").filter(Boolean);
    const numbers = parts[0] === "c" ? (parts.length >= 3 && /^\d+$/.test(parts[1]) ? parts.slice(2) : []) : (parts.length >= 2 && /^[A-Za-z0-9_]+$/.test(parts[0]) ? parts.slice(1) : []);
    return numbers.length >= 1 && numbers.length <= 2 && numbers.every(n => /^\d+$/.test(n) && Number(n) > 0) && typeof cp.procesado_at === "string" && Number.isFinite(Date.parse(cp.procesado_at));
  } catch { return false; }
}
export async function handleAdminExtras(request, env, kv = env.CATALOGO_KV, fetcher = fetch) {
  const url = new URL(request.url);
  const checkpoint = url.pathname === "/admin/checkpoint";
  const metadata = url.pathname.startsWith("/admin/metadata/tmdb/");
  if (!checkpoint && !metadata) return null;
  if (!authorized(request, env.CATALOG_ADMIN_TOKEN)) return respond({ error: "Acceso administrativo rechazado." }, 401);
  try {
    if (checkpoint) {
      if (!kv) return respond({ error: "Falta el binding KV del catálogo." }, 503);
      if (request.method === "GET") {
        const raw = await kv.get(KEY);
        const cp = raw ? JSON.parse(raw) : null;
        if (cp && !validateCheckpoint(cp)) return respond({ error: "Checkpoint guardado inválido." }, 500);
        return respond({ ok: true, checkpoint: cp });
      }
      if (request.method === "PUT") {
        if (Number(request.headers.get("content-length")) > MAX_CHECKPOINT_BYTES) return respond({error:"Checkpoint demasiado grande."},413);
        const bytes = await request.arrayBuffer();
        if (bytes.byteLength > MAX_CHECKPOINT_BYTES) return respond({error:"Checkpoint demasiado grande."},413);
        let cp;
        try { cp = JSON.parse(new TextDecoder("utf-8", {fatal:true}).decode(bytes)); } catch { return respond({error:"JSON inválido."},400); }
        if (!validateCheckpoint(cp)) return respond({ error: "Checkpoint inválido." }, 422);
        // Original URL and processed_at are preserved; server receipt is an additional field.
        await kv.put(KEY, JSON.stringify({ ...cp, recibido_at: new Date().toISOString() }));
        return respond({ ok: true });
      }
      return respond({error:"Método no permitido."},405);
    }
    if (request.method !== "GET") return respond({error:"Método no permitido."},405);
    if (!env.TMDB_BEARER_TOKEN) return respond({error:"TMDB no configurado."},503);
    const path = url.pathname.slice("/admin/metadata/tmdb".length);
    // Fixed upstream, fixed endpoints, one page. Never accept arbitrary URLs or forward admin secrets.
    if (!/^\/(search\/(movie|tv)|(movie|tv)\/\d+(\/(credits|external_ids|videos|images))?|tv\/\d+\/season\/\d+)$/.test(path)) return respond({error:"Consulta no permitida."},400);
    const upstream = new URL(`https://api.themoviedb.org/3${path}`);
    for (const [k, v] of url.searchParams) {
      if (!["query", "language", "include_adult"].includes(k) || v.length > 200) return respond({error:"Parámetro no permitido."},400);
      upstream.searchParams.set(k, v);
    }
    const response = await fetcher(upstream, {headers:{Authorization:`Bearer ${env.TMDB_BEARER_TOKEN}`},signal:AbortSignal.timeout(20000),redirect:"error"});
    if (!response.ok) return respond({error:"La fuente de metadatos no respondió correctamente."}, response.status);
    return new Response(response.body,{status:200,headers:{"content-type":"application/json","cache-control":"no-store"}});
  } catch { return respond({error:"No se pudo completar la operación administrativa."},503); }
}

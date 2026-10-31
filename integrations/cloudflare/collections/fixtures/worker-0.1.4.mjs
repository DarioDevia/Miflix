// Complete MiFlix Worker: existing catalog routes + authenticated TMDB relay.
// No credentials in source. Configure secrets and CATALOGO_KV in Cloudflare.
const CATALOG_KEY = "catalogo";
const MAX_SIZE = 5 * 1024 * 1024;
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, PUT, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
};
function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
// TMDB relay consumed by MiFlix Admin Android 0.1.4. No KV access or checkpoint routes.
const TMDB_RELAY_PREFIX = "/admin/metadata/tmdb";
const TMDB_UPSTREAM = "https://api.themoviedb.org/3";
const TMDB_MAX_BYTES = 5 * 1024 * 1024;

function tmdbAuthorized(request, token) {
  if (typeof token !== "string" || !token) return false;
  const actual = new TextEncoder().encode(request.headers.get("Authorization") || "");
  const expected = new TextEncoder().encode(`Bearer ${token}`);
  let difference = actual.length ^ expected.length;
  for (let i = 0; i < Math.max(actual.length, expected.length); i++) {
    difference |= (actual[i] || 0) ^ (expected[i] || 0);
  }
  return difference === 0;
}

function tmdbRoute(path) {
  if (/^\/search\/(movie|tv)$/.test(path)) return "search";
  if (/^\/(movie|tv)\/[1-9]\d{0,15}$/.test(path)) return "detail";
  const resource = path.match(/^\/(movie|tv)\/[1-9]\d{0,15}\/(credits|external_ids|videos|images)$/);
  if (resource) return resource[2];
  const season = path.match(/^\/tv\/[1-9]\d{0,15}\/season\/(0|[1-9]\d{0,9})$/);
  if (season && Number(season[1]) <= 2147483647) return "season";
  return null;
}

async function tmdbReadLimited(response) {
  if (Number(response.headers.get("Content-Length")) > TMDB_MAX_BYTES) {
    await response.body?.cancel();
    throw new Error("size");
  }
  if (!response.body) throw new Error("empty");
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > TMDB_MAX_BYTES) {
        await reader.cancel();
        throw new Error("size");
      }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
}

// null means this is an existing/non-TMDB route; the original Worker handles it.
export async function handleTmdbRelay(request, env, fetcher = fetch) {
  const url = new URL(request.url);
  if (url.pathname !== TMDB_RELAY_PREFIX && !url.pathname.startsWith(`${TMDB_RELAY_PREFIX}/`)) return null;
  if (!tmdbAuthorized(request, env.CATALOG_ADMIN_TOKEN)) {
    return jsonResponse({ error: "Acceso administrativo rechazado." }, 401);
  }
  if (request.method !== "GET") return jsonResponse({ error: "Método no permitido." }, 405);
  const path = url.pathname.slice(TMDB_RELAY_PREFIX.length);
  const route = tmdbRoute(path);
  if (!route) return jsonResponse({ error: "Consulta no permitida." }, 400);

  const allowed = route === "search" ? ["query", "language", "include_adult"]
    : ["detail", "credits", "videos", "season"].includes(route) ? ["language"] : [];
  const upstream = new URL(`${TMDB_UPSTREAM}${path}`);
  const seen = new Set();
  for (const [key, value] of url.searchParams) {
    if (!allowed.includes(key) || seen.has(key) || value.length > 200) {
      return jsonResponse({ error: "Parámetro no permitido." }, 400);
    }
    if ((key === "language" && !/^[a-z]{2}(?:-[A-Z]{2})?$/.test(value))
      || (key === "include_adult" && !["true", "false"].includes(value))) {
      return jsonResponse({ error: "Parámetro inválido." }, 400);
    }
    seen.add(key);
    upstream.searchParams.set(key, value);
  }
  if (route === "search" && !upstream.searchParams.get("query")?.trim()) {
    return jsonResponse({ error: "Falta el título de búsqueda." }, 400);
  }
  if (typeof env.TMDB_BEARER_TOKEN !== "string" || !env.TMDB_BEARER_TOKEN) {
    return jsonResponse({ error: "TMDB no configurado." }, 503);
  }
  try {
    const response = await fetcher(upstream, {
      method: "GET",
      headers: { Authorization: `Bearer ${env.TMDB_BEARER_TOKEN}`, Accept: "application/json" },
      redirect: "error",
      signal: AbortSignal.timeout(20000),
    });
    if (!response.ok) {
      await response.body?.cancel();
      return jsonResponse({ error: "La fuente de metadatos no respondió correctamente." },
        response.status >= 400 && response.status <= 599 ? response.status : 502);
    }
    const payload = JSON.parse(await tmdbReadLimited(response));
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) throw new Error("format");
    const serialized = JSON.stringify(payload);
    // Do not echo credentials even if an unexpected upstream body includes them.
    if ([env.TMDB_BEARER_TOKEN, env.CATALOG_ADMIN_TOKEN].some(token => token && serialized.includes(JSON.stringify(token).slice(1, -1)))) {
      throw new Error("credential");
    }
    return jsonResponse(payload);
  } catch (error) {
    const timeout = error?.name === "TimeoutError" || error?.name === "AbortError";
    return jsonResponse({ error: "No se pudo consultar la fuente de metadatos. Reintentá." }, timeout ? 504 : 502);
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }
    const metadataResponse = await handleTmdbRelay(request, env);
    if (metadataResponse) return metadataResponse;

    // Comprobar si el Worker está funcionando
    if (request.method === "GET" && path === "/health") {
      const catalogo = await env.CATALOGO_KV.get(CATALOG_KEY, "json");
      return jsonResponse({
        ok: true,
        servicio: "MiFlix Catálogo",
        catalogo_guardado: catalogo !== null,
        elementos: Array.isArray(catalogo?.items) ? catalogo.items.length : 0,
      });
    }
    // Descargar el catálogo desde Android, Windows o un navegador
    if (request.method === "GET" && (path === "/" || path === "/catalogo.json")) {
      const catalogoGuardado = await env.CATALOGO_KV.get(CATALOG_KEY);
      if (catalogoGuardado) {
        return new Response(catalogoGuardado, {
          status: 200,
          headers: {
            ...corsHeaders,
            "Content-Type": "application/json; charset=utf-8",
            "Cache-Control": "no-store",
          },
        });
      }
      return jsonResponse({ schema_version: 1, generated_at: new Date().toISOString(), items: [] });
    }
    // Subir o actualizar el catálogo desde Agente de Videoteca
    if (request.method === "PUT" && path === "/admin/catalogo") {
      const authorization = request.headers.get("Authorization") || "";
      const expectedAuthorization = `Bearer ${env.CATALOG_ADMIN_TOKEN}`;
      if (typeof env.CATALOG_ADMIN_TOKEN !== "string" || !env.CATALOG_ADMIN_TOKEN || authorization !== expectedAuthorization) {
        return jsonResponse({ ok: false, error: "No autorizado" }, 401);
      }
      const contentLength = Number(request.headers.get("Content-Length") || 0);
      if (contentLength > MAX_SIZE) {
        return jsonResponse({ ok: false, error: "El catálogo supera el límite de 5 MB" }, 413);
      }
      const rawBody = await request.text();
      const realSize = new TextEncoder().encode(rawBody).byteLength;
      if (realSize > MAX_SIZE) {
        return jsonResponse({ ok: false, error: "El catálogo supera el límite de 5 MB" }, 413);
      }
      let catalogo;
      try { catalogo = JSON.parse(rawBody); }
      catch { return jsonResponse({ ok: false, error: "El contenido recibido no es un JSON válido" }, 400); }
      if (catalogo === null || typeof catalogo !== "object" || catalogo.schema_version !== 1 || !Array.isArray(catalogo.items)) {
        return jsonResponse({ ok: false, error: "Formato inválido: se requiere schema_version 1 y una lista items" }, 400);
      }
      const updatedAt = new Date().toISOString();
      await env.CATALOGO_KV.put(CATALOG_KEY, JSON.stringify(catalogo, null, 2), {
        metadata: { updated_at: updatedAt, item_count: catalogo.items.length },
      });
      return jsonResponse({ ok: true, mensaje: "Catálogo MiFlix actualizado correctamente", elementos: catalogo.items.length, updated_at: updatedAt });
    }
    return jsonResponse({ ok: false, error: "Ruta no encontrada" }, 404);
  },
};

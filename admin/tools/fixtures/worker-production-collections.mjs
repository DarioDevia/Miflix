// MiFlix Worker: original catalog routes + TMDB relay + collections.
// Uses ONLY existing CATALOGO_KV namespace and "catalogo" key.
// IMPORTANT: Workers KV is eventually consistent and does not provide atomic read-modify-write.
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
function authorized(request, token) {
  if (typeof token !== "string" || !token) return false;
  const actual = new TextEncoder().encode(request.headers.get("Authorization") || "");
  const expected = new TextEncoder().encode(`Bearer ${token}`);
  let difference = actual.length ^ expected.length;
  for (let i = 0; i < Math.max(actual.length, expected.length); i++) {
    difference |= (actual[i] || 0) ^ (expected[i] || 0);
  }
  return difference === 0;
}
function isObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
function byteSize(value) {
  return new TextEncoder().encode(value).byteLength;
}
async function readBodyLimited(request) {
  const contentLength = Number(request.headers.get("Content-Length") || 0);
  if (contentLength > MAX_SIZE) return { error: jsonResponse({ ok: false, error: "El contenido supera 5 MB" }, 413) };
  // Avoid unbounded memory consumption even when Content-Length is missing/wrong.
  if (!request.body) return { error: jsonResponse({ ok: false, error: "Falta contenido" }, 400) };
  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_SIZE) {
        await reader.cancel();
        return { error: jsonResponse({ ok: false, error: "El contenido supera 5 MB" }, 413) };
      }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  try { return { value: JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)) }; }
  catch { return { error: jsonResponse({ ok: false, error: "El contenido recibido no es un JSON válido" }, 400) }; }
}

// The structure is the same one used by Admin Android's colecciones.schema.json.
function validateCollections(data) {
  if (!isObject(data) || data.format !== "miflix-collections" || data.version !== 1 || !Array.isArray(data.colecciones)) {
    return "Formato inválido: se requiere format=miflix-collections, version=1 y colecciones[]";
  }
  if (data.colecciones.length > 1000) return "Máximo 1000 colecciones";
  const ids = new Set();
  for (const c of data.colecciones) {
    if (!isObject(c) || typeof c.id !== "string" || !c.id.trim() || c.id.length > 300 ||
        typeof c.nombre !== "string" || !c.nombre.trim() || c.nombre.length > 200 ||
        !["manual", "tmdb"].includes(c.origen) ||
        !Number.isInteger(c.orden) || c.orden < 0 || c.orden > 1000000 ||
        typeof c.visible !== "boolean" || !Array.isArray(c.titulos) || c.titulos.length > 10000) {
      return "Una colección no cumple el esquema requerido";
    }
    if (ids.has(c.id)) return "IDs de colecciones duplicados";
    ids.add(c.id);
    if (c.origen === "tmdb" && (!Number.isSafeInteger(c.tmdb_collection_id) || c.tmdb_collection_id < 1)) {
      return "La colección TMDB requiere tmdb_collection_id positivo";
    }
    if (c.tmdb_collection_id != null && (!Number.isSafeInteger(c.tmdb_collection_id) || c.tmdb_collection_id < 1)) {
      return "tmdb_collection_id inválido";
    }
    if (c.descripcion != null && (typeof c.descripcion !== "string" || c.descripcion.length > 12000)) {
      return "Descripción de colección inválida";
    }
    for (const field of ["poster_url", "backdrop_url"]) {
      const value = c[field];
      if (value != null && (typeof value !== "string" || value.length > 2000 ||
          (value !== "" && (!/^https:\/\/[^\s/@]+(?:\/[^\s]*)?$/.test(value))))) {
        return `${field} inválida`;
      }
    }
    const titles = new Set();
    for (const id of c.titulos) {
      if (typeof id !== "string" || !id.trim() || id.length > 300 || titles.has(id)) return "IDs de títulos inválidos o duplicados";
      titles.add(id);
    }
  }
  return null;
}
function storedCollections(catalogo) {
  if (!isObject(catalogo) || !Array.isArray(catalogo.colecciones)) return [];
  return catalogo.colecciones;
}
const TMDB_RELAY_PREFIX = "/admin/metadata/tmdb";
const TMDB_UPSTREAM = "https://api.themoviedb.org/3";
const TMDB_MAX_BYTES = 5 * 1024 * 1024;
function tmdbRoute(path) {
  if (/^\/search\/(movie|tv|collection)$/.test(path)) return "search";
  if (/^\/(movie|tv|collection)\/[1-9]\d{0,15}$/.test(path)) return "detail";
  const resource = path.match(/^\/(movie|tv|collection)\/[1-9]\d{0,15}\/(credits|external_ids|videos|images)$/);
  if (resource && (resource[1] !== "collection" || resource[2] === "images")) return resource[2];
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
  return new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes);
}
export async function handleTmdbRelay(request, env, fetcher = fetch) {
  const url = new URL(request.url);
  if (url.pathname !== TMDB_RELAY_PREFIX && !url.pathname.startsWith(`${TMDB_RELAY_PREFIX}/`)) return null;
  if (!authorized(request, env.CATALOG_ADMIN_TOKEN)) {
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
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
    const metadataResponse = await handleTmdbRelay(request, env);
    if (metadataResponse) return metadataResponse;
    if (request.method === "GET" && path === "/health") {
      const catalogo = await env.CATALOGO_KV.get(CATALOG_KEY, "json");
      return jsonResponse({
        ok: true,
        servicio: "MiFlix Catálogo",
        catalogo_guardado: catalogo !== null,
        elementos: Array.isArray(catalogo?.items) ? catalogo.items.length : 0,
        colecciones: storedCollections(catalogo).length,
      });
    }
    if (request.method === "GET" && (path === "/" || path === "/catalogo.json")) {
      const catalogoGuardado = await env.CATALOGO_KV.get(CATALOG_KEY);
      if (catalogoGuardado) {
        return new Response(catalogoGuardado, {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
        });
      }
      return jsonResponse({ schema_version: 1, generated_at: new Date().toISOString(), items: [], colecciones_version: 1, colecciones: [] });
    }
    // Admin-only collections endpoint. PUT replaces just the collections field,
    // while preserving all existing titles and their metadata.
    if (path === "/admin/colecciones" && ["GET", "PUT"].includes(request.method)) {
      if (!authorized(request, env.CATALOG_ADMIN_TOKEN)) return jsonResponse({ ok: false, error: "No autorizado" }, 401);
      if (request.method === "GET") {
        const current = await env.CATALOGO_KV.get(CATALOG_KEY, "json");
        return jsonResponse({ format: "miflix-collections", version: 1, colecciones: storedCollections(current) });
      }
      const body = await readBodyLimited(request);
      if (body.error) return body.error;
      const validationError = validateCollections(body.value);
      if (validationError) return jsonResponse({ ok: false, error: validationError }, 400);
      const current = await env.CATALOGO_KV.get(CATALOG_KEY, "json");
      if (!isObject(current) || current.schema_version !== 1 || !Array.isArray(current.items)) {
        return jsonResponse({ ok: false, error: "No existe un catálogo válido: primero publicá el catálogo normal" }, 409);
      }
      const updated = { ...current, colecciones_version: 1, colecciones: body.value.colecciones };
      const serialized = JSON.stringify(updated, null, 2);
      if (byteSize(serialized) > MAX_SIZE) return jsonResponse({ ok: false, error: "El catálogo final supera 5 MB" }, 413);
      const updatedAt = new Date().toISOString();
      try {
        await env.CATALOGO_KV.put(CATALOG_KEY, serialized, {
          metadata: { updated_at: updatedAt, item_count: current.items.length, collection_count: body.value.colecciones.length },
        });
      } catch { return jsonResponse({ ok: false, error: "No se pudieron guardar las colecciones" }, 503); }
      return jsonResponse({ ok: true, mensaje: "Colecciones MiFlix actualizadas correctamente", colecciones: body.value.colecciones.length, updated_at: updatedAt });
    }
    if (request.method === "PUT" && path === "/admin/catalogo") {
      if (!authorized(request, env.CATALOG_ADMIN_TOKEN)) return jsonResponse({ ok: false, error: "No autorizado" }, 401);
      const body = await readBodyLimited(request);
      if (body.error) return body.error;
      const catalogo = body.value;
      if (!isObject(catalogo) || catalogo.schema_version !== 1 || !Array.isArray(catalogo.items)) {
        return jsonResponse({ ok: false, error: "Formato inválido: se requiere schema_version 1 y una lista items" }, 400);
      }
      // Preserve existing top-level fields and always retain the server's collections.
      // The catalog publisher is NEVER authorized to overwrite collections;
      // only PUT /admin/colecciones can do it.
      const current = await env.CATALOGO_KV.get(CATALOG_KEY, "json");
      if (current !== null && (!isObject(current) || current.schema_version !== 1 || !Array.isArray(current.items))) {
        return jsonResponse({ ok: false, error: "El catálogo remoto no tiene un formato válido" }, 409);
      }
      const updated = { ...(current || {}), ...catalogo };
      if (current && Object.hasOwn(current, "colecciones")) {
        updated.colecciones = current.colecciones;
        updated.colecciones_version = current.colecciones_version ?? 1;
      } else {
        // Ignore a collection payload smuggled through the legacy catalog route.
        delete updated.colecciones;
        delete updated.colecciones_version;
      }
      const serialized = JSON.stringify(updated, null, 2);
      if (byteSize(serialized) > MAX_SIZE) return jsonResponse({ ok: false, error: "El catálogo final supera 5 MB" }, 413);
      const updatedAt = new Date().toISOString();
      try {
        await env.CATALOGO_KV.put(CATALOG_KEY, serialized, {
          metadata: { updated_at: updatedAt, item_count: catalogo.items.length, collection_count: storedCollections(updated).length },
        });
      } catch { return jsonResponse({ ok: false, error: "No se pudo guardar el catálogo" }, 503); }
      return jsonResponse({ ok: true, mensaje: "Catálogo MiFlix actualizado correctamente", elementos: catalogo.items.length, updated_at: updatedAt });
    }
    return jsonResponse({ ok: false, error: "Ruta no encontrada" }, 404);
  },
};

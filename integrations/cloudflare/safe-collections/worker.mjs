// Complete MiFlix Worker: existing catalog routes + authenticated TMDB relay.
// No credentials in source. Configure secrets and CATALOGO_KV in Cloudflare.
const CATALOG_KEY = "catalogo";
const MAX_SIZE = 5 * 1024 * 1024;
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, PUT, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type, If-Match, If-None-Match, Idempotency-Key",
  "Access-Control-Expose-Headers": "ETag, X-Catalogo-ETag, X-Colecciones-ETag, X-Legacy-Unconditional",
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
  if (/^\/collection\/[1-9]\d{0,15}$/.test(path)) return "detail";
  if (/^\/collection\/[1-9]\d{0,15}\/images$/.test(path)) return "images";
  if (/^\/search\/(movie|tv|collection)$/.test(path)) return "search";
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
  return new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes);
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

// Transactional core; also exercised against real SQLite by Node regression tests.
const MAX_CATALOG = 5 * 1024 * 1024;
const MAX_COLLECTIONS = 2 * 1024 * 1024;
const bytes = value => new TextEncoder().encode(typeof value === 'string' ? value : JSON.stringify(value)).byteLength;
const object = v => v !== null && typeof v === 'object' && !Array.isArray(v);
class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
function requireThat(condition, status, message) { if (!condition) throw new ApiError(status, message); }
function validateCatalog(c) {
  requireThat(object(c) && c.schema_version === 1 && Array.isArray(c.items), 400, 'Se requiere schema_version numérico 1 e items array');
  requireThat(bytes(c) <= MAX_CATALOG, 413, 'Catálogo mayor a 5 MiB');
}
const text = (s, max) => typeof s === 'string' && s.length >= 1 && s.length <= max && /\S/.test(s);
const nullableText = (s, max) => s === undefined || s === null || (typeof s === 'string' && s.length <= max);
function image(s) {
  if (s === undefined || s === null || s === '') return true;
  if (typeof s !== 'string' || s.length > 2000 || !/^https:\/\/[^\s/@]+(?:\/[^\s]*)?$/.test(s)) return false;
  try { const u = new URL(s); return u.protocol === 'https:' && !u.username && !u.password && !/(^|\.)thetvdb\.com$/i.test(u.hostname); } catch { return false; }
}
function validateCollections(doc) {
  requireThat(object(doc) && doc.format === 'miflix-collections' && doc.version === 1 && Array.isArray(doc.colecciones), 400, 'Formato de colecciones inválido');
  requireThat(doc.colecciones.length <= 1000 && bytes(doc) <= MAX_COLLECTIONS, 413, 'Límite de colecciones excedido');
  const ids = new Set();
  for (const c of doc.colecciones) {
    requireThat(object(c) && text(c.id,300) && !ids.has(c.id), 400, 'ID de colección inválido o duplicado'); ids.add(c.id);
    requireThat(text(c.nombre,200) && nullableText(c.descripcion,12000),400,'Nombre o descripción inválidos');
    requireThat(['manual','tmdb'].includes(c.origen) && Number.isInteger(c.orden) && c.orden >= 0 && c.orden <= 1000000 && typeof c.visible === 'boolean',400,'Origen, orden o visibilidad inválidos');
    requireThat(c.tmdb_collection_id == null || (Number.isInteger(c.tmdb_collection_id) && c.tmdb_collection_id >= 1),400,'ID TMDB inválido');
    requireThat(c.origen !== 'tmdb' || (Number.isInteger(c.tmdb_collection_id) && c.tmdb_collection_id >= 1),400,'Falta ID TMDB');
    requireThat(image(c.poster_url) && image(c.backdrop_url),400,'Imagen HTTPS inválida o fuente restringida');
    requireThat(Array.isArray(c.titulos) && c.titulos.length <= 10000 && c.titulos.every(id=>text(id,300)) && new Set(c.titulos).size === c.titulos.length,400,'Referencias inválidas o duplicadas');
  }
}
const emptyCollections = () => ({format:'miflix-collections',version:1,colecciones:[]});
function publicCatalog(s) {
  return {...s.catalog, colecciones_version:1, colecciones:s.collections.colecciones.filter(c=>c.visible).sort((a,b)=>a.orden-b.orden)};
}
function validateState(s) {
  validateCatalog(s.catalog); validateCollections(s.collections);
  requireThat(bytes(publicCatalog(s)) <= MAX_CATALOG,413,'Catálogo compuesto mayor a 5 MiB');
}
function catalogOnly(c) { const {colecciones,colecciones_version,...rest} = c; return rest; }
const etag = (s, scope) => `"${scope}-${scope === 'catalogo' ? s.catalogRevision : scope === 'colecciones' ? s.collectionsRevision : s.revision}"`;
function checkMatch(actual, expected, required = true) {
  if (!actual && !required) return;
  requireThat(Boolean(actual),428,'Falta If-Match: leer revisión actual');
  requireThat(actual === expected,412,'Conflicto: volver a leer y reconciliar cambios');
}
class Store {
  constructor(storage) {
    this.storage = storage;
    this.sql = storage.sql;
    this.sql.exec('CREATE TABLE IF NOT EXISTS docs (name TEXT NOT NULL, part INTEGER NOT NULL, body TEXT NOT NULL, PRIMARY KEY(name,part))');
    this.sql.exec('CREATE TABLE IF NOT EXISTS operations (id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, result TEXT NOT NULL, revision INTEGER NOT NULL)');
  }
  read(name) { const rows = this.sql.exec('SELECT body FROM docs WHERE name=? ORDER BY part',name).toArray(); return rows.length ? JSON.parse(rows.map(x=>x.body).join('')) : null; }
  write(name,value) {
    this.sql.exec('DELETE FROM docs WHERE name=?',name);
    const raw=JSON.stringify(value);
    // <= 128 KiB UTF-8 per row, well below SQLite value/row limits.
    for(let i=0;i<raw.length;i+=32768) this.sql.exec('INSERT INTO docs(name,part,body) VALUES(?,?,?)',name,i/32768,raw.slice(i,i+32768));
  }
  snapshot() { return this.storage.transactionSync(()=>this.read('state')); }
  mutate({path,body,match,noneMatch,operationId,fingerprint,strict},now) {
    return this.storage.transactionSync(()=>{
      const previous = this.sql.exec('SELECT fingerprint,result FROM operations WHERE id=?',operationId).toArray()[0];
      if(previous) {
        requireThat(previous.fingerprint === fingerprint,409,'Idempotency-Key reutilizado para otra petición');
        return JSON.parse(previous.result);
      }
      const old=this.read('state');
      let s=old ? structuredClone(old) : null;
      if(path === '/admin/migracion') {
        requireThat(!old,409,'Estado ya inicializado');
        requireThat(noneMatch === '*',428,'Migración requiere If-None-Match: *');
        requireThat(body.confirm === 'IMPORTAR',400,'Confirmar IMPORTAR');
        validateCatalog(body.catalogo);
        const c=body.catalogo;
        requireThat(c.colecciones_version === undefined || c.colecciones_version === 1,400,'Versión de colecciones desconocida');
        s={catalog:catalogOnly(c),collections:body.colecciones ?? {...emptyCollections(),colecciones:c.colecciones ?? []},revision:0,catalogRevision:0,collectionsRevision:0};
        // Never silently discard collections embedded in the supplied catalog.
        if(body.colecciones && c.colecciones) requireThat(JSON.stringify(body.colecciones.colecciones) === JSON.stringify(c.colecciones),400,'Dos fuentes de colecciones diferentes');
      } else {
        requireThat(Boolean(s),503,'Migración explícita pendiente; escrituras bloqueadas');
        if(path === '/admin/catalogo') {
          checkMatch(match,etag(s,'catalogo'),strict);
          validateCatalog(body);
          s.catalog={...s.catalog,...catalogOnly(body)};
          s.catalogRevision++;
        } else if(path === '/admin/colecciones') {
          checkMatch(match,etag(s,'colecciones'));
          requireThat(body.format === 'miflix-collections-operations' && body.version === 1 && Array.isArray(body.upsert) && Array.isArray(body.delete_ids),400,'Formato de operaciones inválido');
          const deletions=body.delete_ids;
          requireThat(deletions.length <= 1000 && deletions.every(id=>text(id,300)) && new Set(deletions).size === deletions.length,400,'Borrados inválidos');
          requireThat(!deletions.length || (Array.isArray(body.confirm_delete) && JSON.stringify(body.confirm_delete) === JSON.stringify(deletions)),400,'Confirmar IDs a eliminar');
          const changes=body.upsert;
          requireThat(changes.length <= 1000 && changes.every(c=>object(c) && text(c.id,300)) && new Set(changes.map(c=>c.id)).size === changes.length && !changes.some(c=>deletions.includes(c.id)),400,'Operaciones duplicadas o contradictorias');
          const map=new Map(s.collections.colecciones.map(c=>[c.id,c]));
          for(const id of deletions) {requireThat(map.has(id),404,'Colección a borrar inexistente');map.delete(id);}
          for(const c of changes) map.set(c.id,{...map.get(c.id),...c});
          s.collections={...s.collections,colecciones:[...map.values()]}; s.collectionsRevision++;
        } else if(path === '/admin/restaurar') {
          checkMatch(match,etag(s,'estado'));
          requireThat(body.confirm === 'RESTAURAR' && body.backup?.format === 'miflix-worker-backup' && body.backup.version === 1,400,'Respaldo o confirmación inválidos');
          requireThat(object(body.backup.state) && object(body.backup.state.catalog) && object(body.backup.state.collections),400,'Estado de respaldo inválido');
          s.catalog=body.backup.state.catalog; s.collections=body.backup.state.collections;
          s.catalogRevision++; s.collectionsRevision++;
        } else throw new ApiError(404,'Ruta desconocida');
      }
      s.revision++; s.updated_at=now;
      validateState(s);
      if(old) this.write(`backup-${old.revision}`,{format:'miflix-worker-backup',version:1,state:old});
      // Retain ten pre-change revisions. External exports are still mandatory.
      const expire=s.revision-11;
      if(expire > 0) this.sql.exec('DELETE FROM docs WHERE name=?',`backup-${expire}`);
      this.write('state',s);
      const result={ok:true,mensaje:'Catálogo MiFlix actualizado correctamente',elementos:s.catalog.items.length,updated_at:now,revision:s.revision,catalogo_etag:etag(s,'catalogo'),colecciones_etag:etag(s,'colecciones'),estado_etag:etag(s,'estado'),legacy_unconditional:path === '/admin/catalogo' && !match};
      this.sql.exec('INSERT INTO operations(id,fingerprint,result,revision) VALUES(?,?,?,?)',operationId,fingerprint,JSON.stringify(result),s.revision);
      // Keep retries for the last 1000 revisions; after expiry stale If-Match fails.
      this.sql.exec('DELETE FROM operations WHERE revision < ?',Math.max(0,s.revision-1000));
      return result;
    });
  }
  backup(revision) {
    return this.storage.transactionSync(()=>{
      const s=this.read('state'); requireThat(Boolean(s),503,'Migración pendiente');
      if(revision !== null) { const b=this.read(`backup-${revision}`); requireThat(Boolean(b),404,'Respaldo no retenido'); return b; }
      return {format:'miflix-worker-backup',version:1,state:s};
    });
  }
}


async function readJSON(request, limit) {
  requireThat(Number(request.headers.get('Content-Length') || 0) <= limit,413,'Cuerpo demasiado grande');
  requireThat(Boolean(request.body),400,'Falta cuerpo JSON');
  const reader=request.body.getReader(); let size=0; const chunks=[];
  try {
    while(true) {
      const {done,value}=await reader.read(); if(done) break;
      size+=value.byteLength;
      if(size>limit) {await reader.cancel();throw new ApiError(413,'Cuerpo demasiado grande');}
      chunks.push(value);
    }
  } finally {reader.releaseLock();}
  const data=new Uint8Array(size);let offset=0;
  for(const c of chunks){data.set(c,offset);offset+=c.byteLength;}
  try {return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(data));}
  catch {throw new ApiError(400,'JSON o UTF-8 inválido');}
}
function withTags(response,s) {
  response.headers.set('ETag',etag(s,'estado'));
  response.headers.set('X-Catalogo-ETag',etag(s,'catalogo'));
  response.headers.set('X-Colecciones-ETag',etag(s,'colecciones'));
  return response;
}
export class CatalogCoordinator {
  constructor(ctx,env) {this.store=new Store(ctx.storage);this.env=env;}
  async fetch(request) {
    try {
      const url=new URL(request.url);const path=url.pathname.replace(/\/+$/,'') || '/';
      if(request.method === 'GET' && ['/','/catalogo.json','/health'].includes(path)) {
        const s=this.store.snapshot();
        if(s) {
          if(path === '/health') return jsonResponse({ok:true,servicio:'MiFlix Catálogo',catalogo_guardado:true,elementos:s.catalog.items.length,colecciones:s.collections.colecciones.length,revision:s.revision,storage:'durable-object'});
          return withTags(jsonResponse(publicCatalog(s)),s);
        }
        // Read-only fallback. Never automatically migrate eventual KV data.
        const raw=await this.env.CATALOGO_KV.get(CATALOG_KEY);
        const catalog=raw ? JSON.parse(raw) : {schema_version:1,generated_at:new Date().toISOString(),items:[]};
        validateCatalog(catalog);
        if(path === '/health') return jsonResponse({ok:true,servicio:'MiFlix Catálogo',catalogo_guardado:raw !== null,elementos:catalog.items.length,migration_required:true});
        return jsonResponse(catalog);
      }
      requireThat(tmdbAuthorized(request,this.env.CATALOG_ADMIN_TOKEN),401,'No autorizado');
      if(request.method === 'GET') {
        if(path === '/admin/respaldo') {
          const rev=url.searchParams.get('revision');
          requireThat(rev === null || /^[1-9]\d{0,14}$/.test(rev),400,'Revisión inválida');
          const b=this.store.backup(rev); const r=jsonResponse(b);r.headers.set('ETag',etag(b.state,'estado'));return r;
        }
        const s=this.store.snapshot();requireThat(Boolean(s),503,'Migración explícita pendiente');
        if(path === '/admin/catalogo' || path === '/admin/colecciones') {
          const scope=path.endsWith('catalogo') ? 'catalogo':'colecciones';
          const r=jsonResponse(scope === 'catalogo' ? s.catalog : {...s.collections,revision:s.collectionsRevision});
          r.headers.set('ETag',etag(s,scope));return r;
        }
      }
      const mutations={'/admin/migracion':'POST','/admin/catalogo':'PUT','/admin/colecciones':'PUT','/admin/restaurar':'POST'};
      requireThat(Object.hasOwn(mutations,path),404,'Ruta no encontrada');
      requireThat(request.method === mutations[path],405,'Método no permitido');
      const operationId=request.headers.get('Idempotency-Key');
      // Legacy catalog callers may omit a key; they cannot safely retry ambiguous success.
      requireThat(!operationId || /^[A-Za-z0-9._:-]{8,128}$/.test(operationId),400,'Idempotency-Key inválido');
      const legacy=path === '/admin/catalogo' && this.env.REQUIRE_CATALOG_IF_MATCH === 'false';
      requireThat(Boolean(operationId) || legacy,428,'Falta Idempotency-Key');
      const body=await readJSON(request,path === '/admin/colecciones' ? MAX_COLLECTIONS : MAX_CATALOG+MAX_COLLECTIONS+65536);
      requireThat(object(body),400,'Se requiere objeto JSON');
      const match=request.headers.get('If-Match');const noneMatch=request.headers.get('If-None-Match');
      const input=JSON.stringify({path,body,match,noneMatch});
      const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(input));
      const fingerprint=Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('');
      const result=this.store.mutate({path,body,match,noneMatch,operationId:operationId || crypto.randomUUID(),fingerprint,strict:!legacy},new Date().toISOString());
      const r=jsonResponse(result);r.headers.set('ETag',path === '/admin/colecciones' ? result.colecciones_etag : path === '/admin/catalogo' ? result.catalogo_etag : result.estado_etag);
      if(result.legacy_unconditional) r.headers.set('X-Legacy-Unconditional','true');
      return r;
    } catch(error) {
      return jsonResponse({ok:false,error:error instanceof ApiError ? error.message : 'Almacenamiento no disponible; consultar estado y reintentar con la misma clave'},error instanceof ApiError ? error.status : 503);
    }
  }
}
export default {
  async fetch(request,env) {
    if(request.method === 'OPTIONS') return new Response(null,{status:204,headers:corsHeaders});
    const url=new URL(request.url);const path=url.pathname.replace(/\/+$/,'') || '/';
    const admin=path === '/admin' || path.startsWith('/admin/');
    // Authenticate before invoking coordinator, buffering request body or upstream relay.
    if(admin && !tmdbAuthorized(request,env.CATALOG_ADMIN_TOKEN)) return jsonResponse({ok:false,error:'No autorizado'},401);
    const metadata=await handleTmdbRelay(request,env);if(metadata) return metadata;
    const known=['/','/catalogo.json','/health','/admin/catalogo','/admin/colecciones','/admin/respaldo','/admin/restaurar','/admin/migracion'];
    if(!known.includes(path)) return jsonResponse({ok:false,error:'Ruta no encontrada'},404);
    if(!admin && request.method !== 'GET') return jsonResponse({ok:false,error:'Ruta no encontrada'},404);
    if(!env.CATALOG_COORDINATOR) return jsonResponse({ok:false,error:'Falta binding CATALOG_COORDINATOR'},503);
    try {
      // Singleton name must never vary by request, user or deployment.
      const id=env.CATALOG_COORDINATOR.idFromName('miflix-catalog-v1');
      return await env.CATALOG_COORDINATOR.get(id).fetch(request);
    } catch {return jsonResponse({ok:false,error:'Coordinador no disponible'},503);}
  }
};

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import worker, {CatalogCoordinator,handleTmdbRelay} from '../worker.mjs';
import original from '../original/worker.mjs';
const token='synthetic-admin-only';
const coll=(id='saga')=>({id,nombre:'Synthetic saga',origen:'manual',orden:0,visible:true,titulos:['movie','absent'],future:{keep:true}});
const cat=()=>({schema_version:1,items:[{id:'movie',titulo:'Synthetic',telegram_url:'https://t.me/synthetic/7',temporadas:[{episodios:[{id:'episode'}]}]}],unknown:{nested:['keep']}});
function database(){
 const db=new DatabaseSync(':memory:');return {db,fail:false,sql:{exec(q,...args){const st=db.prepare(q);return {toArray:()=>st.all(...args)};}},transactionSync(fn){db.exec('BEGIN');try{const r=fn();if(this.fail)throw Error('injected storage failure');db.exec('COMMIT');return r;}catch(e){db.exec('ROLLBACK');throw e;}}};
}
function setup(strict='true') {
 const storage=database();
 let raw=JSON.stringify(cat());let reads=0;
 const env={CATALOG_ADMIN_TOKEN:token,TMDB_BEARER_TOKEN:'synthetic-upstream-only',REQUIRE_CATALOG_IF_MATCH:strict,CATALOGO_KV:{async get(){reads++;return raw;},async put(){throw Error('Unexpected KV write');}}};
 // exec must eagerly execute mutating SQL, like Cloudflare SqlStorage.
 storage.sql.exec=(q,...args)=>{const st=storage.db.prepare(q);const rows=st.all(...args);return {toArray:()=>rows};};
 const coordinator=new CatalogCoordinator({storage},env);
 env.CATALOG_COORDINATOR={idFromName(name){assert.equal(name,'miflix-catalog-v1');return name;},get(){return coordinator;}};
 let seq=0;
 const send=(method,path,body,headers={},auth=token)=>worker.fetch(new Request('https://synthetic.invalid'+path,{method,headers:{...(auth?{Authorization:'Bearer '+auth}:{}),...(body?{'Content-Type':'application/json'}:{}),...(method==='GET'?{}:{'Idempotency-Key':'operation-'+(++seq)}),...headers},...(body===undefined?{}:{body:JSON.stringify(body)})}),env);
 const migrate=(c=cat(),collections)=>send('POST','/admin/migracion',{catalogo:c,...(collections?{colecciones:collections}:{}),confirm:'IMPORTAR'},{'If-None-Match':'*'});
 return {env,send,migrate,storage,reads:()=>reads,stale(){raw=JSON.stringify({schema_version:1,items:[]});}};
}
const ops=(upsert=[],delete_ids=[],confirm_delete=[])=>({format:'miflix-collections-operations',version:1,upsert,delete_ids,confirm_delete});
const get=async(e,path)=>{const r=await e.send('GET',path);return {r,body:await r.json(),tag:r.headers.get('ETag')};};
async function add(e,c=coll()){const {tag}=await get(e,'/admin/colecciones');const r=await e.send('PUT','/admin/colecciones',ops([c]),{'If-Match':tag});assert.equal(r.status,200);return r;}

test('original regression reproduces collection loss; schema_version is numeric',async()=>{
 let raw=JSON.stringify({...cat(),colecciones:[coll()]});const env={CATALOG_ADMIN_TOKEN:token,CATALOGO_KV:{get:async()=>raw,put:async(k,v)=>{raw=v;}}};
 for(const version of ['1',1]){const r=await original.fetch(new Request('https://synthetic.invalid/admin/catalogo',{method:'PUT',headers:{Authorization:'Bearer '+token},body:JSON.stringify({...cat(),schema_version:version})}),env);assert.equal(r.status,version===1?200:400);}
 assert.equal(JSON.parse(raw).colecciones,undefined);
});
test('worker derives relay from original, narrow changes only',()=>{
 const old=readFileSync(new URL('../original/worker.mjs',import.meta.url),'utf8');const updated=readFileSync(new URL('../worker.mjs',import.meta.url),'utf8');
 assert.ok(old.includes('catalogo.schema_version !== 1'));
 const relay=old.slice(old.indexOf('async function tmdbReadLimited'),old.indexOf('export default'));
 assert.ok(updated.includes(relay));
});
test('authentication on all admin routes including unknown and relay',async()=>{
 const e=setup();for(const path of ['/admin/catalogo','/admin/colecciones','/admin/migracion','/admin/respaldo','/admin/restaurar','/admin/metadata/tmdb/search/movie?query=x','/admin/unknown'])for(const auth of [null,'wrong'])assert.equal((await e.send('GET',path,undefined,{},auth)).status,401);
 assert.equal(e.reads(),0);
});
test('pre-migration reads preserve KV and writes fail closed; import requires confirmation',async()=>{
 const e=setup();assert.deepEqual((await get(e,'/catalogo.json')).body,cat());
 assert.equal((await e.send('PUT','/admin/catalogo',cat())).status,503);
 assert.equal((await e.send('POST','/admin/migracion',{catalogo:cat(),confirm:'IMPORTAR'})).status,428);
 assert.equal((await e.migrate()).status,200);assert.equal((await e.migrate()).status,409);
});
test('health, public aliases, OPTIONS, route handling and CORS',async()=>{
 const e=setup();await e.migrate();const h=await get(e,'/health');assert.equal(h.body.elementos,1);assert.equal(h.body.servicio,'MiFlix Catálogo');
 assert.deepEqual((await get(e,'/')).body,(await get(e,'/catalogo.json/')).body);
 const r=await e.send('OPTIONS','/admin/colecciones');assert.equal(r.status,204);assert.match(r.headers.get('Access-Control-Allow-Headers'),/If-Match/);
 assert.equal((await e.send('GET','/missing')).status,404);assert.equal((await e.send('POST','/admin/colecciones',{})).status,405);
});
test('migration preserves embedded collections, unknown fields and numeric schema',async()=>{
 const e=setup();await e.migrate({...cat(),colecciones_version:1,colecciones:[coll()]});
 const out=(await get(e,'/catalogo.json')).body;assert.equal(out.schema_version,1);assert.deepEqual(out.items,cat().items);assert.deepEqual(out.unknown,cat().unknown);assert.deepEqual(out.colecciones,[coll()]);
 const other=setup();assert.equal((await other.migrate({...cat(),schema_version:'1'})).status,400);
 const future=setup();assert.equal((await future.migrate({...cat(),colecciones_version:2})).status,400);
});
test('strict mode rejects unversioned old writers; compatibility mode preserves collections',async()=>{
 const strict=setup();await strict.migrate();assert.equal((await strict.send('PUT','/admin/catalogo',cat())).status,428);
 const e=setup('false');await e.migrate();await add(e);
 for(const body of [cat(),{...cat(),colecciones:[]},{...cat(),colecciones:[{id:'old'}]}]){
   const r=await e.send('PUT','/admin/catalogo',body,{'Idempotency-Key':''});assert.equal(r.status,200);assert.equal(r.headers.get('X-Legacy-Unconditional'),'true');assert.deepEqual((await get(e,'/admin/colecciones')).body.colecciones,[coll()]);
 }
});
test('audiovisual add, series edit, image and deletion preserve collections and unknown root',async()=>{
 const e=setup();await e.migrate();await add(e);
 for(const items of [[...cat().items,{id:'new'}],[{...cat().items[0],temporadas:[{id:'edited'}],poster_url:'https://example.invalid/poster'}],[]]){
 const {tag}=await get(e,'/admin/catalogo');assert.equal((await e.send('PUT','/admin/catalogo',{schema_version:1,items},{'If-Match':tag})).status,200);
 const out=(await get(e,'/catalogo.json')).body;assert.deepEqual(out.items,items);assert.deepEqual(out.unknown,cat().unknown);assert.deepEqual(out.colecciones,[coll()]);}
});
test('collection mutations need version, exact revision, explicit deletions; omission never deletes',async()=>{
 const e=setup();await e.migrate();await add(e);let g=await get(e,'/admin/colecciones');
 assert.equal((await e.send('PUT','/admin/colecciones',ops())).status,428);
 assert.equal((await e.send('PUT','/admin/colecciones',ops(),{'If-Match':'*'})).status,412);
 assert.equal((await e.send('PUT','/admin/colecciones',ops([],['saga']),{'If-Match':g.tag})).status,400);
 assert.equal((await e.send('PUT','/admin/colecciones',ops(),{'If-Match':g.tag})).status,200);
 g=await get(e,'/admin/colecciones');assert.equal(g.body.colecciones.length,1);
 assert.equal((await e.send('PUT','/admin/colecciones',ops([],['saga'],['saga']),{'If-Match':g.tag})).status,200);
 assert.deepEqual((await get(e,'/admin/colecciones')).body.colecciones,[]);
});
test('two concurrent collection writers: exactly one success, one conflict',async()=>{
 const e=setup();await e.migrate();const {tag}=await get(e,'/admin/colecciones');
 const out=await Promise.all(['a','b'].map(id=>e.send('PUT','/admin/colecciones',ops([coll(id)]),{'If-Match':tag})));
 assert.deepEqual(out.map(r=>r.status).sort(),[200,412]);assert.equal((await get(e,'/admin/colecciones')).body.colecciones.length,1);
});
test('two concurrent catalog writers use authoritative version, not KV',async()=>{
 const e=setup();await e.migrate();const {tag}=await get(e,'/admin/catalogo');e.stale();
 const out=await Promise.all([1,2].map(n=>e.send('PUT','/admin/catalogo',{...cat(),items:[{id:String(n)}]},{'If-Match':tag})));
 assert.deepEqual(out.map(r=>r.status).sort(),[200,412]);assert.equal(e.reads(),0);
});
test('idempotency after lost response, same key different body rejected; persists across restart',async()=>{
 const e=setup();await e.migrate();const {tag}=await get(e,'/admin/colecciones');const h={'If-Match':tag,'Idempotency-Key':'retry-exact-123'};
 const first=await (await e.send('PUT','/admin/colecciones',ops([coll()]),h)).json();
 // Restart coordinator using same database: no in-memory mutex or revision.
 const restarted=new CatalogCoordinator({storage:e.storage},e.env);e.env.CATALOG_COORDINATOR.get=()=>restarted;
 const again=await e.send('PUT','/admin/colecciones',ops([coll()]),h);assert.equal(again.status,200);assert.deepEqual(await again.json(),first);
 assert.equal((await e.send('PUT','/admin/colecciones',ops([coll('other')]),h)).status,409);
});
test('invalid collection values, duplicates, versions and restricted image URLs rollback',async()=>{
 const e=setup();await e.migrate();const before=await get(e,'/admin/respaldo');
 const bad=[{...coll(),nombre:' '},{...coll(),visible:1},{...coll(),orden:-1},{...coll(),titulos:['a','a']},{...coll(),origen:'tmdb'},{...coll(),poster_url:'http://x.invalid/a'},{...coll(),poster_url:'https://user:pass@x.invalid/a'},{...coll(),poster_url:'https://artworks.thetvdb.com/a'},{...coll(),descripcion:'x'.repeat(12001)},{...coll(),titulos:[{}]}];
 for(const c of bad) {const {tag}=await get(e,'/admin/colecciones');assert.equal((await e.send('PUT','/admin/colecciones',ops([c]),{'If-Match':tag})).status,400);}
 const {tag}=await get(e,'/admin/colecciones');assert.equal((await e.send('PUT','/admin/colecciones',{...ops(),version:'1'},{'If-Match':tag})).status,400);
 assert.equal((await e.send('PUT','/admin/colecciones',ops([coll(),coll()]),{'If-Match':tag})).status,400);
 assert.deepEqual((await get(e,'/admin/respaldo')).body,before.body);
});
test('visibility, order, missing title references and unknown collection fields survive edit',async()=>{
 const e=setup();await e.migrate();await add(e);let {tag}=await get(e,'/admin/colecciones');
 assert.equal((await e.send('PUT','/admin/colecciones',ops([{...coll(),nombre:'Changed',future:undefined},{...coll('hidden'),visible:false},{...coll('first'),orden:0}]),{'If-Match':tag})).status,200);
 const out=(await get(e,'/catalogo.json')).body;assert.deepEqual(out.colecciones.map(c=>c.id),['saga','first']);assert.deepEqual(out.colecciones[0].future,{keep:true});assert.ok(out.colecciones[0].titulos.includes('absent'));
});
test('failed commit rolls back state, backups and idempotency; same request can retry',async()=>{
 const e=setup();await e.migrate();const before=await get(e,'/admin/respaldo');const {tag}=await get(e,'/admin/colecciones');const headers={'If-Match':tag,'Idempotency-Key':'failure-retry-123'};
 e.storage.fail=true;assert.equal((await e.send('PUT','/admin/colecciones',ops([coll()]),headers)).status,503);e.storage.fail=false;
 assert.deepEqual((await get(e,'/admin/respaldo')).body,before.body);assert.equal((await e.send('PUT','/admin/colecciones',ops([coll()]),headers)).status,200);
});
test('backup and restore use current global revision and preserve monotonic revisions',async()=>{
 const e=setup();await e.migrate();const before=await get(e,'/admin/respaldo');await add(e);const now=await get(e,'/admin/respaldo');
 assert.deepEqual((await get(e,'/admin/respaldo?revision=1')).body,before.body);
 assert.equal((await e.send('POST','/admin/restaurar',{confirm:'RESTAURAR',backup:before.body},{'If-Match':before.tag})).status,412);
 assert.equal((await e.send('POST','/admin/restaurar',{confirm:'RESTAURAR',backup:before.body},{'If-Match':now.tag})).status,200);
 const restored=await get(e,'/admin/respaldo');assert.deepEqual(restored.body.state.catalog,before.body.state.catalog);assert.equal(restored.body.state.revision,3);assert.equal(restored.body.state.collectionsRevision,2);
 assert.deepEqual((await get(e,'/admin/respaldo?revision=2')).body,now.body);
});
test('size limits count UTF-8 and reject merged catalog overflow; >128KiB persists in chunks',async()=>{
 const e=setup();const large={...cat(),extra:'é'.repeat(100000)};assert.equal((await e.migrate(large)).status,200);assert.equal((await get(e,'/catalogo.json')).body.extra,large.extra);
 const {tag}=await get(e,'/admin/catalogo');const before=await get(e,'/admin/respaldo');
 assert.equal((await e.send('PUT','/admin/catalogo',{...cat(),extra:'é'.repeat(3*1024*1024)},{'If-Match':tag})).status,413);
 assert.deepEqual((await get(e,'/admin/respaldo')).body,before.body);
});
test('TMDB original routes and new allowlist, duplicate/query restrictions, safe errors',async()=>{
 const e=setup();let called=0;const fake=async(url,init)=>{called++;assert.equal(url.origin,'https://api.themoviedb.org');assert.equal(init.redirect,'error');return Response.json({id:123});};
 const req=path=>new Request('https://synthetic.invalid/admin/metadata/tmdb'+path,{headers:{Authorization:'Bearer '+token}});
 for(const path of ['/search/movie?query=x&language=es-ES','/search/tv?query=x','/movie/10','/tv/10/credits','/tv/10/season/0','/search/collection?query=saga','/collection/10?language=es-ES','/collection/10/images'])assert.equal((await handleTmdbRelay(req(path),e.env,fake)).status,200,path);
 const count=called;
 for(const path of ['/collection/10/anything','/collection/0','/search/person?query=x','/collection/10?api_key=x','/collection/10?language=es&language=en','/search/collection','/collection/10/images?url=https://x.invalid','/tv/1/season/2147483648'])assert.equal((await handleTmdbRelay(req(path),e.env,fake)).status,400,path);
 assert.equal(called,count);
 const secret=await handleTmdbRelay(req('/movie/1'),e.env,async()=>Response.json({unexpected:token}));assert.equal(secret.status,502);assert.ok(!(await secret.text()).includes(token));
 const timeout=await handleTmdbRelay(req('/movie/1'),e.env,async()=>{throw new DOMException('secret','TimeoutError');});assert.equal(timeout.status,504);
});
test('collection and public merged byte budgets apply transactionally',async()=>{
 const e=setup();await e.migrate();let {tag}=await get(e,'/admin/colecciones');const before=await get(e,'/admin/respaldo');
 assert.equal((await e.send('PUT','/admin/colecciones',ops([{...coll(),future:'é'.repeat(1100000)}]),{'If-Match':tag})).status,413);
 assert.deepEqual((await get(e,'/admin/respaldo')).body,before.body);
 const near=setup();const huge={...cat(),extra:'x'.repeat(5*1024*1024-1000)};assert.equal((await near.migrate(huge)).status,200);
 ({tag}=await get(near,'/admin/colecciones'));assert.equal((await near.send('PUT','/admin/colecciones',ops([{...coll(),descripcion:'x'.repeat(2000)}]),{'If-Match':tag})).status,413);
 assert.deepEqual((await get(near,'/admin/colecciones')).body.colecciones,[]);
});
test('bad JSON, advertised length and malformed restore are safe without mutations',async()=>{
 const e=setup();await e.migrate();const before=await get(e,'/admin/respaldo');
 for(const [body,headers,expected] of [['{',{},400],['{}',{'Content-Length':'99999999'},413]]){
 const r=await worker.fetch(new Request('https://synthetic.invalid/admin/colecciones',{method:'PUT',headers:{Authorization:'Bearer '+token,'Idempotency-Key':'bad-json-123',...headers},body}),e.env);assert.equal(r.status,expected);
 }
 assert.equal((await e.send('POST','/admin/restaurar',{confirm:'RESTAURAR',backup:{format:'miflix-worker-backup',version:1}},{'If-Match':before.tag})).status,400);
 assert.deepEqual((await get(e,'/admin/respaldo')).body,before.body);
});
test('bounded history retains ten previous states and restoration never rewinds ETags',async()=>{
 const e=setup();await e.migrate();for(let n=0;n<12;n++){const {tag}=await get(e,'/admin/colecciones');assert.equal((await e.send('PUT','/admin/colecciones',ops([coll(String(n))]),{'If-Match':tag})).status,200);}
 assert.equal((await e.send('GET','/admin/respaldo?revision=1')).status,404);assert.equal((await e.send('GET','/admin/respaldo?revision=2')).status,404);assert.equal((await e.send('GET','/admin/respaldo?revision=3')).status,200);assert.equal((await e.send('GET','/admin/respaldo?revision=12')).status,200);
});
test('independent revisions permit simultaneous catalog and collection changes',async()=>{
 const e=setup();await e.migrate();const catalogTag=(await get(e,'/admin/catalogo')).tag;const collectionTag=(await get(e,'/admin/colecciones')).tag;
 const out=await Promise.all([e.send('PUT','/admin/catalogo',{...cat(),items:[...cat().items,{id:'new'}]},{'If-Match':catalogTag}),e.send('PUT','/admin/colecciones',ops([coll()]),{'If-Match':collectionTag})]);assert.deepEqual(out.map(r=>r.status),[200,200]);
 const result=(await get(e,'/catalogo.json')).body;assert.equal(result.items.length,2);assert.equal(result.colecciones.length,1);
});
test('reference parser audit: Mobile/TV read items and have no collections model',()=>{
 for(const filename of ['Mobile-0.9.13-Catalog.kt','TV-0.1.13-Catalog.kt']){
 const source=readFileSync(new URL('../reference/'+filename,import.meta.url),'utf8');assert.ok(source.includes('root.getAsJsonArray("items")'));assert.ok(source.includes('Gson().fromJson(root, Catalog::class.java)'));assert.ok(!source.includes('colecciones'));assert.ok(source.includes('root.get("schema_version")?.asString'));
 }
});
test('read-only rollback preserves current catalog and denies writes without bypassing auth',async()=>{
 const rollback=(await import('../rollback-readonly.mjs')).default;
 const e=setup();await e.migrate();await add(e);const before=await get(e,'/admin/respaldo');
 const request=(auth)=>new Request('https://synthetic.invalid/admin/catalogo',{method:'PUT',headers:auth?{Authorization:'Bearer '+token}:{},body:JSON.stringify(cat())});
 assert.equal((await rollback.fetch(request(false),e.env)).status,401);assert.equal((await rollback.fetch(request(true),e.env)).status,503);
 const publicOut=await rollback.fetch(new Request('https://synthetic.invalid/catalogo.json'),e.env);assert.equal((await publicOut.json()).colecciones.length,1);assert.deepEqual((await get(e,'/admin/respaldo')).body,before.body);
});

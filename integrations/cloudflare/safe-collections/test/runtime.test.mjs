// Runs the delivered full Worker inside Cloudflare workerd, SQLite Durable Objects.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Miniflare} from 'miniflare';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
const catalog={schema_version:1,items:[{id:'movie',telegram_url:'https://t.me/synthetic/1'}],extension:{keep:true}};
const collection=id=>({id,nombre:id,origen:'manual',orden:0,visible:true,titulos:['movie','missing']});
const operation=upsert=>({format:'miflix-collections-operations',version:1,upsert,delete_ids:[]});
const token='runtime-synthetic-only';
function config(dir){return {modules:true,scriptPath:new URL('../worker.mjs',import.meta.url).pathname,compatibilityDate:'2026-07-01',durableObjects:{CATALOG_COORDINATOR:{className:'CatalogCoordinator',useSQLite:true}},durableObjectsPersist:dir,kvNamespaces:['CATALOGO_KV'],bindings:{CATALOG_ADMIN_TOKEN:token,REQUIRE_CATALOG_IF_MATCH:'true'}};}
test('workerd real SQLite: migration, parallel conditional writes, idempotency, restart, stale KV, restore',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'miflix-runtime-'));let mf=new Miniflare(config(dir));let n=0;
 const send=(method,path,body,headers={})=>mf.dispatchFetch('https://synthetic.invalid'+path,{method,headers:{Authorization:'Bearer '+token,'Content-Type':'application/json','Idempotency-Key':'runtime-op-'+(++n),...headers},...(body===undefined?{}:{body:JSON.stringify(body)})});
 try{
  const kv=await mf.getKVNamespace('CATALOGO_KV');await kv.put('catalogo',JSON.stringify(catalog));
  assert.deepEqual(await (await send('GET','/catalogo.json')).json(),catalog);
  assert.equal((await send('POST','/admin/migracion',{catalogo:catalog,confirm:'IMPORTAR'},{'If-None-Match':'*'})).status,200);
  const backupResponse=await send('GET','/admin/respaldo');const backup=await backupResponse.json();
  const tag=(await send('GET','/admin/colecciones')).headers.get('ETag');
  const writes=await Promise.all(['a','b'].map(id=>send('PUT','/admin/colecciones',operation([collection(id)]),{'If-Match':tag})));
  assert.deepEqual(writes.map(r=>r.status).sort(),[200,412]);
  const current=await send('GET','/admin/colecciones');const currentTag=current.headers.get('ETag');
  const h={'If-Match':currentTag,'Idempotency-Key':'runtime-retry-123'};
  const first=await send('PUT','/admin/colecciones',operation([collection('retry')]),h);assert.equal(first.status,200);const result=await first.json();
  await mf.dispose();mf=new Miniflare(config(dir));
  const again=await send('PUT','/admin/colecciones',operation([collection('retry')]),h);assert.equal(again.status,200);assert.deepEqual(await again.json(),result);
  await (await mf.getKVNamespace('CATALOGO_KV')).put('catalogo',JSON.stringify({schema_version:1,items:[]}));
  const publicOut=await (await send('GET','/catalogo.json')).json();assert.deepEqual(publicOut.items,catalog.items);assert.equal(publicOut.colecciones.length,2);
  const stale=await send('POST','/admin/restaurar',{confirm:'RESTAURAR',backup},{'If-Match':backupResponse.headers.get('ETag')});assert.equal(stale.status,412);
  const beforeRestore=await send('GET','/admin/respaldo');
  assert.equal((await send('POST','/admin/restaurar',{confirm:'RESTAURAR',backup},{'If-Match':beforeRestore.headers.get('ETag')})).status,200);
  assert.deepEqual((await (await send('GET','/catalogo.json')).json()).colecciones,[]);
 }finally{await mf.dispose();await rm(dir,{recursive:true,force:true});}
});

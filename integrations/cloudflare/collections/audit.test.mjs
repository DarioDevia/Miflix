// Execute the actual recovered Worker source only with synthetic Request/Response and in-memory KV.
// These passing regression tests prove limitations; they do NOT certify a deployed Worker.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from './fixtures/worker-0.1.4.mjs';
const token='synthetic-test-only';
const collection={id:'col-test',nombre:'Saga',origen:'tmdb',tmdb_collection_id:10,orden:2,visible:true,titulos:['movie','temporarily-absent']};
const catalog=()=>({schema_version:1,items:[{id:'movie',tipo:'pelicula',titulo:'Synthetic',telegram_url:'https://t.me/test/1',fecha_agregado:'2024-01-01T00:00:00Z'}],colecciones_version:1,colecciones:[structuredClone(collection)],future:{nested:['keep']}});
function environment(initial=null) {
 let raw=initial==null?null:JSON.stringify(initial);let fail=false;
 const env={CATALOG_ADMIN_TOKEN:token,CATALOGO_KV:{
  async get(_key,type) {return type==='json' && raw!=null?JSON.parse(raw):raw;},
  async put(_key,value){if(fail) throw Error('synthetic KV failure');raw=value;}
 }};
 return {env,read:()=>raw==null?null:JSON.parse(raw),fail:()=>{fail=true;}};
}
const req=(method,path,body,auth=token,headers={})=>new Request('https://synthetic.invalid'+path,{method,headers:{...(auth?{Authorization:'Bearer '+auth}:{}),...headers},...(body===undefined?{}:{body:JSON.stringify(body)})});
const send=(e,r)=>worker.fetch(r,e.env);
test('source PUT and GET roundtrip collections, unknown fields and items',async()=>{const e=environment();const c=catalog();assert.equal((await send(e,req('PUT','/admin/catalogo',c))).status,200);const out=await (await send(e,req('GET','/catalogo.json'))).json();assert.deepEqual(out,c);});
test('unknown collection metadata, order and missing references survive whole-object roundtrip',async()=>{const e=environment();const c=catalog();c.colecciones[0].future={x:1};assert.equal((await send(e,req('PUT','/admin/catalogo',c))).status,200);assert.deepEqual(e.read().colecciones,c.colecciones);});
test('BLOCKER: old writer omitting collections erases them',async()=>{const e=environment(catalog());await send(e,req('PUT','/admin/catalogo',{schema_version:1,items:catalog().items}));assert.equal(e.read().colecciones,undefined);});
test('BLOCKER: old writer can overwrite newer collections with a stale copy',async()=>{const c=catalog();const e=environment(c);const newer=structuredClone(c);newer.colecciones[0].nombre='new revision';await send(e,req('PUT','/admin/catalogo',newer));await send(e,req('PUT','/admin/catalogo',c));assert.equal(e.read().colecciones[0].nombre,'Saga');});
test('BLOCKER: stale If-Match is ignored instead of returning 412',async()=>{const e=environment(catalog());const c=catalog();c.colecciones[0].nombre='stale edit';assert.equal((await send(e,req('PUT','/admin/catalogo',c,token,{'If-Match':'"stale"'}))).status,200);assert.equal(e.read().colecciones[0].nombre,'stale edit');});
test('BLOCKER: GET does not expose an ETag for conditional write',async()=>{const e=environment(catalog());const r=await send(e,req('GET','/catalogo.json'));assert.equal(r.headers.get('ETag'),null);});
test('BLOCKER: dedicated collection endpoint is absent',async()=>{const e=environment(catalog());assert.equal((await send(e,req('PUT','/admin/colecciones',{colecciones:[collection]}))).status,404);});
test('BLOCKER: PATCH is not supported',async()=>{const e=environment(catalog());assert.equal((await send(e,req('PATCH','/admin/catalogo',{colecciones:[collection]}))).status,404);});
test('administrative write rejects absent and wrong credentials',async()=>{const e=environment(catalog());const before=e.read();for(const auth of [null,'incorrect']) assert.equal((await send(e,req('PUT','/admin/catalogo',catalog(),auth))).status,401);assert.deepEqual(e.read(),before);});
test('5 MiB body limit rejects oversized data without changing state',async()=>{const e=environment(catalog());const before=e.read();const big=catalog();big.extra='x'.repeat(5*1024*1024);assert.equal((await send(e,req('PUT','/admin/catalogo',big))).status,413);assert.deepEqual(e.read(),before);});
test('invalid audiovisual envelope rejected',async()=>{const e=environment(catalog());assert.equal((await send(e,req('PUT','/admin/catalogo',{items:[]}))).status,400);});
test('BLOCKER: invalid and duplicate collections are not schema-validated',async()=>{const e=environment(catalog());const c=catalog();c.colecciones=[{id:'duplicate'},{id:'duplicate'}];assert.equal((await send(e,req('PUT','/admin/catalogo',c))).status,200);assert.deepEqual(e.read().colecciones,c.colecciones);});
test('write failure does not change fake KV; real Worker lacks explicit recovery response',async()=>{const e=environment(catalog());const before=e.read();e.fail();await assert.rejects(send(e,req('PUT','/admin/catalogo',catalog())),/synthetic KV failure/);assert.deepEqual(e.read(),before);});
test('health and catalog routes remain public read-only',async()=>{const e=environment(catalog());assert.equal((await send(e,req('GET','/health',undefined,null))).status,200);assert.equal((await send(e,req('GET','/catalogo.json',undefined,null))).status,200);});
test('BLOCKER: separate KV read/version-check/write is not atomic',async()=>{
 let revision=1;let value='original';
 const read=async()=>({revision,value});
 const a=await read();const b=await read();
 // Both callers observed the same revision before either PUT. Comparing this read is insufficient.
 assert.equal(a.revision,b.revision);
 value='A';revision=a.revision+1;
 value='B';revision=b.revision+1;
 assert.equal(revision,2);assert.equal(value,'B');
});

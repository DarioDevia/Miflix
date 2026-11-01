// Offline contract tests. This fixture is the owner's production Worker source,
// not feature/worker-safe-collections. No requests leave this process.
import worker from './fixtures/worker-production-collections.mjs';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { test } from 'node:test';

const fixture=JSON.parse(readFileSync(new URL('../build/collections-worker-contract.json',import.meta.url),'utf8'));
const token='synthetic-admin';
class KV {
  constructor(value=fixture.catalog) {this.raw=JSON.stringify(value);this.writes=0;this.fail=false;}
  async get(key,format) {assert.equal(key,'catalogo');return format==='json'?JSON.parse(this.raw):this.raw;}
  async put(key,value) {assert.equal(key,'catalogo');if(this.fail) throw Error('synthetic storage failure');this.raw=value;this.writes++;}
  value() {return JSON.parse(this.raw);}
}
function setup(value) {
  const kv=new KV(value);
  const request=(path,method='GET',body,auth=token)=>worker.fetch(new Request('https://worker.invalid'+path,{
    method,headers:{Authorization:'Bearer '+auth,'Content-Type':'application/json'},
    ...(body===undefined?{}:{body:typeof body==='string'?body:JSON.stringify(body)}),
  }),{CATALOGO_KV:kv,CATALOG_ADMIN_TOKEN:token});
  return {kv,request};
}
const empty={format:'miflix-collections',version:1,colecciones:[]};
test('authenticated GET and empty stored set use the exact envelope',async()=>{
  const {kv,request}=setup({...fixture.catalog,colecciones:[]});
  assert.deepEqual(await (await request('/admin/colecciones')).json(),empty);assert.equal(kv.writes,0);
});
test('actual Android collection document publishes, reads back, and preserves every audiovisual field',async()=>{
  const {kv,request}=setup();const before=kv.value();const response=await request('/admin/colecciones','PUT',fixture.collections);
  assert.equal(response.status,200);const ack=await response.json();assert.equal(ack.ok,true);assert.equal(ack.colecciones,1);assert.ok(Number.isFinite(Date.parse(ack.updated_at)));
  assert.deepEqual(kv.value().items,before.items);assert.deepEqual(kv.value().unknown_root,before.unknown_root);
  assert.deepEqual(await (await request('/admin/colecciones')).json(),fixture.collections);
  assert.deepEqual((await (await request('/catalogo.json')).json()).colecciones,fixture.collections.colecciones);
});
test('PUT replaces the whole collections set without deleting titles',async()=>{
  const {kv,request}=setup();await request('/admin/colecciones','PUT',fixture.collections);const before=kv.value().items;
  const changed=structuredClone(fixture.collections);changed.colecciones[0].nombre='Nombre revisado';
  assert.equal((await request('/admin/colecciones','PUT',changed)).status,200);assert.deepEqual(kv.value().colecciones,changed.colecciones);assert.deepEqual(kv.value().items,before);
});
test('empty PUT clears collections only (Android requires explicit confirmation)',async()=>{
  const {kv,request}=setup();const before=kv.value().items;assert.equal((await request('/admin/colecciones','PUT',empty)).status,200);assert.deepEqual(kv.value().colecciones,[]);assert.deepEqual(kv.value().items,before);
});
test('401 on both administrative collection routes cannot mutate KV',async()=>{
  const {kv,request}=setup();for(const method of ['GET','PUT']) assert.equal((await request('/admin/colecciones',method,method==='PUT'?fixture.collections:undefined,'wrong')).status,401);assert.equal(kv.writes,0);
});
test('400 rejects invalid format, missing fields, duplicates and invalid JSON',async()=>{
  const {kv,request}=setup();const invalid=structuredClone(fixture.collections);invalid.colecciones.push(invalid.colecciones[0]);
  for(const payload of [{},'{',invalid,{...empty,version:2},{...empty,colecciones:[{id:'missing'}]}]) assert.equal((await request('/admin/colecciones','PUT',payload)).status,400);assert.equal(kv.writes,0);
});
test('413 enforces request size without changing the catalog',async()=>{
  const {kv,request}=setup();const before=kv.raw;assert.equal((await request('/admin/colecciones','PUT',' '.repeat(5*1024*1024+1))).status,413);assert.equal(kv.raw,before);assert.equal(kv.writes,0);
});
test('413 enforces final catalog size, not just collection request size',async()=>{
  const {kv,request}=setup({...fixture.catalog,padding:'x'.repeat(5*1024*1024)});const before=kv.raw;assert.equal((await request('/admin/colecciones','PUT',fixture.collections)).status,413);assert.equal(kv.raw,before);assert.equal(kv.writes,0);
});
test('503 storage failure keeps KV intact; manual retry can succeed',async()=>{
  const {kv,request}=setup();kv.fail=true;const before=kv.raw;assert.equal((await request('/admin/colecciones','PUT',fixture.collections)).status,503);assert.equal(kv.raw,before);kv.fail=false;assert.equal((await request('/admin/colecciones','PUT',fixture.collections)).status,200);
});
test('409 refuses collections before a valid audiovisual catalog exists',async()=>{
  const {kv,request}=setup(null);assert.equal((await request('/admin/colecciones','PUT',fixture.collections)).status,409);assert.equal(kv.writes,0);
});
test('ordinary Android catalog publication keeps server collections and unknown root fields',async()=>{
  const {kv,request}=setup();await request('/admin/colecciones','PUT',fixture.collections);const saved=kv.value().colecciones;
  assert.equal(Object.hasOwn(fixture.publication,'colecciones'),false);assert.equal(Object.hasOwn(fixture.publication,'colecciones_version'),false);
  const publication=structuredClone(fixture.publication);publication.items[0].titulo='Corrección normal';
  assert.equal((await request('/admin/catalogo','PUT',publication)).status,200);assert.deepEqual(kv.value().colecciones,saved);assert.deepEqual(kv.value().unknown_root,fixture.catalog.unknown_root);assert.equal(kv.value().items[0].titulo,'Corrección normal');
});
test('old publisher cannot smuggle a collections replacement through /admin/catalogo',async()=>{
  const {kv,request}=setup();await request('/admin/colecciones','PUT',fixture.collections);await request('/admin/catalogo','PUT',{...fixture.publication,colecciones:[]});assert.deepEqual(kv.value().colecciones,fixture.collections.colecciones);
});
test('health and public routes remain compatible',async()=>{
  const {request}=setup();for(const path of ['/health','/','/catalogo.json']) assert.equal((await request(path)).status,200);assert.equal((await (await request('/health')).json()).elementos,3);
});

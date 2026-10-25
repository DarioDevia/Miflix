import { test } from 'node:test';
import assert from 'node:assert/strict';
import { handleAdminExtras, validateCheckpoint } from './admin-extras.js';
const cp={tipo:'pelicula',titulo:'Prueba',anio:'2020',telegram_url:'https://t.me/c/123/8/99?single',telegram_message_id:99,procesado_at:'2026-10-06T00:00:00Z'};
function fixture(){const map=new Map();return {kv:{get:async k=>map.get(k)??null,put:async(k,v)=>map.set(k,v)},env:{CATALOG_ADMIN_TOKEN:'fake-test-only',TMDB_BEARER_TOKEN:'fake-provider'},map};}
function req(path,method='GET',body=null,token='fake-test-only'){return new Request('https://test.invalid'+path,{method,headers:{Authorization:'Bearer '+token},...(body?{body:JSON.stringify(body)}:{})});}
test('public endpoints untouched',async()=>{const {env,kv}=fixture();assert.equal(await handleAdminExtras(req('/catalogo.json'),env,kv),null);});
test('private checkpoint rejects unauthorized without writes',async()=>{const {env,kv,map}=fixture();assert.equal((await handleAdminExtras(req('/admin/checkpoint','PUT',cp,'wrong'),env,kv)).status,401);assert.equal(map.size,0);});
test('empty checkpoint and exact URL round trip',async()=>{const {env,kv,map}=fixture();assert.deepEqual(await (await handleAdminExtras(req('/admin/checkpoint'),env,kv)).json(),{ok:true,checkpoint:null});assert.equal((await handleAdminExtras(req('/admin/checkpoint','PUT',cp),env,kv)).status,200);const r=await (await handleAdminExtras(req('/admin/checkpoint'),env,kv)).json();assert.equal(r.checkpoint.telegram_url,cp.telegram_url);assert.equal(r.checkpoint.procesado_at,cp.procesado_at);assert.equal(map.has('catalogo'),false);});
test('invalid body rejected',async()=>{const {env,kv}=fixture();assert.equal((await handleAdminExtras(req('/admin/checkpoint','PUT',{...cp,telegram_url:'https://evil.test/a'}),env,kv)).status,422);assert.equal(validateCheckpoint({...cp,telegram_url:'https://t.me/+invite'}),false);});
test('metadata fixed upstream only, separate provider credential',async()=>{const {env,kv}=fixture();let sent;const fetcher=async(u,init)=>{sent={url:String(u),init};return Response.json({results:[]});};const res=await handleAdminExtras(req('/admin/metadata/tmdb/search/movie?query=Prueba&language=es-MX'),env,kv,fetcher);assert.equal(res.status,200);assert.equal(sent.init.headers.Authorization,'Bearer fake-provider');assert.match(sent.url,/^https:\/\/api.themoviedb.org\/3\/search\/movie/);assert.equal((await handleAdminExtras(req('/admin/metadata/tmdb/account'),env,kv,fetcher)).status,400);});
test('unsupported method, oversized checkpoint and KV failure',async()=>{const {env,kv}=fixture();assert.equal((await handleAdminExtras(req('/admin/checkpoint','POST',cp),env,kv)).status,405);assert.equal((await handleAdminExtras(req('/admin/checkpoint','PUT',{...cp,titulo:'x'.repeat(9000)}),env,kv)).status,413);assert.equal((await handleAdminExtras(req('/admin/checkpoint'),env,{get:async()=>{throw Error('private detail')}})).status,503);});
test('movie and TV logos use fixed authenticated upstream, no KV writes',async()=>{
  const {env,kv,map}=fixture();let calls=0;
  const fetcher=async(u,init)=>{calls++;assert.match(String(u),/^https:\/\/api.themoviedb.org\/3\/(movie|tv)\/42\/images$/);assert.equal(init.headers.Authorization,'Bearer fake-provider');return Response.json({logos:[{file_path:'/fixture.png',iso_639_1:'es'}]});};
  for(const type of ['movie','tv']) {const res=await handleAdminExtras(req(`/admin/metadata/tmdb/${type}/42/images`),env,kv,fetcher);assert.equal(res.status,200);assert.equal((await res.json()).logos[0].file_path,'/fixture.png');}
  assert.equal(calls,2);assert.equal(map.size,0);
});
test('images relay keeps authentication, method, path and parameter boundaries',async()=>{
  const {env,kv}=fixture();const fetcher=async()=>{throw Error('must not fetch')};
  assert.equal((await handleAdminExtras(req('/admin/metadata/tmdb/movie/42/images','GET',null,'wrong'),env,kv,fetcher)).status,401);
  assert.equal((await handleAdminExtras(req('/admin/metadata/tmdb/movie/42/images','POST'),env,kv,fetcher)).status,405);
  for(const path of ['/movie/42/images/extra','/collection/42/images','/movie/42/images?url=https://evil.test']) assert.equal((await handleAdminExtras(req('/admin/metadata/tmdb'+path),env,kv,fetcher)).status,400);
});

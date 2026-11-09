// Production-reference Worker, intercepted upstream, synthetic credentials and no KV/network writes.
import { handleTmdbRelay } from './fixtures/worker-production-collections.mjs';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { test } from 'node:test';
const paths=JSON.parse(readFileSync(new URL('../build/direct-relay-requests.json',import.meta.url),'utf8'));
const env={CATALOG_ADMIN_TOKEN:'synthetic-admin',TMDB_BEARER_TOKEN:'synthetic-tmdb'};
for(const path of [...new Set(paths)]) test(`actual Admin direct detail request is allowed: ${path}`,async()=>{
  let calls=0;
  const response=await handleTmdbRelay(new Request('https://worker.invalid'+path,{headers:{Authorization:'Bearer synthetic-admin'}}),env,async(url,options)=>{
    calls++;assert.equal(url.origin,'https://api.themoviedb.org');assert.equal(url.pathname,'/3'+path.split('?')[0].replace('/admin/metadata/tmdb',''));
    assert.equal(options.headers.Authorization,'Bearer synthetic-tmdb');assert.equal(options.redirect,'error');
    return new Response(JSON.stringify({id:42,verified:true}),{headers:{'Content-Type':'application/json'}});
  });
  assert.equal(response.status,200);assert.equal(calls,1);assert.equal((await response.json()).id,42);
});
test('reference relay still authenticates detail requests without requesting new credentials',async()=>{
 let called=false;const response=await handleTmdbRelay(new Request('https://worker.invalid/admin/metadata/tmdb/movie/42'),env,async()=>{called=true;throw Error('must not call')});assert.equal(response.status,401);assert.equal(called,false);
});
test('reference relay rejects arbitrary routes and redirects in query parameters',async()=>{
 for(const path of ['/admin/metadata/tmdb/evil/42','/admin/metadata/tmdb/movie/42?url=https://evil.invalid']) {
  let called=false;const response=await handleTmdbRelay(new Request('https://worker.invalid'+path,{headers:{Authorization:'Bearer synthetic-admin'}}),env,async()=>{called=true;throw Error('must not call')});assert.equal(response.status,400);assert.equal(called,false);
 }
});
test('unavailable upstream detail remains an error rather than fabricated metadata',async()=>{
 const response=await handleTmdbRelay(new Request('https://worker.invalid/admin/metadata/tmdb/movie/42',{headers:{Authorization:'Bearer synthetic-admin'}}),env,async()=>new Response('',{status:404}));assert.equal(response.status,404);
});

// No network: run the exact original Worker source with synthetic KV and Android-produced JSON.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import worker from '../../integrations/cloudflare/collections/fixtures/worker-0.1.4.mjs';
const fixture=JSON.parse(readFileSync(new URL('../build/retired-worker-projection.json',import.meta.url),'utf8'));
const token='synthetic-admin';
let stored=null;let fail=false;
const env={CATALOG_ADMIN_TOKEN:token,CATALOGO_KV:{async get(_key,kind){return kind==='json' && stored!==null?JSON.parse(stored):stored;},async put(_key,value){if(fail) throw Error('synthetic write failure');stored=value;}}};
const request=(method,path,value,auth=token)=>new Request('https://synthetic.invalid'+path,{method,headers:{...(auth?{Authorization:'Bearer '+auth}:{})},...(value===undefined?{}:{body:JSON.stringify(value)})});
test('Android projection backed up originals and contains no retired provider payload',()=>{assert.equal(fixture.original_private_backup_exists,true);assert.equal(JSON.stringify(fixture.publication).includes('TheTVDB'),false);});
test('original Worker accepts projected complete catalog through authenticated PUT',async()=>{const response=await worker.fetch(request('PUT','/admin/catalogo',fixture.publication),env);assert.equal(response.status,200);assert.equal((await response.json()).ok,true);});
test('original public catalog GET returns exactly the Android projection',async()=>{const response=await worker.fetch(request('GET','/catalogo.json'),env);assert.deepEqual(await response.json(),fixture.publication);});
test('unknown fields and existing remote collections survive original Worker serialization',()=>{const parsed=JSON.parse(stored);for(const key of ['unknown_root','colecciones_version','colecciones']) assert.deepEqual(parsed[key],fixture.baseline[key]);});
test('movie, series, anime, episodes, links and incorporation dates survive',()=>{const parsed=JSON.parse(stored);assert.equal(parsed.items.length,3);for(const old of fixture.baseline.items){const item=parsed.items.find(x=>x.id===old.id);assert.ok(item);for(const key of ['id','tipo','titulo','telegram_url','telegram_message_id','playback_id','fecha_agregado','temporadas','poster_url','backdrop_url'])assert.deepEqual(item[key],old[key]);}});
test('no authentication cannot publish and does not alter existing synthetic KV',async()=>{const before=stored;const response=await worker.fetch(request('PUT','/admin/catalogo',fixture.publication,null),env);assert.equal(response.status,401);assert.equal(stored,before);});
test('synthetic KV write failure leaves previous complete catalog intact',async()=>{const before=stored;fail=true;try{await assert.rejects(worker.fetch(request('PUT','/admin/catalogo',fixture.publication),env),/synthetic write failure/);assert.equal(stored,before);}finally{fail=false;}});
test('health and public root routes remain compatible',async()=>{const health=await worker.fetch(request('GET','/health'),env);assert.equal(health.status,200);assert.equal((await health.json()).elementos,3);const root=await worker.fetch(request('GET','/'),env);assert.deepEqual(await root.json(),fixture.publication);});

"""Offline characterization of the original Worker and Admin Windows source ZIPs.
No production access. Requires development Python requests/Pillow and Node.
Usage: python check_collection_compatibility.py WORKER_ZIP WINDOWS_ZIP
"""
import argparse
import importlib
import json
import pathlib
import subprocess
import sys
import tempfile
import zipfile

FIXTURE={'schema_version':1,'unknown_root':{'keep':True},'colecciones':[{'id':'synthetic-collection','nombre':'Example','titulos':['p','missing'],'extra':True}],
         'items':[{'id':'p','tipo':'pelicula','titulo':'Synthetic','fecha_agregado':'2020-01-01T00:00:00Z','telegram_url':'https://t.me/synthetic/1'}]}

def check(worker_zip,windows_zip):
    with tempfile.TemporaryDirectory(prefix='miflix-compat-') as tmp:
        directory=pathlib.Path(tmp)
        with zipfile.ZipFile(windows_zip) as archive:
            for name in ('catalogo.py','rutas.py','descargas.py'):
                matches=[p for p in archive.namelist() if p.endswith('/app/'+name)]
                if len(matches)!=1:raise ValueError('ZIP Windows no reconocido')
                (directory/name).write_bytes(archive.read(matches[0]))
        sys.path.insert(0,tmp)
        catalog=importlib.import_module('catalogo')
        path=directory/'synthetic-catalog.json';catalog.guardar_catalogo(FIXTURE,path)
        assert catalog.cargar_catalogo(path)['colecciones']==FIXTURE['colecciones']
        published=catalog.catalogo_para_android(path)
        assert published['colecciones']==FIXTURE['colecciones']
        assert published['unknown_root']==FIXTURE['unknown_root']
        catalog.reemplazar_catalogo_remoto(published,path)
        assert catalog.cargar_catalogo(path)['colecciones']==FIXTURE['colecciones']
        assert catalog.cargar_catalogo(path)['items']==FIXTURE['items']
        with zipfile.ZipFile(worker_zip) as archive:
            names=[p for p in archive.namelist() if p=='worker.js' or p.endswith('/worker.js')]
            if len(names)!=1:raise ValueError('ZIP Worker no reconocido')
            (directory/'worker.mjs').write_bytes(archive.read(names[0]))
        script="""import assert from 'node:assert/strict';import worker from './worker.mjs';
const root=JSON.parse(process.argv[2]);let raw=JSON.stringify(root),puts=0;
const env={CATALOG_ADMIN_TOKEN:'synthetic-admin',CATALOGO_KV:{get:async(k,t)=>t==='json'?JSON.parse(raw):raw,put:async(k,v)=>{raw=v;puts++;}}};
const r=await worker.fetch(new Request('https://synthetic.invalid/admin/catalogo',{method:'PUT',headers:{Authorization:'Bearer synthetic-admin'},body:JSON.stringify(root)}),env);
assert.equal(r.status,200);assert.equal(puts,1);assert.deepEqual(JSON.parse(raw),root);
const get=await worker.fetch(new Request('https://synthetic.invalid/catalogo.json'),env);assert.deepEqual(await get.json(),root);
console.log('Worker PUT/GET preserves collection and unknown fields');
"""
        (directory/'check.mjs').write_text(script)
        subprocess.run(['node',str(directory/'check.mjs'),json.dumps(FIXTURE)],check=True)
    return {'windows_read_save_public_and_replace_preserve':True,'worker_put_get_preserve':True,'deployed_worker_verified':False,'production_access':False}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('worker_zip');parser.add_argument('windows_zip');args=parser.parse_args()
    print(json.dumps(check(args.worker_zip,args.windows_zip)))

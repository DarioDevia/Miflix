#!/usr/bin/env python3
"""Private backups and conditional requests. Never prints credentials or HTTP bodies on failure."""
import argparse, hashlib, json, os, pathlib, sys, urllib.request, urllib.parse, urllib.error

def read(path):
    return json.loads(pathlib.Path(path).read_text(encoding='utf-8-sig'))

def write(path, value):
    p=pathlib.Path(path)
    # Refuse overwriting a previous backup; create a new timestamped directory instead.
    p.parent.mkdir(parents=True,exist_ok=True)
    raw=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'wb') as f: f.write(raw)
    return hashlib.sha256(raw).hexdigest()

def verify(value):
    catalog=value['state']['catalog'] if value.get('format')=='miflix-worker-backup' else value
    if type(catalog.get('schema_version')) is not int or catalog['schema_version']!=1 or not isinstance(catalog.get('items'),list):
        raise ValueError('Se requiere schema_version numérico 1 e items')
    ids=[x.get('id') for x in catalog['items']]
    if any(not isinstance(x,str) or not x for x in ids) or len(set(ids))!=len(ids): raise ValueError('IDs vacíos/duplicados: revisar antes de migrar')
    collections=value['state']['collections']['colecciones'] if value.get('format')=='miflix-worker-backup' else catalog.get('colecciones',[])
    return {'items':len(ids),'colecciones':len(collections),'schema_version_type':'number'}

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args): raise ValueError('Redirección rechazada para proteger Authorization')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=['verify','prepare-import','public-backup','backup','read-catalog','read-collections','migrate','restore','catalog','collections'])
    p.add_argument('--base',default='http://127.0.0.1:8787')
    p.add_argument('--allow-remote',action='store_true')
    p.add_argument('--input');p.add_argument('--collections');p.add_argument('--output');p.add_argument('--etag');p.add_argument('--operation-id')
    a=p.parse_args()
    if a.action=='verify':
        data=read(a.input);summary=verify(data);summary['sha256']=hashlib.sha256(pathlib.Path(a.input).read_bytes()).hexdigest();print(json.dumps(summary));return
    if a.action=='prepare-import':
        catalog=read(a.input);verify(catalog)
        body={'catalogo':catalog,'confirm':'IMPORTAR'}
        if a.collections:body['colecciones']=read(a.collections)
        write(a.output,body);print('Importación preparada localmente; no publicada');return
    base=urllib.parse.urlsplit(a.base)
    if base.username or base.password or base.query or base.fragment or base.path not in ('','/'):
        raise ValueError('Base debe ser un origen, sin credenciales, ruta ni query')
    local=base.hostname in ('127.0.0.1','localhost','::1')
    if not local and (not a.allow_remote or base.scheme!='https'): raise ValueError('Remoto exige HTTPS y --allow-remote tras autorización')
    token=os.environ.get('CATALOG_ADMIN_TOKEN','')
    if a.action!='public-backup' and not token:raise ValueError('Falta CATALOG_ADMIN_TOKEN en entorno privado')
    routes={'read-catalog':('GET','/admin/catalogo'),'read-collections':('GET','/admin/colecciones'),'public-backup':('GET','/catalogo.json'),'backup':('GET','/admin/respaldo'),'migrate':('POST','/admin/migracion'),'restore':('POST','/admin/restaurar'),'catalog':('PUT','/admin/catalogo'),'collections':('PUT','/admin/colecciones')}
    method,route=routes[a.action];headers={'Accept':'application/json'}
    if a.action!='public-backup':headers['Authorization']='Bearer '+token
    body=None
    if method!='GET':
        data=read(a.input)
        if a.action=='restore':data={'backup':data,'confirm':'RESTAURAR'}
        body=json.dumps(data,ensure_ascii=False).encode();headers['Content-Type']='application/json'
        if not a.operation_id: raise ValueError('Guardar y suministrar --operation-id para reintentos idénticos')
        headers['Idempotency-Key']=a.operation_id
        if a.action=='migrate':headers['If-None-Match']='*'
        else:
            if not a.etag:raise ValueError('Suministrar --etag exacto de la lectura actual')
            headers['If-Match']=a.etag
    request=urllib.request.Request(a.base.rstrip('/')+route,data=body,headers=headers,method=method)
    with urllib.request.build_opener(NoRedirect).open(request,timeout=60) as response:
        data=json.load(response);etag=response.headers.get('ETag')
    if a.output:
        digest=write(a.output,data)
        write(a.output+'.integrity.json',{'sha256':digest,'etag':etag,**(verify(data) if method=='GET' and a.action!='read-collections' else {})})
    print(json.dumps({'ok':True,'etag':etag,'saved':bool(a.output)}))

if __name__=='__main__':
    try:main()
    except urllib.error.HTTPError as error:
        print('Solicitud rechazada: HTTP '+str(error.code)+'; no se imprime el cuerpo',file=sys.stderr);sys.exit(1)
    except (ValueError,OSError,KeyError,TypeError,urllib.error.URLError) as error:
        # Never print exception values that could contain tokens or URLs.
        print('Operación fallida ('+type(error).__name__+'); revisar argumentos, archivo y conexión',file=sys.stderr);sys.exit(1)

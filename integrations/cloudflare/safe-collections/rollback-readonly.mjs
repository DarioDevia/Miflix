// Same Durable Object namespace/class/name: preserves authoritative state on rollback.
import worker from './worker.mjs';
export {CatalogCoordinator} from './worker.mjs';
export default {
  async fetch(request,env) {
    if(!['GET','OPTIONS'].includes(request.method)) {
      // Let the main entry authenticate administrators before returning a denial.
      const headers=new Headers(request.headers);
      const probe=new Request(new URL('/admin/respaldo',request.url),{headers});
      const auth=await worker.fetch(probe,env);
      if(auth.status === 401) return auth;
      return Response.json({ok:false,error:'Mantenimiento: escrituras temporalmente bloqueadas'}, {status:503,headers:{'Cache-Control':'no-store','Access-Control-Allow-Origin':'*'}});
    }
    return worker.fetch(request,env);
  }
};

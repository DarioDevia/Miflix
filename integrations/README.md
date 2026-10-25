## Nota de 0.1.4: logos TMDB

La única adaptación nueva es admitir `/movie/{id}/images` y `/tv/{id}/images` en la allowlist del relay TMDB existente. Pruebas de rutas, autenticación y origen fijo incluidas. **No desplegado**. La fuente exacta de producción no fue suministrada: aplicar sólo ese cambio equivalente al relay vigente si rechaza esas rutas. No instalar la extensión histórica completa ni habilitar checkpoint compartido como parte de 0.1.4. La publicación del catálogo no necesita cambios. Un relay viejo permite el resto de metadata y backdrop, pero Admin omite logo si recibe error. Token TMDB opcional directo en Android también está soportado, sin credenciales nuevas incorporadas.

El resto de este documento describe antecedentes optativos anteriores, fuera del alcance de 0.1.4.

# Integraciones optativas · checkpoint compartido y TMDB

No están desplegadas. Se entregan como una extensión revisable del Worker vigente y un puente de la fuente Windows 1.13.0. No requieren Durable Objects, otra base de datos ni cambios de schema público.

## Cloudflare

1. Obtené la fuente exacta del Worker actualmente publicado. **No sustituyas ese Worker por el ZIP archivado**, que tiene un validador diferente del catálogo vigente.
2. Copiá `cloudflare/admin-extras.js` junto al entrypoint vigente e importá:

```js
import { handleAdminExtras } from './admin-extras.js';
```

3. Al comienzo de su `fetch(request, env, ctx)`, antes del enrutamiento existente, agregá:

```js
const extra = await handleAdminExtras(request, env, env.TU_BINDING_KV_EXISTENTE);
if (extra) return extra;
```

Reemplazá `TU_BINDING_KV_EXISTENTE` por el binding real. El tercer argumento es el namespace KV ya utilizado por el catálogo. Si se llama `CATALOGO_KV`, se puede omitir. Las rutas ajenas a esta extensión devuelven `null` y siguen al Worker original. No modificar `/catalogo.json`, `/admin/catalogo`, su clave KV ni validación original.

4. La extensión espera `env.CATALOG_ADMIN_TOKEN`: mapear la misma credencial administrativa existente si tiene otro nombre, sin pegarla en fuentes. Agregá `TMDB_BEARER_TOKEN` como **secret** de Cloudflare (token de lectura API de TMDB). Nunca como variable pública ni archivo Git. Hacerlo en el panel o con `wrangler secret put` que solicita entrada privada.
5. Ejecutá las pruebas Node y validá en un Worker de prueba antes de desplegar sobre el vigente. Tras desplegar, comprobar que el catálogo público y su PUT administrativo mantienen el comportamiento anterior.

Rutas nuevas, todas con `Authorization: Bearer <credencial administrativa>`:

| Ruta | Método | Función |
|---|---|---|
| `/admin/checkpoint` | GET | `{ok:true,checkpoint:null}` o último avance privado |
| `/admin/checkpoint` | PUT | Valida publicación de película y guarda en clave KV `estado_admin`; respuesta `{ok:true}` |
| `/admin/metadata/tmdb/…` | GET | Relay con endpoints TMDB fijos, origen fijo, parámetros limitados y credencial de proveedor sólo en servidor |

Checkpoint mínimo: `tipo`, `titulo`, `anio`, `tmdb_id`, `telegram_message_id`, `telegram_url`, `procesado_at`; el servidor añade `recibido_at`. La URL completa y el momento de procesamiento se conservan. Máximo 8 KiB. No escribe el catálogo. No loguea headers, cuerpos ni secrets. El catálogo y checkpoint son dos escrituras independientes; si sólo falla la segunda, Android conserva ese pendiente y reintenta sin repetir el catálogo. El relay puede tener límites de cuota de Cloudflare/TMDB y no implementa rate limiting propio.

KV tiene consistencia eventual: después de publicar, esperar y verificar la lectura antes de alternar dispositivos. Esta arquitectura es deliberadamente secuencial. No garantiza exclusión mutua ni protección frente a escritores simultáneos.

## Windows 1.13.0

El ejecutable validado previamente no incluye checkpoint remoto. El puente necesita recompilar y validar una copia nueva; no cambia silenciosamente ese ejecutable.

1. Copiar la carpeta `source` de `AgenteVideoteca_entrega1.13.0-diagnostico` a una carpeta de trabajo.
2. Aplicar desde esta entrega:

```sh
python integrations/windows/aplicar_integracion.py RUTA_A_LA_COPIA/source
```

El instalador comprueba SHA-256 de los tres archivos revisados antes de escribir. Si difieren, se detiene sin modificar. Conserva backups en `source/app/backup_checkpoint_1.13.0` y añade `checkpoint_compartido.py`.

3. Recompilar según `dist/COMPILAR_EN_WINDOWS.txt` de esa entrega. Repetir sus pruebas y la alternancia con Android. No se entregó ni probó un EXE recompilado aquí.

Cambios concretos: al descargar valida y trae checkpoint antes de habilitar el catálogo; al publicar envía avance pendiente; la huella de cambios de Windows incluye checkpoint para advertir también cuando se conserva una película duplicada. Un avance local pendiente no se reemplaza por una lectura remota. Un checkpoint legado no se publica silenciosamente sobre un remoto nuevo: primero descargar. Si el remoto está vacío, conserva el avance legado y lo marca para publicación explícita. Si falta la extensión, la copia integrada de Windows reporta error y no habilita edición; instalar primero la extensión.

La API pública y las fichas de Mobile/TV no contienen checkpoint privado. No usar rutas nuevas con credenciales por query string ni compartir archivos de configuración.

## Pruebas

```sh
node --test integrations/cloudflare/admin-extras.test.mjs
python integrations/windows/test_checkpoint_compartido.py
```

Las pruebas usan mocks. El instalador también se aplicó a una copia limpia de la fuente revisada y se comprobó sintaxis Python. La alternancia real, el despliegue y la ejecución del EXE permanecen pendientes de validación manual.

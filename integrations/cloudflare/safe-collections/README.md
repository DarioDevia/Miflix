# MiFlix Worker: colecciones seguras, candidato para ensayo

**Implementado y probado localmente. No desplegado. No se accedió al catálogo ni al KV de producción. Admin Android continúa con colecciones locales hasta su integración futura.**

Fecha: 31/10/2026. Base del repositorio: `e5a52976e6ff909401fa8516bcffd23f88d08e1c`. Rama de entrega: `feature/worker-safe-collections`. Los archivos nuevos están aislados en `integrations/cloudflare/safe-collections/`; no cambian ninguna aplicación.

## Archivos y ejecución

- `worker.mjs`: Worker completo, autónomo, incluida clase `CatalogCoordinator`; es el punto de entrada real. No necesita importar archivos auxiliares.
- `original/worker.mjs`: fuente completa original normalizada para retirar escapes de pegado. Corresponde al fixture recuperado en el commit base; incorpora el mismo `ignoreBOM:false` explícito del mensaje del propietario. No se afirma una comparación contra el despliegue remoto.
- `test/regression.test.mjs`: regresiones con Request/Response y SQLite real de Node, fallos inyectados y datos sintéticos.
- `test/runtime.test.mjs`: Worker completo en Miniflare/workerd con Durable Object SQLite, concurrencia y reinicio persistente.
- `reference/`: esquema original y parsers Mobile 0.9.13 / TV 0.1.13 conservados para revisión; no son cambios de clientes.
- `admin-tool.py`: exportación, verificación de integridad, preparación de importación y peticiones condicionadas; Python 3.10+, biblioteca estándar.
- `rollback-readonly.mjs`: punto de entrada de mantenimiento que conserva el estado del coordinador y bloquea escrituras.
- `wrangler.toml`: plantilla **solo ensayo**, con namespace KV ficticio. Ningún secreto.

Requisitos: Node 24+, npm, Python 3.10+; `node:sqlite` requiere Node 24 en estas pruebas. Desde esta carpeta:

```sh
npm ci
npm run check
npm test
python3 test/admin-tool.test.py
npm run build:check
```

`build:check` ejecuta únicamente `deploy --dry-run`; empaqueta, no publica ni crea recursos. No hay script automático de despliegue real. Versiones fijadas y lockfile incluidos. Se usó Miniflare 4.20260730.0 / Wrangler 4.27.0, con fecha de compatibilidad 2026-07-01: no se afirma que sean las últimas versiones.

## Diagnóstico y decisión

El original reemplaza `catalogo` con `CATALOGO_KV.put` y no tiene ETag, control condicional ni validación de colecciones. Una publicación antigua puede borrarlas incluso sin intención. El original exige **`schema_version === 1`, número**; el nuevo código conserva esa condición. No acepta `"1"`. Los parsers de clientes convierten la versión con `asString`, pero eso no cambia el contrato de publicación del servidor.

| Diseño | Compatibilidad / costo | Garantía y limitación |
|---|---|---|
| Único JSON KV | Simple, mismo almacenamiento | Un PUT antiguo elimina colecciones; lectura/comparación/PUT no es CAS |
| Otra clave KV para colecciones | Separa colecciones de publicaciones antiguas, suma lecturas/escrituras | Consistencia eventual; no resuelve dos escritores ni una instantánea coherente entre claves |
| Coordinador Durable Object SQLite, documentos lógicamente separados | Nuevo binding, una instancia; disponible en Workers Free dentro de sus cuotas | Transacción para revisión, contenido, historial e idempotencia; lectura pública coherente del mismo estado |

Se elige la tercera. **No se utiliza KV como autoridad tras migrar ni se mantiene un espejo automático**. Esto evita una doble escritura DO/KV sin transacción y el límite de escrituras KV por clave. `CATALOGO_KV` se conserva como lectura anterior a migración y respaldo legado intacto. Una segunda clave KV no aporta garantía adicional aquí. Catálogo y colecciones son campos separados en estado transaccional; nunca se acepta una colección desde PUT audiovisual. La respuesta pública los compone sin reconstruir registros audiovisuales.

Todas las solicitudes de estado van al mismo objeto `idFromName('miflix-catalog-v1')`. El servidor usa `ctx.storage.transactionSync` y SQL síncrono; no hay lecturas KV ni fetch de red dentro de una transacción. Escribe JSON fragmentado en filas de como máximo 128 KiB UTF-8. No depende de un mutex en memoria. Estado, revisión, respaldo anterior y resultado de operación se confirman juntos. Las lecturas previas a migrar siguen sujetas a la consistencia eventual del KV original.

Este diseño prioriza exactitud para la videoteca familiar. Cada lectura pública consulta el coordinador; tiene mayor latencia y menor capacidad de escala que el catálogo servido directamente desde KV. Un fallo del coordinador devuelve 503: **no sirve silenciosamente un KV viejo**. Si el tráfico crece, habrá que medir y diseñar caché/versiones inmutables como otro trabajo.

## Contrato administrativo

Todas las rutas `/admin/*`, incluido relay, exigen `Authorization: Bearer` con el mismo secreto existente `CATALOG_ADMIN_TOKEN`. Se valida antes de leer cuerpos o contactar al coordinador. Tokens ausentes o incorrectos: 401. No hay secretos en URL ni código. Los nuevos campos CORS permiten `If-Match`, `If-None-Match`, `Idempotency-Key` y exponen los ETags.

| Método y ruta | Contrato |
|---|---|
| GET `/health` | Conserva `ok`, `servicio`, `catalogo_guardado`, `elementos`; agrega estado de migración o revisión |
| GET `/`, `/catalogo.json` | Después de migrar: catálogo íntegro más `colecciones_version:1`, colecciones visibles ordenadas por `orden`; ETag de estado, X-Catalogo-ETag y X-Colecciones-ETag |
| GET `/admin/catalogo` | Documento audiovisual completo y ETag `"catalogo-N"` |
| PUT `/admin/catalogo` | Mismo envelope original numérico 1 / items; preserva propiedades raíz desconocidas que falten en el envío. `items` sigue siendo reemplazo completo intencional. Ignora `colecciones` y `colecciones_version` recibidos |
| GET `/admin/colecciones` | `{format:"miflix-collections",version:1,colecciones:[...],revision:N}`, incluidas ocultas; ETag `"colecciones-N"` |
| PUT `/admin/colecciones` | Operaciones aditivas o borrados explícitos; exige If-Match e Idempotency-Key |
| POST `/admin/migracion` | Solo una vez, `If-None-Match: *`, Idempotency-Key, `{confirm:"IMPORTAR",catalogo:objeto,colecciones?:respaldo-local}` |
| GET `/admin/respaldo` | Estado completo actual, incluso ocultas; ETag `"estado-N"` |
| GET `/admin/respaldo?revision=N` | Respaldo previo retenido, 404 si expiró |
| POST `/admin/restaurar` | `{confirm:"RESTAURAR",backup:respaldo-completo}`, If-Match del **estado actual**, Idempotency-Key. Aumenta revisiones; nunca vuelve atrás los contadores |

Publicación de colecciones:

```json
{
  "format": "miflix-collections-operations",
  "version": 1,
  "upsert": [{
    "id": "manual-saga-1", "nombre": "Saga de prueba", "origen": "manual",
    "orden": 0, "visible": true, "titulos": ["id-pelicula-existente"]
  }],
  "delete_ids": [],
  "confirm_delete": []
}
```

`upsert` crea o mezcla por ID estable, conserva propiedades desconocidas anteriores y valida el objeto resultante completo. Al crear se exigen todos los campos del esquema. No cambiar un ID para renombrar. Omitir una colección **no** la elimina. Un borrado requiere `delete_ids` y `confirm_delete` idénticos, en el mismo orden; todos los IDs deben existir. No se admiten IDs repetidos ni alta y baja simultáneas del mismo ID. No existe reemplazo masivo implícito; la única restauración completa es un endpoint aparte con confirmación, revisión global y respaldo previo.

La validación respeta `reference/colecciones.schema.json`: formato/version 1, 1000 colecciones, 10000 referencias únicas por colección, nombres, tipos, orden, visibilidad, URLs HTTPS y TMDB ID obligatorio para origen tmdb. Se conserva la metadata desconocida y las referencias a títulos temporalmente ausentes. No se duplican películas. Como restricción adicional se rechazan credenciales en URLs e imágenes de dominios thetvdb.com. No hay consultas ni enriquecimiento TheTVDB en esta API; los campos adicionales son opacos, no un filtro universal de procedencia.

Límites: documento de colecciones completo 2 MiB; catálogo compuesto público 5 MiB UTF-8; catálogo audiovisual también 5 MiB. Se verifica el estado resultante dentro de la transacción. El lector acota el cuerpo por streaming: operaciones de colecciones 2 MiB, importación/restauración hasta 7 MiB + 64 KiB de envoltura. Una raíz desconocida puede ser preservada y hacer exceder el límite: se rechaza, no se trunca. Colecciones ocultas permanecen en el respaldo administrativo y cuentan en su presupuesto de 2 MiB.

Errores: 400 formato/tipo/confirmación inválidos; 401 autenticación; 404 ruta o colección/borrado/respaldo inexistente; 405 método; 409 migración ya realizada o clave de operación reutilizada con otro contenido; 412 revisión obsoleta; 413 tamaño; 428 falta condición o clave de operación; 503 almacenamiento o migración pendiente. Un 503/timeout no confirma que no se guardó: consultar o repetir exactamente con la misma clave.

La clave de idempotencia tiene 8–128 caracteres `[A-Za-z0-9._:-]`. Se guarda con una huella SHA-256 de ruta, cuerpo JSON y condiciones; repetir la misma solicitud devuelve el mismo resultado **antes** de revisar If-Match. Otro cuerpo con esa clave da 409. El orden de propiedades JSON debe conservarse en el reintento. Se retienen operaciones de las últimas 1000 revisiones más la actual; después una petición estricta antigua falla por ETag obsoleto. No regenerar clave ante un timeout ambiguo. Tras una modificación posterior, un reintento puede devolver su éxito histórico, no el estado actual: volver a leer.

## Administradores antiguos: elección explícita

La plantilla usa `REQUIRE_CATALOG_IF_MATCH = "true"`. PUT audiovisual exige revisión exacta e Idempotency-Key. Los administradores que aún no los envían recibirán 428; no se puede prometer compatibilidad total con publicación ciega y a la vez rechazo de catálogos obsoletos. No se ha modificado ninguna APK.

Para un ensayo de compatibilidad se puede configurar explícitamente `"false"`. PUT audiovisual sin condiciones sigue admitido después de migrar y nunca altera colecciones, estén omitidas o enviadas como copia vieja. Devuelve `legacy_unconditional:true` y cabecera `X-Legacy-Unconditional:true`. Si se proporciona If-Match incluso en este modo, se verifica. Las colecciones siempre exigen revisión e idempotencia; el modo legado no relaja esos endpoints.

**Riesgo restante del modo legado:** dos catálogos audiovisuales sin revisión se serializan pero el último reemplaza items; una copia vieja puede quitar títulos/cambios nuevos. Sin clave de operación, tampoco se reconoce un reintento ambiguo. No hay forma de inferir qué items faltantes se borraron a propósito. No usar ese modo afirmando protección total contra conflictos. El modo estricto es la opción predeterminada segura; habilitar compatibilidad es una decisión operativa consciente.

## Relay TMDB

Se conserva el relay original, autenticación, parámetros restringidos, límite de 5 MiB, timeout 20 s, redirecciones prohibidas, errores seguros y filtro contra eco de credenciales. Se agregan únicamente:

- `/admin/metadata/tmdb/search/collection?query=...&language=es-ES` (también include_adult booleano).
- `/admin/metadata/tmdb/collection/ID?language=es-ES`.
- `/admin/metadata/tmdb/collection/ID/images` (JSON de imágenes; no proxy de archivos de imagen).

ID positivo acotado a 16 dígitos como las rutas originales. No se habilitan rutas arbitrarias, búsqueda de personas, api_key, URL upstream ni parámetros desconocidos/duplicados. Las rutas originales movie/tv, credits, external_ids, videos, images y season se conservan. No se agrega paginación ni append_to_response en esta entrega.

## Compatibilidad de lectores

Revisados `Catalog.kt` de Mobile 0.9.13 (`092ece114c6798d891f50e15808cd0d8250d163f`) y TV 0.1.13 (`152701cf8f57eb72e73b57e99825f63b82d76288`): leen `items`, convierten versión mediante `asString` y deserializan con Gson en un modelo sin `colecciones`. Los campos nuevos son aditivos y no cambian registros. Las pruebas verifican preservación profunda de items, episodios, enlaces y propiedades desconocidas; también inspeccionan esas fuentes de referencia.

**Límite:** esa revisión no es una ejecución de los parsers Kotlin ni una instalación física de las APK. La compatibilidad se fundamenta en el código; su validación real contra staging en teléfono y TV permanece pendiente. No se afirma que los clientes ya muestren colecciones.

## Respaldo previo a cualquier cambio real

Nada de esta sección se ejecutó sobre producción. Antes de autorizar una migración:

1. Congelar publicaciones en Windows, Android, scripts y cualquier escritor externo. No desplegar porcentajes mezclando el Worker viejo y el nuevo. La coexistencia de otro escritor KV invalida una toma de base consistente.
2. Exportar el **código/configuración actuales** desde Cloudflare y registrar versión de despliegue, nombre del Worker, namespace/bindings y nombres de secretos, nunca sus valores. El original adjunto no prueba por sí solo identidad de despliegue.
3. Obtener copia pública del catálogo y exportar directamente el valor `catalogo` del namespace correcto desde Cloudflare (dashboard o Wrangler). Con escritores detenidos, comparar JSON profundo/hash y cantidades. Una lectura pública aislada puede venir de caché KV; no usarla como única evidencia. Conservar metadata de la clave por separado.
4. Exportar colecciones locales de Admin Android y resguardos de los demás dispositivos, revisar diferencias y unirlas explícitamente; no escoger el último archivo por fecha sin comparar IDs. El Worker no puede recuperar colecciones que nunca salieron del teléfono.
5. Guardar copias privadas fechadas en dos destinos distintos. El JSON puede contener enlaces privados; no subirlo al repositorio. Calcular SHA-256, contar items, IDs únicos y colecciones. Verificar no solo cantidad, también campos y enlaces mediante comparación profunda.
6. Preparar la importación local, verificarla en staging y mantener pausados los escritores hasta la comprobación final.

`admin-tool.py` no permite remoto salvo `--allow-remote` y HTTPS. No sigue redirecciones con Authorization. Usa el token desde el entorno privado, no lo imprime ni lo guarda; no pegar valores reales en comandos del historial ni en documentación. Escribe archivos con creación exclusiva para no pisar respaldos existentes. No se suministra ningún secreto.

Ejemplos **locales** (para remoto se necesita autorización y la opción explícita):

```sh
python3 admin-tool.py public-backup --output backups/antes/catalogo.private.json
python3 admin-tool.py verify --input backups/antes/catalogo.private.json
python3 admin-tool.py prepare-import --input backups/antes/catalogo.private.json --collections colecciones-locales.private.json --output importacion.private.json
python3 admin-tool.py migrate --input importacion.private.json --operation-id migracion-ensayo-001 --output backups/migracion/resultado.private.json
python3 admin-tool.py backup --output backups/despues/estado.private.json
python3 admin-tool.py read-collections --output backups/despues/colecciones.private.json
```

Para solicitudes administrativas se configura `CATALOG_ADMIN_TOKEN` en el entorno privado del proceso. Si solo hay catálogo sin colecciones, omitir `--collections`; el servidor preserva las incluidas en el catálogo si las hubiera. Si se suministran ambas fuentes y difieren, rechaza la importación. Un archivo de operaciones no equivale a un respaldo de colecciones.

Cada exportación del cliente genera `.integrity.json` con SHA-256 y ETag. Verificar el hash contra el archivo antes de restaurar; el servidor valida formato pero no autentica criptográficamente el origen de un respaldo. No modificar respaldos a mano. El hash detecta cambios, no prueba confianza del autor.

## Ensayo local y despliegue posterior autorizado

### Local, sin recursos Cloudflare

Ejecutar `npm run dev`. Crear `.dev.vars` privado con token **sintético** para el ensayo y, solo si se probará TMDB real, una credencial de ensayo fuera del repositorio. Las pruebas automatizadas no necesitan este archivo ni ninguna cuenta Cloudflare. Importar un catálogo sintético mediante `/admin/migracion` como en el test runtime; no usar el catálogo real como banco de pruebas. El namespace ficticio de la plantilla solo permite uso local/dry-run.

### Staging remoto, solo después de autorizarlo

1. Autenticarse localmente con Wrangler sin compartir credenciales. Desde esta carpeta, crear **un KV de ensayo nuevo**: `npx wrangler kv namespace create CATALOGO_KV`. Copiar su ID a `wrangler.toml`, nunca el de producción.
2. Mantener el nombre `miflix-collections-test`. La declaración `new_sqlite_classes` crea la clase SQLite cuando se publique el Worker; no crear un DO legacy con almacenamiento KV. Verificar en cuenta que se puede usar el plan gratuito.
3. Configurar secretos para este Worker: `npx wrangler secret put CATALOG_ADMIN_TOKEN` y `npx wrangler secret put TMDB_BEARER_TOKEN`; se ingresan por prompt privado. Si el Worker aún no existe, publicar primero la plantilla sin secretos (admin falla cerrado) y luego agregar secretos. No escribirlos en TOML ni `.env` compartido.
4. Ejecutar `npm run build:check`, luego **únicamente con autorización de staging** `npx wrangler deploy`. Esto sí crea recursos/publica.
5. Importar datos sintéticos; repetir migración, publicaciones paralelas, 412, reintento, respaldo/restauración y límites. Probar Mobile/TV apuntando al catálogo de staging sin modificar sus fuentes. No activar sincronización Android todavía.

### Producción, requiere otra autorización explícita

1. Completar respaldos y detener todos los escritores; registrar cuentas/namespaces/versiones. Mantener el binding `CATALOGO_KV` y secretos actuales. Preparar una configuración separada usando el **nombre real existente**; no ejecutar la plantilla de staging contra producción por accidente.
2. Agregar binding `CATALOG_COORDINATOR`, clase SQLite y tag de migración sin colisionar con tags ya usados; conservar todas las configuraciones existentes (rutas, dominio, compatibilidad, límites, observabilidad) revisadas en Cloudflare. La plantilla no reemplaza ciegamente esa configuración.
3. Elegir explícitamente estricto o compatibilidad. El estricto bloqueará publicación de administradores instalados hasta integrar el protocolo. No hacer un cambio silencioso de ese comportamiento.
4. Publicar en ventana de mantenimiento, sin despliegue gradual. Antes de importar, lecturas públicas usan KV original y escrituras están bloqueadas. Verificar `/health` muestra migración pendiente.
5. Importar la copia previamente validada con `If-None-Match:*`, confirmación y clave de operación persistida. Guardar resultado y un respaldo DO completo. Si falla o se pierde la respuesta, repetir exactamente con la misma clave; no hacer una importación nueva ciega.
6. Comparar respuesta pública y respaldo con la copia: items y todas sus propiedades deben ser idénticos; raíz desconocida preservada; colecciones visibles públicas y ocultas administrativas correctas. Consultar `/health` y probar cargas de lectura Mobile/TV. Solo entonces reanudar escritores compatibles con el modo elegido.
7. A partir de allí, **DO es autoridad**. El KV inicial queda congelado y ya no representa cambios nuevos. Documentar esta transición para no restaurar una copia vieja accidentalmente.

## Recuperación y reversión

En cada escritura se guarda el estado anterior en la misma transacción; se conservan diez revisiones previas, además del estado actual. Los reintentos no consumen otra revisión ni otro respaldo. Ese historial comparte infraestructura con el estado y **no reemplaza exportaciones externas periódicas**. Una escritura se rechaza si el respaldo/commit falla. Almacenar fuera de Cloudflare una exportación antes de cada mantenimiento y tras cambios importantes.

Restauración transaccional normal:

1. Bloquear publicaciones y exportar `/admin/respaldo` actual, con ETag y hash.
2. Elegir un respaldo privado completo verificado, no solo el catálogo público (omite colecciones ocultas). GET de una revisión retenida también es válido; exportarla externamente.
3. POST `/admin/restaurar` con ETag **actual**, clave de operación y confirmación. Con el cliente: `python3 admin-tool.py restore --input respaldo.private.json --etag '"estado-N"' --operation-id restaurar-ensayo-001 --output resultado-restauracion.private.json`.
4. Descargar nuevo respaldo, comparar datos profundos y verificar revisiones crecientes. No cambiar IDs, enlace Telegram ni metadatos al restaurar. Una restauración completa repone de forma deliberada TODO el contenido del respaldo; se debe revisar su alcance antes de confirmar.

Reversión operativa segura: conservar el mismo Worker, binding, clase DO y nombre singleton; cambiar el punto de entrada a `rollback-readonly.mjs` y publicar la configuración revisada. Validar primero con `npx wrangler deploy rollback-readonly.mjs --dry-run --outdir .dry-run-rollback`. Mantiene lectura actual y relay GET; devuelve 503 en escrituras. Es un modo de mantenimiento, no una corrección para cualquier fallo posible del almacenamiento o de lectura. Una versión anterior del código seguro compatible con el mismo estado puede sustituirlo tras ensayo.

**No pulsar rollback al Worker original sin preparar datos.** El original leerá el KV congelado y no verá publicaciones posteriores a la migración. Para volver realmente al original: congelar todas las escrituras, exportar el estado DO más reciente fuera de Cloudflare, componer un catálogo completo con todas las colecciones (incluso ocultas si el contrato legado lo requiere), escribirlo de forma controlada en KV mediante el procedimiento de Cloudflare, verificar propagación y contenido desde varios puntos, y recién cambiar versión. Conservar DO y respaldos; no eliminar namespace ni ejecutar migración de borrado. El Worker original vuelve a ser vulnerable a pérdida de colecciones: no reanudar escritores antiguos hasta tener mitigación. Si DO no se puede leer, recuperar desde un respaldo externo y reconocer explícitamente el intervalo de datos que falta; no fingir recuperación sin pérdida.

## Recursos, costos y límites restantes

Cloudflare publica disponibilidad de Durable Objects SQLite en Workers Free. No se creó ni contrató nada. Referencia actual: 100000 solicitudes DO/día y 13000 GB-s/día de cómputo gratuito; también hay cuotas SQLite/Workers/KV y almacenamiento. En Free exceder cuotas genera errores, no capacidad ilimitada. Cada lectura pública consume Worker + DO y lecturas SQL; cada publicación almacena un snapshot anterior fragmentado, por lo que consume varias filas escritas. Hay que verificar cuotas de la cuenta antes de publicar; no se estima factura sin tráfico y plan reales. No es necesario contratar Paid para este candidato de bajo tráfico si entra en Free.

No se protege contra quien tenga el token administrativo y confirme una restauración/destrucción válida, ni contra escrituras directas externas en la infraestructura. Compartir un único token mantiene el modelo actual, no añade permisos por persona ni auditoría de identidad. No hay sincronización de clientes aún, resolución automática de conflictos, merge semántico de items ni cifrado de respaldos por esta herramienta. Backups completos pueden incluir colecciones ocultas y enlaces privados: mantenerlos privados. La referencia de tamaño cuenta caracteres con límites conservadores JS para textos; la cuota en bytes sí es UTF-8 exacta.

## Integración futura Android

1. Guardar respaldo local antes de sincronizar. Leer `/admin/colecciones` y conservar documento base y ETag.
2. Comparar base, cambios locales y versión remota por ID estable. La ausencia local no se convierte en borrado. Mostrar altas, cambios y bajas explícitas; conservar miembros manuales y campos desconocidos.
3. Ante conflicto en el mismo campo, pedir elección; cambios disjuntos se pueden proponer como mezcla revisada. No usar “último guardado gana” por hora del teléfono.
4. Construir operaciones `upsert/delete_ids/confirm_delete`. Guardar cuerpo exacto, ETag y clave de operación antes de enviar.
5. Enviar PUT condicionado. 412: descargar remoto y reconciliar; no reemplazar simplemente el ETag y reenviar la copia vieja. 428: corregir protocolo; 400/413: revisar contenido.
6. Timeout/503: conservar la operación y repetir idéntica; mismo ID con otro contenido da 409. Confirmación exitosa: volver a leer remoto, guardar nueva base/ETag y respaldo local. No perder cambios locales pendientes si hubo otros cambios remotos.
7. Publicación audiovisual estricta hace el mismo ciclo con GET/PUT `/admin/catalogo` y su ETag independiente. No usar el ETag de la respuesta pública de estado como ETag de catálogo.

## Fuentes oficiales consultadas

- https://developers.cloudflare.com/kv/concepts/how-kv-works/ — consistencia eventual, demoras entre regiones.
- https://developers.cloudflare.com/kv/api/write-key-value-pairs/ — sobrescritura concurrente y límites por clave.
- https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/ — SQL transaccional, transactionSync y output gates.
- https://developers.cloudflare.com/durable-objects/platform/pricing/ — disponibilidad Free y cuotas; consultar otra vez antes de despliegue.
- https://developer.themoviedb.org/reference/search-collection
- https://developer.themoviedb.org/reference/collection-details
- https://developer.themoviedb.org/reference/collection-images

Documentación consultada para arquitectura y rutas; ninguna prueba hizo llamadas TMDB reales ni accedió a producción.

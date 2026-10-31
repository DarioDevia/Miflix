# Colecciones: evaluación de sincronización 0.1.9

**No hay despliegue ni sincronización habilitada.** Este directorio contiene pruebas ejecutables del Worker fuente recuperado y el contrato que debe cumplir una implementación futura. No contiene un servidor nuevo listo para producción. La APK 0.1.9 conserva colecciones locales y muestra el estado pendiente. No hace sondeos ni PUT de colecciones al Worker real.

## Evidencia reproducible

`node --test integrations/cloudflare/collections/audit.test.mjs` ejecuta 15 casos con datos sintéticos, `Request`/`Response` reales de Node y KV en memoria. `fixtures/worker-0.1.4.mjs` es una copia exacta de `worker.js` del ZIP original, no un reemplazo propuesto. ZIP SHA-256 `1d24f44bc4de6251363f0f9628ca9380b3c68d439b9015ec0f7e2a0453e4cdc3`. El código de producción existente y el relay no se modifican.

Los casos `BLOCKER` pasan cuando reproducen el defecto: una subida sin `colecciones` las borra; una copia antigua las sobrescribe; `If-Match` se ignora; no hay ETag, PATCH ni endpoint específico; no se valida el contrato de colecciones. Los otros casos comprueban autenticación, límite de cuerpo de 5 MiB, conservación de propiedades desconocidas si se envía el objeto completo, respuestas públicas, referencias ausentes, y fallo de escritura en el KV simulado. No equivalen a una prueba del despliegue real, de consistencia distribuida ni de recuperación remota.

La prueba de lectura/revisión/escritura concurrente demuestra además por qué añadir una propiedad `revision` a un objeto de KV no basta: dos peticiones pueden leer la misma versión y aprobar ambas escrituras. Un mutex en memoria del Worker solo cubre un isolate y tampoco basta.

## Comparación de opciones

| Opción | Ventaja | Riesgo / condición |
|---|---|---|
| Colecciones dentro de la misma raíz KV | JSON público único y un solo objeto | Un PUT antiguo puede borrar/sobrescribir; requiere preservar del lado servidor y arbitrar todas las escrituras, no solo agregar el campo |
| Otra clave KV para colecciones y composición pública | Separa operaciones; publicaciones audiovisuales antiguas no deben tocar colecciones | KV por sí solo sigue sin transacción ni CAS; lectura de dos claves puede mezclar revisiones y violar límite conjunto |
| Un coordinador transaccional y colección independiente, con composición pública | Impide eliminación por escritor antiguo; revisión y respaldo se pueden confirmar atómicamente | Requiere binding/configuración/migración y prueba del servidor real; no existe en las fuentes actuales |

Se recomienda la tercera alternativa como siguiente implementación del servidor, manteniendo la respuesta pública unificada. Un Durable Object SQLite de Cloudflare es una alternativa mínima **a evaluar**, dentro de la plataforma actual; no se crea aquí ni se presupone habilitado. La documentación actual indica disponibilidad en Workers Free con límites y errores al excederlos. No se contrata un servicio ni plan de pago. La necesidad real es un coordinador transaccional: no imponer una nueva infraestructura sin comprobar cuenta y despliegue.

Documentación oficial consultada el 31/10/2026:

- https://developers.cloudflare.com/kv/concepts/how-kv-works/ — consistencia eventual, no operaciones atómicas/transacciones.
- https://developers.cloudflare.com/kv/api/write-key-value-pairs/ — último escritor gana, 25 MiB por valor, hasta 1 escritura por segundo por clave. El Worker fuente impone un límite propio menor: 5 MiB.
- https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/ — almacenamiento transaccional y consistente.
- https://developers.cloudflare.com/durable-objects/platform/pricing/ — SQLite disponible en Workers Free; verificar límites del proyecto antes de habilitarlo.

## Contrato mínimo pendiente de implementar

1. Conservar dominio, `/health`, `/catalogo.json`, `/admin/catalogo`, autenticación y relay existentes. Todos los caminos de escritura deben pasar por el coordinador; un despliegue paralelo con rutas que aún escriben directamente KV invalida la protección.
2. Propiedad pública aditiva `colecciones_version:1`, `colecciones:[objetos del contrato admin/colecciones.schema.json]`. No cambiar `items` ni `schema_version`. Referencias a títulos ausentes se preservan. Lectores Mobile/TV conocidos ignoran esas propiedades; no las muestran todavía.
3. `/admin/catalogo` audiovisual debe preservar la colección autoritativa del servidor incluso si un cliente envía su copia antigua o la omite; nunca interpretar omisión como eliminación. Mantener propiedades desconocidas de la raíz. No aceptar cambios de colecciones a través del PUT audiovisual antiguo.
4. Endpoint administrativo exclusivo, por ejemplo `GET/PUT /admin/colecciones`, con Bearer existente. GET devuelve versión de contrato, revisión opaca y objetos; PUT requiere revisión base (`If-Match`) y operación explícita. Ninguna autenticación en URL. 401/403 sin secretos; 428 sin condición; 409/412 con revisión desactualizada. Eliminación exige lista explícita de IDs o reemplazo confirmado con revisión exacta; nunca por ausencia en formato antiguo.
5. Validar esquema e invariantes runtime 0.1.8: hasta 1000 colecciones, 10000 IDs únicos por colección, IDs estables/únicos, orden, origen, tipos, límites de texto y URLs HTTPS sin credenciales ni dominios TheTVDB. Conservar propiedades desconocidas de versión conocida; rechazar versiones futuras sin reescribirlas. Límite de colecciones 2 MiB y de catálogo público compuesto 5 MiB **en bytes UTF-8 serializados**, validado dentro de la misma transacción que cada publicación audiovisual o de colecciones.
6. Antes de migrar, descargar y conservar una copia verificable del catálogo y las colecciones locales en un destino privado; bloquear temporalmente escritores para tomar una base consistente. La migración exige confirmación autenticada, revisión y respaldo inmutable identificado. No importar automáticamente al leer una petición pública. Hacer ensayos solo con otro KV de prueba y sin claves de producción.
7. Confirmar revisión, datos y respaldo anterior en una transacción. Una operación fallida no modifica estado autoritativo ni revisión. Idempotencia para reintentos después de timeout: identificador de operación, mismo resultado sin duplicar. Definir historial limitado y exportación de respaldos; restaurar también requiere revisión vigente y nuevo respaldo. No confiar en un espejo eventual de KV como coordinador.
8. Android debe leer revisión remota, comparar bases, mostrar diferencias y pedir confirmación de carga/descarga. Un respaldo 0.1.8 no contiene revisión remota: se trata como origen local a revisar, no como orden de reemplazar. Timeout ambiguo requiere consultar resultado antes de repetir. No habilitar el botón de publicación hasta probar el entorno y las garantías, no solo un `/health` exitoso.
9. Operación de colecciones independiente del catálogo restringido TheTVDB: transmitir solo objetos de colección TMDB/manual y referencias, sin copiar registros audiovisuales ni procedencia restringida. La política de publicación audiovisual TheTVDB permanece intacta. No publicar datos TheTVDB mediante el nuevo endpoint.
10. Prueba de aceptación del servidor futuro: dos escritores con revisión igual => exactamente uno confirma; PUT audiovisual antiguo sin colecciones y con copia obsoleta => colecciones idénticas a las vigentes; fallos antes/durante commit => estado anterior completo; respaldo recuperable; límites conjuntos; ausencia de credenciales; lecturas Mobile/TV. Después, solicitar autorización explícita antes del despliegue de producción.

## Límite de esta entrega

No se dispone de acceso administrativo de despliegue, binding real, KV de ensayo del propietario ni versión verificable del Worker desplegado. Las credenciales de la app instalada permanecen en el Samsung. No se solicita su divulgación. La autorización para desarrollar no autoriza desplegar producción. Preparar un parche KV que ignore estas condiciones sería presentar una falsa garantía. La entrega implementa la detección y las salvaguardas locales; la sincronización requiere la implementación transaccional y validación de servidor descritas arriba, y una autorización posterior para producción.

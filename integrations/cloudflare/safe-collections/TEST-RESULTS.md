# Resultados verificables — 31/10/2026

- Fuente principal: Worker completo suministrado por el propietario, normalizado de escapes de pegado; original preservado.
- Referencia esquema: `admin/colecciones.schema.json` del commit `e5a52976e6ff909401fa8516bcffd23f88d08e1c`.
- Node v24.22.1: **25/25 pruebas JavaScript aprobadas**, sin omitidas.
- Python: **3/3 pruebas aprobadas** de respaldo (hash, no sobreescritura, tipo numérico y duplicados).
- `node --check worker.mjs`: aprobado.
- `python3 -m py_compile admin-tool.py`: aprobado.
- Wrangler 4.27.0 `deploy --dry-run`: empaquetado correcto, binding DO SQLite y KV ficticio. No publicó ni creó recursos.
- `deploy rollback-readonly.mjs --dry-run`: empaquetado correcto; regresión confirma lectura actual y bloqueo de escritura autenticado.
- Miniflare 4.20260730.0: prueba ejecutada en **Cloudflare workerd real local**, usando SQLite Durable Objects y persistencia en directorio temporal; no es solo un mock KV.

## Cobertura importante

1. Reproducción de pérdida de colecciones con Worker original; `schema_version` string rechazado y number 1 aceptado.
2. Conservación del cuerpo del relay original y lista restringida de nuevas rutas collection.
3. Autenticación en todas las rutas administrativas, incluso relay y rutas desconocidas.
4. Lecturas previas a migrar conservan KV; escritura bloqueada; importación explícita de una sola vez.
5. `/health`, `/`, `/catalogo.json`, slash final, CORS, OPTIONS y errores de ruta/método.
6. Colecciones incluidas en catálogo importado, propiedades desconocidas e IDs/referencias preservados.
7. Administradores antiguos bloqueados en modo estricto; modo compatibilidad mantiene colecciones aunque las omitan o envíen una copia vieja.
8. Alta de película, edición de serie/imagen, baja de registro y preservación de metadatos/enlaces.
9. ETag exacto, confirmación de borrado, ausencia sin borrar y versiones inválidas.
10. Dos escritores de colección con la misma revisión: exactamente 200 y 412, también dentro de workerd.
11. Dos publicaciones audiovisuales condicionadas: 200 y 412, sin depender de un KV obsoleto.
12. Reintento de una operación exitosa con respuesta perdida; misma clave/distinto cuerpo 409; persistencia tras reinicio real de Miniflare.
13. Validaciones de IDs duplicados, visibilidad, orden, referencias, URLs HTTPS y TMDB.
14. Propiedades desconocidas, orden estable, colecciones ocultas y referencias temporalmente ausentes.
15. Fallo de commit inyectado en SQLite: rollback de estado/respaldo/operación y reintento posterior correcto.
16. Exportación y restauración con revisión actual, rechazo de restauración obsoleta y contadores monotónicos.
17. Presupuestos UTF-8 de catálogo, colecciones y catálogo compuesto; persistencia de documentos mayores a un fragmento.
18. JSON roto, tamaño anunciado excesivo, restauración malformada y ausencia de mutación.
19. Retención de diez versiones anteriores y publicaciones independientes de catálogo/colecciones.
20. Auditoría estática reproducible de parsers Mobile/TV de los commits documentados.

## Límites de evidencia

No se ejecutaron APK ni parsers Kotlin; no hubo pruebas físicas, despliegue staging/remoto ni validación en producción. La compatibilidad Mobile/TV es revisión de fuente y preservación del JSON, no una prueba del equipo. El fallo de almacenamiento se inyecta en SQLite de Node, no en infraestructura Cloudflare real. La prueba workerd comprueba la transacción y persistencia locales, no latencia global ni cuotas de cuenta.

No hubo lecturas ni escrituras sobre el catálogo/KV de producción, ni llamadas TMDB con credenciales reales. No hay secretos reales en fuentes, fixtures, informes o configuración. No se crean bindings remotos. Las pruebas usan tokens rotulados sintéticos.

No se mide rendimiento ni límite CPU de Workers Free. Un catálogo cercano al máximo y la copia transaccional de historial pueden superar presupuestos de CPU de un plan aunque entren en los límites de bytes; medir en staging antes de elegir plan o habilitar producción. No se garantiza costo cero para tráfico o cuenta no inspeccionados.

Implementado: sí. Probado localmente: sí. Desplegado: no. Sincronización Android operativa: no, falta integración y autorización de despliegue.

# MiFlix Admin Android · 0.1.7-diagnostico

Aplicación independiente para administrar el catálogo schema 1 desde un teléfono. Paquete `ar.com.miflix.admin`, versionCode 8, Android 8.0/API 26 o posterior. No reproduce video ni trailers y no incorpora Media3, ExoPlayer ni TDLib. No cambia el módulo `app`, Mobile ni TV.

## 0.1.7: TheTVDB en español y APIs y proveedores

Configuración → **APIs y proveedores** permite guardar, cambiar o eliminar la API Key de TheTVDB, mostrar/ocultar la entrada y probar las conexiones de TMDB, TVmaze y TheTVDB. La modalidad de proyecto gratuita con atribución usa únicamente API Key; la opción de PIN se habilita solo para proyectos sostenidos por suscriptores. No se pide un token: Admin lo obtiene por `/login`, lo reutiliza en memoria y vuelve a autenticar una sola vez ante 401. El cifrado AES-GCM y Android Keystore siguen el mecanismo nativo existente, con archivo independiente en `noBackupFilesDir`, sin nuevas dependencias ni credenciales incluidas en APK, Git o catálogo.

**Fuentes → TheTVDB — Metadatos en español** reutiliza candidatos, comparación, casillas por campo/episodio, resumen, guardado normal y reversión de 0.1.6. Consulta traducciones `spa` verificadas sin sustituir ausencias por inglés. El texto base se puede consultar explícitamente y queda desmarcado, con idioma sin verificar. Solo se consulta el orden predeterminado; todos sus episodios requieren confirmar la correspondencia y las numeraciones dudosas se excluyen. No se crean ni eliminan episodios.

**Límite contractual explícito:** los datos seleccionados de TheTVDB se pueden guardar localmente, con procedencia por campo; sincronización y copia JSON para publicación quedan bloqueadas mientras existan esos datos. Hace falta verificar los permisos de distribución de la cuenta y añadir atribución con enlace donde Mobile y TV muestran esos metadatos. Esta versión no modifica esos clientes ni permite levantar el bloqueo con una casilla. Las imágenes TheTVDB requieren derechos adicionales y no se importan. El respaldo local permite revertir y guardar para retirar esos cambios. TMDB y TVmaze mantienen su funcionamiento habitual en catálogos sin TheTVDB.

Ver [auditoría, seguridad, condiciones y validación Samsung de 0.1.7](0.1.7-thetvdb.md). La documentación 0.1.6 que sigue es un antecedente; sus pruebas físicas no se presumen realizadas.

## 0.1.6: recuperación selectiva desde TMDB multidioma y TVmaze

En revisión, **Fuentes** ofrece TMDB español, TMDB en otro idioma y TVmaze para series/anime compatibles. También se encuentra a la derecha de **Obtener TMDB**. Consultar no guarda ni sincroniza. Se elige un candidato identificado, se comparan los campos actuales/nuevos y se seleccionan individualmente los datos a aplicar. Los campos vacíos con información válida se seleccionan inicialmente; títulos y nombres genéricos se consideran faltantes. Una correspondencia episódica no verificada permanece bloqueada hasta confirmarla manualmente.

**Aplicar datos seleccionados** presenta un resumen y conserva un respaldo local antes de modificar únicamente el borrador. Después siguen siendo necesarios **Confirmar y guardar** y **Sincronizar**. **Revertir última recuperación en esta ficha** permite volver a los valores anteriores, incluso después de guardar/reiniciar, mientras no se hayan modificado esos mismos campos posteriormente. No hay creación/eliminación de episodios durante la recuperación ni cambios de enlaces, identidades o `fecha_agregado`.

El botón azul se conserva. En temporadas vacías mantiene la carga TMDB tradicional; cuando ya existen episodios, abre la comparación para proteger datos existentes. TVmaze exige mantener atribución y CC BY-SA 4.0: los avisos y URLs se muestran y se conservan por campo dentro de `fuentes_metadata`, incluido el JSON público. No se traducen textos. La prueba física de esta versión sigue pendiente.

Ver [auditoría, arquitectura, licencia, cobertura de Frontera Verde y prueba Samsung](0.1.6-metadata-sources.md). Las notas de versiones anteriores que siguen son antecedentes.

## 0.1.5: fecha de incorporación al catálogo

`CatalogRules.save()` asigna `fecha_agregado = Instant.now().toString()` únicamente cuando el ID definitivo todavía no existe en el catálogo. La fecha usa ISO 8601 UTC con `Z`. Crear/recuperar un borrador o seleccionar metadatos no asigna fecha. La política se comparte entre películas, series y anime, sin cambios para temporadas o episodios.

Si el ID ya existe, se conserva exactamente el campo almacenado, aunque el candidato lo omita o lo cambie. Si el registro histórico no tiene fecha, continúa sin ella. No hay migración ni relleno del catálogo antiguo. Se mantienen schema 1, JSON completo, firma e identificador. El Worker no requiere cambios.

Base recuperada: ZIP de fuentes auditado 0.1.4/code 5, íntegro. No se encontró una versión posterior en las ramas y entregas accesibles. La rama original ya no está publicada; la recuperación conserva los archivos de aquella entrega, incluidos los módulos históricos sin cambios. Este snapshot no debe copiarse encima de un proyecto Mobile/TV actual ni mezclarse automáticamente en sus ramas. Ver [guía de recuperación, firma y prueba física](0.1.5-fecha-agregado.md).

Esta versión debe firmarse localmente con el keystore de la aplicación instalada. No utilizar una firma debug recién generada para actualizar el Samsung. La corrección queda pendiente de validación física con la APK nueva; los resultados de 0.1.4 documentados más abajo son antecedentes, no resultados de 0.1.5.

## Recursos visuales de 0.1.4

Base verificada: rama `miflix-admin-android`, HEAD `d7447d6`, árbol limpio, 0.1.3-diagnostico / código 4 → **0.1.4-diagnostico / código 5**. El usuario confirmó validación física de 0.1.3 en Samsung, incluidos los tres checkpoints. 0.1.4 queda pendiente de validación física.

| Campo JSON | Tipo y ausencia | Ejemplo |
|---|---|---|
| `poster_url` | Existente, sin cambios | `https://image.tmdb.org/t/p/original/poster.jpg` |
| `backdrop_url` | String URL opcional; se omite si TMDB no ofrece imagen; null antiguo admitido | `https://image.tmdb.org/t/p/original/backdrop.jpg` |
| `logo_url` | String URL opcional; se omite si no existe o falla la consulta; null antiguo admitido | `https://image.tmdb.org/t/p/original/logo.png` |
| `fondo_url` | Campo histórico conservado para el editor/revisión | Misma imagen que backdrop al obtener metadata |

Los ejemplos son ilustrativos, no imágenes reales. Se reutiliza `backdrop_path` de la consulta de detalle, **sin otra petición para el backdrop**. Los logos se consultan una vez por selección de ficha en `/movie/{id}/images` o `/tv/{id}/images`, sin filtro de idioma para disponer de todos los fallbacks. Se elige español (`iso_639_1=es`, configuración actual es-MX), sin idioma, inglés, otro idioma; dentro del grupo, valoración descendente, votos descendentes, ancho descendente y path lexicográfico para desempate estable. Se usa sólo `file_path` devuelto por TMDB, sin inventar nombres ni extensiones. La referencia HTTPS original se construye con el mismo helper del póster: `https://image.tmdb.org/t/p/original` + path. Los consumidores futuros pueden cargar directamente esa URL; Admin no descarga archivos permanentes ni incrusta binarios/Base64.

El logo se conserva tal como TMDB lo proporciona; no se procesa el archivo ni se verifica su canal alfa. No se añade galería o selector manual. Ausencia de imágenes, HTTP 400/404/429 u otro fallo de la consulta opcional no bloquean revisión/guardado ni vacían recursos existentes. La consulta opcional añade latencia y comparte el timeout HTTP existente de 25 segundos. No se guarda automáticamente al seleccionar.

Películas y Series reciben los recursos por el flujo TMDB actual. Anime consultado desde TMDB usa el recorrido TV existente; Anime de AniList puede recibirlos mediante su enriquecimiento TMDB y el grupo **Fondo** ya existente, conservando identidad AniList, póster y episodios si no se seleccionan sus grupos. AniList solo no recibe logos TMDB automáticamente: no se añade matching.

`backdrop_url` ya está definido en la documentación de esquema del catálogo; se añade sólo la declaración opcional de `logo_url`. **schema_version permanece 1**. El modelo JSON completo, lectura, copias, edición y publicación conservan estos campos. Editar datos no relacionados no los borra; el editor histórico `fondo_url` también actualiza `backdrop_url` (vaciar el fondo elimina esa referencia), sin tocar logo. No se rellena automáticamente todo el catálogo antiguo ni se modifica un catálogo real del usuario.

### Relay TMDB: adaptación necesaria y no desplegada

El relay de referencia `integrations/cloudflare/admin-extras.js` rechazaba `/images` con HTTP 400. Se amplía **solamente su allowlist** para admitir `/movie/{id}/images` y `/tv/{id}/images`, manteniendo autenticación, origen fijo, métodos, parámetros y almacenamiento existentes. No se cambia descarga/publicación del catálogo ni se implementa checkpoint compartido. No se despliega sobre producción en esta tarea, cuya fuente exacta no se dispone aquí. Si el relay publicado mantiene aquella restricción, sólo faltará el logo: backdrop y resto del flujo siguen funcionando. Con token TMDB opcional en el teléfono se consulta directamente sin esa restricción.

Para habilitar logos por relay, adaptar únicamente la allowlist equivalente del **Worker vigente** después de revisar su fuente exacta. **No reemplazar el Worker por el archivo de referencia completo ni instalar su extensión histórica de checkpoint**. Ver [nota del relay](../integrations/README.md). No se crean endpoints administrativos ni claves KV nuevas.

Fuentes oficiales: [Movie images](https://developer.themoviedb.org/reference/movie-images), [TV images](https://developer.themoviedb.org/reference/tv-series-images), [idiomas](https://developer.themoviedb.org/docs/image-languages) e [imagen base/path/tamaño](https://developer.themoviedb.org/docs/image-basics).

Se conservan checkpoints, eliminación, duplicados, borradores y sincronización. No hay colecciones, reproductor, UI nueva, cambios Mobile/TV/Windows ni secretos incorporados. Los cambios productivos Android se limitan a metadata, el espejo del campo visual del editor y la versión.

## Corrección de integración de 0.1.3 (antecedente)

Base verificada: 0.1.2-diagnostico / versionCode 3 → 0.1.3-diagnostico / versionCode 4. La validación física de 0.1.2 en Samsung confirmó el checkpoint migrado de Películas, pero detectó que **The Wonderfools**, cargada usando enlaces solamente en sus episodios, se guardaba sin avanzar Series. Anime todavía no fue validado físicamente. La corrección 0.1.3 fue validada posteriormente por el usuario en Samsung, incluidos Películas, Series y Anime.

Causa: `episodeField()` guardaba `episodio.telegram_url`, pero no comunicaba esa publicación al campo administrativo `original`. `save()` sí llamaba a `AdminCheckpoints.forSave()`, aunque éste recibía tanto `original` como el enlace principal vacíos y devolvía null. Las pruebas de 0.1.2 ejercitaban helper/repositorio con una URL ya disponible, sin cubrir ese recorrido del ViewModel.

Cambio mínimo: al introducir un enlace en un episodio de Serie/Anime, el editor también conserva esa publicación en `original` y en el borrador administrativo existente. No modifica el enlace principal, otros enlaces episódicos, almacenamiento, migración o UI. El último enlace episódico introducido representa el punto administrativo a registrar; no se elige por número máximo de episodio ni se reconstruye su URL. Vaciarlo vacía esa referencia pendiente. Una URL inválida impide guardar según la validación existente.

El checkpoint sólo avanza al **Confirmar y guardar**, antes de sincronizar el catálogo, mediante el mismo `save()` y escritura atómica de 0.1.2. Es un checkpoint de Series o Anime, no por episodio. Un borrador, su recuperación o descarte no lo avanza. Editar títulos/metadatos sin introducir una publicación conserva el avance. Las reglas de Películas no cambian.

Se agregan pruebas del ViewModel real con Robolectric, HTTP local simulado para catálogo/TMDB y AtomicFile; cubren selección de metadata, enlace episódico, guardado, sincronización, reinicio y recuperación. Las dependencias nuevas son **sólo de test**. No se añade infraestructura a la APK.

No se rellena automáticamente el checkpoint ausente de una serie guardada en 0.1.2: no se puede deducir con seguridad el orden administrativo de sus publicaciones. Al procesar una publicación explícita y guardar la revisión, se registra normalmente.

## Cambios de 0.1.2 (antecedente)

Base real: 0.1.1-diagnostico, versionCode 2 → 0.1.2-diagnostico, versionCode 3. Al entregar 0.1.2 no se había realizado validación física; el caso parcial observado después en Samsung se documenta arriba.

- Tres avances locales independientes: **Películas**, **Series** y **Anime**, con título, año, mensaje y acceso a la URL original de cada publicación. Una categoría nueva muestra **Sin contenido procesado**.
- Estado administrativo en `admin-state.json`, fuera de `catalog` y `base`: un objeto `checkpoints` con claves `pelicula`, `serie` y `anime`. Se mantienen AtomicFile, borrador y almacenamiento existentes. No se modifica schema_version 1, catálogo público ni su fingerprint.
- Al iniciar, si falta `checkpoints`, el registro antiguo `checkpoint` se copia íntegramente a la categoría indicada por su `tipo` explícito (`pelicula`, `serie`, `anime`). El registro antiguo se conserva además para recuperación. Los otros avances quedan vacíos. La presencia del objeto nuevo evita repetir la migración o sobrescribir avances posteriores; también se respetan categorías vacías del formato nuevo.
- Si el registro antiguo no tiene un tipo fiable, se conserva sin asignarlo a ninguna categoría y se muestra un aviso discreto. No se infiere por título, TMDB o URL. Para resolverlo hace falta identificar su categoría con evidencia; esta versión no añade un editor de recuperación ni lo clasifica automáticamente. No borrar los datos de la app. Un formato nuevo incompatible o un fallo de escritura no se sobrescribe: bloquea la edición y permite reintentar al reiniciar; un formato incompatible requiere revisión técnica del estado conservado.
- La migración conserva íntegros `telegram_url`, message_id, fecha, título/año, IDs y campos desconocidos. Los nuevos registros incluyen IDs de contenido/TMDB/IMDb/AniList/MAL disponibles. Nunca se reconstruye una URL a partir del número de mensaje. El botón utiliza exclusivamente la URL del checkpoint de su categoría; sin URL válida no ofrece apertura ni usa otro checkpoint.
- **Películas:** se conserva el punto de avance al guardar una nueva película o actualizar versión multimedia; **Conservar versión actual** también registra la publicación procesada sólo en Películas. Una edición de metadatos de película existente mantiene el comportamiento anterior.
- **Series/Anime:** avanzan al confirmar y guardar una ficha nueva o una publicación original introducida explícitamente en la revisión, incluso si los metadatos coinciden con una ficha existente. Se procesa la ficha como unidad administrativa, no cada episodio. Editar metadatos o episodios sin introducir una publicación no avanza el checkpoint. No se reemplazan enlaces episódicos.
- Crear, buscar, seleccionar, recuperar o descartar borradores no avanza ningún checkpoint. El guardado de catálogo y avance se hace en una sola escritura atómica. Eliminar fichas, episodios o temporadas conserva los tres avances históricos, aunque el contenido ya no esté en el catálogo.
- Descargar o descartar pendientes del catálogo conserva los checkpoints. Actualizar sólo un checkpoint no marca cambios públicos pendientes ni obliga a sincronizar. Se retiraron del cliente las consultas/publicaciones heredadas a `/admin/checkpoint`: sólo descarga `/catalogo.json` y publica `/admin/catalogo`. Los flags remotos antiguos de checkpoint dejan de utilizarse.

**Checkpoint compartido pendiente:** no se modifican Worker, KV, endpoints, Windows, Mobile ni TV. No instalar ni desplegar `integrations/` como parte de esta entrega; sus archivos y pruebas permanecen como antecedentes. El modelo local prepara las tres categorías sin implementar sincronización administrativa remota.

## Cambios históricos de 0.1.1

La base **0.1.0-diagnostico** fue validada manualmente por el usuario en Android real, incluyendo altas de las tres categorías, metadatos y compatibilidad con Mobile y Windows 1.13.0. Esta actualización conserva esa arquitectura y queda pendiente de una nueva validación manual.

- Un campo **Enlace de Telegram** en búsqueda y revisión. Al introducirlo se usan los mismos valores para contenido y publicación procesada; los borradores viejos que tenían dos URLs distintas siguen representándolas internamente. No se sustituyen enlaces episódicos.
- Ficha existente: editar, abrir publicación y **Eliminar del catálogo**, con acción destructiva diferenciada y confirmación.
- Dentro de **Editar ficha y episodios**, expandir temporada para eliminarla o eliminar un episodio. No se renumeran los restantes; una temporada puede quedar vacía. Sólo se modifica el JSON local, nunca Telegram.
- Una eliminación confirmada sobre datos ya guardados marca cambios pendientes inmediatamente. Actualiza atómicamente el borrador del mismo contenido para evitar que vuelva a guardar el elemento eliminado. Si había otros cambios de formulario sin guardar, éstos siguen en el borrador: no se guardan silenciosamente.
- Eliminar una parte sólo presente en un borrador nuevo/importado modifica ese borrador. Hay que guardar la revisión antes de que entre en el catálogo. No marca una publicación ficticia como pendiente.
- Eliminar una ficha entera descarta su borrador si lo tiene; conserva borradores ajenos y el checkpoint local. Cancelar la confirmación no hace cambios.
- Resultados TMDB/AniList con tarjetas táctiles, miniatura, título, año, título original útil y proveedor. Seleccionar abre la revisión y nunca guarda automáticamente.
- Estados principales **Sincronizado** y **Cambios pendientes**, sin contador ficticio ni cambios a la lógica de publicación.

**Fuera de alcance:** checkpoint compartido, cambios de Worker/Windows/Mobile/TV y nuevas dependencias de reproducción. Los archivos `integrations/` y sus pruebas se conservan como antecedentes de 0.1.0, sin modificaciones ni despliegue. No instalar esas integraciones como parte de esta actualización. En 0.1.1 se mantuvieron los avisos y comportamiento de checkpoint heredados; 0.1.2 los sustituye por tres estados exclusivamente locales.

## Firma e instalación

La APK debug 0.1.4 reutiliza el keystore preservado de 0.1.1. Certificado SHA-256: `c5125975ac14bcd11ffb402158ba112f0e72c92b87df268b56b77f810cafd49b`. No se genera ni entrega otra identidad. Puede actualizar una 0.1.1/0.1.2/0.1.3 firmada con ese certificado conservando datos locales. La compilación release permanece **sin firmar**: el proyecto no tiene signingConfig release. La firma final puede hacerse localmente con la clave preservada. El keystore queda fuera de Git y fuentes.

Antecedente histórico de 0.1.0 → 0.1.1:

La clave de firma de la APK 0.1.0 entregada anteriormente no estaba disponible en el entorno de esta actualización. La APK 0.1.1 generada usa una firma de diagnóstico nueva: **no es una actualización instalable sobre aquella APK con firma anterior**. No desinstalar con cambios/borradores pendientes o si se necesita conservar el checkpoint y la configuración locales. Para una actualización que conserve esos datos se debe compilar o volver a firmar con el keystore anterior.

La nueva clave se entrega por separado para conservar la firma en siguientes diagnósticos, fuera de Git y del ZIP de fuentes. Si la 0.1.0 instalada fue compilada por el usuario, usar su propio keystore y verificar el certificado antes de instalar. No se cambiaron applicationId ni schema para esquivar la comprobación de Android.

## Puesta en marcha

1. Instalá el APK de diagnóstico. Es una compilación debug firmada para pruebas; las próximas actualizaciones deberán conservar el certificado. No es una publicación en Play Store.
2. Configurá la URL HTTPS base de tu Worker y la credencial administrativa. También acepta la URL terminada en `/admin/catalogo` o `/catalogo.json`.
3. La app descarga `/catalogo.json`, valida schema 1 y conserva una copia local completa. Hasta terminar esa validación no permite editar ni sincronizar. Si falla, conserva el trabajo y muestra la copia disponible en modo lectura.
4. Entrá en Películas, Series o Anime. Para una nueva ficha, pegá o compartí desde Telegram el enlace original, buscá en TMDB o AniList, seleccioná el resultado y revisá el póster y los campos. Hay creación manual para completar datos sin proveedor.
5. **Confirmar y guardar** guarda únicamente en el teléfono y marca pendientes. **Sincronizar** publica explícitamente. Esperá la confirmación antes de pasar al otro dispositivo.
6. En Windows descargá/actualizá al iniciar. Antes de volver al teléfono, sincronizá Windows y después actualizá Android.

No existe coordinación distribuida. Usá un solo administrador por vez. Dos dispositivos editando o publicando simultáneamente pueden reemplazar cambios. La comprobación local del catálogo base ayuda a detectar un remoto diferente durante la descarga, pero no es un bloqueo ni una transacción servidor.

## Funciones

- Catálogo administrativo completo con campos desconocidos conservados en raíz, fichas, temporadas y episodios. Las otras categorías no se reconstruyen desde un modelo reducido. Sólo se omiten campos de archivos locales al publicar.
- Películas: TMDB en español, créditos, imágenes, géneros, valoración y trailer como URL informativa. Duplicado por TMDB: conservar ficha y avanzar checkpoint, actualizar versión multimedia preservando metadatos, o cancelar. La versión multimedia mantiene la temporada 0 de Windows.
- Series: búsqueda TMDB, temporadas incluidas especiales/0, descarga individual de temporada, episodios editables con título, sinopsis, duración, fecha, imagen y enlace. Al unir temporadas se conservan enlaces y campos locales de episodios existentes.
- Anime: AniList como fuente principal con IDs AniList/MAL, metadatos y procedencia. TMDB es un enriquecimiento opcional con selección de título, año, sinopsis, géneros, póster, fondo, trailer, créditos, valoración y temporadas/episodios. Conserva la identidad AniList y los enlaces ya asignados. La consulta opcional MAL usa Jikan y no bloquea si falla.
- Enlaces Telegram HTTPS públicos, privados `t.me/c/…`, temas y parámetros completos. Compartir varios enlaces permite elegir uno. No se extraen videos ni se inicia reproducción.
- Borrador, catálogo, base y pendientes se guardan atómicamente; sobreviven al cierre del proceso. Volver conserva el borrador. Descargar con pendientes exige confirmación de descarte; cancelar no publica ni avanza checkpoint.
- Tres checkpoints de publicaciones procesadas, independientes del contenido actual del catálogo y separados de la URL multimedia vieja de una ficha conservada. Mensaje original, IDs, título/año y momento de procesamiento son privados.

## Servidor y credenciales

El catálogo y los proveedores conservan las rutas y configuración de 0.1.0. En 0.1.3 los checkpoints siguen siendo exclusivamente locales; la integración compartida es una tarea posterior. No hay cambios ni despliegue de Worker.

TMDB usa preferentemente el relay autenticado del Worker con una credencial TMDB guardada como secret en Cloudflare. El campo opcional TMDB de configuración permite probar con una credencial propia introducida en el teléfono; no hay claves incorporadas. AniList usa su API pública. La configuración del teléfono se cifra AES-GCM con AndroidKeyStore, fuera de backups. No se registran credenciales, no se siguen redirects HTTP administrativos y no se permiten URLs administrativas HTTP.

El GET público puede aceptar un token incorrecto: la autenticación administrativa se verifica al publicar. Un rechazo 401/403 del catálogo sigue conservando los cambios pendientes. No se consulta una extensión de checkpoint para comprobar credenciales.

## Compilar y probar

JDK 17, Android SDK 34, Gradle 8.2 (wrapper del proyecto), AGP 8.2.2, Kotlin 1.9.22.

```sh
bash gradlew :admin:build :admin:lintRelease
node --test integrations/cloudflare/admin-extras.test.mjs
python integrations/windows/test_checkpoint_compartido.py
```

APK debug: `admin/build/outputs/apk/debug/admin-debug.apk`. Release sin firmar: `admin/build/outputs/apk/release/admin-release-unsigned.apk`. No configurar secrets en Gradle, fuentes, fixtures ni Git.

La suite conserva los 94 casos Android de 0.1.2 y agrega 11 casos de integración del ViewModel: 105 tanto en debug como release en 0.1.3, además de 6 Worker y 7 Windows: 118 casos únicos iniciales. 0.1.4 agrega 18 pruebas de metadata visual, 6 recorridos visuales del ViewModel y 2 pruebas de relay: **129 Android en cada variante, 8 Worker y 7 Windows = 144 casos únicos**, sin contar debug/release dos veces. La regresión se reproduce primero contra el código 0.1.2 y luego se verifica corregida. 0.1.4: testDebugUnitTest/testReleaseUnitTest, lintDebug/lintRelease, assembleDebug/assembleRelease finalizaron correctamente. Lint debug: 0 errores y 3 advertencias; release: 0 errores y 1 advertencia. Hay checks de Compose omitidos por incompatibilidad de API, y avisos de versiones de dependencias en debug; no se actualizan dependencias de producción fuera de este alcance. La UI/ACTION_VIEW y la validación física de 0.1.4 quedan pendientes.

## Validación de 0.1.4 pendiente en teléfono real

0.1.3 fue validada por el usuario; esta entrega 0.1.4 no fue probada físicamente. Además del recorrido siguiente, comprobar película y serie con/sin imágenes, enriquecimiento Anime, persistencia de backdrop/logo al editar y sincronizar, y la misma firma al actualizar 0.1.3.

1. Actualizar 0.1.2 con la misma firma sin desinstalar: conservar el checkpoint migrado de Películas y cualquier otro avance/borrador existente. La firma 0.1.0 tiene la limitación histórica indicada arriba.
2. Descargar catálogo real, comprobar cantidades y modo sólo lectura ante fallo.
3. Alta de película: pegar una vez el enlace, elegir resultado TMDB tocando la tarjeta, revisar póster y datos, guardar y sincronizar. Verificar enlace de contenido y checkpoint local.
4. Reproducir el caso de Series: dejar vacío el enlace principal, elegir metadata TMDB y asignar enlaces propios a episodios. Confirmar y guardar: Series debe mostrar la serie y la última publicación ingresada, con URL exacta; Películas/Anime no deben cambiar. Repetir con serie existente y revisión recuperada. Verificar antes/después de sincronizar y reiniciar.
5. Probar también una serie con enlace principal y el editor compartido de Anime con enlaces solamente episódicos; no reemplazar enlaces de otros episodios.
6. Probar selección AniList y enriquecimiento TMDB: mantener IDs, procedencia, metadatos y enlaces episódicos.
7. En cada categoría, abrir ficha y cancelar la eliminación: no debe cambiar nada. Confirmar: debe desaparecer localmente y mostrar pendientes antes de sincronizar.
8. Editar una serie/anime; eliminar episodio y temporada, incluidos especiales/0. Confirmar que se conservan otros episodios, temporadas, IDs y campos. Eliminar el último episodio deja una temporada vacía válida.
9. Con borrador del contenido eliminado, cerrar y reabrir. No debe reaparecer el elemento eliminado; otros cambios del borrador siguen recuperables. Un borrador ajeno no se elimina.
10. Probar borrador nuevo: borrar un episodio antes de guardar sólo modifica la revisión. Guardar, background/reinicio, continuación y descarte siguen disponibles.
11. Sincronizar y comprobar en Mobile y Windows 1.13.0 que desaparece sólo lo eliminado. Comprobar en Telegram que publicación y archivo continúan intactos.
12. Probar error de publicación: pendientes permanecen y se puede reintentar. No validar checkpoint compartido ni desplegar cambios de servidor en este recorrido.

13. Procesar en orden Película → Serie → Anime → Serie → Película. Verificar que sólo avanza cada categoría y que cada botón vuelve a su publicación exacta, incluidos topic/thread/query.
14. Reiniciar entre pasos y después de crear un borrador: los avances y el borrador persisten; el borrador no adelanta ninguno. Descargar/descartar pendientes no borra la historia; eliminar la ficha apuntada tampoco.

Observación **Authorization 0x0a**: no se dispone de Logcat ni reproducción del caso en esta tarea; no se diagnostica ni se declara resuelto. No se modificó TDLib ni la autorización de otras aplicaciones.

Limitaciones conservadas: uso secuencial en un solo dispositivo por vez; una descarga posterior o consulta a TMDB puede reintroducir episodios/temporadas si se solicitan de nuevo deliberadamente. No hay papelera ni eliminación de archivos externos. El EXE Windows y el servicio publicado no se modificaron.

# MiFlix TV 0.1.14 — Colecciones y géneros

## Base y alcance

Base TV: `feature/tv-0.1.13-personal-library`, commit
`152701cf8f57eb72e73b57e99825f63b82d76288`. Inventario remoto: no había una rama
TV posterior al iniciar. Rama independiente: `feature/tv-0.1.14-collections-genres`.
TV: `0.1.14-diagnostico`, versionCode `15`, applicationId `ar.com.miflix.client.tv`.
Mobile conserva su versión y comportamiento de esta base. No se fusionaron ramas.

Se amplía exclusivamente navegación/descubrimiento: Sagas destacadas, Colecciones,
detalle de colección y filas por géneros. No se modifican el Worker, Admin, catálogo
publicado, motor de reproducción, intro, Hero ni almacenamiento de la biblioteca.

## Contrato auditado

Referencias de lectura: `admin/colecciones.schema.json` de
`feature/admin-0.1.11-cloudflare-collections` y `CatalogCollections.kt`,
`FeaturedCollectionsSession.kt`, `CatalogSearch.kt`, `HomeRows.kt` de
`feature/mobile-0.9.14-collections`. Sólo se adaptan políticas compatibles, sin copiar
su interfaz ni fusionar su rama. El esquema raíz usa `schema_version: 1` numérico;
las versiones antiguas 1.x y la compatibilidad previa con 2 se conservan.

La solicitud pública al endpoint real no fue accesible desde Work (HTTP 403 y
herramienta web sin acceso). No se obtuvo un inventario de producción ni se afirma
haber comprobado sus registros. El esquema y código real de Admin/ambos clientes
usan `generos` como arreglo de nombres de texto, no IDs TMDB. Las pruebas usan
fixtures sintéticos, nunca credenciales ni enlaces privados reales.

## Colecciones

El parser TV interpreta opcionalmente `colecciones`/`colecciones_version` del mismo
JSON que ya descarga/cachea `CatalogRepository`. Nunca consulta rutas administrativas.
Una colección inválida se omite individualmente. Versiones desconocidas de colecciones
cierran sólo esa función: los títulos continúan cargando. Catálogos sin el campo o con
arreglo vacío funcionan. Los campos desconocidos siguen ignorándose en modelos y se
conservan en el JSON original de caché como antes: el cliente no lo reserializa.

Se validan los campos obligatorios de Admin (id/nombre/origen/orden/visible/titulos),
TMDB positivo cuando corresponde, metadatos opcionales e imágenes HTTPS sin usuario.
Las referencias se resuelven exclusivamente por `Title.id` de MiFlix. No se convierten
nombres ni tmdb_collection_id en identidad. Sólo películas disponibles mediante la
política existente `tvFeaturedTarget`, sin duplicados y en el orden exacto de `titulos`.
Referencias inexistentes, no películas o no disponibles se omiten. Colecciones ocultas
o sin integrantes disponibles nunca son tarjetas públicas.

La sección completa ordena por `orden`, nombre normalizado e ID para desempates.
No impone el límite de diez de Inicio. Sus tarjetas y el detalle usan carga diferida.
Los pósters de películas reutilizan `TvCard` sin nombres inferiores. Las colecciones
ofrecen su nombre para accesibilidad. La imagen intenta colección → integrantes →
placeholder; también prueba alternativas ante fallos de carga. El detalle muestra
nombre, descripción opcional, fondo y cantidad de películas disponibles.

## Sagas destacadas: duración de proceso

`TvProcessCollections` conserva una sola sesión de IDs, sin retener Activity ni Context.
Se eligen hasta diez al recibir el primer catálogo válido no vacío. Volver de fichas,
recomponer, reconstruir Activity o regresar del segundo plano no crea otra sesión.
El nuevo proceso puede comparar una lista previa en preferencias ligeras
`miflix_tv_sagas` (independientes de la biblioteca). Con más de diez alternativas se
fuerza un cambio de integrantes si el sorteo repite el conjunto anterior; con dos a
diez se evita repetir exactamente el orden si es posible. Con una no se promete variar.

Una carga provisional no borra la selección. Una actualización válida conserva los
IDs supervivientes y su orden, resuelve sus metadatos actuales y reemplaza sólo huecos.
No hay solicitudes de red adicionales ni base de datos ni recomendaciones externas.
Las preferencias de rotación son prescindibles: una lectura/escritura fallida no bloquea.

## Géneros

Se reutiliza la normalización de texto del índice TV/Mobile (Unicode NFD, sin tildes,
minúsculas) y se agrupan espacios como Mobile 0.9.14. No se equiparan semánticamente
Thriller/Suspenso ni se inventan géneros. Los IDs numéricos no se convierten en nombres:
no están en el contrato real; se omiten. TV tolera opcionalmente `generos` nulo, no
arreglo o entradas malformadas sin invalidar títulos. Este saneamiento está condicionado
a `BuildConfig.IS_TV`; el parser Mobile mantiene su comportamiento anterior.

Política de contenido: tipos separados, en orden Películas → Series → Anime; dentro
de cada tipo, géneros por clave alfabética. Encabezados como `Películas · Acción`.
Anime es un tipo, no un género agregado automáticamente. Dentro de cada fila los
títulos están en orden nombre/ID, sin duplicados, sólo disponibles. Pueden pertenecer
a varios géneros reales. No hay límite artificial de géneros ni copias de registros:
las filas contienen referencias a Title y usan LazyColumn/LazyRow e imágenes Coil.
Se prueba agrupación con 12.000 títulos. No se consulta TMDB por título.

Inicio conserva Hero, Continuar viendo y Mi Lista; añade Sagas destacadas antes de
Últimos agregados y géneros antes de las filas originales Películas/Series/Anime.
No se agrega una fila por saga ni Últimos estrenos en Inicio.

## Navegación y protección

El menú lateral incorpora Colecciones. Se reutilizan los componentes de foco TV.
Inicio recuerda claves diferenciadas saga/género/título y la posición horizontal;
la sección y el detalle conservan su scroll y último ID enfocado en ClientApp con
rememberSaveable y estados lazy existentes. Cada devolución consume una sola petición
de foco. Si desaparece un integrante, hay un destino seguro; si desaparece la colección,
el detalle ofrece Volver, sin cerrar la aplicación.

Recorrido: origen → colección → ficha existente → reproductor existente → ficha →
colección → origen. El retorno de colección es independiente del retorno de ficha.
No se cambia ninguna llamada ni implementación de reproducción/streaming. Los archivos
protegidos de 0.1.13 (incluidos biblioteca, progreso, QR, intro, Hero y reproductor)
se comparan byte por byte mediante `check_tv014_contract.py`.

## Verificación reproducible

CI `.github/workflows/tv014-validation.yml`: JDK 17, herramientas del proyecto sin
actualizar dependencias, tests JVM TV/Mobile, Lint TV/Mobile, assembleTvDebug,
assembleTvRelease y compilación de instrumentación TV. Se ejecutan contratos 0.1.13
heredados, extendidos específicamente para el nuevo número de versión y nuevas filas,
sin desactivar ninguna comprobación de reproducción/intro/biblioteca. El script
histórico 0.1.13 permanece intacto; el nuevo compara las ampliaciones autorizadas.

Los tests nuevos cubren parser/envelopes/versiones, IDs, orden, imágenes, disponibilidad,
actualizaciones, selección por proceso, rotación y datos de géneros. Las pruebas JVM
no equivalen a DPAD físico. La instrumentación añade `collections-grid-detail-dpad-back`
y `collections-long-grid` sobre componentes reales, además de todos los casos anteriores.
Se ejecutará sólo si existe un emulador limpio adecuado; no se da por ejecutada por compilar.

En Work hay JDK 17 pero no SDK Android ni distribución Gradle utilizable; el wrapper
no pudo descargar Gradle por restricción de red. No se instaló un SDK completo ni se
inició emulador. Las compilaciones se realizan en GitHub Actions. Los resultados finales se encuentran en la sección siguiente y en el JSON de validación publicado.

## APK y actualización Windows 11

Las APK de CI se generan SIN configuración Telegram privada: debug con firma temporal
de CI y release sin firmar. No son una actualización familiar compatible certificada.
No se accede ni cambia la clave del usuario, ni se incluye ninguna credencial.
Firmar una APK de CI no incorpora la configuración Telegram que le falta.

En el mismo proyecto Windows de 0.1.12/0.1.13, conservando los archivos locales ignorados:

```powershell
cd C:\Users\devia\StudioProjects\Miflix-TV-013
git status --short
git fetch origin
git switch --track origin/feature/tv-0.1.14-collections-genres
.\tools\build_tv_physical_debug.ps1
```

Si ya existe la rama local: `git switch feature/tv-0.1.14-collections-genres` y
`git pull --ff-only`. No descartar cambios locales para cambiar de rama. En Android
Studio usar Gradle JDK 17 y la variante `tvDebug`; el script produce la APK normal,
sin `testOnly` y con todas las ABI, independientemente de la última ejecución del emulador.
El script informa la ubicación real mediante `tools/locate_tv_apk.py` (AGP puede usar
`app/build/intermediates/apk/tv/debug/app-tv-debug.apk`). No confundir APK de instrumentación.

Para generar release: ` .\gradlew.bat :app:assembleTvRelease --console=plain `.
Usar únicamente la identidad original si se firma esa salida, no crear otro keystore.
El proyecto no cambia configuración de firma. Comparar certificado SHA-256 de la APK
instalada/anterior y nueva usando `apksigner verify --print-certs`; mismo package y
certificado son necesarios para actualizar conservando datos. Seguir también el
procedimiento de certificado original en `TV_0.1.13_BIBLIOTECA.md`.

Instalar encima desde administrador de archivos o `adb install -r RUTA_APK`; no usar
`-t`, no desinstalar como solución a conflictos de firma y no borrar datos. Si no
coinciden firmas, recompilar con la clave original. La inspección comprueba realmente
libtdjni.so ARM32/ARM64/x86/x86_64, hashes oficiales ya fijados, intro original, QR,
manifest, versión, package, testOnly y exclusiones de backup de biblioteca/progreso.
La inspección y firma válida no prueban reproducción física ni compatibilidad con el
certificado original si no se proporciona para comparar.

## Lista de validación física pendiente

- Inicio: intro/Hero y filas anteriores intactos; Sagas y géneros sin vacíos ni duplicados.
- Abrir sagas con OK; regresar desde ficha y reproducción: mismo foco y scroll.
- Menú Colecciones: cuadrícula larga, DPAD horizontal/vertical, acceso y regreso al menú.
- Detalle: orden exacto Admin, pósters sin títulos debajo, fondo/fallback y Volver.
- Home/segundo plano/recreación: sagas estables; nuevo proceso: variación si hay alternativas.
- Actualizar catálogo: ocultar/retirar saga válida eliminada; carga provisional sin borrados.
- Géneros: películas, series y anime separados, tildes/espacios sin filas equivalentes duplicadas.
- Regresión: Mi Lista, Vistos, Últimos agregados, Continuar viendo, episodios/Especiales,
  audio/subtítulos/seek, tráilers, Telegram y QR. Verificar persistencia tras actualizar.

La versión sólo queda validada físicamente después de esas pruebas del propietario.

## Referencias exactas y archivos

- Mobile consultado: `85671548a4a696f22eb4fcc408c7b801c4ad3065`.
- Admin/esquema consultado: `b0829c1963354ba6e1c659948e18708f6c63c13d`.
- Los archivos de estas ramas se leyeron con git show; no se modificaron ni fusionaron.
- Fuentes nuevas: TvCatalogCollections, TvCollectionsSession, TvCollectionsStorage,
  TvCollectionScreens, TvGenreRows.
- Cableado acotado: Catalog, MainActivity, TvNavigation, filas de TvScreens.
- Tests nuevos: TvCatalogCollectionsTest, TvCollectionsSessionTest, TvGenreRowsTest;
  dos casos añadidos al runner de instrumentación TV. Todos los tests JVM previos intactos.
- Herramientas: nuevo check_tv014_contract y workflow tv014-validation; verificador
  de APK/fixture actualizado sólo al número esperado de versión y contrato vigente.
- Documentación: README, CHANGELOG_TV y este informe. Gradle sólo cambia versión TV.

## Resultados finales de CI y artefactos

Ejecución aprobada: https://github.com/DarioDevia/Miflix/actions/runs/41294143890

Commit de código compilado: `6f0c9d94b6abc02feaee7207fa33b4dacce36729`.
Los commits posteriores de esta entrega sólo documentan resultados; no cambian APK.
Artefacto `TV-0.1.14-validacion`, ID `13078812575` (publicado en esa ejecución).
Primer intento detenido por una referencia incorrecta al archivo de requisitos QR;
se corrigió únicamente la ruta del workflow. El segundo intento finalizó correctamente.

| Comprobación | Resultado real |
| --- | --- |
| Tests JVM TV | 281, cero fallos, cero omitidos |
| Tests JVM Mobile | 281, cero fallos, cero omitidos |
| Nuevas pruebas JVM | 32 por variante; las 249 anteriores conservadas |
| Lint TV | 0 errores, 17 advertencias |
| Lint Mobile | 0 errores, 14 advertencias |
| Contratos Python | 9 protección + 14 APK + 3 localización + 2 QR: 28 aprobados |
| compileTvDebugKotlin / compileMobileDebugKotlin | Correctos |
| assembleTvDebug / assembleTvRelease | Correctos; BUILD SUCCESSFUL en 4m47s |
| assembleTvDebugAndroidTest | Correcto; instrumentación sólo compilada, NO ejecutada |
| ABI reales en ambas APK | armeabi-v7a, arm64-v8a, x86, x86_64; ELF/hash/size originales |
| Instalación / DPAD / reproducción física | Pendientes, no ejecutados |

Las advertencias coinciden en cantidad e IDs con 0.1.13. Incluyen el verificador
personalizado obsoleto de Compose, SharedPreferences.apply, target SDK, dependencias,
getIdentifier histórico de la intro, WebView JavaScript y StaticFieldLeak anterior.
No se deshabilitó Lint, no se creó baseline ni se actualizaron dependencias para ocultarlas.
El verificador obsoleto implica una limitación previa de cobertura de los detectores
personalizados de esa dependencia; no equivale a una garantía de ausencia de defectos.

Inspección real de manifest: package `ar.com.miflix.client.tv`, versión `0.1.14-diagnostico`,
code `15`, minSdk `26`, targetSdk `34`, testOnly `false`, Leanback/launcher presentes,
pantalla táctil no requerida y permisos sin cambios. Debug: debuggable=true;
release unsigned: debuggable=false. MP4 original (hash ecede13d…) y QR (hash 260478e…)
conservados en ambas. El ZIP descargado coincide con el SHA-256 del artefacto GitHub:
`931497cd6c89b22b2421f829d3ac3e074f2a4149c496dd15c429cf614759eec8`.

El verificador estándar comprueba exclusiones de backup en debug. En release AAPT
renombra los recursos: la inspección complementaria confirmó `res/Qq.xml`
(full-backup-content) y `res/4j.xml` (data-extraction-rules), con las exclusiones de
biblioteca/progreso intactas. Un resultado false del inspector de rutas debug para
esa release no significa que falte la exclusión: se conserva esta distinción en el JSON.

| Archivo | Bytes | Tipo |
| --- | --- | --- |
| MiFlix-TV-0.1.14-diagnostico-CI-debug.apk | 114645257 | Firma temporal de CI, verificada por apksigner |
| MiFlix-TV-0.1.14-diagnostico-unsigned.apk | 108695996 | Release sin firmar; no instalable hasta firmar |

SHA-256 debug:
`f082f3ee921a8250f42bcac80a6ff81e628b9a8bcb284745775477679e019297`

SHA-256 release unsigned:
`96b9ff58d2f688eb3bb0e1488dced4108ffc855bf4a76cdc868d62a9472eb4ca`

Certificado SHA-256 CI debug:
`43b8b393431b998a008ef771472d09026eedb018acba34ded6c204ddd88116ad`.
No se pudo comparar con la APK instalada/clave original porque no está disponible aquí.
Ambas APK de CI carecen de configuración privada Telegram. Para actualizar la versión
familiar, compilar localmente con la configuración y clave originales; no instalar
estas salidas como si su compatibilidad con la TV estuviera comprobada.

Los informes HTML/XML originales y comprobaciones están en el artefacto de Actions.
Resumen reproducible: `docs/TV_0.1.14_CI_VALIDACION.json`. Fuente y artefactos listos
para validación física; no se da por validada la interacción real por aprobar JVM/Lint.

# Mobile 0.9.14-diagnostico / 38 — Colecciones

## Base y alcance

Base verificada en GitHub y checkout limpio:
`feature/mobile-0.9.13-home-discovery`, `092ece114c6798d891f50e15808cd0d8250d163f`,
0.9.13-diagnostico / 37. No había una rama Mobile posterior en la consulta inicial.
Nueva rama `feature/mobile-0.9.14-collections`; no se fusionan ramas.
ApplicationId conservado: `ar.com.miflix.client`. Sin cambios de dependencias.

Contrato leído, sin modificar: `admin/colecciones.schema.json` y CollectionRules.kt
en `feature/admin-0.1.11-cloudflare-collections`, commit
`b0829c1963354ba6e1c659948e18708f6c63c13d`. No se utiliza el Worker experimental.
No se modifican Worker/KV, Admin ni TV; tampoco datos de producción.

## Parser y resolución

Catalog incorpora una proyección de colecciones opcional, leída explícitamente al
parsear 1.x o 2. El `schema_version: 1` numérico del contrato público es compatible.
Sin campo, con arreglo vacío o mal formado, la biblioteca de títulos sigue funcionando.
Una colección individual inválida no invalida el resto. Si colecciones_version está
presente debe ser el entero 1; una versión futura/desconocida oculta solo colecciones.
Se acepta su ausencia para compatibilidad.

Se comprueban los campos requeridos del contrato: id/nombre no vacíos, origen manual
o tmdb, orden entero 0–1000000, visible booleano y titulos como IDs string. Un origen
tmdb requiere tmdb_collection_id positivo. Campos opcionales ausentes/null son seguros.
URLs de imágenes se limitan a HTTPS sin credenciales; URLs inválidas se omiten para
permitir el fallback. No se inventan URLs ni se consulta TMDB. No hay un límite total
de colecciones en Mobile: el límite 10 solo aplica a destacadas.

La resolución publica exclusivamente visible=true y miembros existentes en items de
tipo pelicula, por ID MiFlix exacto, conservando orden y quitando duplicados/referencias
inexistentes. Una colección sin películas resueltas se oculta. La disponibilidad aquí
significa presencia en el catálogo, no una promesa de acceso privado Telegram ni de
reproducción offline. Se devuelven las mismas instancias Title, no copias ni otra base.

El descargador/caché de catálogo no cambia: conserva el JSON raw completo (incluyendo
propiedades desconocidas) y vuelve a parsearlo offline. La proyección de colecciones
es transient para Gson y se asigna explícitamente: una forma inválida del campo no
activa la deserialización estricta de todo el documento. No existe exportación ni
escritura remota desde Mobile.

## Sesión de Sagas destacadas

FeaturedCollectionsSession guarda solo IDs. ProcessSagaSession conserva esa instancia
por proceso, sin Activity/View/Window/Player. El almacenamiento Android retiene solo
SharedPreferences del applicationContext y una lista JSON de hasta diez IDs públicos
en `miflix_client/sagas_previous_ids`; no contiene URLs, usuarios ni credenciales.
No toca preferencias de progreso, biblioteca ni autenticación.

La primera instantánea válida no vacía se mezcla localmente y elige hasta diez.
Si coincide exactamente con los IDs/orden de la sesión anterior y hay dos o más,
intercambia los dos primeros. Una única colección puede repetirse inevitablemente.
La variación puede ser solamente de orden: no se promete un conjunto totalmente
nuevo cuando hay pocas colecciones. La escritura async intenta preservar la selección
anterior, pero una terminación abrupta antes de persistir puede perder esa última
preferencia; no afecta el catálogo ni la estabilidad de la sesión viva.

Navegar, rotar, volver de background o reconstruir Activity no crea otra sesión.
La instantánea provisional Cargando no se trata como una eliminación: espera a una
copia local o respuesta remota válida antes de reconciliar. Esto evita que reconstruir
Activity borre los IDs de la sesión antes de cargar la caché. Un proceso nuevo crea
otra instancia y lee los IDs anteriores; no depende de onResume.

Al refrescar conserva orden de supervivientes, actualiza sus objetos/metadatos y
rellena huecos con candidatos nuevos. No expulsa diez colecciones válidas solo porque
aparecieron otras. Si el catálogo válido elimina/oculta colecciones, se retiran. Una
instantánea realmente vacía deja la fila vacía. No hay llamadas Cloudflare adicionales.

## Interfaz y navegación

Mobile realmente usa Inicio/Buscar/Secciones/Mi Lista en la barra inferior; Películas,
Series y Anime son chips en Secciones. Se añade Colecciones como quinta entrada y
como acceso en Secciones, sin sustituir ni rediseñar esa navegación.

Inicio conserva las filas existentes en su orden relativo y añade Sagas destacadas
inmediatamente después de Continuar viendo. Se mantienen Mi Lista, Últimos estrenos,
Últimos agregados y filas generales/géneros. No se mueve Últimos agregados para evitar
reorganizar las funciones previas innecesariamente.

Colecciones muestra todas las públicas en LazyVerticalGrid adaptable, ordenadas por
orden Admin, nombre normalizado e ID como desempate. Destacadas usa LazyRow con nombre,
póster y cantidad. Imágenes por Coil: póster de colección, imágenes válidas de miembros
y placeholder; al fallar una descarga se intenta la siguiente imagen secuencialmente.
No se cargan todas las imágenes anticipadamente. El detalle tiene backdrop/póster,
nombre, descripción, cantidad y películas en el orden del Admin.

Las películas usan MovieCard existente (solo se amplía visibilidad a internal) y el
mismo openDetail. Se mantiene el camino Player → ficha → colección → origen (Inicio
o Colecciones). Se guardan IDs/origen con rememberSaveable y scroll de detalle con
SaveableStateHolder. No hay pila duplicada. Una colección eliminada durante navegación
muestra estado seguro con Volver; no cierra la app. No se promete retener una ficha
si Android mata el proceso y todavía no hay catálogo: se conserva la recuperación
normal de la app y la lectura de caché.

No se modifica PlaybackScreen ni TDLib, TelegramRangeDataSource, TelegramVideo, Media3,
progreso, cobertura/vistos, episodios, audio/subtítulos, fullscreen, intro, Hero,
Continue Watching, últimos agregados o identidad Google. Los bloques de Player,
fullscreen y navegación de episodios dentro de MainActivity se compararon con la base
y permanecen idénticos. `player.release()` → `video.clear()` no cambia.

## Pruebas y límites

CatalogCollectionsTest cubre catálogos antiguos, vacíos, campos requeridos/opcionales,
malformados, versiones 1.x/2, visibilidad, orden, referencias/IDs duplicados/inexistentes,
1100 colecciones sin límite artificial, imágenes/fallback, objetos originales,
callback de ficha, actualización/eliminación y origen de navegación.
FeaturedCollectionsSessionTest cubre 0/1/5/>10, variedad entre sesiones, estabilidad
sobre la misma instancia, refresh controlado, visibilidad y placeholder de reconstrucción.
Los tests anteriores no se modifican ni eliminan.

Estas pruebas JVM no ejecutan rotación Android, taps, Atrás físico, carga de imágenes,
reproducción real ni instalación. La estabilidad se prueba sobre el modelo que se
conserva por proceso; se debe verificar físicamente su integración Android.
No hay infraestructura Compose instrumentada/SDK/emulador disponible localmente.
Gradle local se bloqueó descargando 8.2 (`Network is unreachable`), antes de tests.
Actions ejecuta testDebugUnitTest, lintDebug, assembleDebug y assembleRelease; se
informan sus resultados reales en la entrega/run, sin presumir aprobación aquí.

## Firma original: limitación explícita

No se proporcionó en esta entrega el keystore/certificado de Mobile ni una APK previa
firmada que permita compararlo. No existe configuración release signing en la base.
No se crea otra clave ni se usa la clave de Admin/TV. CI genera debug y release unsigned,
sin configuración privada Telegram. **No se entrega como firmada/actualizable una
release unsigned, ni se afirma comparación de certificados que no se ejecutó.**

Para producir la actualización familiar en Windows:

1. Abrir esta rama en Android Studio con JDK17/SDK34 y conservar
   telegram.local.properties local. No enviar claves, contraseñas ni sesiones al chat/GitHub.
2. Ejecutar `gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease`.
3. Build → Generate Signed App Bundle or APK → APK → módulo app → seleccionar el
   keystore/alias Mobile **ya existente** → Release. No generar otro keystore.
4. Usar apksigner del SDK para comprobar ambas APK (la instalada/anterior y la nueva):

```bat
apksigner.bat verify --verbose --print-certs MiFlix-anterior.apk
apksigner.bat verify --verbose --print-certs MiFlix-nueva.apk
```

Comparar el SHA-256 del certificado del firmante. Ambas deben tener el mismo package
ar.com.miflix.client y la nueva versionCode 38 (superior a 37). Si el certificado
no coincide, detenerse: no desinstalar para "arreglarlo" ni borrar datos. No instalar
el debug de CI sobre la release familiar. La actualización compatible solo puede
confirmarse tras esa comparación y la instalación real con la clave original.

## Prueba Samsung A21s/A14

Primero confirmar externamente que Admin Android 0.1.11 publicó colecciones en el
catálogo público; este trabajo no publica ni modifica ese catálogo.

1. Firmar/verificar como arriba y actualizar sobre la instalación anterior sin borrar datos.
2. Inicio: Sagas destacadas con hasta diez, sin duplicados; sin colecciones, fila oculta.
3. Abrir Depredador: verificar sus cinco películas si esos cinco IDs están publicados,
   descripción, imágenes y orden configurado. Abrir ficha y reproducir con controles
   habituales; volver Player → ficha → colección → Inicio.
4. Colecciones: verificar todas las visibles y orden Admin; abrir y volver desde ficha/Player.
5. Navegar por secciones, buscar, girar, enviar a background y regresar: misma selección.
6. Conservar proceso y reconstruir Activity si es posible: misma selección tras cargar
   caché. Cerrar realmente el proceso y abrir: puede cambiar conjunto u orden; una sola
   saga no puede variar. No confundir salir al launcher con terminar el proceso.
7. Publicación/ocultamiento/eliminación solo por el administrador y de forma normal:
   refrescar catálogo y comprobar actualización y estado seguro de un detalle eliminado.
8. Sin red, explorar la copia de colecciones cacheada. No esperar reproducción offline.
9. Regresión: favoritos/vistos, Continuar, progreso, seek/fullscreen/audio/subtítulos,
   episodios/Especiales, intro, últimos agregados, Buscar, Edge-to-Edge y configuración.

La firma original, comparación de certificados, instalación y validación física
quedan pendientes del propietario. El código/CI no demuestran esos pasos.

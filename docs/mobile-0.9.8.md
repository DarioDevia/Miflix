# MiFlix Mobile 0.9.8-diagnostico / 32

Base verificada: `feature/mobile-0.9.7-library`, `cf5f95bb13de1b2b8fe44b54e937ee3095f64eea`, 0.9.7 / 31, repositorio limpio.
Rama nueva: `feature/mobile-0.9.8-home-rows`. No fusionar automáticamente.

## Orden y selección

Se conservan los avisos previos de conexión/cache cuando correspondan. Dentro del contenido: Hero sin cambios → Continuar viendo si existe → Mi Lista si hay favoritos presentes → Películas/Series/Anime no vacías → hasta ocho géneros. Todas las filas conservan acceso a ficha y Ver todo. No se añade autoplay de trailers.

HomeRows es un modelo puro sobre el catálogo cargado, sin I/O ni llamadas de reproducción. Agrupa solo IDs/títulos válidos y deduplica ID dentro de filas. Orden de tipos fijo; tarjetas por nombre normalizado/ID. Géneros por campo generos, usando normalización compartida de acentos, mayúsculas y espacios. No equipara Animación con Anime ni Acción con Acción y aventura. Etiqueta elegida entre las variantes realmente presentes, prefiriendo las que no son todo mayúsculas y con desempate lexicográfico.

Para reducir filas equivalentes se omiten géneros cuyo conjunto exacto de títulos coincide con una general u otro género de mayor prioridad. Se prioriza cantidad de títulos y clave normalizada, se toman ocho y se muestran alfabéticamente. Esta omisión no fusiona datos ni borra un género del filtro de búsqueda. En un catálogo de una sola película no se repite su póster en todos sus géneros. No se limita la cantidad de títulos de una fila: LazyRow solo compone las tarjetas necesarias; Ver todo muestra el conjunto del género.

Favoritos: homeFavoriteRow lee PersonalLibraryStore.favorites sin persistencia nueva, sin escribir y sin purgar huérfanos. Ver todo selecciona Mi Lista, no la última pestaña Ya vistos. Género abre SearchFilters.genre existente; limpia query/tipo/año/puntuación/estrenos y usa A-Z para evitar resultados ocultos por filtros viejos. Categorías abren Secciones como antes. HomeRow.select entrega el Title existente al callback openDetail por ID; no crea otra navegación.

## Rendimiento y estado

buildHomeRows se recuerda en ClientApp por titles; no se recalcula con cada tick del Player o recomposición. homeFavoriteRow se recuerda por catálogo, instancia del store y revisión local. No se cambia el store ni cobertura. La creación de índices por snapshot es memoria local y reutiliza referencias Title, no descarga imágenes. No se introduce precarga ni un cliente HTTP.

SaveableStateHolder rodea exclusivamente HomeScreen y conserva sus estados LazyRow al desmontar Inicio. LazyColumn tiene claves para Hero, Continuar viendo, favoritos, avisos, encabezado, tipos y géneros; las tarjetas usan ID existente y continuación su progressKey. La LazyListState vertical permanece en el contenedor existente. Refrescar un catálogo con filas realmente desaparecidas o más cortas puede ajustar índices: no se promete un offset de contenido que ya no existe. El scroll físico exige prueba Android.

Se conserva Hero, MovieCard, orden/barra/episodio/callback de Continuar viendo. Solo se añade peso/ellipsis al encabezado ContentRow para acomodar géneros largos y mantener Ver todo visible. Player, Telegram, rangos, audio, subtítulos, progreso, vistos, intro, persistencia de favoritos y JSON/Cloudflare permanecen sin cambios. Sin dependencias nuevas.

## Automatización

HomeRowsTest: 16 casos de agrupación, varios géneros, datos vacíos/nulos, deduplicación, orden, límites, categorías, catálogo vacío/pequeño, favoritos ausentes y persistentes, lectura sin escrituras, destinos y callback a ficha, equivalencia con filtros de búsqueda y catálogo de 1200 títulos. Se mantienen las suites existentes. El test de navegación verifica el callback del modelo usado por UI; no pretende ejecutar una Activity ni taps Android. Tests de scroll/superficies/Coil no se simulan mediante JVM.

Gradle local puede quedar bloqueado por descarga de distribución; en ese caso verificar mediante Actions testDebugUnitTest, lintDebug, assembleDebug y assembleRelease. Informar resultados reales y checksum de APK final. No se dispone de emulador ni Samsung conectado.

## Pruebas Samsung A21s y A14

1. Abrir desde cierre real. Mantener intro sin destello, Hero y su navegación. Volver desde ficha/reproductor sin repetir intro ni cambiar el Destacado de sesión.
2. Con progreso, comprobar que Continuar viendo sigue inmediatamente después del Hero, con barra, película/episodio y posición correcta. Repetir serie/anime sin alterar progreso.
3. Agregar/quitar favoritos desde ficha; volver a Inicio. La fila debe aparecer/actualizarse/ocultarse. Ver todo abre Mi Lista aunque antes estuviera seleccionada Ya vistos. Cerrar/reabrir y confirmar persistencia. Un favorito temporalmente ausente no debe borrarse.
4. Comparar géneros con catálogo real. Sin filas vacías ni duplicados internos; un título puede estar en varios géneros. Entender que filas idénticas se omiten y que todos los géneros siguen en Buscar. Ver todo debe abrir el filtro correcto sin conservar otros filtros activos.
5. Desplazar verticalmente hasta un género; mover su carrusel horizontalmente; abrir una ficha y volver. Comprobar ambos desplazamientos y que otra fila conserva su posición independiente. Repetir Mi Lista, Continuar viendo y vuelta desde reproducción. Rotar y regresar de background.
6. Actualizar catálogo: claves/filas coherentes sin errores. Cambios reales de tamaño/contenido pueden ajustar scroll. Comprobar scroll fluido y cargas de Coil en catálogo grande, portrait y landscape; probar encabezados largos.
7. Regresión: streaming, seek/barra, brillo/volumen, fullscreen, audio/subtítulos/tamaño, anterior/siguiente, continuación, vistos y similares. No hay modificaciones funcionales en esos módulos.

Instalar actualización familiar desde Android Studio con telegram.local.properties privado y el mismo keystore permanente. No desinstalar para sustituir la release por debug de CI, porque su firma/configuración son distintas. No incluir propiedades, sesiones ni keystore en Git/artefactos. Pendiente físico: arranque, scroll, taps, fluidez y regresiones; no declararlos validados por compilar.

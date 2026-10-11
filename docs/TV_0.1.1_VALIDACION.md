# MiFlix TV 0.1.1 — implementación y validación

## Base y alcance

- Repositorio: https://github.com/DarioDevia/Miflix
- Rama exclusiva: `miflix-tv`.
- HEAD inicial limpio: `b49b2787e123e7f5654f423a040063f989257d5c`.
- TV inicial: 0.1.0-diagnostico / 1; entrega: 0.1.1-diagnostico / 2.
- Mobile incorporado en esta base TV conserva 0.9.2-diagnostico / 26.
- La rama `miflix-cliente` se encontró en `8589ea755af17f8175ca76002752aac766b34a45`.
  No se integra ni se modifica esa rama.
- Se utilizó la descripción textual de la referencia; no se dispuso de su imagen.

## Archivos y decisiones

| Archivo | Cambio |
| --- | --- |
| app/build.gradle.kts | Versión TV y runner de instrumentación solo TV; dependencias intactas |
| app/src/main/java/ar/com/miflix/client/Catalog.kt | Exponer fondo/logo opcionales en ambos parsers; ignorar tipos de imagen mal formados |
| app/src/main/java/ar/com/miflix/client/TvScreens.kt | Extraer Detalle; Home y TvAction sin cambios de comportamiento |
| app/src/main/java/ar/com/miflix/client/TvDetailScreen.kt | Nuevo detalle cinematográfico y diálogo de sinopsis |
| app/src/main/java/ar/com/miflix/client/TvDetailArtwork.kt | Selección de imágenes, rendiciones limitadas y metadatos |
| app/src/test/java/ar/com/miflix/client/TvDetailArtworkTest.kt | 14 pruebas nuevas de datos/compatibilidad |
| app/src/androidTestTv/java/ar/com/miflix/client/TvDetailInstrumentation.kt | Runner nativo de casos visuales y D-pad con fixtures |
| README.md | Versionado, cambios y procedimientos |
| docs/TV_0.1.1_VALIDACION.md | Evidencia, límites y entrega |

Se mantiene el módulo app y sus flavors mobile/tv. El Detalle usa Coil 2.6 existente,
sin librerías nuevas. Fondo a pantalla completa, recorte centrado y gradientes;
logo ajustado sin recorte, título textual durante carga/error; póster vertical o
fondo neutro al faltar fondos. El foco inicial es Reproducir o Elegir episodio.
La sinopsis completa admite ↑/↓ y Atrás; el diálogo devuelve el foco a su botón
tras cerrar su ventana. Los botones de Detalle reutilizan TvAction con texto blanco
y 18 sp mediante un tema local; el tema y los controles de Home permanecen intactos.
Los controles de temporada/episodio conservan sus callbacks y orden.

El único cambio compartido es la lectura de campos visuales que el catálogo ya
publica: no se cambia schema, endpoint, caché, sincronización ni enlaces Telegram.
MainActivity, PlaybackScreen, TelegramSession, TelegramVideo, rangos 1 MiB,
Media3, progreso, permisos y manifests permanecen idénticos a la base.
No se implementan trailers ni funciones adicionales.

## Evidencia automática

- Antes de editar: 109 tests Mobile y 109 TV, cero fallos/omitidos.
- Después: 123 tests Mobile y 123 TV, cero fallos/errores/omitidos.
- `assembleMobileDebug`, `assembleTvDebug` y `assembleTvDebugAndroidTest`: SUCCESS.
- `lintMobileDebug`: FAIL, 41 errores / 7 avisos; `lintTvDebug`: FAIL, 42 errores / 8 avisos.
  Se repitieron ambos lint en un checkout aislado del commit inicial: los informes
  son **idénticos byte por byte** tras normalizar la ruta absoluta del checkout.
  No hay incidencias nuevas. Son opt-in de Media3 en el motor, un aviso/error del
  manifest TV y avisos existentes. No se silenciaron ni se corrigieron fuera de alcance.
- Android TV API 28 x86, 1920×1080, densidad 320, render por software: ambas APK
  instaladas correctamente. Ejecución final: **12 casos de interfaz PASS, 0 fallos**,
  más comprobación PASS de solicitudes de imagen sin API externa.
- Se revisaron capturas del detalle y de la sinopsis: márgenes, legibilidad, gradiente,
  borde de foco y texto blanco de los botones. Imágenes sintéticas, no fotografía real
  ni un televisor físico. El runner también mide el rectángulo del logo renderizado.
- La primera lectura por búsqueda de accesibilidad se sustituyó por recorrido de
  nodos; el runner consulta ventanas interactivas. El retorno del foco desde el
  diálogo espera un frame tras el cierre. El recorrido final completo pasó.

| Requisito | Cobertura y límite |
| --- | --- |
| Fondo/logo, ausencia de imágenes, URL inválida | Unit tests + fixtures instrumentadas; catálogo remoto solo leído |
| Transparencia/proporciones extremas | Fixtures de logo alfa, proporciones 4:1, 12:1 y 1:8; medición en captura |
| backdrop_url/fondo_url/logo_url y metadatos ausentes | Tests schema 1 y 2 |
| Sinopsis larga, foco, D-pad, Atrás | Runner nativo sobre pantalla real de producción |
| Series/anime, temporadas y episodios | Fixture de dos temporadas, elegir segundo episodio y ruta de reproducción |
| Apertura/retorno reproductor | Ruta real hasta pantalla «Conectar Telegram» sin credenciales; no streaming |
| Progreso persistente | Tests existentes y claves iguales al enriquecer imágenes; sin escritura real de reproducción |
| No API TMDB | Selección local de rendición del mismo archivo y solicitudes del runner confinadas a fixtures |
| Motor compartido intacto | Comparación git contra la base; Home/TvAction también comparados |
| Mobile sin regresiones | Tests/build; pendiente validación física adicional |
| Privacidad | Build sin configuración privada; no archivos privados en el commit |

### Salida final de instrumentación

```text
PASS movie-logo
PASS movie-no-logo
PASS movie-poster
PASS movie-neutral
PASS movie-broken
PASS movie-legacy
PASS movie-retry
PASS logo-wide
PASS logo-tall
PASS synopsis-dpad-back-and-player-return
PASS serie-season-episode-player-return
PASS anime-season-episode-player-return
PASS artwork requests confined to fixture images; no API requests
Failures: 0
```

## APK y reproducción de resultados

| Variante | applicationId | Resultado |
| --- | --- | --- |
| Mobile | ar.com.miflix.client | app/build/outputs/apk/mobile/debug/app-mobile-debug.apk |
| TV | ar.com.miflix.client.tv | app/build/outputs/apk/tv/debug/app-tv-debug.apk |

Las APK de esta comprobación se construyeron sin `telegram.local.properties`,
con API ID 0 y hash vacío. Permiten diagnóstico de interfaz; para streaming Darío
debe compilar localmente con su archivo privado. No publicar ese APK familiar.
No se publicaron API ID, API Hash, sesiones Telegram, credenciales ni archivos privados.

En Android Studio: abrir el repositorio, seleccionar `tvDebug` en Build Variants,
Build → Build APK(s); instalar la APK TV con `adb install -r`.
Los comandos completos, runner y diez pasos manuales están en README.md,
sección «Verificar Detalle 0.1.1». Instalar el runner solo en emulador limpio sin
configuración privada. Nunca se considera comprobado streaming real por compilar.

## Limitaciones

Pendiente validación en televisor físico, imágenes del catálogo real sobre TV y
streaming Telegram autorizado, incluyendo seek/audio/subtítulos y progreso real.
Las pruebas instrumentadas emplean catálogo e imágenes sintéticos. El runner no
valida resolución TDLib ni descarga parcial. La sinopsis tiene vista resumida de
tres líneas; géneros, dos líneas; título textual, dos líneas. No se añadió un
framework de foco, precarga global ni animación de imágenes.

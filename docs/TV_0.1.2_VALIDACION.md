# MiFlix TV 0.1.2-diagnostico — validación y entrega

## Base y alcance

Repositorio: https://github.com/DarioDevia/Miflix. Rama exclusiva: `miflix-tv`.
HEAD inicial local/remoto: `ffdfc32d2c98f7e24826bd45ef30ef1df0ea6bcf`, TV
0.1.1-diagnostico / 2, working tree limpio. No se incorporaron cambios de otras
ramas ni se modificó `miflix-cliente`. HEAD final: el commit que contiene este
informe (`git rev-parse HEAD`); su hash completo se entrega al publicar.

Se mantienen un módulo `app`, flavors Mobile/TV y motor compartido. Mobile en esta
base continúa 0.9.2-diagnostico / 26 (`ar.com.miflix.client`); TV pasa a
0.1.2-diagnostico / 3 (`ar.com.miflix.client.tv`). No se actualizan dependencias.

## Diagnóstico previo al cambio visual

Las capturas de Darío muestran en Detalle un póster a la izquierda, título textual,
ningún fondo/logo y espacio negro. En reproducción muestran controles con botones
−10 s, Pausar, +10 s, Ocultar y texto violeta de bajo contraste; Home presenta el
mismo contraste de botones. El diseño del Detalle capturado no coincide con
`TvDetailScreen` en el HEAD 0.1.1 verificado y es consistente con el diseño previo.
Esto no demuestra qué versión estaba instalada: no se tuvo acceso al APK,
caché, logs ni proyecto local del dispositivo del usuario. Debe verificarse
versionName/versionCode después de instalar; 0.1.2 además los hace identificables
mediante versionName en el Detalle.

Consulta de SOLO LECTURA al catálogo Cloudflare para
**Los Ilusionistas: Nada es lo que Parece**, ID `pelicula-imdb-tt1670345`:

| Campo publicado | Archivo existente |
| --- | --- |
| poster_url | `8KbkyaBuFugMcGto7W02PRvmbXA.jpg` |
| backdrop_url y fondo_url | `xEY0MV2jSQBz9iOJfCFvLTiPGMA.jpg` |
| logo_url | `hSOyMqkIy9nN9jypcZWHzE8Dhgu.png` |

Todos usan `https://image.tmdb.org/t/p/original/`. Las rendiciones ya utilizadas
por TV, w1280 para fondo y w500 para logo, respondieron HTTP 200: JPEG 1280×720
(210553 bytes), PNG RGBA 500×71 (35705 bytes), con alfa entre 0 y 255. El logo
real tiene transparencia; las imágenes existen, por lo que no corresponde agregar
fuentes nuevas ni corregir el catálogo.

Se verificaron JSON → parser schema 1/2 → `Title` → caché JSON original → ruta
`BuildConfig.IS_TV` → `tvDetailArtwork` → solicitudes Coil. 0.1.1 ya conserva esos
campos y prueba alternativas de fondo. No se encontró un defecto de ese recorrido
que explique por sí solo la captura. La descarga HTTP y el render se verificaron
por separado; no se afirma haber diagnosticado la instalación del usuario.

## Cambios

- Detalle: conserva fondo proporcional con recorte centrado y logo transparente
  con Fit; reduce opacidad del degradado para hacer visible el fondo manteniendo
  contraste. Mantiene fallback textual, póster/fondo neutro, alternativas de URL,
  metadatos, sinopsis completa y selección de temporadas/episodios de 0.1.1.
  Agrega identificación visible `MiFlix TV 0.1.2-diagnostico`.
- Botones TV: texto blanco, también en los botones reutilizados por Home y
  autenticación. Home no cambia datos, composición, rutas ni navegación.
- Reproductor TV: Play/Pause solo por iconos, foco rojo/borde blanco; barra y tiempo
  temporales. Se eliminan los botones −10 s, +10 s y Ocultar y las instrucciones
  permanentes. Audio/subtítulos permanece como acción secundaria por icono.
- Un único temporizador TV de 4 s, cancelado/reiniciado con interacción o cambio
  de estado. No oculta durante pausa ni con selector de pistas abierto. El
  temporizador Mobile existente ya excluía TV y se conserva igual.
- Pausa: logo del título principal, también para episodios, arriba a la izquierda;
  proporcional con transparencia, URL y caché Coil existentes. Título blanco
  si no existe/carga/falla el logo. No aparece durante reproducción/buffering;
  se retira al reanudar. No hay logo ni audio/video duplicados.
- D-pad: ocultos → primera flecha/OK muestra controles; visibles → OK ejecuta
  icono enfocado, ←/→ seek ±10 s, ↓ Audio/subtítulos y ↑ Play/Pause cuando hay
  pistas. Teclas media conservan funciones. Atrás visible oculta; Atrás oculto
  regresa mediante la ruta existente. En pausa, Atrás puede ocultar controles y
  otro Atrás vuelve a Detalle, conservando solo la identidad hasta salir.

El motor no se reimplementa: mismo ExoPlayer/Media3, TDLib, TelegramVideo,
TelegramRangeDataSource de 1 MiB, callbacks play/pause/seek, resolución, progreso,
ciclo de vida, release, suspensión, preferencias de pistas/subtítulos y volumen.
MainActivity/PlaybackScreen solo pasan metadatos visuales opcionales al overlay
TV; Mobile recibe null y conserva su interfaz. No cambian permisos ni WakeLock.
No se agregan tráileres, servicios, APIs TMDB/AniList ni llamadas de búsqueda.

## Archivos

Modificados:

- `app/build.gradle.kts`: versionado TV.
- `MainActivity.kt`: transmite el título padre al player TV.
- `PlaybackScreen.kt`: pasa metadatos al overlay y su estado de pausa/pistas.
- `TvDetailScreen.kt`: degradados e identificación de versión.
- `TvPlaybackControls.kt`: controles, temporizador y logo de pausa.
- `TvScreens.kt`: contraste de `TvAction`, solo UI TV.
- `app/src/androidTestTv/.../TvDetailInstrumentation.kt`: casos nuevos y capturas.
- `README.md`: versión, uso de remoto y compilación actualizados.

Creados: `app/src/test/.../TvPlaybackControlsTest.kt`, este informe y evidencias
visuales/documentales de pruebas. Las rutas Kotlin son
`ar/com/miflix/client/` dentro del source set indicado.

## Pruebas y límites

**Unitarias:** 128 tests por variante, cero fallos/errores. Los cinco nuevos
comprueban condiciones del temporizador, D-pad visible/oculto, teclas media y
separación entre identidad gráfica del título padre e identidad de progreso del
episodio. Se ejecutaron todos los tests existentes.

**Compilación:** pasan `assembleMobileDebug`, `assembleTvDebug` y
`assembleTvDebugAndroidTest`. JDK 17, Gradle 8.2, SDK/build tools 34 originales.
Las APK se generan sin configuración Telegram privada en este entorno.

**Lint:** Mobile 41 errores/7 avisos; TV 42 errores/8 avisos, igual que
la base 0.1.1 recompilada en worktree aislado. Comparación XML por id, severidad,
mensaje y archivo: cero nuevas y cero removidas; se ignoran líneas/columnas
desplazadas por firmas. El comando combinado termina con código 1 por esos
errores preexistentes; tests y los tres assemble finalizaron correctamente.
No se silenciaron comprobaciones.

**Emulador:** Android TV API 28 x86, 1080p, remoto con CPU/software rendering.
18 casos de interfaz, cero fallos; además, aserción de confinamiento de solicitudes
de artwork sin API adicional. Se generaron 30 capturas 1920×1080. La revisión visual
manual de las capturas confirmó fondo real a pantalla completa, logo transparente
proporcional, texto/botón blanco y foco visible; también logo de pausa en esquina,
fallback textual, iconos de play/pause sin etiquetas visibles y pantalla limpia
cuando se ocultan controles. Se revisaron asimismo póster alternativo, logo sin
fondo, logos extremos y diálogo de sinopsis. Las capturas se entregan en
`MiFlix_TV_0.1.2_capturas.zip`; los logs/sumarios están en
`docs/TV_0.1.2_evidencia/`. Los rectángulos verdes son logos de test, no assets del
producto. El caso real conserva el texto gráfico del logo ya publicado.

El runner usa catálogo sintético, imágenes sintéticas con transparencia y logos
ancho/alto, y un caso de Detalle con las imágenes publicadas reales de Los
Ilusionistas. Esos bytes HTTP 200 se suministran offline mediante Fetcher de test;
no es una prueba de descarga Coil por red dentro del emulador. Sinopsis y enlaces
Telegram del caso son fixtures; no se alteró el catálogo de producción.

Los cuatro casos de reproductor montan el overlay real con callbacks/posición
simulados (película, serie, anime y sin logo); comprueban foco, iconos sin texto,
auto-hide, pausa >4 s, proporciones, reanudación, seek y doble Back. No ejecutan
ExoPlayer ni una sesión Telegram. Las rutas de episodio/Back de la app real se
prueban hasta la pantalla de autorización. No se probó audio audible, buffering,
streaming real, video real ni un televisor físico. La validación previa aportada
por Darío sigue siendo la evidencia de motor funcional.

## Repetir las pruebas aisladas (opcional)

Solo en emulador limpio, build sin configuración privada, nunca sobre sesiones
familiares. El runner restaura catálogo/preferencias al terminar. Para el caso de
imagen real, descargar estos archivos ya publicados (sin API adicional):

```sh
curl -fL https://image.tmdb.org/t/p/w1280/xEY0MV2jSQBz9iOJfCFvLTiPGMA.jpg -o backdrop.jpg
curl -fL https://image.tmdb.org/t/p/w500/hSOyMqkIy9nN9jypcZWHzE8Dhgu.png -o logo.png
bash gradlew testMobileDebugUnitTest testTvDebugUnitTest assembleMobileDebug assembleTvDebug assembleTvDebugAndroidTest
adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk
adb install -r app/build/outputs/apk/androidTest/tv/debug/app-tv-debug-androidTest.apk
adb shell mkdir -p /sdcard/Android/data/ar.com.miflix.client.tv/files/tv012
adb push backdrop.jpg /sdcard/Android/data/ar.com.miflix.client.tv/files/tv012/backdrop.jpg
adb push logo.png /sdcard/Android/data/ar.com.miflix.client.tv/files/tv012/logo.png
adb shell am instrument -w ar.com.miflix.client.tv.test/ar.com.miflix.client.TvDetailInstrumentation
adb pull /sdcard/Android/data/ar.com.miflix.client.tv/files/tv-detail-tests
```

## Actualizar en Android Studio y validar realmente

No hubo acceso a la PC, Android Studio o emulador local de Darío. Ejecutar desde
la raíz del proyecto en la PC; si hay cambios locales, conservarlos antes de
cambiar rama o actualizar, sin reset forzado:

```powershell
git status --short
git switch miflix-tv
git pull --ff-only
git rev-parse HEAD
.\gradlew.bat assembleTvDebug
adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk
adb shell dumpsys package ar.com.miflix.client.tv | Select-String 'versionName|versionCode'
```

En Build Variants seleccionar **tvDebug**. La APK es
`app/build/outputs/apk/tv/debug/app-tv-debug.apk`, 0.1.2-diagnostico / 3. Conservar
la configuración Telegram privada existente para el build familiar; no enviarla.
`app-mobile-debug.apk` es Mobile 0.9.2 / 26 de esta base y no es la APK TV.
`adb install -r` conserva datos si la firma coincide. Si Android informa
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, utilizar la misma firma/keystore de la
instalación existente; no desinstalar automáticamente porque elimina sesión y
progreso. Elegir el dispositivo con `adb -s <serial>` si hay varios conectados.

1. Abrir desde launcher, navegar Home y abrir Los Ilusionistas. Verificar versión
   0.1.2, fondo/logo reales y contraste del botón con foco.
2. Reproducir con sesión local autorizada. No tocar nada durante >4 s: imagen
   limpia, sin barra ni botones persistentes; verificar que el audio no cambió.
3. OK muestra, otro OK pausa: icono de play, logo arriba a la izquierda y barra
   visible después de >4 s. Comprobar transparencia y proporción.
4. OK reanuda: desaparece logo y controles se ocultan después de 4 s. Mostrar
   controles, ←/→ seek, teclas media, Atrás oculta y otro Atrás vuelve a Detalle.
5. Serie y anime: temporada/episodio → reproducir → pausa; debe mostrarse logo
   de la serie/anime, sin cambiar la identidad del progreso. Volver y continuar.
6. Sin logo o con descarga fallida: título blanco legible, sin imagen rota.
7. Con múltiples pistas: ↓ hacia Audio/subtítulos, OK abrir, elegir pistas con
   D-pad y volver. Comprobar audio sin cambios, subtítulos desactivados inicialmente
   y tamaños existentes. Esta prueba física sigue pendiente.

## Seguridad

No se abrieron ni publicaron `telegram.local.properties`, API ID, API Hash,
sesiones, códigos, contraseñas, 2FA, credenciales ni archivos privados. No se
publican APK familiares ni se incorporan logs de autorización. Catálogo e imágenes
se consultaron en lectura; no se escribió Cloudflare, Admin ni ningún backend.
La publicación está limitada al código/documentación/evidencia de `miflix-tv`.

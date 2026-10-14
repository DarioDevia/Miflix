# MiFlix TV 0.1.5-diagnostico / 6

## Base y alcance

Base verificada de GitHub: `backup/miflix-tv-0.1.4`, exactamente
`a05c6a703aecb3387e64ba696522a22ca9d9e276`, árbol local limpio antes de editar.
Nueva rama: `backup/miflix-tv-0.1.5`. Un módulo app, variantes mobile/tv, Media3 1.3.1.
No se modifican las ramas anteriores, TDLib, TelegramVideo, parser, Cloudflare,
Admin ni rutas/identidad de progreso. No se abrieron archivos privados.

El código común sólo añade bifurcaciones TV y diagnósticos específicos de TV;
Mobile mantiene 0.9.2 / 26 en esta base, constructor y hoja inferior originales.
No se incorpora código de versiones móviles posteriores.

## Hallazgos y correcciones

### TV-AUDIO-01

Comparación con `miflix-cliente`: mismo ExoPlayer de plataforma, guardia
`isTrackSupported(index)` y selección mediante `TrackSelectionOverride`. Que el
archivo funcione en Mobile no demuestra compatibilidad de codec/perfil/canales en
el emulador TV. En Media3 1.3.1, `isTrackSupported(index)` usa
`allowExceedsCapabilities=false`: una pista detectada puede no ser seleccionable.
La hoja anterior deshabilita esos botones y DPAD los salta. No hay evidencia de que
TDLib o los rangos causen el silencio (la imagen y otros títulos funcionan).

TV reemplaza la hoja con un componente lateral compartido de reproducción. Las
filas incompatibles pueden enfocarse para leer el motivo, pero OK nunca envía su
selección. Pistas HANDLED, incluidas pistas únicas, sí pueden seleccionarse. La
selección conserva la guardia contra snapshots obsoletos y aplica override por
TrackGroup al mismo Player. Las marcas de pistas proceden exclusivamente de
`Tracks` actualizado por `onTracksChanged`; no se adelantan al pedido de selección.
Desactivados refleja ausencia de pista TEXT seleccionada; tamaño refleja la
preferencia visual local. Los idiomas/labels/canales/CC provienen de metadatos reales.

El panel no posee el Player y no llama a play/pause/seek/release. Back lo cierra
antes de los handlers del reproductor y Activity. Se bloquea seek por flechas
mientras está abierto; vuelve el foco al botón Audio y subtítulos. Un video que
estaba reproduciéndose sigue; uno pausado permanece pausado. Se conservan
progreso, streaming, fullscreen y lifecycle existentes.

Para investigar el silencio se añaden, sólo en TV, eventos de decodificador,
fallos codec/sink, volumen de Player, volumen/mute del sistema, soporte y selección.
Se habilita fallback de inicialización entre decodificadores disponibles. **No
agrega códecs, no fuerza EXCEEDS_CAPABILITIES y no garantiza sonido en House.** Si
ambas pistas son incompatibles se explica en el panel. Si son HANDLED pero siguen
sin sonido, las trazas permiten separar decoder/sink/selección/volumen; la causa
real continúa pendiente hasta la prueba en el dispositivo afectado.

### TV-TRAILER-01

El parser existente conserva ID de once caracteres/start de enlaces HTTPS de
YouTube. No se recibió en esta tarea la URL exacta de 13 fantasmas, por lo tanto no
se atribuye el fallo a su ID ni se declara probada su disponibilidad.

La integración ya usa INTERNET, JavaScript, DOM storage, HTTPS base URL/Referer
identificado por el applicationId, IFrame API oficial y sin acceso a archivos.
Se conserva esa configuración y no se eluden restricciones 100/101/150/153.
Hallazgos: la API recibía dimensiones porcentuales donde documenta valores numéricos;
el foco inicial quedaba en Cerrar; y el timeout sólo vigilaba onReady, sin vigilar
si una solicitud posterior realmente empezó a reproducirse.

Se usan dimensiones numéricas y ajuste al resize, foco en Reproducir al quedar
listo y texto que indica la acción necesaria. No hay autoplay. Tras un pedido de
reproducción, 15 segundos sin estado de reproducción/pausa/finalización generan
un mensaje y alternativa externa. Una carga sin onReady tiene límite de 20 segundos.
Se atienden error de script, errores IFrame, HTTP/red y proceso WebView caído; se
registran códigos/estado/tamaño/aceleración, sin URLs ni mensajes arbitrarios.
Controles oficiales permanecen accesibles; abajo desde WebView vuelve a Cerrar y
Back sale. Cierre destruye WebView; background lo pausa; resume no fuerza autoplay.

**Estos cambios no prueban la causa del video negro ni garantizan su corrección.**
Un proveedor WebView viejo, bloqueo del embed/red o decodificación/render del
dispositivo pueden seguir impidiendo reproducción. Un estado PLAYING tampoco
certifica visualmente que se estén mostrando fotogramas; validar con los ojos.

Referencias primarias consultadas:
- https://developers.google.com/youtube/iframe_api_reference
- https://developers.google.com/youtube/terms/required-minimum-functionality
- https://developer.android.com/media/media3/exoplayer/supported-formats
- https://github.com/androidx/media/blob/1.3.1/libraries/common/src/main/java/androidx/media3/common/Tracks.java
- https://github.com/androidx/media/blob/1.3.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/analytics/AnalyticsListener.java

### Interfaz normal

Se retiran etiquetas de versión de Inicio y Detalle. Configuración muestra el
botón Acerca de MiFlix; sólo al expandirlo se ven nombre, versión y compilación.
No se añaden permisos, dependencias de producción ni etiquetas DEBUG/TEST.

## Comprobaciones de esta entrega

- 27 tests JVM pasaron con Kotlin 1.9.22/JUnit 4.13.2: MediaTrackPolicyTest,
  TrailerLinkTest, TvTrailerPolicyTest y TvTrackPolicyTest. Se ejecutaron directamente
  sobre funciones puras; **no** mediante las tareas Gradle completas.
- 6 contratos estructurales pasaron: propiedad/lifecycle del panel, snapshots,
  guardia Mobile, componente compartido, versión oculta y versiones independientes.
- JavaScript del HTML emitido por la función Kotlin ejecutado con Node y un stub
  de IFrame API: identidad, ID/start, reproducción explícita/pausa, resize mínimo,
  callbacks de estado/error/bloqueo. No es YouTube real ni un navegador.
- Parseo sintáctico Kotlin de los seis archivos Android modificados principales:
  sin errores. No verifica tipos Android/Compose ni reemplaza una compilación.
- `git diff --check` pasó.
- Se añaden seis casos instrumentados de panel (película/serie/anime × reproduciendo/
  pausado), con DPAD, OK, marcas sólo tras snapshot confirmado, pista incompatible,
  Back/Cerrar, foco restaurado, ausencia de IME y no seek/pausa accidental. Usan
  snapshots/callbacks sintéticos; no prueban decoder ni streaming real.
- Casos instrumentados anteriores dejan de identificar Detalle por una etiqueta de
  versión y usan Sinopsis completa de sus fixtures largos.
- **No ejecutados:** todos los tests Gradle, assembleTvDebug, assembleMobileDebug,
  lint, pruebas instrumentadas y reproducción autorizada. Java runtime 17 disponible,
  pero faltan JDK completo (`javac`), Android SDK/Build Tools y adb en este entorno.
  No se creó ni ejecutó ningún emulador remoto.

## Actualizar y compilar en Windows

En la raíz del proyecto, primero `git status --short`. Si hay cambios tuyos,
guardalos mediante tu flujo habitual antes de cambiar de rama; no uses checkout
forzado, reset ni clean. Con árbol limpio:

```powershell
git fetch origin
git switch --track -c backup/miflix-tv-0.1.5 origin/backup/miflix-tv-0.1.5
git rev-parse HEAD
.\gradlew.bat testTvDebugUnitTest testMobileDebugUnitTest assembleTvDebug assembleMobileDebug
```

Si ya existe la rama local, usar `git switch backup/miflix-tv-0.1.5` y
`git pull --ff-only origin backup/miflix-tv-0.1.5`. En Android Studio sincronizar
Gradle y seleccionar tvDebug. Conservar tu configuración privada local sin
abrirla ni publicarla. APK: `app/build/outputs/apk/tv/debug/app-tv-debug.apk`.
Mobile: `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk`.
Acerca de MiFlix debe mostrar 0.1.5-diagnostico / 6.

Tests instrumentados opcionales: usar exclusivamente copia/instalación de pruebas
limpia, sin configuración privada. El runner existente lo exige. Construir
`assembleTvDebugAndroidTest`, instalar APK app/test y ejecutar seis casos:

```powershell
adb shell am instrument -w -e cases "track-panel-pelicula-playing,track-panel-pelicula-paused,track-panel-serie-playing,track-panel-serie-paused,track-panel-anime-playing,track-panel-anime-paused" ar.com.miflix.client.tv.test/ar.com.miflix.client.TvDetailInstrumentation
```

El runner escribe fixtures y restaura catálogo/preferencias al terminar. No usar
en una instalación familiar con sesión/datos. La suite histórica completa requiere
sus imágenes externas documentadas en 0.1.2.

## Prueba real requerida: Android TV 1080p

1. Inicio, menú, Buscar/Secciones, fichas y Configuración con DPAD. Ninguna versión
   visible en uso cotidiano; Acerca de MiFlix contiene versión/compilación.
2. House, mismo episodio reportado: verificar sonido inicial. Abrir panel; recorrer
   subtítulos, Surround y Stereo en ambos sentidos y con OK. Anotar mensajes de
   incompatibilidad. Si compatible, comprobar auditivamente cambio de audio y ✓.
3. Subtítulos: desactivar, seleccionar idioma/CC existente, cambiar tamaño. ✓ debe
   seguir el estado efectivo. Mantener menú abierto y comprobar video detrás.
4. Mientras reproduce, abrir panel, esperar, Back: sólo se cierra el panel, sigue
   la reproducción en la posición actual y conserva pistas. Repetir desde pausa:
   debe permanecer pausado. Reabrir/Cerrar y verificar foco en Audio y subtítulos.
5. Repetir con película, episodio de serie y episodio de anime; probar pista única,
   varias pistas y archivo sin subtítulos. No inventar pistas para completar casos.
6. Seek ±10 s, pause/play, fullscreen, Back y retorno a ficha; progreso/Continuar
   viendo y suspensión/reanudación con lifecycle existente.
7. 13 fantasmas: Ver tráiler, esperar foco en Reproducir, OK y observar video/audio.
   Probar pausa, controles YouTube, abajo/Cerrar y Back. Si falla, registrar el mensaje.
   Probar otro tráiler permitido, error de red y alternativa externa explícita.
8. Comprobar Mobile con su recorrido habitual: hoja inferior y reproducción sin cambios.

Logcat: `MiFlixTracks` y `MiFlixTvTrailer` contienen los diagnósticos nuevos sin
URLs/credenciales. Conservar AUDIO/TRACKS_CHANGED/PANEL_OPEN/OUTPUT/DECODER y los
códigos de tráiler. Otros logs históricos del proyecto pueden incluir enlaces/rutas:
revisarlos antes de compartir. Nunca compartir códigos, hash, sesiones ni tokens.

## Pendientes y criterio de aceptación

0.1.5 no está validada. Pendientes principales: sonido real de House, fotogramas
del tráiler reportado, recorrido DPAD/Back/selecciones reales, compilación de ambas
variantes y regresiones del catálogo/reproductor. Un test puro o un build exitoso
no cierra esas incidencias. No fusionar con estable hasta validación del usuario.

## Archivos de esta entrega

- `CHANGELOG_TV.md`
- `README.md`
- `app/build.gradle.kts`
- `app/src/androidTestTv/java/ar/com/miflix/client/TvDetailInstrumentation.kt`
- `app/src/main/java/ar/com/miflix/client/MainActivity.kt`
- `app/src/main/java/ar/com/miflix/client/PlaybackScreen.kt`
- `app/src/main/java/ar/com/miflix/client/TvDetailScreen.kt`
- `app/src/main/java/ar/com/miflix/client/TvIntegratedTrailer.kt`
- `app/src/main/java/ar/com/miflix/client/TvPlaybackControls.kt`
- `app/src/main/java/ar/com/miflix/client/TvScreens.kt`
- `app/src/main/java/ar/com/miflix/client/TvTrackPanel.kt`
- `app/src/main/java/ar/com/miflix/client/TvTrackPolicy.kt`
- `app/src/main/java/ar/com/miflix/client/TvTrailerPolicy.kt`
- `app/src/test/java/ar/com/miflix/client/TvTrackPolicyTest.kt`
- `app/src/test/java/ar/com/miflix/client/TvTrailerPolicyTest.kt`
- `docs/TV_0.1.5_VALIDACION.md`
- `tools/check_tv015_contract.py`
- `tools/check_tv_trailer.cjs`

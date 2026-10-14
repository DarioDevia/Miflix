# MiFlix TV 0.1.7-diagnostico / 8

## Base y alcance

Repositorio DarioDevia/Miflix; base `backup/miflix-tv-0.1.6`, commit exacto
`94790b811d0eb88d85457ecedfddf50ce755c148`. HEAD y rama remota coincidían y el árbol
local estaba limpio antes de crear `backup/miflix-tv-0.1.7`. Gradle confirmó
0.1.6 / 7. Stable y respaldos anteriores no se actualizan.

El usuario confirma funcionamiento de DPAD y controles de pistas en 0.1.6. Esta
entrega conserva esos flujos y modifica sólo tráiler TV, distribución de controles
TV y presentación del foco de pósteres. No hay cambios en TDLib, rangos, Media3,
audio/AC3, catálogo, progreso, rutas de navegación ni Mobile. No se abrieron archivos
privados ni se cambia la autenticación. Sin dependencias o permisos nuevos.

## Tráiler: diagnóstico y corrección implementada

Flujo auditado: `Title.trailerUrl` lee `trailer_url`; TrailerLink valida HTTPS,
host YouTube, ID de once caracteres y start; TvTrailerAction abre la integración;
TvTrailerPolicy genera IFrame API oficial con ID/start y origen HTTPS de la app;
TvIntegratedTrailer crea WebView y gestiona lifecycle/errores/foco.

No se recibió la URL exacta del caso y la lectura HTTP del catálogo público no
estuvo disponible en este entorno. No se atribuye el fallo a un enlace inválido
ni al emulador. Tampoco hay Logcat nuevo del proveedor WebView para determinar la
causa única de la pantalla completamente negra.

Defectos de flujo comprobados en código: abrir Ver tráiler sólo preparaba/cueaba
el video (autoplay=0) y exigía otro OK; el watchdog de inicio dependía de una
señal de consola JavaScript; ante errores quedaba montada la superficie WebView
negra. La selección externa no priorizaba YouTube TV y usaba directamente la URL
original, aunque el parser ya tenía ID y posición inicial.

Cambios acotados:

- Tras pulsar Ver tráiler, al quedar listo se solicita `tvPlay()` desde Android,
  con área visible mínima de 200×200 píxeles. No hay autoplay desde la ficha ni
  reproducción sin que el usuario abra el tráiler. Se conserva IFrame oficial,
  controles de YouTube, HTTPS base URL/Referer e identificación de la aplicación.
- El contador del watchdog se arma en Android antes de evaluar JavaScript: 20 s
  sin API lista o 15 s sin respuesta de inicio llevan a una explicación visible.
  Se mantienen error de script/IFrame, HTTP/red, proceso WebView caído y bloqueo
  de reproducción. Los errores TLS se cancelan; nunca se ignoran certificados.
- Ante error se desmonta y destruye WebView, dejando mensaje nativo, Reintentar,
  Abrir externamente y Cerrar. Reintentar crea una sesión/WebView nuevos, reinicia
  errores y temporizadores; no reutiliza una instancia fallida.
- Aceleración por hardware explícita sólo para la Activity TV en su manifest.
  Se registra el estado real de aceleración de la vista; no se supone que basta
  con declararla ni se fuerza software/layer mode. Se agregan versión del proveedor,
  tamaño efectivo, comienzo/commit de página y errores de consola por nivel/línea,
  sin imprimir mensajes arbitrarios, URLs, IDs de sesión ni credenciales.
- Abrir externamente intenta paquete oficial TV, paquete oficial móvil y, si no
  están disponibles, un manejador HTTPS. La URL se construye del ID/start validados
  para abrir el mismo video. Se manejan ActivityNotFoundException/SecurityException.
  Si nadie puede abrirlo, se mantiene MiFlix con un mensaje. No se consultan listas
  de apps ni se añaden permisos de visibilidad.
- Si la apertura externa funciona se cierra la integración y se conserva la ficha;
  Back en la app externa debe devolver a MiFlix. Esto requiere validación local
  porque depende del comportamiento de esa aplicación.
- ON_STOP pausa WebView; ON_RESUME lo reanuda sin forzar reproducción. Cerrar/Back
  libera la vista y restaura foco a Ver tráiler. No interviene ExoPlayer de Telegram.

**No se declara que YouTube funcione ni que la causa única del negro esté resuelta.**
Restricciones de propietario, disponibilidad, identidad/proveedor, red o renderizado
pueden persistir. Si YouTube informa PLAYING pero no presenta fotogramas, los
callbacks no permiten certificar visualmente la imagen: usar la opción externa y
compartir sólo las trazas sanitizadas. No hay elusión de restricciones de YouTube.

## Controles: causa y corrección

La 0.1.6 usaba una Column con Row de botones/tiempos y la barra como segunda hija.
Se reemplaza únicamente ese contenedor por una Row:

**Play/Pausa → Transcurrido → Barra con weight(1f) → Duración total → Engranaje**.

Alineación vertical centrada, botones 48 dp, texto 18 sp y separación horizontal
12 dp. La barra absorbe el ancho central; no ocupa una fila aparte. Se conserva
su foco/borde, habilitación cuando hay duración conocida y callbacks de ±10 s.
Se elimina sólo la constante de espaciado vertical que ya no se usa.

La navegación validada sigue igual: ↓ Play → Audio → Barra; ↑ para regresar.
Sin pistas, Play ↔ Barra. ←/→ sigue seek y OK acciona el botón enfocado. Panel
lateral, Back, pausa, posición, pistas y marcas efectivas no se modifican.
El cambio se aplica al único reproductor TV compartido por películas/series/anime.

## Carátulas: causa y corrección

TvCard no tenía un filtro blanco explícito: usaba clickable con la indicación
predeterminada de Material/Compose, capaz de dibujar una capa de interacción sobre
la imagen al recibir foco. Se desactiva esa capa sólo en TvCard mediante
MutableInteractionSource e indication=null; se mantiene el borde blanco de 3 dp.
No se modifica el bitmap, su alpha, contraste, escala o carga Coil. Es una corrección
de la capa identificada en código; el resultado visual aún debe comprobarse en TV.

Se conservan clickable/Role.Button, requester, callback onFocused, claves, listas,
scroll y recuperación de foco. Otros botones, episodios y Mobile no se restilan.
TvCard se comparte en Inicio, Continuar viendo, Buscar y Secciones TV.

## Pruebas ejecutadas

| Comprobación | Resultado / alcance |
| --- | --- |
| JVM: cinco clases de políticas, Kotlin 1.9.22/JUnit 4.13.2 | 38 pruebas pasaron; incluye las 34 anteriores y cuatro nuevas de URL canónica, paquetes y entradas inválidas |
| tools/check_tv015_contract.py | 6 contratos pasaron: panel/snapshots/lifecycle, guardia Mobile y versión oculta |
| tools/check_tv017_contract.py | 7 contratos pasaron: orden/fila, peso central, foco sin capa, fallo/reintento, watchdog nativo, TLS/fallback y fuentes protegidas |
| JavaScript generado por la función Kotlin | Stub IFrame pasó: tvPlay/toggle/pause, ID/start, origen, controles, resize y callbacks/error/bloqueo |
| Parseo sintáctico Kotlin | Cinco archivos Android modificados sin errores; no comprueba tipos Android/Compose |
| git diff --check | Sin errores |
| Diff de fuentes protegidas respecto de 0.1.6 | PlaybackScreen, TelegramVideo/Session, Catalog, MainActivity, MediaTrackPolicy, TvTrackPanel, TvAudioDiagnostics, TrailerPreview y TrailerLink idénticos |

El runner Android amplía los tres casos compact-controls (película/serie/anime)
con posiciones físicas ordenadas y centros verticales alineados de los cinco
controles. Añade poster-focus-original-colors: fixture de color antes/después del
foco, tolerancia de cuatro niveles RGB en el centro y navegación/OK. Estos casos
**no se ejecutaron aquí**. Son pruebas de UI sintética, no de streaming o YouTube.

No se ejecutaron assembleTvDebug, assembleMobileDebug, tests Gradle completos,
lint ni emuladores. El entorno no dispone de SDK/Build Tools/JDK completo. No se
instaló un entorno Android nuevo ni se intentó controlar Android Studio.
No hay prueba visual superada ni reproducción confirmada en dispositivo real.

## Obtener, compilar e instalar en Windows

Primero `git status --short`; guardar cambios propios antes de cambiar de rama.
No usar checkout forzado, reset, clean ni tocar configuración privada. Con árbol limpio:

```powershell
git fetch origin
git switch --track -c backup/miflix-tv-0.1.7 origin/backup/miflix-tv-0.1.7
git rev-parse HEAD
.\gradlew.bat testTvDebugUnitTest testMobileDebugUnitTest assembleTvDebug assembleMobileDebug
adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk
```

Si ya existe la rama local: `git switch backup/miflix-tv-0.1.7` seguido de
`git pull --ff-only origin backup/miflix-tv-0.1.7`. Sincronizar Gradle en Android
Studio y elegir tvDebug. Usar tu configuración privada local habitual sin abrirla
ni publicarla. La instalación conserva datos si mantiene firma/applicationId.
Versión sólo en Configuración → Acerca de MiFlix: 0.1.7-diagnostico / 8.

Instrumentación opcional sólo en copia/instalación limpia de pruebas sin credenciales
Telegram: el runner exige configuración privada ausente. Construir
assembleTvDebugAndroidTest e instalar app/test APK según instrucciones previas:

```powershell
adb shell am instrument -w -e cases "compact-controls-pelicula,compact-controls-serie,compact-controls-anime,poster-focus-original-colors" ar.com.miflix.client.tv.test/ar.com.miflix.client.TvDetailInstrumentation
```

Repetir también los seis casos track-panel de 0.1.5 y los casos de retorno al menú.
No ejecutar el runner sobre la instalación familiar con sesión/datos.

## Validación manual obligatoria

1. Android TV 1080p: abrir 13 fantasmas → Ver tráiler. Observar carga, solicitud de
   reproducción, imagen y sonido. Probar otro tráiler permitido y conocido. No dar
   por válido un estado READY/PLAYING ni un build exitoso sin ver/escuchar el video.
2. Si falla: la vista debe mostrar explicación, Reintentar, Abrir externamente y
   Cerrar; no quedarse sólo en negro. Reintentar después de recuperar red.
   Probar caída de red, embed no disponible y Back en carga/reproducción/error.
3. Apertura externa con YouTube TV disponible; comprobar video correcto y Back a
   la misma ficha. Repetir sin app oficial: navegador si disponible y mensaje si no
   existe ningún manejador. Nunca debe cerrarse inesperadamente MiFlix.
4. Película, serie y anime: Play, transcurrido, barra, duración y engranaje en una
   sola fila, en ese orden, sin recortes a 1080p y otra resolución habitual. Revisar
   tiempos largos y escalado de fuente. Verificar foco, ±10 s y Play/Pausa.
5. Abrir/cerrar panel lateral con OK/Back. Confirmar pistas, ✓, subtítulos/tamaño,
   reproducción continua o pausa voluntaria y posición sin reinicios.
6. Carátulas de Inicio, Continuar viendo, Buscar y Secciones: conservar colores al
   enfocar, borde visible, OK/Enter, desplazamiento de filas y foco al volver de ficha.
7. Regresión: temporadas/episodios, fullscreen/Back, progreso/Continuar viendo,
   sesión Telegram y suspensión/reanudación. Mobile conserva su recorrido habitual.
8. AC3 fuera de alcance: no esperar nuevos codecs. El usuario informa que
   CodecInspector no encontró AC3/E-AC3 en dos emuladores. TV físico pendiente.

Logcat: MiFlixTvTrailer. Eventos sanitizados: WEBVIEW_PROVIDER, WEBVIEW_LAYOUT,
PAGE_STARTED/PAGE_COMMITTED, PLAY_REQUEST, PLAYER_STATE, YOUTUBE_ERROR,
PLAYBACK_TIMEOUT, WEB_RESOURCE_ERROR, WEB_HTTP_ERROR, TLS_ERROR,
JS_CONSOLE_ERROR y EXTERNAL_HANDLER_*. No compartir mensajes completos de otros
logs históricos que puedan contener enlaces/rutas, ni sesiones/códigos/credenciales.

## Fuentes primarias consultadas

- https://developers.google.com/youtube/iframe_api_reference
- https://developers.google.com/youtube/terms/required-minimum-functionality
- https://developer.android.com/reference/android/webkit/WebView
- https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions

## Estado de entrega

Correcciones implementadas y pruebas disponibles superadas. Compilación Android,
resultado visual, regreso desde YouTube y reproducción real del tráiler pendientes.
No fusionar con estable hasta validación del usuario. No se declara solucionado
AC3 ni confirmado que los tráilers funcionen en dispositivo.

## Archivos de esta entrega

- `CHANGELOG_TV.md`
- `README.md`
- `app/build.gradle.kts`
- `app/src/androidTestTv/java/ar/com/miflix/client/TvDetailInstrumentation.kt`
- `app/src/main/java/ar/com/miflix/client/TvIntegratedTrailer.kt`
- `app/src/main/java/ar/com/miflix/client/TvPlaybackControls.kt`
- `app/src/main/java/ar/com/miflix/client/TvPlayerControlsPolicy.kt`
- `app/src/main/java/ar/com/miflix/client/TvScreens.kt`
- `app/src/main/java/ar/com/miflix/client/TvTrailer.kt`
- `app/src/main/java/ar/com/miflix/client/TvTrailerPolicy.kt`
- `app/src/test/java/ar/com/miflix/client/TvTrailerPolicyTest.kt`
- `app/src/tv/AndroidManifest.xml`
- `docs/TV_0.1.7_VALIDACION.md`
- `tools/check_tv017_contract.py`
- `tools/check_tv_trailer.cjs`

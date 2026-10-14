# MiFlix TV 0.1.6-diagnostico / 7

## Base y alcance

Origen verificado `backup/miflix-tv-0.1.5`, commit
`4d63ccdd9acd47f9fa9727ea900019addab0a968`, árbol local limpio antes de editar.
Rama nueva `backup/miflix-tv-0.1.6`. Stable y respaldos anteriores no se actualizan.

**AC3 software no implementado ni validado en esta entrega.** Ver
[investigación técnica y decisión](TV_0.1.6_AC3_INVESTIGACION.md). Se mantiene el
motor Media3 1.3.1, nativos de plataforma y fallback de inicialización de 0.1.5.
No se incorporan bibliotecas/ABIs/AAR de FFmpeg ni se simula soporte de pistas.

## Implementado

- Controles TV más compactos: botones 48 dp, iconos 30 dp, espacios horizontales
  12 dp y separación de 2 dp entre la fila y la barra. Padding inferior/superior
  16 dp. La fila conserva alineación central: Play/Pausa, transcurrido a la
  izquierda, duración total y Audio y subtítulos a la derecha. Barra debajo.
  La altura nominal baja aproximadamente de 124 dp a 94 dp (cálculo de layout,
  no medición en dispositivo). Texto 18 sp y foco blanco permanecen legibles;
  lectura a distancia sigue pendiente.
- Barra con foco/borde y descripción accesible cuando se conoce duración; con duración desconocida no se enfoca. Se conserva ↓ Play → Audio y
  subtítulos y ↑ Audio y subtítulos → Play. Se extiende con ↓ desde Audio a Barra,
  ↑ desde Barra a Audio. Sin pistas, ↓ Play → Barra y ↑ Barra → Play.
  ←/→ siguen saltando ±10 s desde cualquiera de los objetivos, por el callback
  original; OK conserva Play/Pausa y apertura de panel. La barra no introduce
  slider táctil obligatorio ni cambia el motor de seek.
- Panel 0.1.5, Back, foco restaurado, estado de pausa/reproducción y ✓ permanecen.
- Log del registro AC3 de plataforma al crear Player TV: cantidad y nombres de
  decodificadores que anuncian audio/ac3. Se informa que no hay extensión software
  empaquetada. Una pista UNSUPPORTED_SUBTYPE AC3 explica ese codec expresamente.
  Otros MIME y valores de soporte conservan los mensajes/reglas anteriores.
- Mismo componente para películas, episodios de series/anime y demás videos.
  Mobile mantiene constructor, versión y presentación sin cambios funcionales.
- TDLib, TelegramVideo/RangeDataSource, navegación del catálogo, progreso y YouTube
  no se modifican. Tráiler negro de 13 fantasmas sigue pendiente de validación.
- Versión sólo en Configuración → Acerca de MiFlix. Sin permisos ni dependencias nuevas.

## Comprobaciones automáticas

- 34 tests JVM pasaron usando Kotlin 1.9.22 y JUnit 4.13.2 directamente:
  MediaTrackPolicyTest, TrailerLinkTest, TvTrailerPolicyTest, TvTrackPolicyTest,
  TvPlayerControlsPolicyTest. Incluyen ruta DPAD con/sin pistas, límites,
  seek/media keys, reveal de controles y ocultado bloqueado por panel; mensajes
  AC3 sin alterar compatibilidad, metadatos/idiomas y regresión de trailers.
- 6 comprobaciones estructurales de `tools/check_tv015_contract.py` pasaron.
  El checker ahora acepta el versionado TV vigente y sigue comprobando Mobile
  0.9.2 / 26, propiedad/lifecycle del panel, snapshots y versión oculta.
- JavaScript del tráiler no modificado pasó nuevamente su contrato con IFrame API
  simulada. No comprueba YouTube real.
- Parseo sintáctico Kotlin de seis archivos Android principales: sin errores.
  No equivale a type checking Android/Compose.
- `git diff --check` pasó.
- Se añaden tres casos `compact-controls-pelicula/serie/anime` al runner existente:
  foco Play/Tracks/Barra, ±10 s, panel/Back, pausa/posición y retorno a Play.
  Son UI/callbacks sintéticos, no decoder ni streaming. **No ejecutados aquí.**
- No ejecutados: tests Gradle completos, assembleTvDebug, assembleMobileDebug,
  instrumentación, AAC/AC3 real, medidas de APK/CPU o validación visual. Faltan
  SDK/Build Tools/NDK y JDK completo. No se ejecutaron emuladores remotos.

## Actualizar Android Studio

Primero revisar `git status --short` y guardar cambios propios antes de cambiar
de rama. No borrar/forzar archivos ni configuración privada. Con árbol limpio:

```powershell
git fetch origin
git switch --track -c backup/miflix-tv-0.1.6 origin/backup/miflix-tv-0.1.6
git rev-parse HEAD
.\gradlew.bat testTvDebugUnitTest testMobileDebugUnitTest assembleTvDebug assembleMobileDebug
```

Si la rama local existe: `git switch backup/miflix-tv-0.1.6`, después
`git pull --ff-only origin backup/miflix-tv-0.1.6`. Sincronizar Gradle y elegir tvDebug.
No requiere instalar NDK: esta entrega no agrega ningún módulo nativo.
APK TV: `app/build/outputs/apk/tv/debug/app-tv-debug.apk`. Acerca de MiFlix:
0.1.6-diagnostico / 7. Conservar credenciales locales sin abrirlas ni publicarlas.

Instrumentación opcional: sólo instalación/copia de pruebas limpia sin configuración
Telegram privada; el runner lo exige. Construir assembleTvDebugAndroidTest e instalar
app/test APK según receta de 0.1.5, luego:

```powershell
adb shell am instrument -w -e cases "compact-controls-pelicula,compact-controls-serie,compact-controls-anime" ar.com.miflix.client.tv.test/ar.com.miflix.client.TvDetailInstrumentation
```

Repetir también los seis casos track-panel de 0.1.5. No usar runner en instalación
familiar con sesión/datos.

## Pruebas reales Android TV 1080p

1. Película, serie y anime: comprobar fila de controles compacta, transcurrido,
   duración total, engranaje y barra alineados, márgenes y legibilidad a distancia.
2. Play ↓ Audio ↓ Barra; volver con ↑. Desde Barra, ←/→ debe mover ±10 s, sin
   reiniciar ni cambiar de contenido. Repetir con archivo sin selección de pistas.
3. Abrir panel con OK, recorrer subtítulos/audio, seleccionar pista compatible;
   ✓ representa la selección efectiva. Back/Cerrar conserva posición, selección
   y reproducción, o pausa si el usuario había pausado. Retorna foco al engranaje.
4. House en emuladores reportados: registrar AC3_PLATFORM_DECODERS y snapshots
   MiFlixTracks. Si el entorno sigue sin decoder, se espera que AC3 continúe sin
   selección/sonido y aparezca el motivo. **Eso no es una corrección AC3.**
5. Probar nativamente en un TV con soporte AC3 si está disponible: Stereo español
   y Surround inglés, cambios de pista, seek, sincronía, pause/play y retorno.
   Ninguna prueba aislada demuestra soporte universal.
6. AAC y formatos previamente compatibles: audio, pistas, seek, progreso,
   Continuar viendo, fullscreen y suspensión/reanudación sin regresiones.
7. Inicio, menú lateral, pósteres, fondos, temporadas/episodios e inicio de sesión
   siguen como antes. No versiones en uso normal; sí en Acerca de MiFlix.
8. Tráiler de 13 fantasmas y manejo de errores siguen pendientes como en 0.1.5.
9. Mobile: compilar y comprobar hoja inferior/audio/seek con su recorrido habitual.

No compartir sesiones, códigos, tokens ni archivos privados. Logs nuevos sólo
registran codec/nombres y clases de error; otros logs históricos pueden contener
rutas/enlaces y deben revisarse antes de compartir.

## Estado final

0.1.6 pendiente de compilación y validación local. Mejora visual implementada y
contratos comprobados; AC3 software requiere build nativo reproducible y pruebas,
no una bandera de compatibilidad. No fusionar con estable sin validación del usuario.

## Archivos de esta entrega

- `CHANGELOG_TV.md`
- `README.md`
- `app/build.gradle.kts`
- `app/src/androidTestTv/java/ar/com/miflix/client/TvDetailInstrumentation.kt`
- `app/src/main/java/ar/com/miflix/client/PlaybackScreen.kt`
- `app/src/main/java/ar/com/miflix/client/TvAudioDiagnostics.kt`
- `app/src/main/java/ar/com/miflix/client/TvPlaybackControls.kt`
- `app/src/main/java/ar/com/miflix/client/TvPlayerControlsPolicy.kt`
- `app/src/main/java/ar/com/miflix/client/TvTrackPolicy.kt`
- `app/src/test/java/ar/com/miflix/client/TvPlayerControlsPolicyTest.kt`
- `app/src/test/java/ar/com/miflix/client/TvTrackPolicyTest.kt`
- `docs/TV_0.1.6_AC3_INVESTIGACION.md`
- `docs/TV_0.1.6_VALIDACION.md`
- `tools/check_tv015_contract.py`

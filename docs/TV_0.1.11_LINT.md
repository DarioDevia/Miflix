# MiFlix TV 0.1.11: corrección acotada de Lint

Base: `feature/tv-0.1.11-cinematic-intro`, commit
`eb5559ef299d3939c611cb749a562fcb2ff90a45`.
Rama de corrección: `fix/tv-0.1.11-lint-physical`.
TV conserva `0.1.11-diagnostico` / `12`; Mobile conserva su versión.
No se fusionan ramas ni se cambian dependencias, ABI, firma o reproductores.

## Informe recibido y cambios

El informe local aportado contiene 51 errores y 17 advertencias:

| Archivo | Errores | Corrección |
| --- | ---: | --- |
| PlaybackScreen.kt | 27 UnsafeOptInUsageError | OptIn de Media3 en PlaybackScreen y logMediaTracks |
| TelegramVideo.kt | 23 UnsafeOptInUsageError | OptIn de Media3 en factory y TelegramRangeDataSource |
| Manifiesto TV | 1 IntentFilterExportedReceiver | exported=true explícito para MainActivity |

Se agregan exactamente cuatro anotaciones
`@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)`.
Aceptan el contrato de las API ya utilizadas; no modifican cuerpos de funciones,
selección de pistas, lógica de ExoPlayer ni streaming. No son supresiones de Lint.
La comprobación nueva compara ambos archivos contra la base después de retirar
únicamente esas cuatro anotaciones.

El manifiesto principal ya declara la misma MainActivity como exported=true.
La declaración TV incorpora el mismo valor explícito para su intent-filter
LEANBACK_LAUNCHER. Se compararon los XML fuente; la fusión real generada por AGP
queda pendiente de la compilación local.

Se conserva getIdentifier para el layout de la intro: el recurso existe únicamente
en src/tv/res y la clase está en src/main. Una referencia R.layout directa allí
rompería la variante Mobile. Su reemplazo requeriría otra separación de fuentes;
no se amplía esta corrección para eliminar una advertencia. No se agrega ninguna
baseline ni se desactiva ninguna comprobación.

## Verificaciones ejecutadas y pendientes

- 38 pruebas Python de contratos, alcance, configuración física e intro: aprobadas.
- 54 pruebas JVM existentes: reejecutadas y aprobadas usando las clases compiladas
  durante la recuperación anterior; sus fuentes no cambiaron en esta corrección.
- Análisis sintáctico Kotlin de los dos archivos modificados: aprobado; no equivale
  a comprobación de tipos Android.
- git diff --check y comparación de fuentes/manifiestos: aprobados.
- Configuración Gradle, versiones, dependencias, procedencia TDLib y scripts de
  compilación/inspección APK: idénticos a la base.

Este entorno dispone de Java 17, pero no de javac ni SDK/build-tools Android.
No se ejecutaron compileTvDebugKotlin, compileMobileDebugKotlin, lintTvDebug ni los
unit tests de Gradle de esta corrección. **No se declara Lint solucionado** hasta
obtener una ejecución correcta. No se generó APK nueva ni se inspeccionaron su
manifest fusionado, firma, testOnly o bibliotecas ARM. Tampoco hubo pruebas físicas.

Las 17 advertencias del informe siguen pendientes de reevaluación local. Incluyen
getIdentifier/recurso localizado dinámicamente, avisos de dependencias, preferencias,
WebView y un ObsoleteLintCustomCheck de Compose cuyo detector omite comprobaciones
por incompatibilidad de API. No se actualizan dependencias para resolverlo en esta
entrega. No se garantiza que sean las únicas advertencias de una nueva ejecución.

## Archivos de la corrección

- app/src/main/java/ar/com/miflix/client/PlaybackScreen.kt
- app/src/main/java/ar/com/miflix/client/TelegramVideo.kt
- app/src/tv/AndroidManifest.xml
- tools/check_tv0110_contract.py
- tools/check_tv0111_contract.py
- tools/test_tv_physical_apk.py
- tools/check_tv0111_lint_contract.py (nuevo)
- README.md, CHANGELOG_TV.md y este documento.

Los contratos anteriores se ajustan sólo para admitir las anotaciones y el atributo
explícito. Intro, Hero, recursos MP4/ARM y demás código de aplicación no cambian.

## Windows 11 / Android Studio

Conservar primero cualquier cambio local. Desde la carpeta del repositorio:

```powershell
git fetch origin
git switch --track origin/fix/tv-0.1.11-lint-physical
```

Si esa rama ya existe localmente, usar git switch con su nombre y después
`git pull --ff-only`. Seleccionar JDK 17 y el SDK requerido por el proyecto.
No copiar credenciales al repositorio ni imprimir sus valores.

```powershell
.\gradlew.bat :app:compileTvDebugKotlin :app:compileMobileDebugKotlin :app:lintTvDebug :app:testTvDebugUnitTest :app:testMobileDebugUnitTest
.\tools\build_tv_physical_debug.ps1
```

Revisar el informe Lint y el manifiesto fusionado en Android Studio. El script
existente genera/verifica la APK física en
`app\build\outputs\apk\tv\debug\app-tv-debug.apk` si termina correctamente.
No distribuirla si falla alguna verificación. Conservar la firma usada anteriormente;
si cambia, no desinstalar ni borrar datos para forzar la actualización.

```powershell
adb install -r .\app\build\outputs\apk\tv\debug\app-tv-debug.apk
```

No requiere -t cuando el verificador confirma testOnly=false. También puede
actualizarse desde un administrador de archivos con la misma firma. Confirmar en
TV físico intro/OK/Atrás, Inicio/Hero, DPAD, películas/series, audio/subtítulos,
seek/progreso y retorno sin repetir la intro. Esta lista es pendiente, no aprobada.

## Referencias técnicas

- https://developer.android.com/media/media3/exoplayer/troubleshooting
- https://developer.android.com/build/manage-manifests
- https://developer.android.com/guide/topics/manifest/activity-element#exported

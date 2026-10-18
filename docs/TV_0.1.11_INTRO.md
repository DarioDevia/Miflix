# MiFlix TV 0.1.11-diagnostico / 12

## Recuperación

Se recuperó íntegramente la implementación local desde la base exacta
5fed7e317710d8ad71e19212bffa6f88a727b3f4. Una ejecución intermedia ya había creado
el commit local 279407506ef6cc75b5adda4691b722626a76d122; al retomar esta entrega
GitHub aún no contenía la rama objetivo. No se reconstruyó la intro ni el video.
La rama 0.1.10 sigue intacta. Se completan comprobaciones, documentación y respaldo
en feature/tv-0.1.11-cinematic-intro sin fusión automática.

MP4 recuperado: app/src/tv/assets/miflix_tv_intro.mp4, 2134895 bytes.
SHA-256: ecede13d1d994ba31de549b7d3ca557e64d69c4f3518bbae07caae5841423b17.

## Comportamiento

TvIntroSession permite una intro por proceso nuevo; no interrumpe tareas restauradas.
TvStartupGate no compone ClientApp hasta finalizar. El reproductor ExoPlayer local
es independiente de TDLib, rangos, películas, audio/subtítulos y tráilers.
Sin repetición, con audio original y volumen del sistema. PlayerView sin controles,
texture_view y ajuste fit. Tema TV negro para la ventana inicial.

Final natural, OK/Enter/Atrás, error, timeout 10000 ms o paso a segundo plano
terminan la intro. TvIntroLifetime libera antes de completar, de forma idempotente.
En segundo plano se difiere ClientApp hasta RESUMED; al volver no repite la intro.
El temporizador se cancela al desmontar la composición. Logs sólo de códigos fijos.

## Validación y límites

- 54 pruebas JVM compiladas y ejecutadas juntas con Kotlin 1.9.22/JUnit 4.13.2:
  10 de sesión/concurrencia/teclas/liberación de intro y 44 regresiones existentes.
- 14 pruebas Python de empaquetado y protección de fuentes.
- 6 contratos Hero, 6 del panel y 6 nuevos de arranque/recursos: 32 comprobaciones
  Python en total. Verifican que el Hero, motor de reproducción, autenticación,
  pistas, tráilers y procedencia ARM se conservan frente a 0.1.10.
- Sintaxis PSI de MainActivity y los dos archivos de intro comprobada. PSI no
  verifica tipos Android/Compose ni reemplaza la compilación de la aplicación.
- MP4 original comprobado por hash, tamaño y ffprobe: H.264, 1280×720, 24 FPS,
  yuv420p; AAC estéreo 48 kHz; 4,01 s. Decodificación completa con ffmpeg sin errores
  en Linux; esto no prueba decodificación, audio o fluidez en Android TV.
- Configuración ARM/procedencia intacta. El verificador exige cuatro ELF reales,
  MP4 original, versión 0.1.11/12, testOnly=false y firma por apksigner. Imprime el
  SHA-256 de la APK que se compruebe localmente.

Este entorno no dispone de SDK/Build Tools ni JDK completo. Un intento anterior
de Gradle también falló al descargar gradle-8.2-bin.zip (Network is unreachable).
No se ejecutaron tests Gradle completos, lint, compilación Android, inspección de
APK nueva, emulador o pruebas físicas. No hay APK 0.1.11 ni SHA-256 de APK para
entregar aquí. Las pruebas de política no validan foco, audio o transición reales.

Restaurar una Activity/tarea (incluso después de muerte del proceso) no vuelve a
mostrar la intro, para no interrumpir la navegación restaurada. Un lanzamiento
fresco en proceso nuevo sí la reproduce. Home del control no se interpreta como
muerte de proceso. Si no existe sesión Telegram, después de la intro se conserva
el flujo original de autenticación; no se saltea para forzar Inicio.

El arranque usa fondo negro; el splash que imponga Android/fabricante antes de que
la app pueda dibujar no se puede certificar ni eliminar desde estas pruebas. La
introducción usa su propia ventana fullscreen y no cambia la ventana del player.

## Archivos de la entrega

- README.md, CHANGELOG_TV.md y app/build.gradle.kts (versión TV solamente).
- MainActivity.kt (sólo el arranque; ClientApp permanece idéntico).
- Nuevos TvCinematicIntro.kt, TvIntroPolicy.kt y TvIntroPolicyTest.kt.
- app/src/tv/AndroidManifest.xml (sólo tema de ventana inicial TV).
- app/src/tv/assets/miflix_tv_intro.mp4 (original, sin conversión).
- app/src/tv/res/layout/miflix_tv_intro_player.xml y res/values/styles.xml.
- tools/build_tv_physical_debug.ps1 (añade lint TV/Mobile).
- tools/verify_tv_physical_apk.py, tools/test_tv_physical_apk.py,
  tools/check_tv0110_contract.py y nuevo tools/check_tv0111_contract.py.
- docs/TV_0.1.11_INTRO.md.

## Compilar en Windows

Guardar primero cambios propios. Con árbol limpio:

```powershell
git fetch origin
git switch --track -c feature/tv-0.1.11-cinematic-intro origin/feature/tv-0.1.11-cinematic-intro
.\tools\build_tv_physical_debug.ps1
```

Si la rama ya existe: git switch feature/tv-0.1.11-cinematic-intro y
git pull --ff-only origin feature/tv-0.1.11-cinematic-intro.
El script ejecuta tests TV/Mobile, lint TV/Mobile, assembleTvDebug e inspección
MP4/versión/manifest/cuatro ELF y firma con apksigner. Requiere el SDK, Python y
la configuración privada local existente. No cambiar keystore ni borrar datos.
APK esperada: app/build/outputs/apk/tv/debug/app-tv-debug.apk.
Sólo distribuir si todas las comprobaciones pasan.

## Prueba física pendiente

1. Iniciar con proceso cerrado: video completo con audio y transición negra sin
   destello de Inicio, controles ni pantalla intermedia.
2. Omitir con OK y Atrás: Inicio aparece una vez y la tecla no abre una película.
3. Home/suspender durante intro: no queda audio; volver abre Inicio sin repetirla.
4. Volver de ficha, película y tráiler: intro no se repite; Hero conserva su sesión.
5. Proceso nuevo: intro vuelve. Restauración de tarea no debe interrumpirse.
6. Confirmar rotación Hero fuera de foco, películas, tráilers, seek, pistas y progreso.
7. Probar error/archivo ausente en una copia de pruebas: Inicio antes de 10 s,
   sin bloqueo. No modificar el MP4 original de la rama publicada para esta prueba.

No se fusiona en 0.1.10 ni en miflix-tv. Publicación de código diagnóstico;
validación Android y física pendiente.

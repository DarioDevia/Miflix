# Intro oficial — Mobile 0.9.6 / 30

Base: `miflix-cliente`, `8589ea755af17f8175ca76002752aac766b34a45` (0.9.5 / 29).
Rama de trabajo: `feature/mobile-0.9.6-intro`. No fusionar automáticamente.

## Recurso

MP4 oficial adjunto, copiado sin transcodificar a `app/src/main/res/raw/miflix_intro.mp4`.
4,010 s; 720 × 1280; H.264, 24 FPS, yuv420p; AAC; 1.890.276 bytes.
SHA-256: `2b59bb7315b537cb07958fdd5619751bd1c2ec8a3f079790f6820b48c60a99f0`.
FFprobe verifica propiedades; FFmpeg decodifica todas las pistas sin errores. Esto no prueba un decodificador Samsung.

## Integración y ownership

- MainActivity mantiene ClientApp compuesto: el catálogo cacheado/remoto y la sesión se inicializan una sola vez mediante el flujo preexistente. La intro no condiciona autenticación ni requiere red.
- IntroSession representa el lanzamiento nuevo. La Activity existente maneja orientation/screenSize: no se reemplaza el Player al girar. Una Activity restaurada omite la intro, incluso si la anterior no había terminado; así nunca se reinicia sobre navegación restaurada.
- IntroPreview abre un Dialog inmersivo con su propia Window, Player y PlayerView. No toca orientación ni flags del Player principal. TextureView permite componer el icono inicial sobre el video hasta onRenderedFirstFrame. FIT conserva imagen completa y bandas según orientación.
- Sin controles; audio original, volumen multimedia normal y gestión de foco de Media3. No se fuerza el volumen del teléfono ni se evade silencio/foco Android.
- STATE_ENDED, PlaybackException, fallo de preparación, ON_PAUSE/ON_STOP o watchdog de 8 s finalizan. No hay polling ni reintentos. Atrás permite omitir mediante el dismiss normal del Dialog.
- Se desacopla PlayerView, se libera el Player independiente y después se oculta la intro. Dispose elimina listener/observer y la liberación es idempotente. La Window del Dialog desaparece sin alterar la Activity. No hay audio oculto ni reinicio al regresar.
- Se conserva el orden histórico del Player principal: player.release() → video.clear(). Ningún cambio en PlaybackScreen, TelegramVideo, TDLib, DataSource, trailers ni progreso.
- Las novedades no se presentan ni consumen mientras introActive es true.

## Verificaciones automatizadas y límites

IntroSessionTest comprueba lanzamiento nuevo, restauración sin intro, finalización idempotente y nueva sesión. AppUpdatesTest comprueba versión/historial actuales. Se ejecuta la suite existente y lint/build mediante Actions si el entorno local no descarga Gradle. El workflow habilita exclusivamente la nueva rama además de la rama anterior para producir artifacts de diagnóstico.

No existen tests instrumentados ni emulador configurados. Los tests JVM no demuestran sincronía, superficies, lifecycle real ni ausencia absoluta de leaks. No se afirma validación física de la intro.

## Samsung A21s / A14 — validación pendiente

1. Para actualizar la instalación familiar, compilar y firmar con el mismo keystore permanente y configuración privada local. El artifact debug de CI no tiene configuración privada y su firma no actualiza una release firmada por el usuario; no desinstalar la instalación habitual solo para probarlo, porque se pierde estado local.
2. Abrir desde cierre real: intro con imagen/audio una vez, sin controles; al terminar Inicio (o conexión Telegram si falta autenticar). Comprobar novedades solo después, y Acerca de 0.9.6 / 30.
3. Sin Internet: intro idéntica; luego catálogo cacheado/estado offline habitual.
4. Rotar durante los 4 segundos: no reinicia ni corta audio; conserva proporciones en portrait y landscape.
5. Enviar a background durante intro y volver; repetir apagando pantalla. Debe detener/liberar inmediatamente y no repetir al regresar. Abrir/cerrar otra app después de intro tampoco la repite.
6. Navegar Inicio/Buscar/Detalle y volver; reproducir película/episodio y salir: sin intro adicional.
7. Reanudar película y comprobar posición, seek/barra, ±10, brillo, volumen, fullscreen/Back/giro, pistas/tamaño de subtítulos, episodios/temporadas, Continuar viendo y trailers imagen/audio.
8. Filtrar `tag:MiFlixIntro`: CREATE → FIRST_FRAME → FINISH reason=ENDED → RELEASED. En interrupción: FINISH reason=BACKGROUND → RELEASED. Comprobar que no crea otro Player de intro al navegar. TIMEOUT/ERROR deben llevar a la app; no se suministra recurso dañado ni un botón de fallo en la build.
9. Repetir arranque/cierre varias veces y observar si quedan audio, ventanas o decodificadores activos. La inspección de fugas real requiere dispositivo/Profiler; no se agrega dependencia de diagnóstico.

No se modifican credenciales, sesiones ni configuración privada. El problema histórico de suspensión continúa siendo un diagnóstico separado de 0.9.5; esta entrega no presume corregirlo.

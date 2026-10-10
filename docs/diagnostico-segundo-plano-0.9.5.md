# Segundo plano — MiFlix Mobile 0.9.5 diagnóstico

Base: rama miflix-cliente, 82948cf069a17a2442d6a043825103c162be24f4, 0.9.4 / 28.
Entrega: 0.9.5 / 29. La causa del incidente real sigue sin confirmarse.

## Auditoría y alcance

- MainActivity conserva orientación/screenSize sin recreación por giro. Sus nuevas trazas de START/RESUME/PAUSE/STOP observan powerSave/deviceIdle; no solicitan excepciones de batería.
- PlaybackScreen guarda resumePosition/resumePlay y observa START/STOP. La resolución se cancela al cambiar foreground. Media3 guarda periódicamente y al STOP/salida mediante PlaybackProgressStore existente.
- VideoPlayer en ON_STOP guarda posición/intención, libera ExoPlayer, limpia TelegramVideo y notifica onStopped para resolver nuevamente al volver. El Dispose posterior es idempotente. Esta conducta no cambia en 0.9.5.
- TelegramSession es singleton de applicationContext; la autorización persiste en TDLib. Esta entrega no crea clientes, sesiones ni ciclos de reconexión adicionales. UpdateConnectionState se usa solo para diagnóstico.
- GetMessageLinkInfo usa una continuación cancellable sin timeout propio. Una respuesta que no llega puede dejar la preparación pendiente. No hay evidencia del incidente que justifique asignarle un timeout o reiniciar la sesión en esta revisión.
- DownloadFile mantiene su timeout de 45 s. DeleteFile mantiene 5 s, incluso en finally. Cancelar la continuación no demuestra que TDLib haya cancelado la operación remota: TD_LATE_RESPONSE permite observar respuestas tardías.
- TelegramVideo.read y clear son sincronizados. read espera downloadRange mediante runBlocking; clear puede esperar esa lectura, incluso después de player.release(). VIDEO_CLEAR_BEGIN/COMPLETE permite medirlo sin cambiar locks, rangos ni ownership.
- Estado de conexión, autorización, solicitudes pendientes y Media3 deben correlacionarse. No se deduce falta de sesión a partir de buffering, ni se culpa a batería por una mera coincidencia.

Sin Logcat de la reproducción fallida ni dispositivo con sesión privada no es posible demostrar una causa exacta. No se implementa una corrección de recuperación especulativa. Quedan pendientes la reproducción del fallo y una corrección mínima basada en sus eventos.

## Logcat seguro

Seleccionar ar.com.miflix.client y filtrar `tag:MiFlixLifecycle`. Alternativa: `adb logcat -v time -s MiFlixLifecycle:D`.

El tag contiene solo nombres de estados/operaciones, contadores diagnósticos, tiempos, posiciones y banderas. No incluye teléfono, códigos, 2FA, API ID/hash, URLs ni objetos de sesión. El contador request es local y efímero, no un identificador de Telegram. pending cuenta continuaciones activas en MiFlix, no la cola nativa de TDLib; una operación remota puede continuar después de cancelar la continuación. No registrar llamadas de autorización ni sus parámetros. Revisar igualmente cualquier captura antes de compartirla.

Eventos esperados al pausar, ir a background y volver:

1. ACTIVITY_PAUSE/STOP y SCREEN_CHECKPOINT muestran posición/intención y estado de resolución.
2. TD_SNAPSHOT muestra authorization, connection, clientPresent, generation, pending y oldestPendingMs. Authorization Ready no implica por sí sola conexión disponible.
3. PLAYER_RELEASE → PLAYER_RELEASE_COMPLETE → VIDEO_CLEAR_BEGIN → VIDEO_CLEAR_COMPLETE. PLAYER_RELEASED_CLEAR_VIDEO confirma el orden histórico.
4. ACTIVITY_START/RESUME y RESOLVE_PLAYBACK con la posición/intención anterior.
5. TD_REQUEST_START/END para GetMessageLinkInfo, DownloadFile y DeleteFile; resultado COMPLETE, CANCELLED, TIMEOUT o tipo de error, sin mensaje crudo.
6. PLAYER_CREATE y PLAYER_LIFECYCLE muestran state/position/duration/buffered/playWhenReady. Estos registros son por evento, no por frame.

Si se atasca:

- REQUEST_START sin END y pending>0: anotar operación, estado de conexión y antigüedad.
- RELEASE sin COMPLETE: espera en liberación de Media3.
- VIDEO_CLEAR_BEGIN sin COMPLETE: espera en limpieza; correlacionar lectura/rango pendiente.
- Resolución completada y Player creado con state=2: buffering en Media3; correlacionar descarga y red.
- TD_LATE_RESPONSE: respuesta posterior a cancelación/timeout; no se aplica a la continuación cancelada.
- Más de un TD_CLIENT_CREATE sin logout/reset/cierre real: conservar logs para investigar; la instrumentación no corrige ni provoca esa situación.

## Validación automatizada y sus límites

Los tests JVM prueban datos de versión e historial empaquetado, aviso una vez por versión y sus guardas de Home/RESUMED, y contabilidad idempotente de solicitudes concurrentes/finalizadas. Se mantienen las suites de progreso, scrubbing, controles, navegación y selección de pistas.

No equivalen a tests Android de Activity/Window, reconexión TDLib, red real, ocho horas de Doze ni gestos físicos. No hay infraestructura de instrumentación/emulador configurada. No se agregan archivos multimedia, credenciales ni una simulación de TDLib que pretenda validar esos casos.

## Pruebas en Samsung (pendientes)

Compilar con propiedades privadas locales y firmar con el mismo keystore permanente. Debug/unsigned de CI no actualizan la release instalada.

1. **Novedades:** actualizar desde 0.9.4; llegar a Inicio, cerrar aviso; cerrar/reabrir y volver desde background: no debe repetirse. Restaurar una reproducción no debe mostrar el aviso sobre Player.
2. **Acerca de offline:** abrir Configuración → Acerca de con modo avión; verificar MiFlix, 0.9.5-diagnostico, versionCode 29, DarioDevia e historial desplazable. No requiere cargar catálogo remoto.
3. **Pausado/background:** reproducir >30 s, pausar, anotar posición; ir a otra app 5–10 min y volver. Debe conservar pausa/posición; pulsar Play. Capturar desde antes de PAUSE hasta recuperación o atasco.
4. **Pantalla apagada:** repetir durante unos minutos y varias horas, tanto pausado como reproduciendo, vertical/fullscreen. No habilitar un WakeLock ni alterar globalmente la política de batería.
5. **Red:** durante reproducción retirar Internet; esperar el timeout/error existente. Volver a conectar y reintentar mediante el flujo actual. Registrar cambios de conexión, requests y posición; no afirmar recuperación hasta verla. Repetir background sin red y regresar con red.
6. **Repeticiones:** realizar varias idas/vueltas a background. Confirmar que cada Player antiguo se libera/limpia una sola vez, conserva posición e intención y no aparece una sesión adicional. No forzar cierre antes de capturar el fallo.
7. **Controles:** tap 25/75 %, drag adelante/atrás, seek final único, ±10, Play/Pausa, auto-hide, brillo/restauración, volumen físico/slider, audio/subtítulos/tamaño, fullscreen/Back/giro, episodios entre temporadas, Continuar viendo y trailers imagen/mute/unmute.

Las pruebas 3–6 son las necesarias para diagnosticar y decidir una corrección posterior. Si falla, entregar el tag MiFlixLifecycle, secuencia, duración en background, red usada y posición aproximada; no compartir credenciales, base TDLib ni propiedades privadas.

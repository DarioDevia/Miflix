# MiFlix Cliente Android 0.7.1

Series y anime con publicaciones navegables muestran controles ⏮/⏭ para ir al episodio reproducible anterior o siguiente cuando aparecen los controles del video. La búsqueda sigue el orden del catálogo, cruza temporadas y omite episodios sin enlace válido y temporadas vacías. El destino usa el diálogo habitual **Continuar desde... / Empezar desde el principio** si tiene progreso. El salto antes del final conserva el progreso del episodio actual; un episodio terminado se marca completado. Se retiraron la cuenta regresiva y el inicio automático: los créditos no activan ningún salto. Las películas no muestran controles de episodios.

La ficha muestra `Audio: ...` cuando el catálogo trae el campo opcional `audio`, que describe el archivo real y no el idioma original de TMDB. Las series no muestran el botón general Reproducir: se inicia cada episodio desde su tarjeta y su propio `telegram_url`. Películas y anime conservan su botón general cuando el enlace es válido.

## Progreso de reproducción

MiFlix guarda en el dispositivo la posición, duración conocida y fecha de cada video. Películas sin episodios usan `Title.id`; los episodios usan título y temporada, más `tmdb_id` si existe, `Episode.id` antiguo o el enlace como respaldo. Si el botón Reproducir de la ficha apunta a un episodio, comparte su progreso. Las publicaciones del índice de anime, cuyos IDs se generan por posición en la lista, usan una clave derivada de su enlace de mensaje para que reordenarlas no mezcle progresos. Las claves se resumen antes de guardarse en preferencias privadas.

Se escribe como máximo cada 15 segundos de reproducción, al pasar a segundo plano y al salir del reproductor, siempre antes de liberar ExoPlayer. Menos de 30 segundos no generan una opción de continuar. Se borra el progreso cuando resta como máximo el menor valor entre cinco minutos y el 5 % de la duración, o al terminar el video. Al volver a tocar Reproducir, MiFlix ofrece Continuar desde la posición guardada o Empezar desde el principio; esta segunda opción borra la posición antigua. El progreso se excluye de copias de seguridad y transferencias de Android mediante reglas específicas.

## Instalación familiar y actualización del catálogo

El catálogo predeterminado es `https://miflix-catalogo.deviadario.workers.dev/catalogo.json`. Una instalación nueva no requiere configurarlo. MiFlix muestra primero la copia local válida, consulta el remoto en segundo plano al abrir o volver a Inicio si pasaron al menos diez minutos desde la última comprobación, y mantiene la copia anterior ante errores. En Inicio, deslizar hacia abajo fuerza una consulta inmediata; durante reproducción no hay consultas programadas. Configuración muestra la fecha de la última respuesta remota válida y deja la URL manual en Opciones avanzadas.

Para compilar en tu PC una APK familiar, creá **solo en la raíz del proyecto local** el archivo `telegram.local.properties` con estas dos líneas (sustituí los ejemplos por tus datos, nunca los envíes a GitHub):

```properties
TELEGRAM_API_ID=123456
TELEGRAM_API_HASH=0123456789abcdef0123456789abcdef
```

El API ID debe ser un entero positivo y el API hash, 32 caracteres hexadecimales. Android Studio: abrí la raíz del proyecto, creá el archivo junto a `settings.gradle.kts`, sincronizá Gradle (**File > Sync Project with Gradle Files**), elegí la variante `debug` y ejecutá **Build > Build APK(s)**. El APK estará en `app/build/outputs/apk/debug/app-debug.apk`. Instalalo en el teléfono del familiar: MiFlix reconocerá la configuración y ofrecerá conectar su propia cuenta de Telegram con teléfono, código y 2FA cuando corresponda. Cada teléfono crea su propia sesión TDLib.

`telegram.local.properties` y `local.properties` están ignorados por Git. Antes de compartir cambios, verificá `git check-ignore telegram.local.properties` y `git status --short`; nunca uses `git add -f` con ese archivo. No copies `app/build/` ni la base de TDLib a Git. Si falta el archivo privado, Actions y el desarrollo siguen compilando y la app ofrece el ingreso manual de API ID/hash. El APK familiar contiene los identificadores de aplicación y quien tenga el APK podría extraerlos: compartilo solo con familiares autorizados. No incluye la sesión Telegram de quien compila.

La versión 0.5.2 acepta `puntuacion` numérica, texto decimal o texto con sufijo `/10`. Si falta o tiene un formato desconocido, omite solo esa puntuación y conserva el resto del catálogo. No cambia el formato publicado por MiFlix Admin.

## Ficha y barra de estado

La barra de estado usa el fondo oscuro de MiFlix y los iconos del sistema claros. El contenido continúa bajo los insets normales; el reproductor conserva su modo inmersivo al entrar en pantalla completa.

La ficha muestra imagen, título, año/duración/calidad, géneros y, si existen en el catálogo, audio, sinopsis, puntuación (escala de 0 a 10), dirección y reparto. El botón Reproducir general aparece para películas y anime con enlace válido; en series se reproduce desde cada episodio. El enlace de Telegram sigue siendo necesario para reproducir, pero ya no aparece como botón en la ficha normal. Los accesos de respaldo en errores del reproductor permanecen disponibles.

Los campos opcionales nuevos por título son `puntuacion` (número entre 0 y 10), `director` (texto) y `reparto` (lista de textos). Para que aparezcan con datos reales, MiFlix Admin deberá publicarlos en cada item de `catalogo.json`; no se agregan valores de ejemplo a la aplicación ni se modifica Admin aquí. Los catálogos previos siguen funcionando aunque omitan esos campos.

La configuración de Telegram todavía pide API ID/hash; cada teléfono mantiene su propia sesión. Antes de simplificar el acceso familiar hay que decidir cómo distribuir una credencial de aplicación dedicada sin publicarla en este repositorio. No se integran credenciales ni sesiones personales en el APK.

## Reproductor móvil

La rotación se maneja en `MainActivity` sin recrear la Activity ni el Player. Al activar pantalla completa se solicita orientación horizontal, se ocultan temporalmente las barras del sistema y se conserva el mismo `ExoPlayer`/`TelegramVideo`; Atrás sale primero de pantalla completa. Como respaldo para otras recreaciones de Activity, se conserva la posición y `playWhenReady` para reanudar la misma publicación. `player.release()` sigue ocurriendo antes de `video.clear()` al abandonar el reproductor.

El reproductor presenta Play/Pausa, tiempo, duración, barra de progreso y botón de pantalla completa. Un toque simple muestra u oculta controles; doble toque en la mitad izquierda retrocede 10 segundos y en la derecha avanza 10 segundos. Tanto la barra como los gestos usan `ExoPlayer.seekTo`, sobre el DataSource por rangos ya validado. Las trazas `MiFlixPlayback` registran `ORIENTATION_CHANGE`, `FULLSCREEN_ENTER`/`FULLSCREEN_EXIT` y `SEEK` sin agregar registros por cada fotograma.

Prueba en teléfono: reproducir, doble toque derecho e izquierdo, mover la barra, girar vertical/horizontal y volver, entrar/salir de fullscreen, Atrás dentro de fullscreen y finalmente Atrás para abandonar. Al girar no deben aparecer `PLAYER_DISPOSE` ni `CACHE_CLEAR`; al abandonar deben aparecer `PLAYER_DISPOSE` → `PLAYER_RELEASED_CLEAR_VIDEO` → `CACHE_CLEAR`, en ese orden.

## Compilación de diagnóstico

La compilación `0.7.1-diagnostico` conserva las trazas con la etiqueta `MiFlixPlayback` en Logcat. Se conserva el ownership corregido en 0.4.2: `clear()` se ejecuta al liberar el Player. En Android Studio, seleccioná el proceso `ar.com.miflix.client`, filtrá `tag:MiFlixPlayback`, iniciá la reproducción de un video y conservá las líneas desde `NAV_PLAY` hasta `PLAYER_ERROR` o `PLAYER_STATE`. También podés ejecutar `adb logcat -c` y luego `adb logcat -v time -s MiFlixPlayback:D`. El registro incluye el enlace del mensaje y la ruta temporal del video; revisalo antes de compartirlo. Nunca compartas códigos de Telegram, API hash ni datos de inicio de sesión.

Eventos clave: `RESOLVE_RESULT` indica el mensaje; `VIDEO_FILE`, el ID/tamaño; `RANGE_REQUEST` y `RANGE_RESULT`, el rango y el estado de TDLib; `RANGE_READ`, los bytes físicos leídos; `DS_OPEN`/`DS_READ`/`DS_EOF`, los bytes entregados a Media3; `PLAYER_TIMELINE`/`PLAYER_ERROR`, reconocimiento del video y fallo; `PLAYER_DISPOSE`/`PLAYER_RELEASED_CLEAR_VIDEO`/`CACHE_CLEAR`, el cierre en ese orden.

Aplicación cliente independiente de MiFlix Admin. Lee el catálogo público de Cloudflare, conserva la última copia válida y permite buscar películas, series y anime. El flujo Admin → Worker/KV → Android no cambió.

## Novedad: reproducción interna

- La ficha ofrece **Reproducir** para películas y anime con enlace válido. Las series se reproducen desde sus episodios. **Abrir publicación en Telegram** queda disponible como alternativa de diagnóstico en estados del reproductor.
- TDLib resuelve el enlace al mensaje con la cuenta de Telegram que inicia sesión dentro de MiFlix. Un enlace de mensaje no es una URL directa de video. El mensaje debe contener un video o un archivo de tipo video.
- Media3 pide los bytes a un DataSource de MiFlix. TDLib descarga solo tramos de 1 MiB usando `downloadFile(fileId, priority, offset, limit, true)`; al hacer seek se solicita la nueva posición. No es necesario esperar el archivo completo. La conexión y el formato del video afectan el tiempo inicial y el buffering.
- La cuenta debe pertenecer al canal. Cada instalación usa la cuenta de esa persona. Si Telegram pide correo, código o contraseña de dos pasos, la app los solicita. No se incluyen tokens de sesión ni el API ID/hash en GitHub ni en Cloudflare.
- Configuración permite cerrar la sesión de Telegram en MiFlix o corregir las credenciales de la aplicación en este teléfono.
- Cada tramo leído se copia a una caché temporal LRU de hasta 8 MiB en memoria. Después se llama a `deleteFile` para retirar de TDLib la copia parcial de ese video. Al salir del reproductor se vacía la caché propia. Un seek a un tramo expulsado vuelve a descargarlo. TDLib conserva sus propios datos de sesión y otros archivos fuera de esta caché de reproducción.

## Dependencia de TDLib

Usamos el paquete comunitario io.github.tdlib-android:core:0.1.1, que distribuye binarios nativos precompilados y clases Java de TDLib para Android. Es un componente de terceros: el proyecto oficial TDLib publica instrucciones para compilar su propio binario Android, pero no un AAR oficial en Maven Central. Media3 1.3.1 reproduce los archivos. No se implementó un protocolo alternativo ni una API privada.

## Instalar y probar

1. Abrí el proyecto con Android Studio, JDK 17 y Android SDK 34. Ejecutá gradlew.bat testDebugUnitTest assembleDebug.
2. Instalá app/build/outputs/apk/debug/app-debug.apk en Android 8 o superior. La instalación sobre una versión anterior conserva la URL del catálogo cuando está firmada con la misma clave de depuración.
3. Comprobá que MiFlix sigue mostrando el catálogo. Su endpoint verificado es https://miflix-catalogo.deviadario.workers.dev/catalogo.json y la URL guardada en Configuración sigue vigente.
4. En Telegram oficial, uní la cuenta al canal privado. En my.telegram.org creá las credenciales API ID y API hash para tu propia aplicación Telegram. **No compartas códigos de inicio de sesión ni contraseña con otras personas.**
5. Abrí una ficha con enlace a un mensaje de video y tocá Reproducir en MiFlix. Configurá el API ID/hash en ese teléfono y seguí los pasos de autorización de Telegram. Verificá que empieza antes de descargar todo el archivo, pausa, seek cerca del final y regreso a la ficha. La caché parcial necesita conexión para los tramos aún no cargados.
6. Si el enlace apunta a un texto o tema sin video, se explica el problema y podés abrir la publicación en Telegram para revisar el enlace exportado desde MiFlix Admin.

## Arquitectura

MainActivity.kt mantiene la interfaz existente y abre la ruta de reproducción. TelegramSession.kt guarda la sesión de TDLib dentro de la aplicación, resuelve los enlaces y solicita rangos. TelegramVideo.kt implementa la caché acotada y el DataSource de Media3. PlaybackScreen.kt muestra autenticación, buffering y PlayerView. Catalog.kt mantiene la descarga, validación y caché del catálogo; Cloudflare Worker y MiFlix Admin no se modifican.

La compilación automatizada de GitHub Actions ejecuta testDebugUnitTest y assembleDebug y adjunta el APK. Que la compilación pase confirma dependencias y código, pero el inicio de sesión y la reproducción deben verificarse en un teléfono con cuenta autorizada y una publicación real con video.

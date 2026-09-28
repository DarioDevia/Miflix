# MiFlix Cliente Android 0.4.1

Aplicación cliente independiente de MiFlix Admin. Lee el catálogo público de Cloudflare, conserva la última copia válida y permite buscar películas, series y anime. El flujo Admin → Worker/KV → Android no cambió.

## Novedad: reproducción interna

- La ficha ahora ofrece **Reproducir en MiFlix** para una publicación con enlace válido. Permanece **Abrir publicación en Telegram** como alternativa.
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

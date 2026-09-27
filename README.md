# MiFlix cliente · Android celular

Aplicación cliente separada del administrador. Su identificador es `ar.com.miflix.client`, por lo que no reemplaza la instalación de Mi Videoteca. Usa Kotlin y Jetpack Compose. Lee `catalogo.json` remoto (`schema_version` 1.x), muestra películas, series y anime, permite buscar, abrir fichas y conserva la última copia válida para uso sin conexión. Si el servidor devuelve HTML, un estado HTTP de error o un JSON inválido, mantiene esa copia y muestra el motivo.

## Estado funcional (0.2.0)

- Portada destacada, filas horizontales de carátulas, búsqueda, categorías y fichas: implementadas.
- Ícono de MiFlix aportado por el propietario del proyecto.
- El enlace privado de invitación al canal se guarda en el teléfono. Cada usuario se une desde Telegram con su propia cuenta. Se abren allí las publicaciones de películas y episodios.
- La URL HTTPS del catálogo se configura una vez en cada instalación; al abrir/volver a la app y cada 30 minutos mientras está abierta, se comprueba la versión remota y se guarda la última copia válida.
- Inicio de sesión Telegram dentro de MiFlix y reproducción dentro de la app: **pendientes**. Un `t.me/c/...` es un enlace a un mensaje, no una URL directa del archivo de video. No se puede reproducir como video sin autenticar la cuenta y obtener el archivo mediante la API de Telegram.
- Esta versión requiere compilar un nuevo APK; el APK 0.1.0 anterior no contiene los cambios.

## Arquitectura

`MainActivity.kt` muestra Inicio → sección → ficha y ajustes. `Catalog.kt` descarga, valida y guarda el JSON. La app administradora publica el catálogo; este proyecto es solo cliente. La siguiente etapa agregará una capa `TelegramSession` con TDLib y luego un reproductor conectado a sus archivos. La API ID y API hash de Telegram deberán obtenerse para **esta aplicación**; no se incluyen credenciales personales, tokens de bot ni claves administrativas en el código o el catálogo. Cada familiar entrará con su propia cuenta que tenga acceso al canal.

## Probar

1. Abrí esta carpeta como proyecto en Android Studio (JDK 17, SDK Android 34).
2. Ejecutá `./gradlew testDebugUnitTest assembleDebug` (en Windows: `gradlew.bat testDebugUnitTest assembleDebug`).
3. Instalá `app/build/outputs/apk/debug/app-debug.apk` en Android 8 o superior.
4. En **Configuración**, pegá la URL HTTPS que responde con el JSON real. Si el canal es privado, también pegá su enlace de invitación (`https://t.me/+…`). Guardá los ajustes y abrí el canal con Telegram desde el inicio de la app. Unirse al canal es una acción personal en la cuenta de cada familiar.
5. Verificá que el catálogo y las fichas coincidan con el administrador. Desactivá Internet: debe mostrarse **Copia local** con los mismos títulos. El botón de cada película/episodio abre la publicación con Telegram.

No se incluye una URL predeterminada porque el endpoint del administrador todavía no está verificado. Para que los familiares no tengan que escribirla habrá que acordar una URL HTTPS estable y agregarla como valor predeterminado en una siguiente versión; un canal privado no debe publicarse con un enlace de invitación hardcodeado en un repositorio público. El archivo `catalogo_fallback.json` está vacío a propósito.

Se incluye un flujo opcional de GitHub Actions que compila y adjunta el APK desde un repositorio cuyo directorio raíz sea esta carpeta.

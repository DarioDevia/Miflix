# MiFlix cliente · Android celular

Aplicación cliente separada del administrador. Su identificador es `ar.com.miflix.client`, por lo que no reemplaza la instalación de Mi Videoteca. Usa Kotlin y Jetpack Compose. Lee `catalogo.json` remoto (`schema_version` 1.x), muestra películas, series y anime, permite buscar, abrir fichas y conserva la última copia válida para uso sin conexión. Si el servidor devuelve HTML, un estado HTTP de error o un JSON inválido, mantiene esa copia y muestra el motivo.

## Estado funcional (0.1.0)

- Catálogo, búsqueda y fichas: implementados en código fuente.
- Abrir enlace de la publicación con Telegram: implementado; requiere acceso al canal privado desde la cuenta de Telegram del teléfono.
- Inicio de sesión Telegram dentro de MiFlix y reproducción dentro de la app: **pendientes**. Un `t.me/c/...` es un enlace a un mensaje, no una URL directa del archivo de video. No se puede reproducir como video sin autenticar la cuenta y obtener el archivo mediante la API de Telegram.
- APK: aún no compilado ni instalado en un teléfono.

## Arquitectura

`MainActivity.kt` muestra Inicio → sección → ficha y ajustes. `Catalog.kt` descarga, valida y guarda el JSON. La app administradora publica el catálogo; este proyecto es solo cliente. La siguiente etapa agregará una capa `TelegramSession` con TDLib y luego un reproductor conectado a sus archivos. La API ID y API hash de Telegram deberán obtenerse para **esta aplicación**; no se incluyen credenciales personales, tokens de bot ni claves administrativas en el código o el catálogo. Cada familiar entrará con su propia cuenta que tenga acceso al canal.

## Probar

1. Abrí esta carpeta como proyecto en Android Studio (JDK 17, SDK Android 34).
2. Ejecutá `./gradlew testDebugUnitTest assembleDebug` (en Windows: `gradlew.bat testDebugUnitTest assembleDebug`).
3. Instalá `app/build/outputs/apk/debug/app-debug.apk` en Android 8 o superior.
4. En **Configuración** pegá la URL HTTPS que responde con el JSON real y tocá **Guardar y actualizar**. Verificá primero que el catálogo y las fichas coincidan con el administrador.
5. Desactivá Internet: debe mostrarse **Copia local** con los mismos títulos. El botón de cada película/episodio abre la publicación con Telegram.

El URL predeterminado procede del proyecto Android anterior y puede responder con una página o un catálogo viejo. La app lo comunica y permite cambiarlo. El archivo `catalogo_fallback.json` está vacío a propósito: nunca representa títulos de demostración como si fuesen parte de la videoteca familiar.

Se incluye un flujo opcional de GitHub Actions que compila y adjunta el APK desde un repositorio cuyo directorio raíz sea esta carpeta.

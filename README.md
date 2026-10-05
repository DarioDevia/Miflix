# MiFlix Mobile y Android TV

| Variante | versionName | versionCode | applicationId | APK debug |
| --- | --- | --- | --- | --- |
| Mobile | 0.9.2-diagnostico | 26 | ar.com.miflix.client | app-mobile-debug.apk |
| TV | 0.1.0-diagnostico | 1 | ar.com.miflix.client.tv | app-tv-debug.apk |

TV se desarrolla en `miflix-tv`, basada en `miflix-cliente` HEAD
`b3b3156cb6aff846822d591317f9f377c5d2df4c` (README corregido), que contiene
el commit funcional móvil validado `5d896013478f8126c2dac3ed2f692fc305904509`.
La rama móvil no se modifica. Cada variante conserva su propio versionado.

## Arquitectura TV 0.1.0

Se conserva un único módulo `app` y se agregan product flavors `mobile` / `tv`.
`BuildConfig.IS_TV` selecciona la presentación; el manifest y banner TV se agregan
mediante `src/tv`. Las APK tienen identificadores diferentes y pueden coexistir.
Comparten código, pero **no** sesión Telegram ni progreso entre instalaciones.
No se requieren dependencias Leanback de widgets: Compose presenta la UI y el manifest declara el soporte TV.

Reutilizados sin reimplementación: CatalogRepository/parser/caché/Cloudflare,
TelegramSession/TDLib, TelegramVideo/TelegramRangeDataSource (1 MiB), Media3 1.3.1,
PlaybackProgress, ContinueWatching, FeaturedSession, episodios y selección de pistas.
MainActivity conserva las rutas y los diálogos de continuación. PlaybackScreen
conserva creación, resolución, lifecycle, release → clear y progreso del reproductor;
solo bifurca interacción y presentación TV. Gradle/AGP/Compose/Media3 no se actualizan.

Se descartaron detección de dispositivo en una sola APK (no separa versionado e identidad),
otro módulo con extracción de core (movimientos innecesarios para este MVP) y source sets
que copien pantallas completas del cliente (duplicarían navegación y estado).

TV incluye Home 16:9 con Destacado, filas horizontales Películas/Series/Anime,
Continuar viendo local, foco con borde/contraste, Detalle, selector básico de temporadas
y episodios, reproducción fullscreen, controles remotos y teclas media, saltos ±10 s,
y acceso a audio/subtítulos con la misma lógica y tamaños. No tiene trailers.
Los controles TV permanecen visibles hasta Ocultar/Atrás para evitar perder el foco
por un temporizador. Un D-pad/OK los muestra cuando están ocultos; Atrás primero
los oculta y después vuelve a Detalle. En Detalle, Atrás vuelve a Home; en Home
prevalece la salida normal de Android. No se agregan autoplay ni descarga offline.

La restauración de Home usa la última tarjeta enfocada, índice inicial de su fila y
el scroll vertical existente. Es razonable, no una garantía de foco idéntico si cambia
el catálogo; el Destacado y las filas no visitadas pueden recuperar una posición distinta.
La navegación entre filas usa el algoritmo de foco y scroll de Compose, sin framework propio.
TV no expone todavía búsqueda/filtros, configuración avanzada ni Anterior/Siguiente en
el reproductor; los episodios se eligen en Detalle. Estas extensiones quedan para después
de validar Home → Detalle → Play. El acceso a las cuentas/canales sigue siendo externo.

## Compilar las variantes

Requisitos: JDK 17, Android SDK 34, Build Tools 34.0.0, Android Studio y conexión
para resolver las dependencias originales. En Android Studio elegir `mobileDebug`
o `tvDebug` en Build Variants.

Windows PowerShell, desde la raíz:

```powershell
.\gradlew.bat testMobileDebugUnitTest testTvDebugUnitTest assembleMobileDebug assembleTvDebug
```

Linux/macOS:

```sh
bash gradlew testMobileDebugUnitTest testTvDebugUnitTest assembleMobileDebug assembleTvDebug
```

Resultados:
- Mobile: `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk`.
- TV: `app/build/outputs/apk/tv/debug/app-tv-debug.apk`.

Para una APK familiar de TV, conservar el archivo privado local
`telegram.local.properties` de la compilación móvil, en la raíz. No subirlo, no copiar
sesiones y no compartir códigos/2FA. Sin ese archivo la APK compila para diagnóstico,
pero no permite autorizar Telegram ni validar streaming real. Cada variante autoriza
su propia cuenta. La APK familiar contiene los identificadores de aplicación compilados;
no se debe publicar como artifact público. Actions compila sin configuración privada.

## Prueba manual Android TV API 28, 1080p

1. Android Studio → Device Manager → Create Device → TV → Android TV (1080p).
   Elegir imagen Android TV API 28 e iniciar el emulador.
2. Compilar `tvDebug` **con configuración privada** para el recorrido completo.
3. Instalar: `adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk`.
4. Ir al launcher del emulador con Home y abrir **MiFlix TV** solo con D-pad/OK.
   Si se necesita diagnóstico del launcher:
   `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER ar.com.miflix.client.tv`.
5. En instalación nueva, enfocar teléfono → OK → teclado TV; ingresar número,
   Continuar, código y 2FA cuando corresponda. No enviar esos datos en capturas/logs.
6. Verificar carga real de Cloudflare, Destacado y filas; recorrer una fila más allá
   de las tarjetas visibles, bajar/subir, comprobar borde y desplazamiento del foco.
7. Abrir película con OK; verificar imagen, título, metadatos, sinopsis y Reproducir.
8. Reproducir; verificar resolución Telegram, inicio antes de descarga completa y
   ausencia de error. Probar Pausar/Reproducir y ±10 s; probar teclas media si están disponibles.
9. Ocultar controles; OK/una flecha debe mostrarlos; Atrás los oculta, otro Atrás
   retorna a Detalle. Atrás vuelve a Home; verificar restauración razonable del foco.
10. Volver a reproducir: comprobar diálogo Continuar/Empezar y progreso local.
11. Abrir una serie/anime; temporada → episodio → reproducir; volver a Detalle.
12. Si el archivo tiene varias pistas, abrir Audio y subtítulos, recorrer opciones
    con D-pad y seleccionar con OK. Verificar subtítulos inicialmente desactivados,
    activación/desactivación y tamaños Pequeño/Mediano/Grande persistentes.
13. Suspender/reanudar emulador o enviar app a background y regresar; comprobar
    recuperación usando el lifecycle existente. Repetir build/prueba móvil para regresiones.

Verificación de implementación: `testMobileDebugUnitTest` y `testTvDebugUnitTest`
pasaron 109 pruebas cada uno (106 existentes + 3 de remoto), sin fallos ni omitidos;
`assembleMobileDebug` y `assembleTvDebug` terminaron correctamente. Los manifest
fusionados conservan Mobile 0.9.2/26 y su launcher, y TV 0.1.0/1 con Leanback/banner.
Las APK de diagnóstico generadas aquí no contienen configuración privada Telegram.

**El build y los tests JVM no certifican el recorrido con remoto ni streaming real.**
La validación manual familiar es el punto de parada del primer vertical slice.

---

# MiFlix Cliente Android 0.9.2-diagnostico

**Versión actual:** MiFlix `0.9.2-diagnostico` · `versionName = "0.9.2-diagnostico"` · `versionCode = 26`.

## 0.9.2 diagnóstico: fullscreen entre episodios y Destacado por sesión

El reproductor conserva fullscreen y la orientación al cambiar de episodio mediante **Anterior/Siguiente**, incluidos los saltos entre temporadas. Si el usuario estaba en modo vertical/no fullscreen, conserva ese modo. Atrás sigue saliendo primero de fullscreen.

El **Destacado de Inicio** se selecciona aleatoriamente entre títulos aptos al comenzar una nueva sesión y permanece estable durante navegación y recomposiciones. Un refresh conserva la selección mientras el título siga existiendo y siendo apto; si deja de serlo, se selecciona otro. Una nueva sesión puede repetir legítimamente el mismo título. No se agregan recomendaciones ni persistencia permanente del Destacado.

## 0.9.1 diagnóstico: búsqueda y exploración local (histórico)

Base exacta: 0.9.0-diagnostico, código 24, commit a24b7fa32900d61a25f9f66e4976baff0a416dda. La entrega incrementa el código a 25. No incorpora cambios experimentales posteriores.

Buscar permite explorar el catálogo completo aun con texto vacío. La entrada busca por título y géneros sin distinguir mayúsculas, acentos ni ñ/n. Una fila horizontal de controles permite combinar Tipo (Todos/Películas/Series/Anime), Género, Año, puntuación mínima (Todas/6+/7+/8+/9+) y Últimos estrenos. Géneros y años salen del catálogo local: los géneros se deduplican por nombre normalizado y se ordenan; los años van del más reciente al más antiguo. Los filtros activos se resaltan y la pantalla muestra cantidad de resultados y orden actual. Limpiar borra texto y filtros, conservando el orden elegido. Las selecciones se conservan al visitar una ficha y regresar. No hay filtro de calidad.

**Últimos estrenos es un filtro:** solo incluye títulos del año máximo presente en el catálogo y del año calendario inmediatamente anterior. No usa el reloj ni un año fijo. Todos los filtros se combinan por intersección, incluso Año y Últimos estrenos: un año fuera de esa ventana produce cero resultados. Si no hay años válidos, el control de Últimos estrenos queda deshabilitado.

**Más recientes es un orden:** conserva todo el conjunto filtrado y lo ordena por año descendente. Los otros órdenes son A-Z, Z-A, Más antiguos y Mejor puntuados. Títulos sin año o puntuación quedan al final en el orden correspondiente; no se excluyen a menos que se active un filtro que requiera ese dato. Los empates se resuelven por título normalizado y ID. Los géneros vacíos y los campos opcionales faltantes son seguros.

CatalogSearchIndex prepara títulos/géneros normalizados, opciones y cinco listas ordenadas una vez por actualización del catálogo. No recorre episodios. remember conserva el índice y los resultados entre recomposiciones; una consulta solo normaliza la entrada y recorre el orden preparado aplicando filtros. No hay consultas remotas, endpoints nuevos ni acceso a TMDB. El modelo y el parser JSON permanecen intactos.

Se agregan 25 pruebas JVM de búsqueda, filtros, combinaciones, cinco órdenes, ventana móvil de estrenos, datos incompletos, esquemas legacy y selección sobre 1000 títulos (sin afirmar un benchmark ni emular una UI real). TDLib, Media3, reproducción, rangos, lifecycle, progreso, episodios, trailers, autenticación y keepScreenOn no cambian.

Validación Samsung pendiente: buscar accion/ACCION/senor con datos reales; combinar Películas + Terror + 7+ sin texto; probar año y todos los órdenes; comparar Más recientes (todos los años) con Últimos estrenos (dos años); abrir una ficha y volver; limpiar; provocar cero resultados; reproducir y verificar controles, Continuar viendo y retorno de suspensión. La instalación y el arranque real requieren dispositivo/emulador. El artifact de Actions se compila sin credenciales privadas; la APK familiar se compila localmente con telegram.local.properties.

## 0.9.0 diagnóstico: continuación local y retorno de suspensión

Inicio muestra **Continuar viendo** cuando existen progresos válidos del catálogo actual, del más reciente al más antiguo. Usa las mismas preferencias y claves de PlaybackProgress/ProgressKeys; no crea un historial ni sincroniza dispositivos. Las películas abren su ficha; los episodios muestran temporada/episodio y ofrecen el diálogo habitual para retomar ese episodio, conservando anterior/siguiente. El póster tiene barra de progreso si la duración es conocida. Se excluyen progresos menores a 30 segundos, completados según el umbral existente y contenidos que ya no aparecen en el catálogo. Una publicación de anime mantiene su identidad aunque cambie el orden del catálogo.

La auditoría encontró que ON_STOP solo pausaba ExoPlayer y conservaba Player, DataSource y TelegramVideo sin recuperación en ON_START. Los checkpoints posteriores también podían sustituir la intención previa de reproducción por la pausa de background. Sin Logcat del incidente no se puede atribuir la suspensión real a un recurso específico ni afirmar pérdida de sesión. Ahora ON_STOP guarda inmediatamente posición e intención, libera primero ExoPlayer y después TelegramVideo y no deja reproducción en background. Al volver a STARTED, resuelve de nuevo el mismo enlace con la sesión existente y prepara recursos nuevos en la posición conservada. Una pausa del usuario permanece pausada. El cierre es idempotente: Dispose no vuelve a limpiar recursos ya liberados por ON_STOP. No se modifican TDLib, DataSource, rangos ni trailers; tampoco se agregan permisos, WakeLocks, servicios o polling de background. Rotación/fullscreen siguen usando la configuración existente de Activity.

Logcat: filtrar **MiFlixLifecycle** y **MiFlixPlayback**. Se esperan SCREEN_LIFECYCLE ON_STOP, PLAYER_RELEASE, PLAYER_RELEASED_CLEAR_VIDEO; al volver, ON_START, RESOLVE_PLAYBACK y PLAYER_CREATE con posición e intención anteriores. Las trazas nuevas no incluyen URLs ni credenciales. La liberación conserva el clear sincronizado preexistente: si había una lectura de rango en curso, puede esperar a que finalice o alcance su timeout existente; este trabajo no cambia ese mecanismo.

Validación Samsung pendiente: reproducir varios minutos y volver a Inicio; retomar una película y un episodio; comprobar el umbral de completado; apagar pantalla unos minutos y luego varias horas y retomar sin cierre forzado; repetir pausado, fullscreen y rotación. El progreso es local. Las pruebas JVM verifican selección, orden, umbrales, huérfanos, identidad de películas/episodios y publicaciones reordenadas; no simulan suspensión Android. La APK de Actions compila sin credenciales privadas y no sustituye la APK familiar configurada en Android Studio.

## Tráilers en la ficha

MiFlix usa exclusivamente `trailer_url` del catálogo publicado por Admin. Acepta enlaces HTTPS de YouTube (`watch?v=`, `youtu.be`, `embed` y `shorts`); otros formatos conservan el backdrop. Si está habilitada la opción **Reproducir trailers automáticamente** (activada inicialmente en Configuración), espera aproximadamente dos segundos y prepara el reproductor IFrame oficial de YouTube en un WebView separado del Player de Telegram. Comienza silenciado; el control de sonido está debajo del video. La preferencia se guarda en el teléfono. Al terminar, fallar, perder visibilidad o salir de la ficha, libera el WebView y vuelve al backdrop. El autoplay puede ser bloqueado por YouTube, el navegador o la disponibilidad del video; la ficha sigue funcionando. No se repite el trailer dentro de la misma ficha. La ficha conserva el timeout normal de Android.

Series y anime con publicaciones navegables muestran controles ⏮/⏭ para ir al episodio reproducible anterior o siguiente cuando aparecen los controles del video. La búsqueda sigue el orden del catálogo, cruza temporadas y omite episodios sin enlace válido y temporadas vacías. El destino usa el diálogo habitual **Continuar desde... / Empezar desde el principio** si tiene progreso. El salto antes del final conserva el progreso del episodio actual; un episodio terminado se marca completado. Se retiraron la cuenta regresiva y el inicio automático: los créditos no activan ningún salto. Las películas no muestran controles de episodios.

La ficha muestra `Audio: ...` cuando el catálogo trae el campo opcional `audio`, que describe el archivo real y no el idioma original de TMDB. Las series no muestran el botón general Reproducir: se inicia cada episodio desde su tarjeta y su propio `telegram_url`. Películas y anime conservan su botón general cuando el enlace es válido.

## Progreso de reproducción

MiFlix guarda en el dispositivo la posición, duración conocida y fecha de cada video. Películas sin episodios usan `Title.id`; los episodios usan título y temporada, más `tmdb_id` si existe, `Episode.id` antiguo o el enlace como respaldo. Si el botón Reproducir de la ficha apunta a un episodio, comparte su progreso. Las publicaciones del índice de anime, cuyos IDs se generan por posición en la lista, usan una clave derivada de su enlace de mensaje para que reordenarlas no mezcle progresos. Las claves se resumen antes de guardarse en preferencias privadas.

Se escribe como máximo cada 15 segundos de reproducción, al pasar a segundo plano y al salir del reproductor, siempre antes de liberar ExoPlayer. Menos de 30 segundos no generan una opción de continuar. Se borra el progreso cuando resta como máximo el menor valor entre cinco minutos y el 5 % de la duración, o al terminar el video. Al volver a tocar Reproducir, MiFlix ofrece Continuar desde la posición guardada o Empezar desde el principio; esta segunda opción borra la posición antigua. El progreso se excluye de copias de seguridad y transferencias de Android mediante reglas específicas.

## Instalación familiar y actualización del catálogo

La entrada de teléfono muestra un prefijo fijo `+54` para esta versión familiar argentina. El usuario escribe código de área y número; MiFlix elimina espacios y guiones, acepta pegar un número que ya empieza por `+54` sin duplicarlo y rechaza letras. No reescribe códigos de área ni agrega reglas de numeración móvil.

Los errores al resolver o reproducir contenido Telegram muestran un diálogo con **Entendido** y vuelven a la ficha. Se reconoce falta de acceso únicamente ante `CHANNEL_PRIVATE` o `Have no access to the chat` (códigos 400/403/406 de operaciones de lectura); `Message not found`, `MSG_ID_INVALID` y `MESSAGE_ID_INVALID` con código 400 indican contenido no disponible. Errores de red específicos de Java, incluso envueltos como causas, muestran Revisá tu conexión. Mensajes ambiguos, timeouts y errores desconocidos reciben el mensaje genérico; ni un código 403 por sí solo ni una IOException prueban falta de acceso. No se unen cuentas automáticamente a canales. Los detalles técnicos de contenido quedan en Logcat y nunca se incorporan al mensaje familiar. La validación real de permisos corresponde al Samsung A14 con otra cuenta.

El catálogo predeterminado es `https://miflix-catalogo.deviadario.workers.dev/catalogo.json`. Una instalación nueva no requiere configurarlo. MiFlix carga el catálogo en segundo plano incluso mientras autoriza Telegram. Si no hay una sesión autorizada, abre directamente Conectar Telegram; cuando termina la autorización, abre Inicio. Una sesión ya autorizada entra directamente a Inicio. El usuario introduce su teléfono, el código y la contraseña 2FA si Telegram los solicita; no se piden datos técnicos ni invitación al canal. Si Telegram requiere correo o confirmar en otro dispositivo, se conserva ese flujo de autorización.

MiFlix muestra primero la copia local válida, consulta el remoto al abrir o volver a Inicio si pasaron al menos diez minutos desde la última comprobación, y mantiene la copia anterior ante errores. En Inicio, deslizar hacia abajo fuerza una consulta inmediata; durante reproducción no hay consultas programadas. Si falla la primera carga, ofrece Reintentar. Configuración muestra la fecha de la última respuesta remota válida y deja la URL manual únicamente en Opciones avanzadas.

Para compilar en tu PC una APK familiar, creá **solo en la raíz del proyecto local** el archivo `telegram.local.properties` con estas dos líneas (sustituí los ejemplos por tus datos, nunca los envíes a GitHub):

```properties
TELEGRAM_API_ID=123456
TELEGRAM_API_HASH=0123456789abcdef0123456789abcdef
```

El API ID debe ser un entero positivo y el API hash, 32 caracteres hexadecimales. Android Studio: abrí la raíz del proyecto, creá el archivo junto a `settings.gradle.kts`, sincronizá Gradle (**File > Sync Project with Gradle Files**), elegí la variante `mobileDebug` y ejecutá **Build > Build APK(s)**. El APK estará en `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk`. Instalalo en el teléfono del familiar: MiFlix reconocerá la configuración y ofrecerá conectar su propia cuenta de Telegram con teléfono, código y 2FA cuando corresponda. Cada teléfono crea su propia sesión TDLib.

`telegram.local.properties` y `local.properties` están ignorados por Git. Antes de compartir cambios, verificá `git check-ignore telegram.local.properties` y `git status --short`; nunca uses `git add -f` con ese archivo. No copies `app/build/` ni la base de TDLib a Git. Si falta el archivo privado, Actions y el desarrollo siguen compilando, pero la conexión muestra un diagnóstico de compilación sin configuración privada; no solicita API ID/hash. El artifact de Actions sirve para validar tests/build y no reemplaza la APK familiar compilada en tu PC con ese archivo. El APK familiar contiene los identificadores de aplicación y quien tenga el APK podría extraerlos: compartilo solo con familiares autorizados. No incluye la sesión Telegram de quien compila.

La versión 0.5.2 acepta `puntuacion` numérica, texto decimal o texto con sufijo `/10`. Si falta o tiene un formato desconocido, omite solo esa puntuación y conserva el resto del catálogo. No cambia el formato publicado por MiFlix Admin.

## Ficha y barra de estado

La barra de estado usa el fondo oscuro de MiFlix y los iconos del sistema claros. El contenido continúa bajo los insets normales; el reproductor conserva su modo inmersivo al entrar en pantalla completa.

La ficha muestra imagen, título, año/duración/calidad, géneros y, si existen en el catálogo, audio, sinopsis, puntuación (escala de 0 a 10), dirección y reparto. El botón Reproducir general aparece para películas y anime con enlace válido; en series se reproduce desde cada episodio. El enlace de Telegram sigue siendo necesario para reproducir, pero ya no aparece como botón en la ficha normal. Los accesos de respaldo en errores del reproductor permanecen disponibles.

Los campos opcionales nuevos por título son `puntuacion` (número entre 0 y 10), `director` (texto) y `reparto` (lista de textos). Para que aparezcan con datos reales, MiFlix Admin deberá publicarlos en cada item de `catalogo.json`; no se agregan valores de ejemplo a la aplicación ni se modifica Admin aquí. Los catálogos previos siguen funcionando aunque omitan esos campos.

Las credenciales de aplicación se incorporan únicamente durante la compilación privada mediante `telegram.local.properties`. Cada teléfono autoriza su propia cuenta; no se comparte la sesión de quien compila ni se solicitan credenciales técnicas en la interfaz.

## Reproductor móvil

Mientras el reproductor está abierto, incluso en pausa, mantiene encendida la pantalla con `View.keepScreenOn`. Al abandonarlo restaura el valor anterior para que Android vuelva a aplicar el tiempo de espera normal; en segundo plano prevalece el comportamiento normal del sistema.

La rotación se maneja en `MainActivity` sin recrear la Activity ni el Player. Al activar pantalla completa se solicita orientación horizontal, se ocultan temporalmente las barras del sistema y se conserva el mismo `ExoPlayer`/`TelegramVideo`; Atrás sale primero de pantalla completa. Como respaldo para otras recreaciones de Activity, se conserva la posición y `playWhenReady` para reanudar la misma publicación. `player.release()` sigue ocurriendo antes de `video.clear()` al abandonar el reproductor.

El reproductor presenta Play/Pausa, tiempo, duración, barra de progreso y botón de pantalla completa. Un toque simple muestra u oculta controles; doble toque en la mitad izquierda retrocede 10 segundos y en la derecha avanza 10 segundos. Tanto la barra como los gestos usan `ExoPlayer.seekTo`, sobre el DataSource por rangos ya validado. Las trazas `MiFlixPlayback` registran `ORIENTATION_CHANGE`, `FULLSCREEN_ENTER`/`FULLSCREEN_EXIT` y `SEEK` sin agregar registros por cada fotograma.

Prueba en teléfono: reproducir, doble toque derecho e izquierdo, mover la barra, girar vertical/horizontal y volver, entrar/salir de fullscreen, Atrás dentro de fullscreen y finalmente Atrás para abandonar. Al girar no deben aparecer `PLAYER_DISPOSE` ni `CACHE_CLEAR`; al abandonar deben aparecer `PLAYER_DISPOSE` → `PLAYER_RELEASED_CLEAR_VIDEO` → `CACHE_CLEAR`, en ese orden.

## Compilación de diagnóstico

La compilación actual `0.9.2-diagnostico` (`versionCode = 26`) conserva las trazas con la etiqueta `MiFlixPlayback` en Logcat. Se conserva el ownership corregido en 0.4.2: `clear()` se ejecuta al liberar el Player. En Android Studio, seleccioná el proceso `ar.com.miflix.client`, filtrá `tag:MiFlixPlayback`, iniciá la reproducción de un video y conservá las líneas desde `NAV_PLAY` hasta `PLAYER_ERROR` o `PLAYER_STATE`. También podés ejecutar `adb logcat -c` y luego `adb logcat -v time -s MiFlixPlayback:D`. El registro incluye el enlace del mensaje y la ruta temporal del video; revisalo antes de compartirlo. Nunca compartas códigos de Telegram, API hash ni datos de inicio de sesión.

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

1. Abrí el proyecto con Android Studio, JDK 17 y Android SDK 34. Ejecutá gradlew.bat testMobileDebugUnitTest assembleMobileDebug.
2. Instalá app/build/outputs/apk/mobile/debug/app-mobile-debug.apk en Android 8 o superior. La instalación sobre una versión anterior conserva la URL del catálogo cuando está firmada con la misma clave de depuración.
3. Comprobá que MiFlix sigue mostrando el catálogo. Su endpoint verificado es https://miflix-catalogo.deviadario.workers.dev/catalogo.json y la URL guardada en Configuración sigue vigente.
4. El administrador agrega externamente la cuenta al canal privado desde Telegram. Compilá la APK familiar con el archivo privado indicado arriba. **No compartas códigos de inicio de sesión ni contraseña con otras personas.**
5. En una instalación limpia, MiFlix abre la conexión de Telegram con teléfono, código y 2FA cuando corresponda, carga el catálogo predeterminado y entra a Inicio al autorizarse. Abrí una ficha con enlace a un mensaje de video y tocá Reproducir en MiFlix. Verificá que empieza antes de descargar todo el archivo, pausa, seek cerca del final y regreso a la ficha. La caché parcial necesita conexión para los tramos aún no cargados.
6. Si el enlace apunta a un texto o tema sin video, se explica el problema y podés abrir la publicación en Telegram para revisar el enlace exportado desde MiFlix Admin.

## Arquitectura

MainActivity.kt mantiene la interfaz existente y abre la ruta de reproducción. TelegramSession.kt guarda la sesión de TDLib dentro de la aplicación, resuelve los enlaces y solicita rangos. TelegramVideo.kt implementa la caché acotada y el DataSource de Media3. PlaybackScreen.kt muestra autenticación, buffering y PlayerView. Catalog.kt mantiene la descarga, validación y caché del catálogo; Cloudflare Worker y MiFlix Admin no se modifican.

La compilación automatizada de GitHub Actions ejecuta tests y assemble de ambas variantes y adjunta el APK. Que la compilación pase confirma dependencias y código, pero el inicio de sesión y la reproducción deben verificarse en un teléfono con cuenta autorizada y una publicación real con video.

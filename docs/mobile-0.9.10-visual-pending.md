# MiFlix Mobile 0.9.10-diagnostico / 34

Base comprobada: feature/mobile-0.9.9-google-identity, 5f9641f2a511dd28b4fd91735681f051ecefd7d3, 0.9.9 / 33, limpia; CI previo correcto. Se revisaron las ramas publicadas y no había Mobile posterior. Rama independiente fix/mobile-0.9.10-visual-pending; sin merge ni sobrescritura de las versiones anteriores.

## Causas y corrección mínima

MovieCard siempre dibujaba título y era compartida por Inicio, búsquedas, biblioteca y similares. Continuar viendo agregaba un segundo Text con T/E/nombre del episodio. Esas estructuras seguían presentes desde las versiones de filas/biblioteca; no es una modificación de pósteres ni del catálogo. Se introduce showTitle/showTitles con valor predeterminado true: solo Inicio pide false. Se elimina el Text adicional de continuación. Los Text ausentes no se componen; Column.spacedBy no reserva separación si no existe otro hijo. Se mantienen los 7 dp entre carátula y barra cuando hay progreso, no una altura ficticia de subtítulo. No se cambia ancho/ratio de póster, callbacks, etiquetas Visto/Episodios vistos, orden, LazyRow, claves ni estado de desplazamiento. Los nombres permanecen como contentDescription para accesibilidad sin ocupar espacio visual. Hero y sus textos no se cambian.

Detail usaba directamente los índices del catálogo, que puede comenzar por temporada 0. SeasonPresentation produce opciones de presentación con catalogIndex original y etiqueta: normales primero y Especiales al final, mediante partición estable. No reordena los datos ni las temporadas normales entre sí. La opción seleccionada inicial es la primera de esa presentación, normalmente T1. Al tocar se guarda catalogIndex, nunca la posición visual. Episodios, progress keys, asociaciones TMDB y funciones anterior/siguiente siguen usando las listas originales. La navegación del Player no se reordena en esta entrega: es una regla del selector visual, no un nuevo orden de reproducción automática.

Season.numero permanece en 0. No se crean ni se filtran temporadas/episodios: se conservan exactamente la disponibilidad y el tratamiento de enlaces publicados que ya existían. El parser schema 2 crea una temporada sintética 0 titulada Publicaciones para anime, con IDs titleId-publicacion-…; esa estructura reconocible conserva su nombre y no se confunde con especiales reales. Películas/Publicaciones permanecen sin cambios. Una serie que solo tenga temporada 0 muestra Especiales normalmente; un título sin temporadas no adquiere una ficticia.

## Pruebas

SeasonPresentationTest: diez casos JVM para orden/índices, normales y nombres existentes, ausencia de especiales, lista vacía/solo especiales, especiales ya al final, referencias/orden de episodios/enlaces/TMDB/legacy keys, anime real, publicaciones schema 2 y disponibilidad sin cambios. Comprueba navegación existente usando índices de catálogo; no simula un Player o Telegram.

Ejecutar testDebugUnitTest, lintDebug, assembleDebug y assembleRelease mediante el workflow existente con el trigger de la nueva rama. Sin cambios en dependencias ni Worker. El job de identidad existente puede ejecutarse sin despliegue como regresión del workflow. La compilación local puede fallar por descarga de Gradle; informar si se verificó en Actions. No hay suite androidTest configurada ni dispositivo/emulador conectado en este entorno. No crear un test JVM de búsqueda textual de fuentes como falsa prueba de renderizado Compose. Las alturas, badges, colores, TalkBack y taps requieren Android real.

## Validación en Samsung A21s / A14

1. Actualizar con la misma firma y configuración privada local, sin desinstalar, para conservar datos. Desde Android Studio abrir el proyecto, seleccionar esta rama y compilar app debug o generar APK con el mismo keystore de la versión instalada. La APK de CI no contiene configuración privada Google/Telegram; no reemplazar una instalación de otra firma forzando desinstalación.
2. Con progreso/favoritos/vistos existentes: revisar Continuar viendo, Mi Lista, categorías y géneros. Solo carátulas y badges; barra de progreso donde corresponda. No título/T/E/nombre debajo ni huecos de texto. Revisar películas, series y anime; TalkBack debe seguir identificando el contenido y episodio de continuación.
3. Tocar cada fila: ficha, continuación y diálogo de posición funcionan como antes. Comprobar scroll al volver. Buscar/Secciones/Mi Lista como pantalla independiente y Más títulos similares conservan títulos. Las fichas y episodios mantienen toda su información.
4. Serie sin especiales: mismo selector/episodios. Serie con T0 al comienzo (House u otra): normales primero, Especiales al final. Al abrir debe seleccionar la primera normal. Tocar Especiales y reproducir un episodio correcto; comprobar progreso y marcado visto. Si solo hay especiales deben aparecer sin crash. No deben generarse temporadas o enlaces nuevos.
5. Comprobar Anterior/Siguiente y reproducción normal, seek, fullscreen/audio/subtítulos, intro, Google/Telegram y filas sin regresiones. El orden del Player sigue siendo el del catálogo, no el nuevo orden visual del selector.

## Otras aplicaciones, sin modificar

TV y Admin Android necesitan una auditoría aparte para saber si comparten el mismo problema. Si muestran T0 primero, aplicar allí una opción de presentación que conserve índices originales y distinga las publicaciones sintéticas; no cambiar JSON/TMDB como solución global. Esta entrega no modifica esas ramas, Admin Windows, Worker/KV ni catálogo.

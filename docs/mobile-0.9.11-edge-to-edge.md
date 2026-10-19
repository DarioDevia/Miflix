# MiFlix Mobile 0.9.11-diagnostico / 35

## Base comprobada y alcance

Rama de origen fix/mobile-0.9.10-visual-pending, commit 95982b39252a6f1fe3b256e21a6a79352dbec274, 0.9.10 / 34, árbol limpio. Se revisaron las ramas GitHub y no había otra Mobile posterior. El propietario confirmó validación física de 0.9.10 en Samsung con su firma habitual. Nueva rama feature/mobile-0.9.11-edge-to-edge, sin merge ni cambios en ramas anteriores.

Solo fondo/insets de las pantallas normales. Se preservan proporciones, posiciones relativas de Inicio y Hero, filas, carátulas sin captions, badges, progreso, Especiales al final y navegación. No se modifica ningún almacenamiento ni aplicación distinta de Mobile.

## Ventana y propiedad

La base usaba Theme.MiFlix con statusBarColor oscuro opaco y la ventana normal fitted. Ya existía un DisposableEffect exclusivo para fullscreen: landscape, decorFits=false, hide(systemBars), y al salir show(systemBars), decorFits=true y restauración de orientación. Ese bloque queda byte por byte igual. IntroPreview usa otra ventana Dialog, tampoco modificada.

NormalEdgeToEdge activa la extensión solo cuando no hay Player, fullscreen/transición fullscreen ni intro. Usa ComponentActivity.enableEdgeToEdge y SystemBarStyle de androidx.activity 1.8.2 (API disponible desde 1.8.0). WindowCompat.enableEdgeToEdge más reciente no está en core 1.12.0; no se actualizan dependencias para acceder a ella. Es la API oficial compatible ya instalada, no flags legacy escritos manualmente para activar el modo.

statusBarStyle.dark(TRANSPARENT) dibuja el fondo detrás de los indicadores con íconos claros. No llama hide() y no oculta hora/notificaciones/batería. navigationBarStyle.dark usa el color previo de la barra inferior para conservar su tratamiento visual y contraste, sin rediseñar navegación. La prioridad es transparencia superior; no se promete navegación inferior transparente. Se conserva el fondo oscuro de MiFlix y no se hace un Hero extendido o nuevo recorte.

El efecto guarda colores, apariencia, contraste y modo de cutout antes de activar la API, y restaura sus propiedades al salir. Solo restaura los flags que la API de Edge-to-Edge puede administrar; no pisa flags ajenos como KEEP_SCREEN_ON. Se restaura decorFits=true, la base original del Player. El Player es siempre excluido, también vertical: no se cambian sus insets o controles originales. Durante la transición Detail fullscreen de episodios también se excluye para que la orientación siga estable.

Compose descarta los efectos anteriores antes de ejecutar los nuevos: al entrar al Player la limpieza de la ventana normal precede al efecto fullscreen que ya existía; al volver a una pantalla normal se libera fullscreen y se activa de nuevo Edge-to-Edge. No se agrega otro reproductor, listener de Media3, observador lifecycle o sesión Telegram. El efecto solo retiene la Activity de su composición y suelta las referencias al descartarse. La intro no modifica esa ventana y conserva su Dialog inmersivo original.

## Insets y teclado

El Box de fondo existente y Scaffold ocupan la ventana; no tienen statusBarsPadding global que recorte el fondo. Los controles sí respetan WindowInsets.systemBars union displayCutout: TopAppBar recibe Top+Horizontal y NavigationBar Bottom+Horizontal. El contenido de Scaffold usa esos insets y su padding existente (Scaffold no suma top/bottom otra vez cuando hay app bar que ya los maneja). El Player recibe los defaults de Scaffold y componentes previos, no estos overrides.

Para no tapar teléfono, 2FA, búsqueda y configuración al abrir teclado, la Activity declara adjustResize y el Scaffold normal aplica imePadding, que consume el inset IME. Esto evita sumar dos veces teclado+barra inferior; no cambia campos, credenciales ni el flujo Telegram. El Player no recibe ese modificador. Se debe comprobar con teclado real y navegación por gestos/tres botones.

No hay alturas fijas de barra superior. La API Activity gestiona diferencias de nivel Android y cutouts; el override Compose protege también laterales al rotar en teléfonos con notch/cámara. No se usa medición manual de status_bar_height ni se ocultan controles con alpha.

## Compatibilidad y pruebas

MinSdk 26, compile/targetSdk 34 y todas las versiones de bibliotecas permanecen iguales. La API incluida contempla Android 8–9 y Android 10+; los atributos de contraste solo se guardan/restauran en API 29+, y cutout/divider en API 28+. Android 15+ con target 35 puede imponer Edge-to-Edge: esta entrega no sube targetSdk ni pretende validar esa futura migración. El comportamiento real de OEM, Android 15/16+, recortes y tres botones requiere prueba específica; no se declara probado por compilar.

NormalEdgeToEdgeTest contiene cinco pruebas JVM de la política de alcance: todas las pantallas normales, exclusión de Player vertical/fullscreen, puente de episodio fullscreen, intro y cambios de pantalla. No simula Window, una barra real ni un teléfono. Se mantienen todos los tests anteriores, especialmente PlaybackFullscreenTest. No crear tests JVM artificiales de píxeles/padding que no ejerciten Android.

Ejecutar testDebugUnitTest, lintDebug, assembleDebug y assembleRelease. Si la descarga local de Gradle está bloqueada, verificar los cuatro en GitHub Actions e informar los resultados reales. El workflow incluye la rama nueva; el job existente de identidad sigue siendo dry-run sin despliegue. No hay androidTest ni emulador/adb disponibles en este entorno. UI, contraste, navegación y teclado no quedan validados por esos tests.

## Lista Samsung A21s / A14

1. Actualizar sin desinstalar, con la firma y configuración local existentes. Abrir desde proceso cerrado: intro normal una sola vez y después fondo oscuro continuo hasta el borde superior. Hora, notificaciones, señal y batería deben seguir visibles y claras.
2. Inicio: logo, Buscar y Configuración deben quedar debajo de la zona de indicadores. Revisar Hero, scroll vertical y filas, carátulas sin captions, badges/progreso y retorno desde ficha. No debería existir padding superior duplicado ni una franja de color distinta.
3. Rotar fuera del Player: botones protegidos de cámara/notch también lateralmente. Abrir Buscar, filtros, Mi Lista, ficha y Configuración. Abrir teclado en Buscar y opciones de texto: campo y controles accesibles; cerrarlo sin hueco inferior persistente. Si corresponde, probar teléfono/código/2FA en una instalación de prueba, sin cerrar una sesión válida solo para esta revisión.
4. Repetir con navegación Android por gestos y tres botones. Todas las entradas inferiores de MiFlix deben estar por encima del gesto/botones, sin superposición y sin doble margen. No rediseñamos el color inferior del sistema.
5. Player vertical, fullscreen, rotación, salida de fullscreen y Back: mismo comportamiento de 0.9.10. Al volver a ficha/Inicio se recupera la transparencia superior. Repetir Anterior/Siguiente en fullscreen (incluido puente entre temporadas), seek, audio/subtítulos, brillo/volumen, keepScreenOn y continuación. No debe reiniciarse el stream por este efecto.
6. Ir/volver de background y cerrar/reabrir app. Confirmar barras visibles en pantallas normales, intro sin repetición por navegación y estado restaurado. Repetir con tema del sistema claro y oscuro: MiFlix conserva diseño oscuro e íconos de estado claros.

## Compilar y firmar en Windows

Seleccionar esta rama en el proyecto **Mobile**, conservando telegram.local.properties privado y propiedades Google locales si ya están configuradas. No copiar archivos privados al repositorio, al chat ni a artefactos de CI. No se cambia applicationId ni se crea keystore.

Para verificación: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease`. Debug queda en app\build\outputs\apk\debug\app-debug.apk. Si la instalación actual fue firmada con debug local, usar esa misma firma. Si usa tu keystore release, Android Studio → Build → Generate Signed Bundle / APK → APK → seleccionar el keystore y alias **ya existentes** → release; ingresar las contraseñas únicamente en la PC. No crear otra clave. Instalar como actualización para conservar datos.

La release de CI es unsigned; la debug de CI no incorpora configuración privada ni garantiza la firma de tu instalación. No recomendar desinstalar para saltar un error de firma: corregir la firma/configuración del build local. Ninguna configuración privada, contraseña, sesión o keystore se publica.

## Referencias oficiales consultadas

- https://developer.android.com/develop/ui/compose/system/setup-e2e
- https://developer.android.com/reference/androidx/activity/EdgeToEdge
- https://developer.android.com/develop/ui/compose/system/material-insets

# Experimento MiFlix → Home del sistema (Google TV / Android TV)

## Base, recuperación y límites de acceso

Base exacta: `feature/tv-0.1.14-collections-genres`,
`e20be389c79e0176fc9fa068fd1c91354f824641`, TV 0.1.14-diagnostico / 15,
applicationId `ar.com.miflix.client.tv`. No se incrementa una versión estable para una
sonda debug. Rama independiente: `experiment/tv-google-home-probe-work`.

Work tiene una copia Linux de GitHub. No tiene acceso a
`C:\Users\devia\StudioProjects\Miflix-TV-013`, Android Studio de Windows ni ADB
`emulator-5554`. No hay ADB/SDK/KVM en este entorno. JDK 17 sí está disponible, pero
el wrapper no pudo descargar Gradle 8.2 por la restricción de red. No se instaló un
SDK completo ni se inició un emulador remoto. Las compilaciones se verifican en CI.

No se encontró `experiment/tv-google-home-channels` en GitHub al iniciar. Por tanto
NO se recuperó el código local anterior ni se afirma haberlo revisado. Esta rama
no lo sobrescribe. El log que aportó el usuario acredita nivel 2 para SU prototipo
anterior (canal 2 creado y recuperado); no acredita ejecución de esta nueva sonda.

## Investigación oficial y conclusión

Fuentes consultadas:

1. [Android TV: Channels on the home screen](https://developer.android.com/training/tv/discovery/recommendations-channel)
2. [TvContract/requestChannelBrowsable](https://developer.android.com/reference/android/media/tv/TvContract)
3. [PreviewProgram.Builder](https://developer.android.com/reference/androidx/tvprovider/media/tv/PreviewProgram.Builder)
4. [ChannelLogoUtils](https://developer.android.com/reference/androidx/tvprovider/media/tv/ChannelLogoUtils)
5. [Google TV: Best practices](https://developer.android.com/training/tv/get-started/google-tv)
6. [Engage SDK for TV: Getting started](https://developer.android.com/guide/playcore/engage/tv/getting-started)
7. [Engage recommendations](https://developer.android.com/guide/playcore/engage/tv/recommendations)
8. [Google: Increasing app discovery and engagement on Google TV, 2026](https://android-developers.googleblog.com/2026/05/increase-google-tv-app-discovery.html)

Los Preview Channels de Android TV utilizan TvProvider, `Channel` TYPE_PREVIEW y
`PreviewProgram`. Existen desde API 26: API 36 puede ejecutar la API, pero esto no
prueba la política del launcher instalado. La documentación de Android TV describe
un canal predeterminado y canales adicionales aceptados por el usuario. Publicar
un registro no es lo mismo que aprobar visibilidad, ni aprobar browsable demuestra
que LauncherX dibujó tarjetas. `requestChannelBrowsable()` no devuelve una captura
ni una garantía observada de presentación de un launcher concreto.

Permiso oficial: `com.android.providers.tv.permission.WRITE_EPG_DATA` únicamente.
READ_EPG_DATA está obsoleto; no se agregan READ_TV_LISTINGS, permisos privilegiados,
root, WRITE_SECURE_SETTINGS ni cambios de launcher o región. Una consulta shell
con `No result found` usa otra identidad de llamante y no refuta la consulta que
hace la propia app. Es una diferencia de acceso que debe diagnosticarse desde la app;
no se presenta como prueba de canal eliminado ni como garantía de otra causa exacta.

Las vías oficiales de descubrimiento de Google TV no son equivalentes a las filas
clásicas de Android TV. Google documenta Media Actions con incorporación de proveedores,
Watch Next con certificación de calidad para aparecer en Google TV, y actualmente
Engage SDK TV con inscripción/elegibilidad, disponibilidad del servicio y aplicación
de verificación. La publicación en esa aplicación de verificación tampoco sería
prueba de aparición en LauncherX. El blog oficial de 2026 aconseja migrar de Watch
Next a Engage y anuncia el fin de soporte de la experiencia heredada en la segunda
mitad de 2027. No se implementa otro SDK como supuesto atajo a esos requisitos.

**No se encontró una garantía oficial de que el LauncherX concreto del usuario muestre
cualquier Preview Channel de terceros en su Home. Tampoco se afirma una imposibilidad
universal de todos los modos/regiones/versiones sin observarlo.** El prototipo es una
prueba acotada y falsable de esa API; si TvProvider registra y devuelve las cinco
entradas pero LauncherX no las muestra, se detiene en nivel 3. No perseguirlo cambiando
Media3, permisos o aumentando registros. YouTube no demuestra que una app privada
instalada por ADB tenga las mismas integraciones/distribución.

El emulador Google TV API 36 informado (LauncherX HomeActivity) sirve para comprobar
su comportamiento concreto. No valida un televisor físico ni todos los Google TV.
No se pudo inspeccionar esa imagen ni su modo/cuenta/región/launcher desde Work.

## Archivos y aislamiento

Nuevos en `app/src/tvDebug/java/ar/com/miflix/client/`:
- `TvHomeProbeActivity.kt`: interfaz nativa con foco de botones, acciones manuales y diagnósticos.
- `TvHomeProbePolicy.kt`: selección y reconciliación puras.
- `TvHomeProbeImages.kt`: proveedor de lectura exclusivo de PNG generadas para la sonda.

Manifiesto `app/src/tvDebug/AndroidManifest.xml`: actividad explícita ADB (sin icono
ni integración automática en Inicio), permiso EPG, proveedor de imágenes de prueba.
Gradle sólo añade `tvDebugImplementation` de `androidx.tvprovider:tvprovider:1.1.0`,
que no estará en TV Release ni Mobile. No cambia SDK/AGP/Kotlin/Gradle/JDK/versiones,
applicationId ni firma. Ninguna fuente o recurso estable previo se modifica.

Tests: `app/src/testTvDebug/.../TvHomeProbePolicyTest.kt` (12 casos),
`tools/check_tv_home_probe_contract.py` (4 contratos),
`tools/inspect_tv_home_probe_apk.py` y workflow `.github/workflows/tv-home-probe.yml`.
Automatización Windows: `tools/run_tv_home_probe.ps1`. Este informe es la documentación.

## Cómo funciona la sonda

No se ejecuta desde MainActivity ni en segundo plano automáticamente. Abrirla sin
argumentos sólo consulta datos; `probe_action=publish` actualiza las cinco tarjetas.

- Consulta canales de la propia aplicación y TYPE_PREVIEW. Reutiliza el ID persistido
  si sigue siendo válido, el marcador propio o un canal existente de nombre exacto
  MiFlix. Puede adoptar así el canal de la prueba anterior sin duplicarlo.
- Si hay canales propios con otros nombres no reconocidos, se detiene antes de crear
  otro: no adivina ni borra el trabajo local que no se pudo consultar. Si hay varios
  MiFlix, elige establemente uno, sin borrar canales adicionales.
- Una consulta fallida o sin cursor no se transforma en lista vacía ni permite crear
  duplicados. Un ID eliminado se recupera por marcador/nombre o se vuelve a crear si
  realmente no queda ningún canal propio. Se añade logo mediante API oficial.
- Usa primero la caché pública existente del catálogo. Si falta, intenta una sola
  descarga del endpoint público fijo, con tiempo máximo de 20 segundos. No lee
  preferencias privadas ni usa credenciales. No escribe catálogo ni Worker.
- Selecciona hasta cinco títulos disponibles, equilibrando película/serie/anime con
  los metadatos reales. Cuando falten entradas, completa sólo esos huecos con tarjetas
  llamadas PRUEBA SINTÉTICA. El log indica reales/sintéticas y origen; no finge datos.
- Cada clave de programa es SHA-256 del ID estable del catálogo dentro de un namespace
  propio. No publica Telegram URL ni ID de mensaje como clave/enlace de apertura.
- La imagen real usa sólo backdrop/fondo publicado. Si falta, genera una PNG horizontal
  960×540 con texto de prueba. Las sintéticas están identificadas también en su imagen.
- El proveedor exportado sirve únicamente esas PNG de un directorio exclusivo, con
  nombres SHA-256 y modo lectura. No expone sesiones, propiedades, caché, biblioteca,
  otras rutas ni escritura. Sólo existe en tvDebug para que el launcher pueda leerlas.
- Reconcilia por clave: inserta faltantes, actualiza cambios, no reescribe entradas
  idénticas y elimina duplicadas/obsoletas sólo del namespace de esta sonda. Programas
  heredados sin esa marca permanecen intactos y se cuentan aparte. No borra el canal.
- Verifica después desde la propia app que existen exactamente las cinco claves
  esperadas con sus metadatos. El log PROGRAMAS_VERIFICADOS no se emite por contar
  solamente inserciones. Limpia PNG obsoletas sólo tras completar la reconciliación.
- Binder/HTTP/bitmap/archivos se ejecutan fuera del hilo principal, con exclusión mutua
  entre actividades y cancelación al destruir la actividad. Si se interrumpe a mitad,
  repetir repara el estado por identidad, sin una segunda copia de cada programa.

## Apertura: limitación deliberada

MainActivity usa una función ClientApp privada y navegación Compose interna; actualmente
no consume deep links a Title.id. Para no modificar la fuente estable, cada programa
contiene un Intent URI explícito a MainActivity, con clave de sonda estable. Reutiliza
la actividad cuando es posible; NO selecciona la película ni inicia reproducción.
Es la prueba intermedia de abrir MiFlix autorizada en el alcance, no nivel 5 pleno.
No existe un intent-filter que prometa una ficha que la app no maneja.

Sólo después de obtener evidencia de nivel 4 tendría sentido una integración acotada
para consumir Title.id real cuando el catálogo esté listo. No se introduce ahora
una segunda arquitectura de navegación ni se toca el reproductor para conseguirlo.

## Pruebas y niveles (no confundir)

1. Compila: evidencia de CI y manifiestos inspeccionados.
2. Canal registrado y recuperable: requiere ejecutar la sonda en el dispositivo.
3. Cinco programas publicados y consultados: requiere PROGRAMAS_VERIFICADOS de esta
   compilación y segunda ejecución estable. browsable=true sigue siendo nivel 3.
4. Canal/tarjetas visibles en Home de LauncherX: exige observación/captura de ese Home.
5. Seleccionar tarjeta y navegar a ficha correspondiente: exige prueba real; pendiente
   porque la apertura actual es únicamente MiFlix como paso intermedio.

CI no contiene Google TV LauncherX ni se declara nivel 2/3/4/5 por pruebas JVM.
Se compilan TV Debug, TV Release y Mobile Debug, se ejecutan todos sus tests unitarios
aplicables y Lint; se inspeccionan las tres APK. Se comprueba que los componentes y
permiso sólo están en tvDebug y que tvprovider no figura en las dependencias de Release
ni Mobile. No se debilitan los contratos de la versión estable: la nueva comprobación
compara byte a byte todas las fuentes, tests, recursos y herramientas previas.
Los resultados finales se agregarán tras recuperar CI.

## Windows: conservar tu trabajo local y probar sin editar código

No ejecutar git switch sobre cambios locales sin guardar. Usar un worktree distinto,
que deja intacta la rama local `experiment/tv-google-home-channels` y su árbol:

```powershell
cd C:\Users\devia\StudioProjects\Miflix-TV-013
git status --short
git fetch origin
git worktree add ..\Miflix-TV-HomeProbe-Work origin/experiment/tv-google-home-probe-work
cd ..\Miflix-TV-HomeProbe-Work
```

El worktree nuevo no incluye archivos privados ignorados. Para una prueba que conserve
la app operativa, utilizar exclusivamente tu configuración privada local habitual y
la clave original; no compartirlas ni agregarlas a Git. El script sólo comprueba la
presencia del archivo, no lo imprime. Si querés probar sin esa configuración, usar
`-AllowUnconfiguredProbe` sólo en tu emulador de pruebas: las tarjetas/sonda no necesitan
Telegram pero la app instalada perdería la configuración compilada si la reemplazás
por una build sin ella. No instalar una build de CI encima de la app familiar.

Con la configuración local conservada y el emulador ya iniciado:

```powershell
.\tools\run_tv_home_probe.ps1
```

Usa las rutas SDK/JDK proporcionadas por el usuario; permite `-AndroidSdk` y `-Jdk17`.
Valida que existe la clave debug local antes de compilar, sin crear otra. Ejecuta tests,
Lint y assembleTvDebug; instala con `adb install -r` (sin `-t`) y abre la sonda publicando.
Nunca desinstala, limpia datos, cambia launcher o usa root. Si falla la firma o falta
espacio, se detiene. No cambia la configuración persistente de Telegram.

Comandos de diagnóstico necesarios desde PowerShell (sin Logcat global):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5554 shell getprop ro.build.version.sdk
& $adb -s emulator-5554 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
& $adb -s emulator-5554 shell am start -n ar.com.miflix.client.tv/ar.com.miflix.client.TvHomeProbeActivity --es probe_action publish
& $adb -s emulator-5554 logcat -d -s MiFlixHomeProbe:I '*:S'
```

Esperar a que la pantalla muestre PROGRAMAS_VERIFICADOS antes de repetir. La segunda
publicación, sin cambios de catálogo, debe mostrar el mismo canal, cinco claves y
upserts=0. Programas heredados de otra prueba se informan como otrosProgramas y no
se borran por falta de una marca propia.

Luego solicitar visibilidad desde el botón o:

```powershell
& $adb -s emulator-5554 shell am start -n ar.com.miflix.client.tv/ar.com.miflix.client.TvHomeProbeActivity --es probe_action request
& $adb -s emulator-5554 shell input keyevent 3
```

Inspeccionar visualmente el Home y desplazarse por sus filas. Una captura necesaria:

```powershell
& $adb -s emulator-5554 shell screencap -p /sdcard/miflix-home-probe.png
& $adb -s emulator-5554 pull /sdcard/miflix-home-probe.png .\miflix-home-probe.png
```

No se publica ninguna captura automáticamente. Si aparecen tarjetas, seleccionar una
con el mando y comprobar apertura de MiFlix; no afirmar todavía ficha directa. Si no
aparecen aunque la propia app confirma cinco programas, documentar API/launcher,
browsable y captura: nivel 3 sin demostración de visibilidad, no borrar ni recrear
canales sin más evidencia. Se puede probar el botón de confirmación: si LauncherX no
ofrece handler, la sonda lo informa sin caer ni inventar una autorización.

## Rollback y datos

La rama estable y su APK no cambian. Volver a instalar la APK estable con la misma
firma mediante install -r conserva datos; no usar uninstall ni clear. Las entradas
heredadas de TvProvider pueden persistir tras reemplazar un APK; la sonda no presume
que desaparecerán y no borra canales o programas ajenos a su namespace.
Una APK debug de CI tiene firma temporal y carece de configuración Telegram privada;
no se garantiza actualización compatible con la instalación del usuario. La release
y Mobile son sólo artefactos de verificación de aislamiento y no se publican como
nuevas versiones. No hay despliegue de producción ni alta de un proveedor Google.

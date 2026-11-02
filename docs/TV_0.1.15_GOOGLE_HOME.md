# MiFlix TV 0.1.15-diagnostico / 16 — candidata privada

## Origen y estado

Base estable e20be389c79e0176fc9fa068fd1c91354f824641 (0.1.14 / 15).
Migración selectiva desde 2c241c68dbaf470713d71dc8e4a5c100bbff6316, con nivel 5 experimental confirmado por el propietario en Google TV API 36: diez tarjetas, imágenes y ficha específica. No es validación física de esta candidata.
Rama feature/tv-0.1.15-google-home; applicationId ar.com.miflix.client.tv. Sin merges, despliegues ni cambios de Worker/Admin/Mobile funcionales. Las referencias anteriores se conservan.

## Arquitectura

- TvHomeIntegration conecta el catálogo validado y el ciclo RESUMED de la Activity con TvHomeSync. No hay servicio, receiver de arranque, WorkManager, alarma ni sondeo periódico. MainActivity/ClientApp/openDetail conservan las fichas y la reproducción.
- Sync serializa con un Mutex en IO, usa exclusivamente el archivo atómico del catálogo ya descargado por CatalogRepository y comprueba que sus títulos coincidan con el catálogo en memoria. No hay segunda petición HTTP del catálogo. Se preservan las fechas opcionales originales sin cambiar Catalog ni el JSON remoto.
- Un hash del JSON y día coalesce éxitos dentro del proceso. El primer intento de un proceso nuevo consulta el proveedor real. Los fallos iguales tienen espera mínima de diez minutos, sin temporizador: el siguiente catálogo válido o retorno a primer plano puede reintentar. Catálogo nuevo permite un intento independiente. El límite cooperativo es 45 segundos; una llamada Binder bloqueada por el sistema no se puede certificar cancelable desde JVM.
- Conjunto vacío/error/mismatch no autoriza eliminación. Los reemplazos se preparan, escriben y vuelven a consultar antes de quitar obsoletos propios. No hay transacción global de TvProvider: un error después de escrituras puede dejar cambios parciales recuperables; nunca se afirma atomicidad.
- El namespace de programas, el marcador del canal, las preferencias y la autoridad de imágenes experimentales se conservan para migrar sin duplicados. Sólo se adopta un canal Preview único reconocido y del paquete propio. ID 2 no tiene tratamiento especial. Varios candidatos reconocidos son ambiguos: se conserva todo y se registra el fallo. Nunca se borran canales.
- La visibilidad se solicita una vez por canal verificado usando la API normal, con la app en primer plano. No se modifica LauncherX ni se fuerza browsable. El usuario/launcher decide presentación, ocultación y cantidad visible. No se insiste para contradecir una ocultación posterior.
- Dependencia tvprovider 1.1.0 únicamente en tvImplementation. Sin TvHomeProbeActivity ni componentes de diagnóstico. La rama experimental queda intacta.

## Selección y navegación

Se mantiene la política probada: diez como máximo, year/anio normalizados, año actual y anteriores descendentes, fecha completa opcional para desempatar dentro del año, sin días inventados, sin fecha_agregado ni estrenos futuros conocidos. IDs estables, sin duplicados ni relleno sintético. Para series/anime sólo fecha original del título, no temporadas/episodios. Una selección con 1–10 títulos actualiza normalmente; cero conserva la anterior.

Intents explícitos, misma acción validada, ID opaco acotado; no URLs/archivos/opciones de reproducción. MainActivity singleTask sólo TV. La solicitud pendiente sobrevive a recreación, respeta intro/autorización y espera hasta 15 segundos por catálogo; la última tarjeta gana y se consume antes de llamar al openDetail existente. Ausente/ambiguo: mensaje amigable y salida controlada. Atrás: Inicio. Reproducir requiere acción explícita.

Los puntos compartidos son aditivos y acotados. Mobile implementa adaptadores inactivos y conserva versión 0.9.2 / 26, identidad y comportamiento. En PlaybackScreen sólo se sustituye la rama TV de aprovisionamiento de API; toda la lógica de audio, Media3, TDLib, streaming y reproducción permanece idéntica a la base.

## Imágenes y permisos

Se prefieren los backdrops HTTPS publicados, se descargan sólo cuando falta la imagen correspondiente en la caché y se decodifican con límites. Timeout 3 s por imagen, máximo 2 MiB de entrada, dimensiones 16384, muestreo de decodificación <=2 millones de píxeles y salida horizontal 960x540 sin estirar. Ante fallo se genera un fondo MiFlix con el título, sin etiquetas de prueba. No se consulta TMDB.

Caché sólo de PNG públicos generados: máximo 32 archivos / aproximadamente 64 MiB incluyendo margen de creación. Los referenciados no se expulsan por espacio: si no se puede preparar todo, se aborta la sincronización. Se limpian huérfanos tras consultar el proveedor y tras reconciliar. Un fallback puede reintentarse en el próximo proceso o cambio de catálogo; no hay bucle de descargas.

TvHomeImages conserva la autoridad ar.com.miflix.client.tv.homeprobe.images para las tarjetas anteriores. Expone sólo nombres hexadecimales de 64 caracteres terminados en .png, un segmento, sin query/fragment y dentro de su directorio canónico. Sólo lectura; insert/update/delete fallan. No expone preferencias, catálogo, sesiones ni rutas arbitrarias.

Permiso nuevo TV: WRITE_EPG_DATA. READ_EPG_DATA de la biblioteca se elimina en la fusión. No hay permisos privilegiados ni nuevas solicitudes de red/almacenamiento del sistema. INTERNET existente se reutiliza para las imágenes. Credenciales, sesiones TDLib, caché Home y preferencias de IDs de canal quedan excluidas del backup/transferencia TV; no se borran sus datos locales.

Documentación oficial: https://developer.android.com/training/tv/discovery/recommendations-channel . Preview Channels requiere API 26+. La presentación no es universal entre Android TV, Google TV y launchers de TV Box. No se utiliza una segunda API ni se promete presencia en todos.

## Auditoría de credenciales: cambio necesario para distribución

La configuración original carga credenciales de compilación desde un archivo privado y puede incorporarlas a BuildConfig. No se abrió ese archivo ni se leyó una sesión. TV 0.1.15 fuerza API_ID=0 y API_HASH vacío en ambas variantes, aunque el PC esté configurado. CI verifica los dos BuildConfig TV generados; no imprime valores privados. No se publica Mobile como artefacto familiar.

La UI anterior sólo avisaba que faltaba configuración. Se agrega un formulario TV para ingresar credenciales propias (API ID positivo y hash hexadecimal de 32 caracteres), con los campos DPAD existentes y hash oculto. No se guardan en rememberSaveable ni se registran. Llama a session.configure existente: teléfono/código/2FA, base TDLib y almacenamiento no se reimplementan. Cada familiar obtiene sus credenciales en my.telegram.org → API development tools; el propietario puede asistir en el dispositivo. No hay extracción automática desde el número telefónico ni envío al administrador.

**Bloqueo para distribución sin asistencia:** quien dependía de las credenciales incrustadas necesita aprovisionar unas propias. La versión anterior no necesariamente las guardó en preferencias. La sesión queda en disco, pero no se garantiza que Telegram evite una nueva autorización. Nunca borrar datos para resolverlo. El almacenamiento existente es SharedPreferences privado, no una bóveda cifrada; se excluye del backup TV. No se cambia la implementación de autenticación.

## Firma y actualización: no hay clave familiar disponible en Work

Gradle previo: Debug firmado con la clave debug local; Release sin configuración de firma. No se tuvo acceso a tu PC, certificado instalado ni keystore, por lo que la firma de la APK familiar actual sigue SIN COMPROBAR. Un nombre de variante no prueba su certificado. La huella de CI no se considera la familiar.

No se crea un keystore. Para actualizar sin desinstalar se necesita mismo package, versión superior y certificado compatible. Cambiar a una nueva clave Release no es una actualización compatible por defecto. Recomendación inicial: conservar/resguardar la identidad actual y decidir una migración de firma separada. Una clave dedicada futura necesita autorización, copia cifrada y plan de recuperación; no se genera aquí ni se inventa continuidad con Debug.

El artefacto Debug CI es una candidata de laboratorio, no actualización compatible afirmada. Release se entrega SIN FIRMA: necesita firma local. Un APK release firmado localmente con la misma clave instalada puede conservar la identidad aunque no sea debuggable; se debe comparar la huella antes de instalar y confirmar la actualización real.

## Windows 11 / Android Studio

Crear otra carpeta sin sobrescribir trabajos locales:

```powershell
cd C:\Users\devia\StudioProjects\Miflix-TV-HomeDetails-Work
git status --short
git fetch origin
git worktree add ..\Miflix-TV-015 origin/feature/tv-0.1.15-google-home
cd ..\Miflix-TV-015
.\tools\build_tv_physical_debug.ps1
```

Abrir esa carpeta en Android Studio con JDK 17 y SDK/Build Tools existentes. No hace falta copiar el archivo privado de Telegram para TV. Conservar la clave debug original en su ubicación local; el script se detiene si falta para evitar crear otra identidad sin aviso. No publicar archivos privados en Git.

Las rutas se obtienen sin adivinar el nombre:

```powershell
py -3 tools/locate_tv_apk.py
py -3 tools/locate_tv_apk.py --variant tvRelease
```

Salidas habituales en app/build/outputs/apk/tv/debug y tv/release. Release unsigned no se instala tal cual. El script compila tests TV Debug/Release y Mobile, Lint y las tres APK, comprueba ARM, manifiestos y credenciales vacías.

### Comparar el certificado instalado sin abrir claves

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$tools=Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory | Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$signer=Join-Path $tools.FullName 'apksigner.bat'
$remote=(& $adb -s emulator-5554 shell pm path ar.com.miflix.client.tv | Where-Object { $_ -match '/base.apk' } | Select-Object -First 1) -replace '^package:',''
$installedApk=Join-Path $env:TEMP ('MiFlix-installed-'+[guid]::NewGuid().ToString()+'.apk')
& $adb -s emulator-5554 pull $remote.Trim() $installedApk
& $signer verify --print-certs $installedApk
Remove-Item $installedApk
$apk=py -3 tools/locate_tv_apk.py
& $signer verify --print-certs $apk
```

Comparar públicamente Signer #1 certificate SHA-256 digest. También confirmar package/version con el inspector; no basta el nombre del archivo. Si difieren, NO desinstalar. Buscar/restaurar la clave original.

Para firmar la release, tools/sign_tv_candidate.ps1 exige rutas explícitas, alias y huella pública esperada. Usa contraseñas en variables de entorno MIFLIX_KS_PASS/MIFLIX_KEY_PASS, nunca argumentos de texto/archivos versionados. Cargarlas desde Read-Host -AsSecureString en una consola local si se requiere, sin pegar contraseñas en el historial; eliminarlas al finalizar. El script sólo llama al apksigner local, no crea claves, y descarta la salida si el certificado no coincide con la huella suministrada.

```powershell
# Con contraseñas ya cargadas localmente en las dos variables, sin mostrarlas:
.\tools\sign_tv_candidate.ps1 -UnsignedApk 'RUTA_APK_RELEASE_UNSIGNED' -OutputApk '.\MiFlix-TV-015-local.apk' -KeystorePath 'RUTA_CLAVE_ORIGINAL' -KeyAlias 'ALIAS_ORIGINAL' -ExpectedCertificateSha256 'HUELLA_PUBLICA_INSTALADA'
```

Después de confirmar certificado y contenido:

```powershell
& $adb -s emulator-5554 install -r $apk
& $adb -s emulator-5554 shell monkey -p ar.com.miflix.client.tv -c android.intent.category.LEANBACK_LAUNCHER 1
& $adb -s emulator-5554 logcat -d -s MiFlixHome:I MiFlixHomeEntry:I '*:S'
```

No ejecutar la sonda ni comandos publish. Abrir MiFlix/cargar catálogo basta para la sincronización oportunista; esperar las imágenes en segundo plano. En TV física se puede copiar la APK local compatible por pendrive/Telegram y confirmar la actualización con el instalador, conservando los datos. No prometer instalación si el certificado o el almacenamiento no son compatibles.

## Lista física antes de declarar estable

- En entorno de prueba nuevo: instalación segura con firma controlada, aprovisionamiento propio y arranque/intro/Hero.
- En dispositivo existente: actualización compatible sin desinstalar; favoritos, vistos, progreso, sesiones y colecciones presentes.
- Catálogo válido: canal único, hasta diez registros reales automáticos y carátulas; elegir dos diferentes en Home abre cada ficha, sin reproducción automática.
- Repetir catálogo/retorno a primer plano: cero upserts/eliminaciones innecesarios, sin duplicados.
- Catálogo cambiado, vacío, inválido y sin Internet: preservar la selección válida ante fallos; no bloquear MiFlix.
- Proveedor inexistente/rechazo y launcher sin soporte: app utilizable, sin bucle ni errores técnicos al usuario.
- DPAD, Atrás, reproducción manual, seek, audio/subtítulos, series/anime, Especiales y Continuar viendo.
- Google TV API 36 y TV ARM Android 9 representativos; TV Box sólo según Android/ABI/proveedor/launcher reales.
- Firma familiar y actualización real comprobadas; no distribuir definitivamente hasta autorización expresa.

Las pruebas JVM/políticas no equivalen a Activity, TvProvider ni DPAD reales. Work no dispone del PC, Android Studio ni emulador del propietario. Esta entrega mantiene la denominación candidata mientras falten esas comprobaciones.

## Referencias y resguardo de firma

- Android Preview Channels: https://developer.android.com/training/tv/discovery/recommendations-channel
- Firma Android: https://developer.android.com/studio/publish/app-signing
- Compatibilidad de actualizaciones: https://developer.android.com/google/play/app-updates
- API Telegram: https://core.telegram.org/api/obtaining_api_id

El aprovisionamiento individual es la solución elegida para esta candidata sin credenciales del desarrollador, no una afirmación de que Telegram exija un API ID por cada usuario final. Requiere asistencia si el familiar no sabe obtenerlo; no se declara resuelta una instalación familiar sin configuración.

Resguardar la clave de firma original en al menos dos copias cifradas separadas y conservar alias/huella pública y el procedimiento de recuperación. No guardar sus contraseñas junto a la única copia ni subir keystores o APK antiguas configuradas al repositorio. Restaurar la identidad original antes de compilar una actualización; Work no posee una copia para recuperarla.

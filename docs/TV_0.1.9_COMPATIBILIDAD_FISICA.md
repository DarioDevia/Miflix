# MiFlix TV 0.1.9-diagnostico / 10 — preparación para TV físico

## Estado y base

Base local limpia, rama backup/miflix-tv-0.1.8 y commit remoto/local exacto:
`d947969c4100b6ae22ec6e06723bd74c64b22ec7`, versión 0.1.8/9.
Nueva rama backup/miflix-tv-0.1.9, sin modificar origen ni estable.
Se revisaron Gradle, manifests, carga JNI, README y documentación previa.

**Configuración preparada y bibliotecas del AAR verificadas. No se compiló ni se
inspeccionó una APK 0.1.9 aquí. Compatibilidad ARM del APK final pendiente.**
No hay validación de instalación o reproducción en TV físico.

## Diagnóstico de la APK 0.1.8

Se inspeccionó la APK aportada por el usuario (app-tv-debug.apk, 48.010.716 bytes),
sin leer DEX/BuildConfig, sesiones ni datos privados. SHA-256 del APK examinado:
`b219e8f9913abb3952ed3e422835dfb6f367daff2b02e83162f87e0721dcfe25`.
Manifest binario:

- package ar.com.miflix.client.tv; versión 0.1.8-diagnostico / 9.
- minSdk 26, targetSdk 34; debuggable=true y testOnly=true.
- LEANBACK_LAUNCHER presente; leanback requerido y touchscreen no requerido.
- Tráfico HTTP no permitido. Permisos fusionados: INTERNET, ACCESS_NETWORK_STATE
  y permiso propio DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION agregado por AndroidX.
- Única biblioteca nativa: lib/x86/libtdjni.so. Coincide byte por byte con el AAR.

El repositorio 0.1.8 no contiene abiFilters, splits, jniLibs locales ni una regla
que elimine ARM. Tampoco declara testOnly o una configuración de firma propia.
La dependencia ya contiene las cuatro ABI: **no faltan bibliotecas ARM en TDLib**.
El recorte se encuentra en el proceso/artefacto de compilación local, no en la
lista de dependencias versionada. La explicación más probable es una compilación
Run optimizada para el emulador: Android Studio puede inyectar ABI de destino y
marca testOnly en Run. Sin el registro/comando original no se puede demostrar qué
invocación exacta lo produjo; un comando manual con parámetros equivalentes puede
producir el mismo resultado. No se atribuye a un filtro inexistente del proyecto.

## TDLib y procedencia

Se mantiene exactamente `io.github.tdlib-android:core:0.1.1`, ya utilizado por TV y
Mobile. Es un paquete comunitario, **no un AAR oficial de Telegram**. Descargado de:

https://repo.maven.apache.org/maven2/io/github/tdlib-android/core/0.1.1/core-0.1.1.aar

Se leyeron también .pom y .module del mismo directorio. Tamaño AAR: 39.862.474 bytes.
SHA-256 coincidente con el metadata Gradle publicado en Maven Central:

`1ec6d6c6c83b655349d8a91182145cdd5400392246136ad79432253ad6004106`

Bindings originales en libs/tdlib-classes.jar; contiene Client y TdApi, con SHA-256:

`e6f921efaba2c652cde2637de3f0d74e698529ff7034e590ee842c06649b2fa0`

No se separan bindings y nativos de diferentes paquetes/versiones, no se cambian
APIs ni la carga System.loadLibrary("tdjni"). La cadena de versión observada en
las cuatro bibliotecas es 1.8.67; esto no reemplaza un getOption("version") en
runtime ni certifica un commit upstream. El commit exacto de TDLib no pudo
establecerse independientemente; el repositorio declarado en POM devolvió 404
al consultarlo. No se incorporan binarios nuevos ni se actualiza la dependencia.

La licencia declarada por el POM es Boost Software License 1.0 (BSL-1.0), la misma
familia usada por TDLib oficial. Se conserva la obtención mediante Maven Central;
no se suben .so/AAR/JAR al repositorio ni se crea una distribución nativa separada.
Las obligaciones de las dependencias incorporadas y avisos de licencia deben
conservarse al distribuir. No se presenta el checksum del mismo proveedor como
una auditoría independiente de todo el código nativo.

## Arquitecturas reales verificadas en el AAR

| ABI | ELF | Máquina | Bytes |
| --- | --- | --- | ---: |
| armeabi-v7a | ELF32 | ARM (40) | 16.371.168 |
| arm64-v8a | ELF64 | AArch64 (183) | 22.729.264 |
| x86 | ELF32 | Intel 80386 (3) | 27.180.944 |
| x86_64 | ELF64 | AMD x86-64 (62) | 25.265.264 |

Hashes exactos por biblioteca en tools/tdlib-0.1.1-provenance.json. Se inspeccionaron
cabeceras ELF, exportación JNI_OnLoad y readelf -n/-d: nota Android API 21 / NDK r27c en las cuatro;
dependencias dinámicas liblog, libdl, libz, libm y libc. No hay dependencia de
libc++_shared faltante declarada. Es evidencia de empaquetado nativo, no una prueba
de carga/ejecución en Android 9. Se conserva minSdk 26 (Android 8); Android 9/API 28
cumple ese mínimo. compileSdk/targetSdk se mantienen en 34 y AGP en 8.2.2.
No se promete compatibilidad universal con todos los fabricantes ni Android nuevo.
x86_64 se conserva porque tiene un ELF real en el mismo AAR; no se fabrica soporte.

## Cambios mínimos

- Sólo el flavor tv cambia a 0.1.9/10 y declara abiFilters con las cuatro ABI reales.
- No se agregan splits ni jniLibs, dependencias, NDK o compilación C++ al proyecto.
- El filtro expresa qué incluir; no genera bibliotecas ni sustituye inspección.
- tools/build_tv_physical_debug.ps1 invoca Gradle desde terminal para generar el APK
  debug normal, con lista ABI completa y testOnly=false sólo en esa invocación.
  No cambia propiedades globales ni fuerza testOnly en manifests de Run.
- El script ejecuta tests Gradle TV/Mobile y verifica el APK con Python y apksigner.
  Se detiene ante errores: no anunciar ni distribuir como ARM si falla la inspección.
- Mobile mantiene versión 0.9.2/26, dependencia y configuración. No se publica Mobile.
- Todo app/src permanece idéntico a 0.1.8: lógica, recursos, permisos y manifests.
  Reproductor, rangos, etiquetas, selección, progreso, Inicio, foco y autenticación
  intactos. Tráilers/AC3 congelados; ninguna función o prueba específica de anime.

## Debug, release, firma y testOnly

Debug, debuggable y testOnly no son sinónimos. El APK de pruebas solicitado es
**tvDebug universal**, debuggable=true y testOnly ausente/false, firmado con el
keystore debug local que Android Gradle Plugin ya utiliza. Puede instalarse por
administrador de archivos o ADB sin -t si pasa la inspección y el TV permite esa
fuente. No es una release para publicación de producción.

No se crean, abren, imprimen ni publican claves privadas. El release predeterminado
no tiene firma de distribución configurada: no se entrega como APK instalable.
Para producción habrá que firmar con una clave privada estable y mantenerla fuera
del repositorio. Una APK nueva sólo actualiza conservando datos si mantiene el
applicationId y certificado de la instalada (y un versionCode aceptable). Otro PC
puede tener una clave debug distinta. No borrar la instalación familiar para
resolver un conflicto de firma: se perderían sesión/progreso locales. Comparar
certificados con apksigner --print-certs localmente, sin compartir claves.

## Windows 11 / Android Studio

Guardar cambios locales antes de cambiar de rama. No usar reset/clean forzados.

```powershell
git fetch origin
git switch --track -c backup/miflix-tv-0.1.9 origin/backup/miflix-tv-0.1.9
git rev-parse HEAD
```

Si ya existe la rama: git switch backup/miflix-tv-0.1.9 y git pull --ff-only
origin backup/miflix-tv-0.1.9. Abrir proyecto, sincronizar Gradle y elegir tvDebug.
Usar el JDK 17 de Android Studio y SDK habitual (API 34/Build Tools instalados).
Conservar tu configuración Telegram privada local; nunca publicarla.

Para compilar **y comprobar** desde la terminal PowerShell de Android Studio,
con Python 3 disponible mediante py:

```powershell
.\tools\build_tv_physical_debug.ps1
# Si tu SDK está en otra carpeta:
.\tools\build_tv_physical_debug.ps1 -AndroidSdk 'D:\Android\Sdk'
```

El script usa el Build Tools estable instalado más reciente y su apksigner.bat.
No cambia la política de ejecución de PowerShell. Si Windows bloquea scripts,
se pueden ejecutar directamente los mismos comandos, sin alterar esa política:

```powershell
.\gradlew.bat :app:testTvDebugUnitTest :app:testMobileDebugUnitTest :app:assembleTvDebug "-Pandroid.injected.build.abi=armeabi-v7a,arm64-v8a,x86,x86_64" "-Pandroid.injected.testOnly=false"
py -3 tools/verify_tv_physical_apk.py app/build/outputs/apk/tv/debug/app-tv-debug.apk --apksigner "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat"
```

Ajustar la ruta de Build Tools a la versión que tengas instalada. Si falla Gradle,
no inspeccionar/distribuir una APK vieja que haya quedado en la carpeta. La revisión
exige versión 0.1.9/10 y los hashes de las cuatro bibliotecas reales. No omitirla.
Una diferencia de hash debe investigarse (por ejemplo transformación de stripping),
no sustituirse a ciegas en el archivo de procedencia.

APK esperada: **app/build/outputs/apk/tv/debug/app-tv-debug.apk**.
En Android Studio, Build → Generate/Build APK(s) también genera un APK compartible;
no usar la copia desplegada por Run como entrega familiar. Revisar siempre la APK
final con el verificador. APK Analyzer permite revisar manifest y las cuatro rutas
lib/<ABI>/libtdjni.so; py --inspect sólo informa, no valida firma ni distribución.

## Instalación en Android TV 9

1. Confirmar ABI real del sistema (un procesador 64 bits puede ejecutar Android 32):
   `adb shell getprop ro.product.cpu.abilist`. Debe coincidir con una ABI incluida.
2. Copiar la APK ya verificada a pendrive, descargarla desde Telegram o pasarla al
   administrador de archivos del TV. Autorizar instalación desde esa fuente en los
   ajustes del sistema sólo si hace falta. No se agrega permiso alguno a MiFlix.
3. Abrir APK e instalar. Por ADB: `adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk`.
   No usar -t. Un INSTALL_FAILED_TEST_ONLY indica que se tomó el artefacto incorrecto;
   corregir compilación, no saltear el requisito. Firma incompatible: conservar datos
   y resolver la firma antes de seguir. Otros bloqueos del fabricante pueden existir.
4. Confirmar acceso desde el lanzador TV, control remoto, versión en Acerca de MiFlix,
   inicio de Telegram y reproducción de una película y un episodio de serie.
5. Probar seek, pausa/reanudar, pistas/subtítulos, Back, progreso y Continuar viendo.
   No publicar logs privados. Un APK instalado no demuestra streaming funcional.
6. Tráilers/AC3 siguen pendientes de prueba física, sin investigación en esta versión.

## Pruebas y límites de entrega

- AAR descargado de Maven Central: SHA-256 y metadata coincidentes; cuatro ELF y
  bindings del mismo paquete verificados. Script verify_tdlib_aar.py pasó.
- APK real 0.1.8 inspeccionada: x86 sólo/testOnly=true; rechazada por el verificador.
- 11 tests Python de configuración/inspector/fuentes congeladas pasaron. Las pruebas
  de firma usan mocks; no cuentan como validación de una firma real.
- 33 pruebas JVM de políticas existentes pasaron (selección compartida, etiquetas TV,
  sugerencias, controles y parser de enlaces existente, sin investigar/modificar tráilers).
  Ejecutadas con Kotlin 1.9.22/JUnit 4.13.2; no equivalen a tests Gradle completos.
- 6 contratos previos del panel pasaron. Diff de todo app/src vacío frente a 0.1.8.
- Revisión de sintaxis Python y git diff --check; sin instalación de SDK ni emulador.
- El script PowerShell/Gradle no pudo ejecutarse aquí: Windows/SDK/JDK completos
  no disponibles. El verificador requiere el apksigner oficial instalado localmente.
- Tests Gradle TV/Mobile, assembleTvDebug/assembleMobileDebug, APK 0.1.9, firma de
  esa APK, instalación normal y reproducción física: pendientes del entorno local.

**La preparación de código no completa la compatibilidad ARM final. El paso
imprescindible pendiente es compilar e inspeccionar la APK 0.1.9 real, y después
comprobarla en el TV. No se entrega una APK remota ni se declara validación física.**

## Archivos de esta entrega

- app/build.gradle.kts (sólo flavor TV).
- README.md y CHANGELOG_TV.md.
- docs/TV_0.1.9_COMPATIBILIDAD_FISICA.md.
- tools/tdlib-0.1.1-provenance.json.
- tools/verify_tdlib_aar.py.
- tools/verify_tv_physical_apk.py.
- tools/test_tv_physical_apk.py.
- tools/build_tv_physical_debug.ps1.

## Fuentes

- Maven Central: .aar, .module y .pom de la coordenada exacta (URL anterior).
- https://developer.android.com/build/build-for-release (Run/testOnly frente a APK normal).
- https://developer.android.com/tools/apksigner (verificación oficial de firma).
- https://developer.android.com/ndk/guides/abis (ABI y bibliotecas nativas).
- https://github.com/tdlib/td y https://core.telegram.org/tdlib (origen oficial).
- https://android.googlesource.com/platform/tools/base/ (propiedades inyectadas ABI/testOnly).

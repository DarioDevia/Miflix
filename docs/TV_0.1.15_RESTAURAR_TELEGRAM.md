# MiFlix TV 0.1.15 / 16 — restauración de preconfiguración Telegram

## Base y causa

Rama feature/tv-0.1.15-google-home, base b4cfe6e14793cad9d71b9112bb6166ed2e477620. Referencia funcional e20be389c79e0176fc9fa068fd1c91354f824641 (0.1.14). Se conserva 0.1.15-diagnostico / 16 y ar.com.miflix.client.tv.

Gradle ya leía telegram.local.properties desde la raíz y definía BuildConfig con esos datos en defaultConfig. La regresión estaba en el flavor TV: dos buildConfigField sobrescribían los valores por 0/vacío, aunque el archivo fuera válido. Se eliminan esas dos sobrescrituras. No se reemplaza silenciosamente una configuración válida por datos de prueba.

TelegramSession es idéntico a 0.1.14: detecta configuración incorporada y llama a configure automáticamente; si no la hay conserva el mecanismo existente de preferencias. No se modifica TDLib, teléfono/código/2FA, bases de sesión, favoritos ni progreso. No se borra almacenamiento. La pantalla ya da prioridad a BuildConfig válido; el formulario queda sólo como alternativa cuando no está configurado. Se corrige el texto de Configuración para que no afirme falsamente que una APK configurada carece de credenciales.

Todos los componentes de Google TV Home permanecen byte por byte respecto a la base indicada, salvo el texto de configuración alternativa, que no participa en publicación o navegación. No se revierte la rama ni se modifica la estable 0.1.14. Mobile conserva código, identidad, versión y sus valores originales de defaultConfig.

## Compilación privada local

El script tools/build_tv_physical_debug.ps1 requiere por defecto el archivo en la raíz y aplica -PmiflixTvFamilyBuild=true. Gradle rechaza archivo ausente/vacío o configuración incompleta/inválida con un diagnóstico sin valores. El inspector usa modo private-preconfigured, comprueba presencia válida en los dos BuildConfig TV y no imprime los campos. No firma con una identidad nueva ni publica nada.

El modo explícito -DiagnosticWithoutTelegram sirve sólo para un checkout sin archivo; no vacía un archivo existente. CI utiliza ese resultado no configurado como diagnóstico, no como APK familiar.

### Windows / Android Studio, sin sobrescribir tu proyecto

Desde la terminal PowerShell del proyecto local que contiene tu archivo original:

```powershell
$miflixOrigen=(Get-Location).Path
git status --short
git fetch origin
git worktree add ..\Miflix-TV-015-Telegram origin/feature/tv-0.1.15-google-home
cd ..\Miflix-TV-015-Telegram
if (-not (Test-Path (Join-Path $miflixOrigen 'telegram.local.properties'))) {
    throw 'La carpeta de origen no tiene el archivo original. Usa como origen el proyecto 0.1.14 donde compilabas; no crees datos vacios ni los compartas.'
}
Copy-Item (Join-Path $miflixOrigen 'telegram.local.properties') .\telegram.local.properties
git check-ignore telegram.local.properties
.\tools\build_tv_physical_debug.ps1
```

La copia ocurre exclusivamente en tu PC y no muestra el contenido. Si tu proyecto actual no tiene el archivo, el origen debe ser la carpeta original (por ejemplo el proyecto TV 0.1.14 que ya compilabas). No subirlo ni adjuntarlo a Work. Usar JDK 17 y el SDK instalado de Android Studio; el script proporciona el SDK encontrado a Gradle y restaura el entorno al terminar.

Alternativa Gradle directa, siempre con el archivo original en la raíz:

```powershell
.\gradlew.bat :app:assembleTvDebug :app:assembleTvRelease -PmiflixTvFamilyBuild=true '-Pandroid.injected.testOnly=false' '-Pandroid.injected.build.abi=armeabi-v7a,arm64-v8a,x86,x86_64'
```

El indicador familiar es obligatorio en este procedimiento para no confundir una APK de diagnóstico sin configuración con la entrega privada.

### Qué APK distribuir

```powershell
py -3 tools/locate_tv_apk.py
py -3 tools/locate_tv_apk.py --variant tvRelease
```

- La primera ruta identifica **TV Debug compilada en tu PC con tu archivo**, normalmente app/build/outputs/apk/tv/debug/app-tv-debug.apk. Se puede usar como actualización privada si su certificado coincide con el instalado; no es la Debug descargada de CI.
- TV Release local contiene la preconfiguración, pero queda sin firmar hasta aplicar tu procedimiento de firma existente. El script de firma compara la huella esperada; no genera un keystore. No distribuir una Release unsigned.
- Mobile se compila sólo para comprobar regresiones; no es parte de esta entrega ni debe distribuirse por esta tarea.

La firma, applicationId y compatibilidad real deben comprobarse como indica el informe principal. Conservar la clave original, no desinstalar ni limpiar datos para solucionar diferencias. La versión permanece 0.1.15 / 16 por instrucción: esta corrección no redefine una publicación independiente.

## Seguridad y CI

El propietario acepta expresamente que valores incorporados a una APK son extraíbles. .gitignore mantiene fuera telegram.local.properties, APK y claves locales. Eso no impide extraer los datos del binario privado: la distribución debe ser controlada. No subir APK familiar ni BuildConfig generado a GitHub, Drive público, logs o artefactos CI. Firma digital no equivale a cifrado de credenciales.

Work no abre ni solicita el archivo real. CI empieza desde un checkout sin él. Una prueba aislada:

1. Comprueba error familiar ante ausencia.
2. Comprueba error ante configuración parcial sintética.
3. Crea una configuración válida sintética no secreta, compila Kotlin TV Debug/Release y verifica campos configurados sin mostrarlos.
4. Elimina el archivo en finally y limpia todas las salidas.
5. Genera constantes vacías y comprueba que el modo público rechaza valores configurados.
6. Ejecuta tests/Lint/build de TV Debug, TV Release y Mobile; exige ausencia del archivo y campos TV vacíos antes de publicar artefactos.

No se genera ni publica una APK con las credenciales del propietario. Las APK de CI no son las familiares configuradas. El caso sintético no acredita autorización real de Telegram.

## Verificación física pendiente

En una actualización con certificado compatible: sesión existente conservada; si hace falta autorización, teléfono/código/2FA sin pedir API ID/hash; reproducción manual y datos personales presentes. Google TV Home debe seguir sincronizando, mostrando hasta diez tarjetas y abriendo la ficha correcta. En APK de CI sin configuración puede aparecer la alternativa manual: es deliberado y no corresponde a la entrega familiar.

No se ha ejecutado esta APK en el dispositivo desde Work. La compilación con tu archivo real ocurre sólo en tu PC; no se declara probada aquí. Cualquier estrategia futura distinta de aprovisionamiento se evaluará aparte y con autorización, sin imponerla a los familiares. Opciones futuras: mantener el aprovisionamiento en el equipo del administrador o permitir una configuración alternativa voluntaria; ninguna hace inexpugnables los valores que finalmente llegan al cliente.

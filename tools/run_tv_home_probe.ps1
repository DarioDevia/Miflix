# Run only on the chosen emulator. No source edits, uninstall, data clear or privileged commands.
[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5554',
    [string]$AndroidSdk = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$Jdk17 = (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium\jdk-17.0.20.101-hotspot'),
    [switch]$SkipBuild,
    [switch]$AllowUnconfiguredProbe
)
$ErrorActionPreference='Stop'
$adb = Join-Path $AndroidSdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw 'ADB no encontrado. Usar -AndroidSdk con el SDK local existente.' }
if (-not (Test-Path (Join-Path $Jdk17 'bin\java.exe'))) { throw 'JDK 17 no encontrado. Usar -Jdk17.' }
$originalDebugKey = Join-Path $env:USERPROFILE '.android\debug.keystore'
if (-not (Test-Path $originalDebugKey)) { throw 'Falta la clave debug local existente. No crear otra para reemplazar una instalación previa.' }
Push-Location (Split-Path $PSScriptRoot -Parent)
$previousJavaHome=$env:JAVA_HOME
try {
    $env:JAVA_HOME=$Jdk17
    & $adb -s $Serial get-state
    if ($LASTEXITCODE -ne 0) { throw 'Emulador seleccionado no accesible.' }
    # Presence only. Never read or print private properties.
    if (-not (Test-Path 'telegram.local.properties') -and -not $AllowUnconfiguredProbe) {
        throw 'Esta carpeta no tiene la configuración privada habitual. Conservarla localmente; o usar -AllowUnconfiguredProbe sólo en un emulador de prueba sin credenciales. La sonda no necesita Telegram, pero la app estable sí.'
    }
    if (-not $SkipBuild) {
        & .\gradlew.bat :app:testTvDebugUnitTest :app:lintTvDebug :app:assembleTvDebug --console=plain '-Pandroid.injected.testOnly=false' '-Pandroid.injected.build.abi=armeabi-v7a,arm64-v8a,x86,x86_64'
        if ($LASTEXITCODE -ne 0) { throw 'Gradle falló. No instalar.' }
    }
    $apk=& py -3 tools/locate_tv_apk.py
    if ($LASTEXITCODE -ne 0) { throw 'No se encontró una APK tvDebug única.' }
    & $adb -s $Serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Instalación rechazada. No desinstalar ni borrar datos; revisar certificado o espacio.' }
    & $adb -s $Serial shell am start -n 'ar.com.miflix.client.tv/ar.com.miflix.client.TvHomeProbeActivity' --es probe_action publish
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir la sonda.' }
    Write-Host 'Consultar sólo el tag de sonda, sin volcar Logcat global:'
    Write-Host "& '$adb' -s '$Serial' logcat -d -s MiFlixHomeProbe:I '*:S'"
    Write-Host 'Cuando aparezca PROGRAMAS_VERIFICADOS, solicitar visibilidad desde la sonda y abrir Home.'
    Write-Host 'Publicar una segunda vez debe dar upserts=0 y duplicados=0 si el catálogo no cambió.'
    Write-Host 'Sólo la inspección visual de LauncherX permite nivel 4. Abrir MiFlix es una prueba intermedia; ficha directa pendiente.'
} finally {
    $env:JAVA_HOME=$previousJavaHome
    Pop-Location
}

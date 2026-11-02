# Google TV Home: apertura directa de fichas (experimental tvDebug)

## Base y evidencia previa

Base `244428a2a8f2c2d5f1abc3d535658835771886b6`, rama `experiment/tv-google-home-probe-work`.
El propietario confirmó visualmente el nivel 4: canal 2, diez títulos reales, carátulas horizontales y navegación por la fila. La primera tarjeta era Ápex. Esa observación no implica que esta entrega tenga nivel 5 validado.

Se conserva 0.1.14-diagnostico / 15 y applicationId ar.com.miflix.client.tv. La rama estable e20be389c79e0176fc9fa068fd1c91354f824641 no se modifica.

## Arquitectura e implementación

MainActivity es la única Activity de la aplicación normal. ClientApp mantiene Screen, selectedId y detailReturnScreen con rememberSaveable. Inicio, Búsqueda y Colecciones llaman a openDetail; TvDetail usa los controles DPAD existentes. Reproducir llama a un flujo separado.

Se agregan únicamente puntos aditivos de conexión en MainActivity/ClientApp. ExperimentalHomeEntry tiene implementaciones inactivas en Mobile y TV Release (sin interpretación de Intent ni callbacks) y una implementación real sólo en tvDebug. No se cambia el motor, la intro, Hero, autenticación, catálogo ni biblioteca.

Las tarjetas conservan su clave de reconciliación y pasan ahora el ID estable MiFlix del título en un Intent explícito hacia MainActivity. No hay búsqueda por nombre/TMDB ni URLs de Telegram. Las tarjetas anteriores se actualizan con la reconciliación existente: mismo canal, mismos programas cuando ya coinciden, sin borrado anticipado. La imagen se preserva exactamente.

MainActivity usa singleTask únicamente en el manifiesto tvDebug; los Intents incluyen NEW_TASK, CLEAR_TOP y SINGLE_TOP. Android entrega una selección nueva a onNewIntent con la instancia existente. No se añade una Activity exportada ni un intent-filter. MainActivity ya era el lanzador exportado. Mobile/Release conservan el launchMode anterior.

## Seguridad, espera y consumo

Acción explícita ar.com.miflix.client.tv.HOME_DETAIL, extra miflix_home_title_id. Sólo IDs opacos de 1–256 caracteres ASCII alfanuméricos con punto, guion, subrayado o dos puntos; se rechazan URLs, rutas, control, tipos incorrectos, data, selector y ClipData. No se interpretan parámetros de reproducción ni otros extras. Un Intent inválido se ignora sin borrar la selección válida pendiente. No se registran IDs, títulos ni credenciales.

El ID se resuelve por igualdad exacta contra el catálogo cargado y sólo admite película, serie o anime. Una identidad duplicada se rechaza como ambigua. La última tarjeta válida reemplaza cualquier solicitud pendiente anterior. El consumo ocurre antes del callback de navegación y una finalización antigua no puede consumir una solicitud más nueva.

La solicitud pendiente se guarda en savedInstanceState. Una recreación sin solicitud pendiente no vuelve a interpretar el Intent original. Durante la intro se mantiene en la Activity; después espera hasta 15 segundos al catálogo listo y fuera de actualización. Si el usuario necesita autorizar Telegram, espera su flujo normal sin saltarlo. Una ausencia/timeout muestra un mensaje amigable y vuelve controladamente a Inicio si la sesión está autorizada; si no, conserva Conectar.

Una apertura externa cancela diálogos/transiciones pendientes, vuelve al origen Inicio y llama al openDetail habitual. Atrás desde esa ficha vuelve a Inicio. Inicio/Búsqueda/Colecciones mantienen su ruta anterior. No hay callback de reproducción automática. Una nueva tarjeta mientras se reproduce sale por el cambio de pantalla habitual y la liberación existente del reproductor.

## Pruebas y límites

Doce tests JVM nuevos de políticas/reductor: ID válido/ausente/malformado, arranque pendiente, autorización, ID ausente/ambiguo, dos tarjetas consecutivas, consumo único/Atrás, recreación, solicitud inválida y resolución de los tres tipos. No equivalen a ejecutar Android Intent, Activity, Compose ni DPAD.

Nueve contratos Python verifican los puntos exactos permitidos en el archivo compartido, implementaciones inactivas, ausencia de reproducción en el callback, uso de openDetail, manifiesto debug e imagen idéntica a la base. Los demás archivos preexistentes protegidos siguen comparándose íntegramente. Se añade testTvReleaseUnitTest a CI, manteniendo las pruebas Mobile/TV, Lint, builds e inspección ARM/manifiestos.

Work no tiene acceso al Windows, Android Studio, ADB ni emulador del propietario. No se declara nivel 5 hasta observar desde Google TV Home la ficha específica correcta.

## Actualización sin sobrescribir el worktree actual

En PowerShell:

```powershell
cd C:\Users\devia\StudioProjects\Miflix-TV-Home10-Protected
git status --short
git fetch origin
git worktree add ..\Miflix-TV-HomeDetails-Work origin/experiment/tv-google-home-probe-work
cd ..\Miflix-TV-HomeDetails-Work
```

Preparar en esa carpeta la configuración privada local y la misma firma debug existente. No copiar esos archivos a Git, no crear otra identidad de firma, no desinstalar ni limpiar datos. JDK 17 y SDK locales existentes:

```powershell
.\tools\run_tv_home_probe.ps1 -Serial emulator-5554
```

El script compila e instala con adb install -r usando la configuración local. No usar la APK de CI sobre la instalación local sin comprobar certificados.

### Comprobación real obligatoria

1. Actualizar catálogo dentro de MiFlix si corresponde.
2. En la sonda, sincronizar las diez tarjetas para actualizar sus Intents. No recrear canal.
3. Repetir: cero upserts/eliminaciones si nada cambia.
4. Abrir Home, elegir Ápex y comprobar su ficha, sin reproducción automática.
5. Volver a Home y elegir otra tarjeta con MiFlix aún abierto; comprobar la ficha nueva.
6. DPAD/OK/Atrás: ficha → Inicio; reproducir sólo por acción explícita y volver normalmente.
7. Probar arranque frío sin borrar datos, con catálogo y autorización existentes; repetir la selección desde Home.
8. Probar ausencia de ID y título obsoleto: no cierre, no título incorrecto.

Comandos de observación (no son resultados observados en Work):

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5554 shell am start -n ar.com.miflix.client.tv/.TvHomeProbeActivity --es probe_action publish
& $adb -s emulator-5554 shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
& $adb -s emulator-5554 logcat -d -s MiFlixHomeEntry:I MiFlixHomeProbe:I '*:S'
# Arranque frío opcional, conserva datos/canal:
& $adb -s emulator-5554 shell am force-stop ar.com.miflix.client.tv
# Elegir nuevamente una tarjeta en Home.
```

Diagnósticos: SOLICITUD_RECIBIDA, FICHA_SOLICITADA, SOLICITUD_RECHAZADA, TITULO_NO_DISPONIBLE o CATALOGO_ESPERA_AGOTADA. FICHA_SOLICITADA prueba el despacho del callback, no reemplaza la observación de la ficha específica.

## Resultado de entrega

Código validado: `27f5fbb98fc7c2d79f02fd11fc53f6b743760781`.
CI: https://github.com/DarioDevia/Miflix/actions/runs/41386182365 — conclusión success.

- Nueve contratos Python aprobados y sintaxis PowerShell aprobada.
- Doce tests JVM nuevos de políticas/reductor incorporados; todas las tareas testTvDebugUnitTest, testTvReleaseUnitTest y testMobileDebugUnitTest aprobaron.
- lintTvDebug, lintTvRelease y lintMobileDebug aprobaron sin deshabilitar controles. Hay advertencias existentes y de parámetros sin uso en los adaptadores deliberadamente inactivos; no se afirma cero advertencias.
- assembleTvDebug, assembleTvRelease y assembleMobileDebug aprobaron. Gradle BUILD SUCCESSFUL in 6m 22s.
- Inspección de APK aprobada: ARM/TDLib y recursos protegidos, identidad/versionado, componentes y permisos experimentales sólo en tvDebug; MainActivity singleTask sólo en tvDebug, modo anterior en Release/Mobile.
- Artefacto: https://github.com/DarioDevia/Miflix/actions/runs/41386182365/artifacts/13115378382 . Incluye APK debug de CI (nombre histórico MiFlix-TV-0.1.14-Home10-CI-debug.apk), informes Lint/tests, SHA256SUMS y certificado de CI. No es una firma comprobada contra la instalación del usuario. El artefacto ZIP tiene SHA-256 `6ba0ad44d5914ef091fb0c89ddeec3861ac5e6a386a0ca696f680b5f3d898857` (no confundir con hash de APK).
- No se ejecutaron ADB, Android Studio, emulador ni pruebas de DPAD en Work. No se ha verificado físicamente el nivel 5 ni se afirma que las diez tarjetas sean visibles simultáneamente.
- La selección de estrenos y la protección contra conjunto vacío no cambian. Tras compilar localmente se debe sincronizar para actualizar los Intents de los programas ya publicados.

El commit posterior de cierre contiene únicamente este informe; el código entregado es idéntico al validado por CI.

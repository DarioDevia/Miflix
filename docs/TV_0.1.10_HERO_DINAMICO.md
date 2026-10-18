# MiFlix TV 0.1.10-diagnostico / 11 — Hero dinámico

## Base y estado de validación

Base local limpia y ref remota verificada antes de editar:
backup/miflix-tv-0.1.9, commit 2f067c63fcee7c0378d30ad565745fc01d5999e3,
versión 0.1.9-diagnostico / 10. Rama nueva feature/tv-0.1.10-dynamic-hero.
No se parte de miflix-tv ni se actualiza estable o el respaldo 0.1.9.

El usuario informó posteriormente instalación y pruebas de MiFlix TV en dispositivo
físico, con películas, tráilers de YouTube y navegación general funcionando. Esa
confirmación corresponde a la base, no valida el nuevo Hero ni certifica AC3.
No se reconstruyen ni investigan esas funciones en esta entrega.

**Implementado y comprobado en políticas/contratos. No compilado para Android aquí,
no se generó una APK 0.1.10 ni se inspeccionó su contenido ARM. No declarar esta
entrega preparada para una nueva prueba física hasta completar esa comprobación.**

## Selección de películas

El Hero usa exclusivamente películas del catálogo existente con ID/nombre válidos
y al menos una URL HTTPS de backdrop/fondo aceptada por tvDetailArtwork. No usa
series, episodios, anime, puntuación, fecha de incorporación ni orden por año para
el sorteo. Las filas de sugerencias por año de Inicio se mantienen sin cambios.

Hasta diez títulos únicos, sin inventar títulos ni repetir para rellenar catálogos
pequeños. Menos de diez elegibles implica menos diapositivas; cero implica fondo
neutro y catálogo normal, sin forzar una serie o un póster vertical como Hero.

El orden parte de un barajado aleatorio y favorece títulos ausentes de la sesión
anterior. Entre candidatos de igual prioridad favorece géneros distintos del
anterior y variedad dentro de la lista. Con pocos títulos/géneros puede haber
repeticiones entre sesiones: se evita sólo en la medida que permite el catálogo.
Los géneros desconocidos no se inventan. No se utilizan “las más votadas”.

Sólo IDs y géneros de la selección se guardan en preferencias TV separadas
(miflix_tv_hero). No se consulta ni cambia ninguna preferencia de Telegram, progreso
u otras apps. El historial guardado sirve para evitar repeticiones en el siguiente
inicio real. No se registran enlaces ni credenciales en esas preferencias.

La lista y el índice viven en una instancia sin Context/Activity del proceso TV.
Recomposición, cambio de pantalla y recreación de Activity no sortean nuevamente.
Un proceso nuevo inicia un sorteo nuevo; volver desde Recientes con proceso vivo
conserva la sesión. El catálogo temporalmente vacío durante carga no borra el
sorteo anterior. Al refrescar se conservan títulos/orden/índice supervivientes y
sólo se rellenan huecos. Si desaparece la película activa, se elige una disponible.

## Rotación, imágenes y DPAD

- Intervalo de 8.000 ms entre cambios mientras Inicio esté RESUMED, Hero visible
  en el viewport, más de una película y foco fuera de sus botones.
- Con foco dentro del Hero se pausa; no se cambia la película debajo de OK/Enter.
  El foco inicial se conserva en el Hero como antes: para observar rotación,
  bajar a una fila mientras el Hero siga visible. No interpretar esa pausa como
  un temporizador roto. Al salir del Hero se cuenta un intervalo completo nuevo.
- Al abrir ficha/reproductor/otra pantalla se desmonta la lógica del Hero y se
  cancela el temporizador. Al pausar la Activity también se cancela. Volver conserva
  la película y el índice; no avanza de golpe por el tiempo transcurrido fuera.
- La rotación no solicita foco ni scroll. El efecto anterior LaunchedEffect(featured)
  habría llevado el foco al inicio en cada rotación; se reemplaza por una solicitud
  inicial única. Se mantienen el retorno desde filas y las claves de carátulas.
- Fade de 500 ms sólo entre fondos decorativos. Título, logo, sinopsis y callbacks
  cambian desde el mismo Title actual; los botones no se duplican ni animan.
  El logo conserva la sustitución por texto que ya tenía MiFlix.
- Se precargan sólo fondo y logo de la película siguiente mediante el ImageLoader
  Coil existente. Tamaños acotados por la política actual (backdrop 1280×720),
  sin decodificar diez fondos a la vez ni agregar dependencias.
- Las URLs se validan con la política existente; no se asume disponibilidad de red
  por tener sintaxis válida. Si falla una imagen se intenta el siguiente backdrop
  publicado; se descarta de la sesión una película cuyos fondos fallan o se
  detectan verticales al cargar. Si su botón está enfocado, se mantiene esa película
  sobre fondo neutro hasta que el foco salga: un fallo decorativo no cambia OK. No se consulta TMDB ni se inventa una imagen.
  Un fallo transitorio de red puede excluirla hasta el próximo proceso de app.
- Reproducir y Ver ficha usan exactamente los callbacks existentes, con progreso
  y confirmaciones previas intactos. No hay reproducción automática ni tráiler nuevo.

## Componentes congelados

Diff contra la base: reproductor Media3, rangos, TDLib/Telegram, autenticación,
tráilers, audio/subtítulos, panel, controles, carátulas, catálogo, persistencia,
Continuar viendo y manifests idénticos. TvCard y todas las filas posteriores al
Hero de TvHome siguen idénticas. No hay cambios de Mobile en versión o selección
FeaturedSession; la bifurcación es sólo TV. Sin cambios específicos de anime.

Gradle cambia únicamente versión TV a 0.1.10/11. Se conservan exactamente:

- minSdk 26, targetSdk/compileSdk 34.
- abiFilters TV: armeabi-v7a, arm64-v8a, x86, x86_64.
- io.github.tdlib-android:core:0.1.1 y hashes de procedencia.
- manifests, permisos, LEANBACK_LAUNCHER y touchscreen no requerido.
- firma local debug y comando de build universal normal sin testOnly.

El verificador de APK se actualiza para exigir 0.1.10/11. Mantiene los mismos
hashes ELF y la comprobación por apksigner oficial; no se flexibiliza para aceptar
bibliotecas ausentes. No se crea ni publica una clave, una sesión o una APK sin
configuración privada. No se abrieron archivos de credenciales.

## Comprobaciones ejecutadas

| Comprobación | Resultado y alcance |
| --- | --- |
| Kotlin 1.9.22 / JUnit 4.13.2 | 44 pruebas pasaron: 11 nuevas de selección/rotación y 33 regresiones puras existentes |
| tools/check_tv0110_contract.py | 6 contratos pasan: MainActivity acotada, filas/carátulas intactas, foco, lifecycle/acciones, fade y ARM/firma |
| tools/test_tv_physical_apk.py | 11 pruebas pasan, incluidas las guardas actualizadas de versión, ARM y fuentes protegidas; firma mock, no firma real |
| tools/check_tv015_contract.py | 6 contratos previos del panel pasan |
| tools/verify_tdlib_aar.py | AAR exacto, bindings y cuatro ELF pasan con los hashes de 0.1.9 |
| Sintaxis PSI Kotlin | Cuatro archivos de aplicación modificados sin errores sintácticos; no comprueba tipos Android/Compose |
| Python y git diff --check | Sin errores |

Las pruebas nuevas cubren límite/deduplicación, catálogos vacíos/pequeños,
persistencia de lista/índice, evitar selección anterior, diversidad de géneros,
rotación circular, descarte de fondos y condiciones de pausa. El intervalo se
comprueba como política; no se ha medido el temporizador en una UI real.

No se ejecutaron tests Gradle completos de TV/Mobile, assembleTvDebug,
assembleMobileDebug, lint, instrumentación o emuladores. No hay SDK/Build Tools ni
JDK completo en este entorno. Se utilizó el compilador Kotlin de pruebas puras,
no se instaló un entorno Android. El script Windows no puede ejecutarse aquí.
No se declara validación visual, rendimiento en ARM ni prueba física de 0.1.10.

## Actualizar y compilar en Windows 11

Primero guardar tus cambios locales. No usar reset/clean/checkout forzados ni
borrar datos de la app. Con árbol limpio:

```powershell
git fetch origin
git switch --track -c feature/tv-0.1.10-dynamic-hero origin/feature/tv-0.1.10-dynamic-hero
git rev-parse HEAD
```

Si la rama ya existe localmente: git switch feature/tv-0.1.10-dynamic-hero y
luego git pull --ff-only origin feature/tv-0.1.10-dynamic-hero.
Sincronizar Android Studio, elegir tvDebug y mantener JDK/configuración privada y
keystore debug del PC usado para la instalación física que funciona.

Con Python 3 disponible mediante py, usar el mismo script de 0.1.9:

```powershell
.\tools\build_tv_physical_debug.ps1
# SDK en otra ubicación:
.\tools\build_tv_physical_debug.ps1 -AndroidSdk 'D:\Android\Sdk'
```

El script ejecuta testTvDebugUnitTest y testMobileDebugUnitTest, assembleTvDebug y
luego inspecciona la APK generada. Exige cuatro lib/<ABI>/libtdjni.so reales,
versión 0.1.10/11, minSdk 26, manifest TV correcto, testOnly=false y firma verificada
por apksigner. No basta leer abiFilters. Si falla, no usar una APK vieja residual.

Alternativa manual si PowerShell bloquea ejecución de scripts (no cambiar su política):

```powershell
.\gradlew.bat :app:testTvDebugUnitTest :app:testMobileDebugUnitTest :app:assembleTvDebug "-Pandroid.injected.build.abi=armeabi-v7a,arm64-v8a,x86,x86_64" "-Pandroid.injected.testOnly=false"
py -3 tools/verify_tv_physical_apk.py app/build/outputs/apk/tv/debug/app-tv-debug.apk --apksigner "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat"
```

Ajustar la ruta de Build Tools a la instalada. Sólo continuar si ambos comandos
terminan correctamente. La APK esperada es app/build/outputs/apk/tv/debug/app-tv-debug.apk,
firmada debug con la clave local. No se modificó la firma. No enviar una APK de Run
como entrega normal ni usar -t para saltear testOnly. Más detalles en
[compatibilidad física 0.1.9](TV_0.1.9_COMPATIBILIDAD_FISICA.md).

## Instalación y validación manual pendiente

Tras superar la inspección ARM/firma, instalar por archivo/pendrive/Telegram o:

```powershell
adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk
```

Mantener applicationId/firma para actualizar sin borrar sesión/progreso. Ante un
conflicto de firma no desinstalar la copia familiar a ciegas.

1. Catálogo con más de diez películas elegibles: Hero sólo películas, sin duplicados,
   máximo diez; repetir con menos de diez, una y cero. Películas sin backdrop quedan
   fuera, sin impedir que aparezcan en el catálogo normal.
2. Al mover el foco a una fila aún con Hero visible, observar cambios cada 8 s y
   fade suave. Título/logo/sinopsis/botones corresponden a la película nueva.
3. Enfocar cada botón del Hero y esperar más de 8 s: debe mantenerse esa película.
   OK/Enter abre/reproduce la indicada. Bajar reinicia el intervalo; sin saltos de foco.
4. Scroll hasta ocultar Hero, abrir ficha, reproducir, Home del control/suspender y
   volver: sin rotación oculta, saltos de scroll ni reinicio de selección o jugador.
5. Volver de ficha: conservar película activa y foco anterior. Cerrar realmente el
   proceso y abrir: nueva selección, minimizando títulos/géneros repetidos cuando
   haya alternativas. No es necesario repetir nuevos tests específicos de anime.
6. Fondo caído/vertical, logo ausente, red lenta: fondo seguro/alternativa, texto
   legible, botones coherentes, sin cargar todas las imágenes simultáneamente.
7. Regresión habitual: navegación general, películas, episodios, seek, pausa,
   subtítulos/audio, progreso y Continuar viendo. Tráilers siguen su flujo validado;
   no se cambió su implementación. No se agrega soporte AC3.
8. En el TV ARM comprobar fluidez y memoria durante varios ciclos y al reanudar.
   Una compilación exitosa o el verificador de APK no validan esa experiencia.

## Archivos de esta entrega

- README.md, CHANGELOG_TV.md, app/build.gradle.kts.
- app/src/main/java/ar/com/miflix/client/MainActivity.kt.
- app/src/main/java/ar/com/miflix/client/TvScreens.kt.
- app/src/main/java/ar/com/miflix/client/TvFeaturedSession.kt.
- app/src/main/java/ar/com/miflix/client/TvDynamicHero.kt (nuevo).
- app/src/test/java/ar/com/miflix/client/TvFeaturedSessionTest.kt (nuevo).
- tools/verify_tv_physical_apk.py, tools/test_tv_physical_apk.py.
- tools/check_tv0110_contract.py (nuevo).
- docs/TV_0.1.10_HERO_DINAMICO.md (este documento).

## Fuentes de implementación

- https://developer.android.com/develop/ui/compose/side-effects
- https://developer.android.com/develop/ui/compose/animation/composables-modifiers

No fusionar automáticamente. Entrega de código respaldado; nueva APK ARM y
validación física pendientes de los pasos locales anteriores.

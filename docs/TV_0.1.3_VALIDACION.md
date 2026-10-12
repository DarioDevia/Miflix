# MiFlix TV 0.1.3-diagnostico / 4 — pendiente de validación manual

## Estado y procedencia

Rama de respaldo: `backup/miflix-tv-0.1.3`.
Base: `28da9ac2ecf22ce7fecfa5f0dcaf4cef6cbcf108` (TV 0.1.2 / 3).
La rama estable `miflix-tv` no se actualiza con este respaldo.

El código se reconstruyó a partir de comandos revisados del historial, sobre una
copia limpia. No se recuperaron la APK ni los archivos del antiguo contenedor.
Checkpoints locales: `ee6933c` (reconstrucción), `d8d99de` (aserción de pantalla),
`a3fce3e` (restauración de foco). El respaldo inicial remoto `ef01eb6` contiene
exactamente el árbol de `d8d99de`; el hash difiere porque se publicó mediante el
conector de GitHub. Los resultados de la ejecución antigua no validan esta copia.

## Alcance

| Área | Cambio |
| --- | --- |
| Inicio | Fondo a pantalla completa, degradados, identidad con logo o título, sinopsis, Reproducir/Ver ficha y filas integradas. |
| Menú | Accesos laterales a Inicio, Buscar, Secciones, Telegram, Configuración y Actualizar; etiqueta al enfocar. |
| Películas, Series y Anime | Mismos componentes de imagen, identidad, pósters y acciones. Sólo series/anime con temporadas ofrecen selección de episodios. |
| Detalle | Acciones compactas, audio cuando existe, cabecera común y navegación vertical explícita en temporadas/episodios. |
| Retorno | Conservación de temporada al volver del reproductor; regreso del primer episodio a temporadas y de allí a cabecera. |
| Foco | Se consume una sola vez la restauración de tarjeta/cabecera para evitar que una recomposición posterior robe el foco. |
| Telegram TV | Foco inicial después de componer el campo, OK abre teclado, arriba/abajo permiten salir; teclado numérico para códigos y ocultación de contraseña. |
| Tráiler TV | Acción externa para la URL YouTube existente y mensaje si no existe una aplicación compatible. No se afirma resuelto el render negro del WebView Mobile. |
| Reproductor | Motor, controles, ocultación, pausa con logo, seek, Atrás y progreso de 0.1.2 conservados. |

No se modifican TDLib, TelegramRangeDataSource, el parser/caché del catálogo,
Cloudflare, Admin Windows ni Admin Android. Los cambios en archivos compartidos
seleccionan la presentación TV mediante `BuildConfig.IS_TV`; los caminos
exclusivos Mobile conservan su comportamiento. Mobile sigue en 0.9.2 / 26.

## Fallo `movie-logo`

El historial registra un fallo al intentar ir de Inicio a Ficha. La comprobación
antigua podía aceptar el texto de toda la raíz como si perteneciera al control
enfocado; además Inicio y Detalle contienen un botón Reproducir. El helper ahora
busca el descendiente enfocado y exige el identificador visible de Detalle antes
de seguir. Se corrigieron también solicitudes de foco repetidas por composición.

**Esto no demuestra que el fallo esté resuelto en ejecución.** Debe repetirse
Inicio → Ver ficha → OK en el emulador del usuario y verificar la pantalla real,
el logo, el foco y Atrás. No hay nuevas capturas ni pruebas instrumentadas ejecutadas
para esta entrega. La compilación de su APK de pruebas no equivale a ejecutarlas.

## Verificación de esta copia

Resultados finales: ver `TV_0.1.3_evidencia/verificacion.txt`.
Las pruebas unitarias cubren lógica; no validan el aspecto ni el mando remoto.
No se utilizan credenciales Telegram privadas para compilar o probar.

La revisión estática **lint no pasa**: Mobile 41 errores/11 avisos; TV 42
errores/12 avisos. Los errores corresponden a opt-in de Media3 y, en TV, a un
receiver del manifiesto; sus líneas ya existían en `28da9ac` y los recuentos
coinciden con el informe histórico 0.1.2. No se volvió a ejecutar lint sobre la
base, por lo que esto no es una comparación exhaustiva de ambos informes.
Hay además un aviso de incompatibilidad de comprobaciones lint de Compose.
No se desactivaron reglas ni se modificó Mobile para ocultar estos resultados.
Ver [detalle de lint](TV_0.1.3_evidencia/lint.txt).

## Descargar sin alterar tu copia estable

En una carpeta nueva, abrir PowerShell:

```powershell
git clone --single-branch --branch backup/miflix-tv-0.1.3 https://github.com/DarioDevia/Miflix.git Miflix-TV-013
cd Miflix-TV-013
git status --short
git branch --show-current
git rev-parse HEAD
```

La rama debe ser `backup/miflix-tv-0.1.3`. Comparar el commit con el entregado en
la conversación. Si ya tenés una copia de ESTA rama, revisar primero
`git status --short` y, sólo estando limpia, usar `git pull --ff-only`.
No hacer reset ni descartar cambios de tu proyecto anterior.

## Android Studio y compilación

1. Abrir la carpeta `Miflix-TV-013` como proyecto existente.
2. En Settings → Build, Execution, Deployment → Build Tools → Gradle, seleccionar
   **JDK 17**. No actualizar Gradle, Kotlin ni dependencias para esta prueba.
3. En SDK Manager, disponer de Android SDK Platform 34 y Build Tools 34.0.0.
4. Dejar finalizar la sincronización. En Build Variants seleccionar **tvDebug**.
5. Compilar desde Android Studio o ejecutar en su terminal con `JAVA_HOME`
   apuntando al JDK 17:

```powershell
.\gradlew.bat testMobileDebugUnitTest testTvDebugUnitTest assembleMobileDebug assembleTvDebug
```

APK TV: `app\build\outputs\apk\tv\debug\app-tv-debug.apk`.
APK Mobile de compatibilidad: `app\build\outputs\apk\mobile\debug\app-mobile-debug.apk`.

Para instalar, seleccionar tu emulador TV en Android Studio y pulsar Run, o listar
dispositivos y usar el identificador concreto que aparezca (no copiar un ID ajeno):

```powershell
adb devices
adb -s TU_ID_DE_EMULADOR install -r app\build\outputs\apk\tv\debug\app-tv-debug.apk
adb -s TU_ID_DE_EMULADOR shell dumpsys package ar.com.miflix.client.tv | Select-String 'versionName|versionCode'
```

Debe indicar `0.1.3-diagnostico` / `4`, paquete `ar.com.miflix.client.tv`.
Si `adb` no está en PATH, usar `platform-tools\adb.exe` del SDK de Android Studio.
Si falla por firma incompatible, no desinstalar a ciegas: informar el error;
desinstalar elimina datos y sesión. Compilar desde tu PC mantiene tu firma debug habitual.

Sin configuración privada se puede compilar y revisar el catálogo/interfaz,
pero la autorización y reproducción Telegram reales requieren la configuración
privada que ya utilizás. Conservarla sólo en tu PC; `telegram.local.properties`
está excluido de Git. No pegar códigos ni contraseñas en informes o capturas.

## Pruebas manuales PENDIENTES — usuario

| Prueba | Resultado esperado |
| --- | --- |
| Versión instalada | TV 0.1.3 / 4, no paquete Mobile. |
| Inicio y Ver ficha | Fondo estable al cambiar foco; OK abre la ficha correcta; Atrás vuelve a Inicio. Repetir `movie-logo`. |
| Menú lateral | Acceder y salir con DPAD de Inicio, Buscar, Secciones, Telegram, Configuración y Actualizar. |
| Película | Fondo/logo proporcionales; probar sin logo, sin fondo y URL fallida; título alternativo y acciones legibles. |
| Series y Anime | Misma cabecera visual; elegir temporada, bajar muchos episodios, subir a temporadas y cabecera. Repetir con episodios no disponibles. |
| Retorno | Abrir reproducción y volver; conserva temporada. Abrir tarjeta y volver; foco en la tarjeta, sin saltos al desplazarse después. |
| Búsqueda/secciones | Campo, filtros, filas y selección de Anime operables con DPAD. |
| Código Telegram | Alcanzar campo, OK abre teclado, escribir, ir a Continuar, corregir error y usar Atrás. Probar también teléfono y contraseña si aplica. |
| Player | Video con relación de aspecto correcta; controles desaparecen al reproducir; reaparecen con DPAD/OK; pausa muestra logo/título; seek y Atrás conservados. |
| Continuar viendo | Película/serie/anime, progreso, reanudación y cambio de episodio. |
| Tráiler | Abre aplicación externa al elegirlo; sin aplicación compatible muestra mensaje; volver a MiFlix. |

Reportar título/tipo, pantalla, secuencia exacta de teclas, resultado observado,
versión instalada y captura sin información privada. **La interfaz permanece
pendiente hasta que el usuario confirme estas pruebas.**

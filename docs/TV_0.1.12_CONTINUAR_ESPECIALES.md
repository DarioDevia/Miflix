# MiFlix TV 0.1.12: Continuar viendo y Especiales

## Base y alcance

Base confirmada por el usuario en su PC: `fix/tv-0.1.11-lint-videoplayer`,
`68953b2c1d02bf8796a8d36364d885c28d9420b9`, árbol limpio.
Rama nueva: `fix/tv-0.1.12-continue-specials`.
TV: `0.1.12-diagnostico` / `13`, applicationId `ar.com.miflix.client.tv`.
Mobile conserva `0.9.2-diagnostico` / `26` en esta línea de desarrollo.
Las ramas de Mobile más recientes pertenecen a otra línea y no se fusionan.

La base no tiene biblioteca personal ni marcado manual Visto. No se incorporan.
Hero, intro MP4, Media3, TDLib, Telegram, pistas, tráilers, manifiestos, firma y ABI
permanecen idénticos a la base. No se modifican los cuerpos del reproductor ni las
reglas compartidas de progreso. No se desactiva Lint ni se agregan baselines.

## Diagnóstico y corrección de Continuar viendo

La regla existente ya excluye contenidos cuando resta como máximo
`min(300000 ms, duración / 20)`, y exige 30 segundos para considerar progreso
pendiente. STATE_ENDED ya llama al callback existente de finalización.
No había justificación para cambiar esos criterios.

Se reprodujo en una prueba de persistencia esta secuencia:

1. Guardar un progreso con duración conocida.
2. Recibir un checkpoint final con duración desconocida (0 o TIME_UNSET).
3. El almacén original convierte esa duración en cero y reemplaza el registro.
4. El filtro no puede reconocer la finalización sin duración, por lo que devuelve
   una tarjeta pendiente aunque la posición corresponda al final conocido.

Esto demuestra un defecto del camino de datos, no que todos los casos observados
en el televisor hayan seguido esa secuencia. El caso físico debe confirmarse.

`TvProgressStorage` conserva la última duración positiva del mismo identificador,
leyéndola del registro o recordándola durante la sesión. No inventa duración,
posición, claves, episodios ni estados Visto. Un checkpoint tardío tampoco pierde
la duración tras una limpieza de finalización. La proyección existente filtra el
registro resultante usando la misma regla. Los registros completados conservados
por este camino no se borran para ocultar tarjetas. La política de limpieza que
ya tenía PlaybackProgressStore para finalizaciones conocidas/reinicios permanece
intacta; esta entrega no crea un historial que no existía.

Inicio antes sólo recalculaba la fila al cambiar pantalla/catálogo o reanudar.
Se agrega un listener de las mismas preferencias, activo sólo en Inicio TV,
para recalcular ante cambios persistidos y retirarlo al salir. Esto cubre cambios
que sucedan cuando la pantalla ya se encuentra abierta. No se alteran las tarjetas.

Límites: si una instalación ya perdió la duración y tampoco recibe una nueva
positiva, no hay información fiable para reconstruirla. Si un archivo nunca
informa duración ni STATE_ENDED, no se adivina su finalización. No se escanea
Telegram ni se cambia el motor para intentar inferirla.

## Especiales

La vista recorría las temporadas directamente en el orden del catálogo y siempre
usaba la etiqueta Temporada N. Ahora recorre índices de presentación: temporadas
normales por número ascendente y temporada 0 al final, con etiqueta Especiales.
No se ordena ni se copia el catálogo persistido. Los callbacks, selección guardada,
FocusRequester, claves de progreso y navegación de episodios siguen utilizando los
índices originales. La posición inicial de LazyRow se traduce al índice visual.
La selección conservada puede ser Especiales; ordenar no la cambia arbitrariamente.
No se crean temporadas ni episodios y no se cambia el orden de reproducción.

## Pruebas

- 87 pruebas JVM compiladas desde fuentes actuales y aprobadas: 15 nuevas de
  persistencia/presentación y 72 existentes de progreso, Continuar viendo, intro,
  Hero, pistas, controles y enlaces de tráiler.
- El ejecutor `tools/run_tv012_policies.py` usa Kotlin 1.9.22 y JUnit 4.13.2;
  extrae sólo los modelos y las políticas de progreso, omitiendo anotaciones JSON
  y la implementación Android de preferencias. No simula ni valida Android I/O.
  Ejemplo: `python tools/run_tv012_policies.py <carpeta-con-jars-de-Kotlin-y-JUnit>`.
- 46 comprobaciones Python de contratos, alcance e inspección APK: aprobadas.
- Análisis sintáctico de cuatro archivos Kotlin: aprobado; no es compilación Android.
- git diff --check: aprobado.
- Los contratos históricos se ajustan únicamente para admitir el cableado TV y la
  versión nueva. Un contrato adicional compara con la base y exige que el resto
  del código existente, incluidos los reproductores y recursos, sea byte idéntico.

No hay SDK ni javac en este entorno. Gradle Android, Lint, APK Debug/Release y
verificación ARM de una APK nueva requieren ejecución CI o local. Se incorpora
`.github/workflows/tv012-validation.yml`: enumera tareas antes de utilizarlas,
compila TV Debug y Release si está disponible, ejecuta tests TV/Mobile, Lint TV,
compila Kotlin Mobile e inspecciona la APK Debug con apksigner. No usa secretos.
La firma de CI no es la firma de la PC; no se distribuye esa APK para actualizar
la instalación familiar. Sólo se publican informes como artefactos del workflow.
El estado real de CI se informa en la entrega; definir un workflow no demuestra
que se haya ejecutado ni que haya aprobado. Pruebas de interfaz, DPAD y TV físico
siguen pendientes incluso si CI termina correctamente.

## Compilar y actualizar en Windows 11

Conservar cualquier cambio local antes de cambiar de rama:

```powershell
git fetch origin
git switch --track origin/fix/tv-0.1.12-continue-specials
.\gradlew.bat :app:tasks --all --console=plain
.\tools\build_tv_physical_debug.ps1
```

Si la rama ya existe, usar git switch con su nombre y `git pull --ff-only`.
El script conserva JDK 17/SDK del proyecto, tests, Lint e inspección de firma,
manifest, intro y cuatro ABI. El verificador espera ahora 0.1.12 / 13.
APK: el script resuelve el nombre real usando output-metadata.json de AGP;
puede consultarse con `py -3 tools/locate_tv_apk.py`.

La variante Release se comprueba con `:app:assembleTvRelease` si aparece en la lista
de tareas; no hay configuración nueva de firma Release. Una salida sin firma no
es una APK distribuible. Para el TV familiar utilizar el script Debug con la
misma clave local que firmó la instalación anterior; no generar otra clave.

```powershell
$tvApkPath = py -3 tools/locate_tv_apk.py
adb install -r "$tvApkPath"
```

No desinstalar ni borrar datos para actualizar. Mantener espacio libre suficiente;
el usuario detectó falta de espacio al instalar 0.1.11. Si la firma difiere, detener
la actualización y recuperar la clave correcta. No usar `-t` para saltar la revisión.

## Lista de pruebas físicas pendientes

1. Película pendiente permanece; película finalizada desaparece al volver a Inicio.
2. Episodio pendiente permanece; episodio finalizado desaparece sin agregar el siguiente.
3. Reabrir MiFlix: fila coherente, posición de otros contenidos conservada y sin duplicados.
4. Reiniciar voluntariamente una película y avanzar: reaparece como pendiente real.
5. Serie con temporada 0: orden 1, 2, …, Especiales; serie sin 0 sin opciones agregadas.
6. DPAD/OK en cada temporada reproduce el episodio y archivo correctos; Back conserva
   selección, foco y progreso. Verificar navegación anterior/siguiente existente.
7. Regresión breve: intro/omisión, Hero, tráilers, audio/subtítulos y reproducción ARM.

Esta versión no está declarada físicamente validada.

## Primera ejecución Android y ajuste del localizador

CI ejecutó correctamente tests unitarios TV/Mobile, compilación Kotlin Mobile,
Lint TV, assembleTvDebug y assembleTvRelease en el commit inicial d2ace82.
La inspección posterior falló porque el nombre fijo esperado de APK no existía.
Se reemplaza esa suposición por lectura de output-metadata.json de AGP, tanto en
CI como en PowerShell. Se exige una única salida tvDebug real y se conserva toda
la inspección de contenido/firma. Tres pruebas adicionales cubren nombres variables,
variantes, ambigüedad y rutas fuera del directorio de salida. No es un cambio de
empaquetado ni del código de aplicación. La segunda ejecución se informa en la entrega.

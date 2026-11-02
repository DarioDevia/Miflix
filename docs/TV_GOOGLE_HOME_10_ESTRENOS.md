> Apertura directa experimental: ver [TV_GOOGLE_HOME_DIRECT_DETAILS.md](TV_GOOGLE_HOME_DIRECT_DETAILS.md). La selección y su protección se mantienen.

> **Corrección vigente: años válidos y conservación de tarjetas.** La política estricta descrita más abajo es histórica y queda reemplazada por esta sección.

## Selección corregida

- `year` y `anio`, normalizados por el parser existente, son años de estreno válidos (desde 1888 hasta el año del sistema).
- Una fecha completa válida del título tiene prioridad sobre su año aproximado; las discrepancias se diagnostican. No se usan fechas de temporadas ni episodios para fechar la serie.
- Orden: año descendente, fecha completa descendente cuando exista, títulos sin día/mes después de los fechados del mismo año, desempate por ID estable. No se fabrican días ni meses.
- Fecha completa futura: excluida incluso si el año es actual. Año futuro: excluido. Fecha ausente, inválida o contradictoria: se puede utilizar el año válido del título; se conserva el diagnóstico.
- Máximo diez, sin duplicados, sin usar fecha de incorporación. El año cambia automáticamente con el reloj del sistema.

## Protección del último conjunto publicado

Un error de descarga o lectura aborta la operación. Una selección vacía devuelve `SELECCION_VACIA_CONSERVADA` antes de crear/modificar el canal, imágenes o programas. El reconciliador también trata el conjunto deseado vacío como cero escrituras: las cinco tarjetas anteriores se conservan.

Con uno a diez títulos se preparan todas las imágenes y metadatos, se insertan/actualizan los programas deseados y se vuelve a consultar el proveedor. Sólo después de comprobar todos los reemplazos se eliminan programas obsoletos propios. Fallos parciales no autorizan limpieza anticipada; una nueva sincronización reconcilia lo pendiente. El proveedor no ofrece aquí una transacción global y no se afirma atomicidad.

Se mantiene la adopción segura del canal propio id=2 y el nombre `MiFlix TV`. No hay cambios de API, imágenes, enlaces, Release, Mobile, reproducción, Cloudflare ni Admin.

## Actualización local sin sobrescribir cambios

No hacer reset/clean ni reemplazar el worktree con la corrección local. Crear otro:

```powershell
cd C:\Users\devia\StudioProjects\Miflix-TV-HomeProbe-Work
git status --short
git fetch origin
git worktree add ..\Miflix-TV-Home10-Protected origin/experiment/tv-google-home-probe-work
cd ..\Miflix-TV-Home10-Protected
# Preparar configuración privada y firma originales localmente, sin agregarlas a Git.
.\tools\run_tv_home_probe.ps1
```

Actualizar el catálogo desde MiFlix y sincronizar la sonda. Revisar `PROGRAMAS_VERIFICADOS`, `ESTRENOS` y `CAMBIOS`. Repetir: cero upserts/eliminaciones si no cambió el catálogo ni la selección. Si no hay elegibles, comprobar `SELECCION_VACIA_CONSERVADA` y que siguen las tarjetas anteriores. No desinstalar ni borrar datos ante un conflicto de firma; compilar con la clave local original.

El nivel 4 previamente observado se conserva como antecedente. La nueva selección no está observada en el emulador de Work, no se declaran diez tarjetas visibles ni nivel 5. El launcher controla la presentación.

## Resultado automático de esta corrección

CI https://github.com/DarioDevia/Miflix/actions/runs/41382075815 aprobada para el commit `6b6550303e775070bc06b195042f07dd35dedaa9` (código funcional en `459f32f8d746e12fb2d47a332cf48769c00f1ff9`).

- Ocho contratos Python y sintaxis PowerShell: aprobados.
- `testTvDebugUnitTest` y `testMobileDebugUnitTest`: aprobados. Cuatro pruebas nuevas de años/formatos mixtos/protección de cinco tarjetas y actualización de los casos anteriores.
- `lintTvDebug`, `lintTvRelease`, `lintMobileDebug`: aprobados sin deshabilitar controles; no se afirma ausencia de advertencias.
- `assembleTvDebug`, `assembleTvRelease`, `assembleMobileDebug`: aprobados.
- Inspección real de manifiestos, dependencias y empaquetado: aprobada; experimento aislado de Release/Mobile y ARM conservado.
- APK debug de CI disponible en el artefacto `TV-Home10-validacion` de esa ejecución; no se ha comprobado su certificado contra la instalación del usuario. Usar compilación local con la clave existente para actualizar sin borrar datos.
- No se ejecutó ADB, Logcat ni validación visual nueva. El caso de cinco tarjetas conservadas está probado en JVM y protegido en el flujo real antes de toda escritura; queda pendiente confirmarlo con el proveedor del emulador.

---

# MiFlix Google TV Home — hasta 10 estrenos con fecha completa

## Base y prueba real anterior

Base remota verificada: `experiment/tv-google-home-probe-work`,
`1bed37f520dd14bb1f34ed35dda7dfbf76a11e69`. La rama estable 0.1.14 y sus fuentes
se conservan. Versión experimental sin cambio: 0.1.14-diagnostico / 15, mismo package.

El usuario confirmó nivel 4 en Google TV API 36: fila MiFlix TV con cinco carátulas
reales. Se registra como evidencia aportada por el usuario; Work no volvió a observar
el emulador. No se reconstruye la integración ni se cambia TvProvider/PreviewProgram.
Nivel 5 pendiente: los enlaces existentes abren MiFlix, no la ficha específica.

No hay acceso desde Work a la PC ni al worktree Windows. La corrección local no estaba
en el HEAD remoto; se incorpora su compatibilidad por el nombre informado, sin afirmar
que se recuperó el archivo local ni sobrescribirlo. Publicación con comparación de HEAD.

## Decisión explícita y bloqueo de datos

El usuario eligió **exigir fecha completa**, aceptando menos de diez o ninguna tarjeta.
No se sustituye con año, fecha_agregado, posición del JSON, ID Telegram, sincronización,
fecha actual ni fecha de una temporada/episodio. No se rellenan huecos con fixtures.

Inventario de código real (sin abrir credenciales):
- `catalogo.schema.json` y `Title`: año en `year`; compatibilidad de lectura de `anio`.
- Admin Android actual consultado:
  `feature/admin-0.1.11-cloudflare-collections`, `b0829c1963354ba6e1c659948e18708f6c63c13d`.
  `Metadata.kt` recorta release_date/first_air_date de TMDB a cuatro caracteres al guardar
  `anio`. Para AniList guarda seasonYear como anio. No conserva una fecha completa de
  estreno del título en ese flujo.
- `fecha_emision` existe en temporadas y episodios: es emisión de ESA parte, no estreno
  de la serie. Ni siquiera la fecha de T1 se utiliza como sustituto implícito.
- Mobile consultado: `feature/mobile-0.9.14-collections`,
  `85671548a4a696f22eb4fcc408c7b801c4ad3065`; no aporta una fecha completa al modelo Title.
- El catálogo de producción no se obtuvo desde Work; en el entorno previo devuelve 403.
  No se inventa un inventario de sus fechas actuales. No se accede al relay administrativo
  ni a tokens para enriquecer datos. Admin y Worker quedan intactos.

**Limitación imprescindible:** el catálogo generado únicamente por esos flujos puede
carecer de todas las fechas necesarias. El selector no puede recuperar días/meses
que no existen. En ese caso informa sinFecha y faltan, y publica cero títulos elegibles.
Que compile no significa que el catálogo actual permita mostrar diez estrenos.

## Lectura experimental de fechas

`TvHomeProbeReleases.kt` lee el mismo JSON público en una estructura tvDebug separada;
no modifica Catalog, Title ni el esquema remoto. Si existen en el JSON, admite campos
opcionales completos en la RAÍZ de cada título:

| Tipo | Fechas completas admitidas |
| --- | --- |
| Película | release_date o fecha_estreno |
| Serie | first_air_date o fecha_estreno (estreno de la serie) |
| Anime | first_air_date, release_date o fecha_estreno del título completo |

Estas columnas opcionales son compatibilidad explícita, **no una afirmación de que
Admin ya las publique ni de que existan actualmente en producción**. Los nombres
release_date/first_air_date corresponden a los datos originales que Admin recorta.
fecha_estreno se admite como campo inequívoco del título si está presente; no se crea.
No se inspeccionan temporadas/episodios para obtener la fecha raíz. En anime la fecha
se refiere al título (serie o película), no a su última temporada.

Formato admitido: YYYY-MM-DD válido (incluidos años bisiestos, año >=1888 como el
esquema de catálogo). Fechas parciales, formatos inválidos y valores de tipo distinto
se excluyen individualmente. Campos completos contradictorios excluyen el título,
en lugar de elegir silenciosamente uno. Si el año simple difiere de la fecha completa,
manda la fecha completa y se informa discrepanciasAnio; no se inventa otra fecha.

La fecha de corte proviene de LocalDate.now() en el dispositivo y se captura una sola
vez por selección. No se fija 2026: al cambiar el año del sistema cambia la selección.
Se excluye cualquier fecha posterior al día actual, incluso dentro de ese mismo año.
Se ordena por fecha completa descendente: año actual, anterior y todos los anteriores
necesarios; empates por ID estable. No se impone un límite de sólo un año de retroceso.
Se deduplica por Title.id, se conservan los objetos originales y la política existente
de disponibilidad. Se eligen hasta diez. faltan siempre es 10 menos el total elegido.

## Canal, programas e imágenes

- Nombre canónico único: MiFlix TV.
- Se reconocen MiFlix TV, MiFlix y MiFlix - Prueba Home, además del marcador existente.
- Se prefiere id=2 sólo tras comprobar dueño, tipo Preview e identidad reconocida.
  Un id=2 de otra app, otro tipo o nombre ajeno no se adopta. Si fue eliminado,
  se busca otro canal propio reconocido; no se fuerza ese número al proveedor.
- No se borran canales existentes. Canales propios desconocidos provocan una salida
  segura antes de crear otro. La preferencia local es una pista, no prueba de identidad.
- Se actualiza el nombre del canal reconocido a MiFlix TV cuando sea necesario.
- Mismo namespace de programas y claves SHA de ID MiFlix: se conservan identidades y
  se actualizan metadatos/peso, en vez de crear una nueva familia de programas.
- Pesos de diez a uno reflejan el orden elegido. El launcher controla cuántas tarjetas
  dibuja y cuántas son visibles simultáneamente; no se promete ver diez en pantalla.
- Se eliminan duplicados/obsoletos sólo del namespace administrado por esta sonda.
  Registros desconocidos o heredados sin marcador quedan intactos.
- Las funciones de imagen horizontal y apertura existentes son idénticas a la base.
  No cambian APIs, dependencias, permisos ni el comportamiento de fichas/reproducción.

## Actualización e idempotencia

Cada sincronización relee la caché pública actual, incluidos los campos opcionales
que el modelo estable no conserva pero sí el JSON original. Por tanto primero usar
Actualizar en MiFlix cuando cambie el catálogo remoto, luego ejecutar la sonda. No
hay una selección persistida para siempre. Si no existe una copia válida, se intenta
el endpoint público fijo, sin credenciales. Un fallo de transporte/parseo aborta antes
de tocar canal/programas y no se transforma en una lista vacía artificial.

Un catálogo válido vacío o sin fechas elegibles SÍ produce selección vacía, según la
regla estricta elegida. **Puede retirar las cinco tarjetas anteriores de la sonda y
ocultar la fila hasta que existan fechas completas elegibles.** No borra datos de la
biblioteca ni progreso ni programas ajenos. Esto es falta de datos, no fallo de API.

Se verifica desde la propia app que el total propio coincide con el seleccionado,
que los metadatos corresponden y que no quedan duplicados. Repetir con el mismo
catálogo y fecha de corte produce cero upserts/eliminaciones. Al cambiar catálogo o
hacerse elegible un estreno en un nuevo día, se actualizan sólo las diferencias.

Logcat de diagnóstico, sin URLs/credenciales/IDs crudos del catálogo:
- CANAL_EXISTENTE id=2 nombre=MiFlix TV (si efectivamente se reutiliza ese registro).
- PROGRAMAS_VERIFICADOS propios=N reales=N sinteticos=0 faltan=10-N fuente=...
- ESTRENOS anioActual=... fechaCorte=... sinFecha=... invalidas=... futuras=...
  conflictivas=... discrepanciasAnio=...
- CAMBIOS upserts=... eliminadosSonda=... browsable=... logoGuardado=...
- SIN_FECHAS_COMPLETAS_ELEGIBLES cuando N=0.

Los anteriores son patrones esperados, NO logs observados de esta nueva versión en
el emulador. Work no tiene ADB ni acceso a emulator-5554; esa confirmación queda pendiente.

## Cambios y pruebas

Sólo tvDebug, sus tests, herramientas experimentales y documentación. Nuevos:
TvHomeProbeReleases.kt y TvHomeProbeReleasesTest.kt. Cambios acotados:
TvHomeProbeActivity, selección de TvHomeProbePolicy y sus cinco tests de selección,
check_tv_home_probe_contract, mensaje del script PowerShell y workflow experimental.
Los siete tests existentes de reconciliación de programas permanecen. Fuentes y tests
estables de TV/Mobile siguen comparándose byte a byte con la base 0.1.14.

Pruebas nuevas: 10/6/3/0 estrenos del año actual, límite diez, varios años de retroceso,
orden por día, cambio automático de año, fechas futuras, ausentes/inválidas/parciales,
bisiestos, conflictos, duplicados, catálogos incompletos, root versus temporadas,
actualización/idempotencia y reconocimiento seguro del canal legado id=2.
Se preservan exactamente los bloques existentes de imagen y apertura mediante contrato.

CI ejecuta tests TV/Mobile, Lint TV Debug/TV Release/Mobile, compilaciones de las tres
variantes, sintaxis PowerShell y el inspector de aislamiento de manifiestos/ABI/firma.
Sin baseline ni supresiones. No inicia emuladores ni prueba visualmente LauncherX.
Los resultados finales están a continuación y en TV_GOOGLE_HOME_10_CI.json.

## Windows: no sobrescribir el worktree con tu corrección local

No usar reset --hard ni cambiar encima de archivos modificados. Abrir un worktree
nuevo desde el repositorio existente, dejando intacto el anterior:

```powershell
cd C:\Users\devia\StudioProjects\Miflix-TV-HomeProbe-Work
git status --short
git fetch origin
git worktree add ..\Miflix-TV-Home10-Work origin/experiment/tv-google-home-probe-work
cd ..\Miflix-TV-Home10-Work
```

Conservar en esa carpeta tu configuración privada local habitual, sin publicarla ni
compartirla; los worktrees no copian archivos ignorados. Usar la clave de firma local
original. El script comprueba presencia de configuración, sin imprimir su contenido;
no crea keystore, desinstala ni limpia datos.

```powershell
.\tools\run_tv_home_probe.ps1
```

Usa el JDK 17 y SDK existentes. Compila, ejecuta tests/Lint, instala con -r y abre la
sincronización. Actualizar previamente el catálogo de MiFlix. No ejecutar esperando
que un catálogo con sólo años produzca diez estrenos: el resultado estricto será vacío.

Para repetir sin recompilar, una vez actualizada la app:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5554 shell am start -n ar.com.miflix.client.tv/ar.com.miflix.client.TvHomeProbeActivity --es probe_action publish
& $adb -s emulator-5554 logcat -d -s MiFlixHomeProbe:I '*:S'
```

Esperar al resultado en pantalla antes de repetir/volcar logs; no confundir registros
anteriores del mismo tag con la nueva ejecución (comprobar hora y formato con faltan).
Primera sincronización: N<=10, fechas no futuras, canal 2 si sigue reconocido. Segunda:
mismo canal, N y orden, upserts=0, eliminadosSonda=0, duplicados=0. Inspeccionar después
Home con el mando; Google TV decide su presentación. No declarar nivel 5 por abrir MiFlix.

Si la fila queda vacía por falta de fechas, el bloqueo está documentado: poblar esos
datos requerirá una tarea de catálogo/Admin fuera de este alcance, o cambiar la regla
con autorización. Esta entrega no modifica producción para saltarse esa limitación.

## Resultados finales de esta entrega

CI aprobada: https://github.com/DarioDevia/Miflix/actions/runs/41379860970

Código compilado: `243a0b5edcbef25c4b823b581b9ba772726ab9a0`.
Los commits posteriores de esta entrega sólo registran resultados; no cambian el código
compilado. Artefacto `TV-Home10-validacion`, ID `13113296746`.

| Comprobación | Resultado real |
| --- | --- |
| Tests JVM TV | 310 aprobados, 0 fallos, 0 omitidos |
| Tests JVM Mobile | 281 aprobados, 0 fallos, 0 omitidos |
| Nuevos tests | 17 añadidos; cinco expectativas de selección actualizadas a la regla estricta |
| Contratos de protección | 8 aprobados; fuentes estables y bloques de imágenes/enlaces conservados |
| Lint TV Debug | 0 errores, 20 advertencias |
| Lint TV Release | 0 errores, 17 advertencias |
| Lint Mobile Debug | 0 errores, 14 advertencias |
| Sintaxis PowerShell | Aprobada por el parser; no ejecutó ADB ni leyó configuración privada |
| Compilación | TV Debug, TV Release y Mobile Debug correctas; BUILD SUCCESSFUL en 5m04s |
| Inspección APK/dependencias | Componentes/permiso EPG/tvprovider sólo en tvDebug |
| ABI reales | ARM32, ARM64, x86, x86_64, ELF/hash/tamaño originales |
| Package/versión | ar.com.miflix.client.tv / 0.1.14-diagnostico / 15, sin cambios |
| testOnly | false en el APK debug inspeccionado |
| Logcat de la nueva selección / observación visual | Pendientes; Work no accede al emulador del usuario |

Las advertencias conservan cantidades e IDs de la sonda anterior: incluyen el detector
personalizado obsoleto de Compose (limitación previa de su cobertura), proveedor público
de PNG de prueba y textos diagnósticos fijos. No se introdujeron supresiones ni baseline.
El nuevo algoritmo no añade permisos, dependencias ni modificaciones a Release/Mobile.

APK de CI: `MiFlix-TV-0.1.14-Home10-CI-debug.apk`, 114715548 bytes.
SHA-256: `ea147c03549d15e5e5105b1604f92a235131727d4987b9d35fd099478ce13b06`.
Firma debug temporal de CI, certificado SHA-256:
`9492e013f46f522a557e4b115012c6d38c251dc09d4181380cfe13767e347907`.
No incluye configuración privada Telegram ni se comparó con la firma del usuario.
Compilar localmente para actualizar conservando configuración e identidad existentes;
no instalar el artefacto CI como si fuera una actualización familiar compatible.

Archivo del artefacto descargado y verificado, SHA-256:
`0d743b2bb50c8e85aeeec476e180a343e8626192ea5edde8e29453de4672680c`.
Informes originales HTML/XML y la inspección están en esa ejecución de GitHub Actions.

Estado: integración anterior nivel 4 confirmada por el usuario; selección nueva
implementada y comprobada automáticamente, sin validación de Logcat/Home desde Work.
Nivel 5 no declarado. **Mostrar diez estrenos queda condicionado a que el catálogo
aporte al menos diez fechas completas elegibles.** No se declara resuelto ese bloqueo
mediante fechas inventadas ni modificaciones a producción fuera de alcance.

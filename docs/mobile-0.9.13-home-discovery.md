# MiFlix Mobile 0.9.13-diagnostico / 37

## Base verificada

- Base: `fix/mobile-0.9.12-continue-watching`.
- Commit: `824320fba405f314acb88ffce1012d1d41052bf9`.
- Versión: 0.9.12-diagnostico / 36; árbol limpio.
- Nueva rama: `feature/mobile-0.9.13-home-discovery`, sin fusionar anteriores.
- No se encontró una rama Mobile posterior en la consulta inicial de GitHub.
- No hay AGENTS.md aplicable ni infraestructura instrumentada Compose existente.

## 1. Buscar sin textos inferiores

SearchScreen usaba MovieGrid, cuyo MovieCard mantenía `showTitle=true` por defecto,
a diferencia de las filas de Inicio. MovieGrid ahora recibe `showTitles=false`
solamente desde Buscar. El componente omite realmente el Text (sin alpha ni altura
reservada), conserva nombres accesibles y los indicadores Visto/Episodios vistos.
La separación vertical de esa cuadrícula pasa de 20 a 12 dp. No cambia el póster
2:3, el callback de ficha ni el motor de búsqueda/filtros/orden. Secciones y las
otras vistas conservan su presentación anterior.

## 2. Últimos estrenos

HomeDiscovery calcula la fila una vez por snapshot de catálogo con remember.
Reutiliza CatalogSearchIndex, SearchFilters(latestReleases=true) y NEWEST: año
máximo disponible y año calendario anterior, año descendente y empate por
nombre normalizado/ID. Es una ventana relativa al catálogo, no al reloj del móvil.
Si solo hay un título de 1987, se conserva la semántica existente de Buscar y ese
es el año más reciente disponible. No se modificó esa regla.

El modelo Title solo aporta year; fecha_emision corresponde a temporadas/episodios,
no es una fecha general confiable de estreno de la película/serie/anime. No se
usa para sustituir year, ni se inventan fechas completas. La fila no puede ordenar
cronológicamente dos estrenos del mismo año sin nuevos metadatos fiables.

Son elegibles IDs/títulos válidos, tipos película/serie/anime y contenido reproducible
según playableCatalogTitle ya existente. La ventana se calcula sobre esos títulos,
por lo que una ficha sin contenido no desplaza el año máximo. No hay duplicados por
ID, solicitudes remotas, cambios de imágenes ni autoplay. Las tarjetas conservan
las instancias del catálogo y el callback de ficha.

## 3. Últimos agregados: dato existente y límite de auditoría

`catalogo.schema.json` ya declara `fecha_agregado` opcional, string date-time.
El cliente no lo mapeaba y no había valores representativos en los fixtures; el
fallback está vacío. El código escritor de Admin no está en esta rama Mobile:
no pudo comprobarse aquí cuándo lo crea, si lo conserva al editar ni qué zona usa.
La consulta de solo lectura al catálogo público respondió HTTP 403 desde este
entorno; el intento de lectura web tampoco pudo acceder. Esto **no demuestra un
problema de Cloudflare** ni que el campo falte en producción.

Se implementó soporte compatible para ese campo ya definido: Title.addedAt y lectura
opcional/tolerante en 1.x y 2. No cambia el esquema/contrato remoto ni escribe datos.
Valores ausentes/null/no string no invalidan la biblioteca; texto de fecha inválido
se ignora en la fila. OffsetDateTime exige fecha/hora ISO con zona explícita y ordena
por Instant, de modo que diferentes offsets se comparan correctamente. Un texto sin
zona se excluye: no asumimos la zona del dispositivo ni fabricamos cronología.

La fila conserva fechas de origen y ordena descendente, con desempate por nombre/ID.
Excluye títulos no reproducibles y duplicados. Si no tiene fechas elegibles, no se
compone. No usa year, posición en JSON, IDs, actualizado_en/generated_at ni detección
de primera descarga local. No existe migración, fecha de hoy por defecto ni backfill.

**Pendiente de datos:** comprobar en un catálogo real y en Admin la población y
semántica estable de fecha_agregado. La fila está implementada, pero no se afirma
que vaya a aparecer en la APK familiar sin esos datos. Si faltan o son ambiguos,
la cronología real queda bloqueada; se requiere una tarea separada en los escritores
para guardar fecha de primera incorporación con zona, conservarla al editar/exportar
y no inventar fechas antiguas. No se hizo ese cambio aquí.

Impacto de esa tarea futura: Admin Windows/Android serían responsables de crear y
preservar el dato; Mobile ya tolera su ausencia y lo lee; TV necesitaría su propia
presentación/lector; Worker/KV solo deberían preservar el JSON, sin generar fechas
por sincronización. No se cambió ninguno de esos módulos ni se consultó TMDB.

## 4. Sugerencias y pedidos

La opción existe en Configuración, con descripción y estado deshabilitado explícito
si no hay destinatario. Solo admite usuario público (5–32 caracteres ASCII,
primero letra, luego letras/números/guion bajo), con @ opcional. Construye
`https://t.me/<usuario>` sin teléfono, invitación, consulta ni fragmento.
La configuración inválida se rechaza durante el build; el helper también falla
cerrado si recibe un valor inválido. No se inventó un destinatario.

Para habilitarla, el propietario debe aportar su **usuario público real**. Puede
agregar la propiedad `MIFLIX_SUGGESTIONS_USERNAME` con ese valor al archivo local
`%USERPROFILE%\.gradle\gradle.properties` (configuración del usuario, fuera del repo).
También puede pasar la misma propiedad con `-P` al compilar. No editar
telegram.local.properties: ese mecanismo y sus secretos permanecen intactos.
El username es configuración pública incorporada a la APK, no una credencial.
No hay dependencia de un servicio nuevo ni almacenamiento adicional.

Al pulsar, reutiliza openTelegram: Intent ACTION_VIEW de Android para ese HTTPS,
sin forzar un paquete. Android puede abrir Telegram o navegador según instalación
y preferencias; si no hay aplicación capaz, muestra el aviso existente. No usa
TDLib, no inicia otra sesión, no pide permisos ni envía mensajes automáticamente.
El destinatario no configurado deja el botón deshabilitado en las APK CI.

## Organización y alcance

Hero → Continuar viendo → Mi Lista → Últimos estrenos → Últimos agregados → filas
generales Películas/Series/Anime y géneros existentes. Las filas vacías no aparecen.
Se mantienen claves estables, estado guardable y LazyRow/Coil; no hay precarga global.
No hay Ver todo adicional para las nuevas filas: todos sus títulos están en su
carrusel y usan la ficha existente. El acceso general a tipos y géneros no cambia.

Sin cambios en Player, Telegram/TDLib, rangos, seek, lifecycle, audio/subtítulos,
progreso, cobertura/vistos, favoritos, Continuar viendo, intro, Hero, Edge-to-Edge,
identidad Google, Workers, KV, Admin, TV, firma, applicationId o dependencias.
El único dato nuevo leído es fecha_agregado; no se modifica almacenamiento local.

## Verificación técnica y límites

Se agregan pruebas HomeDiscoveryTest, CatalogDiscoveryMetadataTest y
TelegramSuggestionsTest. Cubren orden/ventana, años incompletos, offsets, fechas
inválidas, esquema 1.x/2, contenido disponible, duplicados, metadatos opcionales,
independencia de las filas generales y validación/construcción de enlaces.
Los tests anteriores permanecen intactos. No hay una prueba Compose falsa para
"demostrar" píxeles, altura, taps, intents del sistema o navegación física.

Ejecutar `testDebugUnitTest lintDebug assembleDebug assembleRelease`. Si Gradle no
puede descargarse localmente, se usa Actions; resultados reales en el workflow y
la entrega. No se declara prueba en Samsung ni emulador. La apertura real del
enlace está pendiente de configurar el destinatario y probar resolutores Android.

## Compilar e instalar en Samsung A21s/A14

1. Abrir la nueva rama en Android Studio con JDK 17 y SDK 34. Conservar
   telegram.local.properties local y el keystore existente, sin compartirlos.
2. Si corresponde, configurar el username público como se indica arriba y
   sincronizar Gradle. Sin él la app compila y Sugerencias sigue deshabilitada.
3. Ejecutar:

```bat
gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

4. Build → Generate Signed App Bundle or APK → APK → app → keystore/alias existente
   → Release. No crear otra firma ni cambiar applicationId. Instalar sobre 0.9.12,
   sin desinstalar. Los artifacts CI sin configuración privada no sustituyen esa
   actualización familiar; la release CI está sin firmar.

## Validación física pendiente

- Buscar películas, series y anime, nombres largos, varios tamaños de resultados,
  con/sin filtros y distintos órdenes: sin textos ni huecos inferiores; indicadores
  y selección a ficha deben conservarse. Verificar nombre accesible con TalkBack.
- Inicio: comprobar orden de filas, Hero/Continuar/Mi Lista, carátulas 2:3 y scroll.
  Estrenos debe respetar años reales disponibles y desempates por título.
- Agregados: con fechas reales válidas, una película antigua agregada recientemente
  debe preceder a una reciente agregada antes. Sin fechas, fila oculta. No modificar
  el catálogo productivo solo para simular una prueba de cliente.
- Sugerencias sin configurar: descripción y botón deshabilitado. Con el usuario
  real: abrir conversación correcta, volver a MiFlix. Repetir con un dispositivo
  sin Telegram para verificar el navegador; no enviar sugerencias automáticamente.
- Regresión: reproducción, barra/seek, fullscreen/rotación, audio/subtítulos,
  episodios/Especiales, progreso, vistos/Continuar, favoritos, intro, Edge-to-Edge
  y Cuenta MiFlix si está habilitada.

Android TV conserva estos pendientes como tarea independiente. No se modificó TV.

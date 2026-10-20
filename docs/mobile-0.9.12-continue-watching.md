# Mobile 0.9.12-diagnostico · versionCode 36

## Base y causa

Base validada por el propietario: `feature/mobile-0.9.11-edge-to-edge`,
`847848fc532831c69e532256e95ef917e9e57613`, 0.9.11/35. Árbol inicial limpio.
Rama independiente: `fix/mobile-0.9.12-continue-watching`. Sin fusión.

`ContinueWatching.kt` solo consultaba `PlaybackProgressStore.resumableProgress`.
No consultaba `PersonalLibraryStore`, independiente desde 0.9.7, por lo que un
marcado Visto coexistía legítimamente con una posición pendiente antigua y la
fila seguía mostrando el contenido. No era corrupción de datos.

`MainActivity.kt` guardaba una copia de la fila y la actualizaba por pantalla,
catálogo y ON_RESUME, pero no por `LocalLibraryRevision`. La recomposición de los
indicadores Visto no reconstruía esa copia. El regreso normal del Player ya
releía progreso; se conserva esa capacidad sin depender de una respuesta remota.

No hay evidencia de otro fallo del Player al finalizar: STATE_ENDED limpia el
progreso existente, y las salidas guardan antes de release. No se modifica ese
código ni se atribuye un incidente físico a otra causa sin Logcat.

## Corrección mínima y no destructiva

La proyección recibe la biblioteca local existente y excluye claves vistas.
Para películas usa `isTitleWatched`: las publicaciones alternativas comparten el
estado visual de película ya definido por la biblioteca. Para series/anime
filtra por clave de episodio; un episodio visto no oculta los pendientes de los
otros ni marca la serie completa. Un marcado manual del padre tampoco marca sus
episodios, respetando la política preexistente. La vía de enlace general comprueba
la misma clave para no reintroducir una publicación/episodio visto como título.

Inicio conserva su estado de fila y amplía las claves de `LaunchedEffect` con
biblioteca, revisión local y estado RESUMED, además de pantalla/catálogo. La lectura
ocurre tras composición/desmontaje, cuando el Player saliente ya guardó su última
posición, evitando una lectura anticipada durante composición. Navegar de vuelta
o recuperar foreground relee posiciones locales; cambiar Visto reconstruye la
proyección. No introduce
consultas ni cambia la actualización automática preexistente del catálogo.

La proyección solo lee: no borra ni migra progreso, vistos, cobertura, favoritos o
historial. Desmarcar Visto conserva la semántica existente de la biblioteca; la
fila reaparece únicamente si `resumableProgress` aún devuelve progreso válido.
Huérfanos se ignoran; deduplicación por clave y orden reciente se conservan.

## Reglas verificadas, sin cambios

- Progreso mínimo: 30 segundos.
- Zona final: distancia restante <= `min(300000 ms, duración / 20)` para todos los
  tipos. No son cinco minutos incondicionales: una duración de 60 minutos usa tres.
- El progreso completo se excluye mediante `ProgressRules`; los registros antiguos
  completos no se borran al construir la fila.
- Visto automático: unión de intervalos reproducidos >=80 % y llegada reproducida
  a esa zona final. El muestreador no cuenta saltos/pausa/buffering como consumo.
- No cambia ninguna de esas reglas, ni se marca Visto por filtrar un progreso al
  final. El borrado de progreso al completar por el Player es comportamiento
  anterior, no una nueva operación para ocultar tarjetas.

## Pruebas y alcance

`ContinueWatchingWatchedTest` usa los stores reales con almacenamiento en memoria:
pendiente, completado, marcado/desmarcado, reapertura con progreso antiguo, visto
automático, películas con publicaciones, episodios serie/anime mezclados, no
iniciados, alias directo, deduplicación, orden, huérfanos y lectura tras reproducción.
Verifica que la proyección no escribe ni borra datos. La revisión local se prueba
como señal; no se presenta como test Compose instrumentado.

Se ejecutan `testDebugUnitTest lintDebug assembleDebug assembleRelease` mediante
Actions si la infraestructura local impide Gradle. Los resultados efectivos se
informan en la entrega y en el run, no se presuponen aquí. No hay teléfono ni
emulador en este entorno. El informe lint puede conservar advertencias anteriores.
Sin cambios en Player, TDLib, rangos, audio, subtítulos, lifecycle, cobertura,
ProgressKeys, persistencia, Edge-to-Edge, intro, catálogo o Workers.

## Validación Samsung A21s/A14

1. Actualizar conservando datos y firma. Reproducir una película más de 30 segundos
   y salir antes de la zona final: debe estar en Continuar viendo, con barra.
2. En su ficha marcar Título visto y volver: debe desaparecer inmediatamente.
   Cerrar/reabrir: debe seguir ausente y conservar Visto/Mi Lista.
3. Desmarcar: debe volver solo si su progreso pendiente sigue existiendo. Un
   título nunca iniciado o completado no debe aparecer por desmarcar.
4. Terminar una película normalmente y regresar: no debe estar en la fila.
5. Repetir con episodios serie y anime: dos pendientes, uno visto/completo y uno
   nunca iniciado. Solo los pendientes deben aparecer, con destino correcto.
6. Marcar/desmarcar un episodio desde otra pantalla, regresar a Inicio, enviar la
   app a segundo plano y volver: no debe requerir refresh de catálogo.
7. Probar seek, fullscreen/rotación, audio/subtítulos, anterior/siguiente, progreso,
   Mi Lista, vistos, filas, Especiales, Edge-to-Edge e intro como regresión física.

## Compilar y actualizar con firma existente

Abrir esta rama en Android Studio (JDK 17/SDK 34), conservar localmente
`telegram.local.properties` y sincronizar Gradle. Ejecutar:

```bat
gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Para actualizar la instalación familiar: **Build > Generate Signed Bundle / APK >
APK**, módulo app, variante release, seleccionar el keystore/alias **ya existente**
y firmar localmente. Mantener `ar.com.miflix.client`. Instalar esa APK sobre la
instalación previa, sin desinstalar ni crear otra clave. No compartir archivos
privados. El debug de CI y la release unsigned no sustituyen esa actualización.

## Traslado posterior a Android TV

No se modificó TV ni se verificó su implementación. En una tarea independiente,
comprobar si su proyección de Continuar viendo ignora Vistos y si observa revisiones
locales. Reutilizar sus propias claves/políticas, filtrar sin borrar y comprobar
alias de publicaciones, episodios pendientes y retorno/foreground. No trasladar
ciegamente código Compose Mobile ni asumir que TV tiene la misma causa sin auditar.

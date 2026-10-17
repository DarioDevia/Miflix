# Mobile 0.9.7-diagnostico / 31

Base confirmada: `feature/mobile-0.9.6-intro`, `bfb869845ca46e77911b030278403e7fbdbbc491`, 0.9.6 / 30, working tree limpio.
Rama nueva: `feature/mobile-0.9.7-library`. Sin merges automáticos.

## Arranque

ClientApp se componía/dibujaba antes de aparecer la ventana independiente de IntroPreview. Se conserva la inicialización, pero la capa ClientApp tiene alpha 0 y semántica vacía mientras introActive. Una máscara oscura bloquea toque hasta la intro; Back permite terminar. No hay delay añadido. El Dialog permanece inmersivo y conserva Player, lifecycle, watchdog y manejo de restauración; placeholder oscuro sin icono adicional. No se altera el MP4 (SHA-256 `2b59bb7315b537cb07958fdd5619751bd1c2ec8a3f079790f6820b48c60a99f0`). El cambio de código elimina la ventana lógica de exposición de Inicio; el resultado visual exige comprobar un arranque físico.

## Favoritos y vistos

PersonalLibraryStore usa una interfaz de almacenamiento testeable; AndroidLibraryStorage utiliza SharedPreferences separadas con applicationContext. `schemaVersion:1` permite futura migración/exportación sin añadir sync o función de exportación actual. IDs favoritos persistentes, deduplicados; catálogo ausente no elimina nada. Lectura tolerante de entradas dañadas; schema futuro no se sobrescribe. No se modifica el progreso preexistente.

Vistos manuales por título (`title:id`) o episodio (ProgressKeys existente: TMDB/legacy/publicación por digest del enlace). Los indicadores distinguen título visto de episodios vistos; el tab Ya vistos incluye padres que tienen episodios vistos, sin afirmar que estén completos. Una marca manual del título es independiente de sus episodios. Al quitar la marca, se borra cobertura de esa identidad para que no reaparezca por cobertura vieja. No es una exclusión permanente: otro consumo suficiente vuelve a marcar automáticamente.

## Cobertura y riesgo acotado

PlaybackCoverageObserver es solo lector: no invoca seek/prepare/release ni modifica tracks/playWhenReady. Se conecta como listener adicional al Player existente. Utiliza el polling de posición existente cada 500 ms: no crea timer de red/background ni otro Player. onIsPlayingChanged delimita pausa/buffering; onPositionDiscontinuity usa old/new para cerrar la zona previa y anclar la nueva, sin cubrir el salto. Las muestras solo atribuyen desplazamientos positivos acordes al reloj monotónico/velocidad; pausas largas de scheduling (>2,5 s) se descartan conservadoramente. Se admiten 100 ms de tolerancia de muestreo, no huecos de seeks.

Los intervalos se recortan a duración válida, se ordenan y se fusionan sin cerrar huecos. Se unen entre sesiones y se cuentan una sola vez. Se marca automáticamente con >=80 % de unión y extremo reproducido en la zona final `min(300000 ms, duration/20)`. Llegar al final mediante seek no basta; ENDED por sí solo tampoco. Sin duración válida no se atribuye visto automático.

Solo se añaden parámetros de biblioteca, creación del observador, una muestra en el loop existente y flush antes de release a PlaybackScreen. Se conserva exactamente `player.release()` → `video.clear()`. finish del observador es idempotente; al Dispose elimina su listener sin consultar un Player ya liberado. No modifica TelegramVideo, TDLib, DataSource, progreso, pistas, controles, fullscreen ni EpisodeNavigation.

Persistencia cada 15 s y al liberar; proceso matado sin lifecycle puede perder hasta el tramo pendiente, no inventa consumo. La medición puede subestimar con scheduling muy irregular, nunca debe rellenar automáticamente los huecos. No se infiere historial de las posiciones guardadas antes de 0.9.7.

## Similares

Solo catálogo local. Excluye ID propio/vacío, duplicados, título vacío y enlaces no reproducibles. Series requieren un episodio válido (su enlace de agrupación no basta); películas/anime aceptan publicación directa o episodio. Debe haber género normalizado compartido. Orden lexicográfico: cantidad de géneros compartidos ↓, mismo tipo, palabras no triviales compartidas de sinopsis ↓, distancia de año ↑, puntuación válida ↓, título/ID. No usa rating como requisito, ni popularidad o llamadas TMDB/Cloudflare. Como máximo 12, estado vacío explícito. No afirma comprender semánticamente una sinopsis; las palabras son solo un desempate simple.

## Pruebas y límites

JVM: favoritos, persistencia/reapertura, ausencia temporal, no duplicados, schema/corrupción, vistos manuales, episodios/anime, cobertura fragmentada/deduplicada, umbrales, seek, pausa/buffering y suspensión del sampler. Similares: candidatos, exclusiones, desempates y orden estable. Se conservan IntroSessionTest (restauración/fin/error/background en el modelo) y suites existentes de progreso/streaming UI helpers. No se simula falsamente un archivo reproduciéndose en Android. Un test del sampler no demuestra callbacks reales del teléfono.

Gradle local no descarga su distribución por restricción de red; Actions ejecuta testDebugUnitTest, lintDebug, assembleDebug, assembleRelease en rama nueva. Reportar el resultado real al terminar. No hay emulador ni Samsung conectado.

## Samsung A21s y A14

Actualizar con misma clave privada de firma y configuración local de Telegram; el debug de CI sin secretos no sustituye la release familiar.

1. Forzar cierre y abrir varias veces: Android icono → oscuro → MP4 → Inicio/conexión habitual; sin destello de Inicio. Girar durante intro, enviar a background y volver. Mantener audio, error/timeout/Back sin quedar atrapado. No afirmar resuelto hasta observarlo.
2. Abrir película/serie/anime, agregar/quitar Mi Lista; comprobar estado al volver y tras cerrar app. Mi Lista no duplica títulos. Si un título desaparece temporalmente, debe reaparecer favorito al regresar al catálogo.
3. Marcar/desmarcar título y episodio. Ya vistos debe distinguir serie con episodios vistos de título marcado entero. Marcar un episodio no marca hermano ni serie; marcar serie manualmente no marca episodios.
4. Reproducir un archivo corto completo: debe quedar visto. Otro archivo: ver pocos segundos y hacer seek al final; no debe marcarse automáticamente. No debe alterar la regla independiente de progreso/Continuar viendo, que puede quitar la posición cerca del final aunque no haya cobertura suficiente para visto.
5. Repetir el mismo 20 % varias veces y saltar al final: no debe contar como 80 %. Reproducir fragmentos distintos entre sesiones hasta superar 80 % y consumir la zona final: debe marcarse. Pausar y dejar buffering no deben sumar. Quitar marca y ver segundos finales no debe remarcar por cobertura antigua.
6. Detalle → similares: sin duplicado propio; abrir relacionado y Back vuelve a la sección de origen. Sin metadatos compartidos: estado vacío.
7. Regresión: película/episodios, seek/barra, ±10, pausa, brillo, volumen, fullscreen/giro, anterior/siguiente entre temporadas, progreso/Continuar viendo, trailers imagen/audio, selección de audio y subtítulos/tamaño. Repetir background/reanudación sin pérdida de posición ni recursos duplicados.

Pendiente físico: destello, ergonomía en pantallas chicas, medición automática durante Media3 real y lifecycle; no se declaran validados por compilar.

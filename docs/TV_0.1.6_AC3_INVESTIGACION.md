# TV-AUDIO-02: AC3 en MiFlix TV 0.1.6

## Conclusión y estado entregado

La decodificación AC3 por software con la extensión oficial de Media3 es
**técnicamente viable**. No es un motor nuevo ni requiere convertir/descargar
películas: el extractor existente entrega muestras comprimidas al renderer de
audio, que decodifica a PCM para AudioSink. TDLib y TelegramRangeDataSource pueden
permanecer idénticos.

**En esta 0.1.6 no se incorpora FFmpeg ni se declara resuelto AC3.** No se dispone
de Android SDK/NDK, CMake/Ninja ni JDK completo para construir, empaquetar y verificar
la extensión. La edición Windows del usuario no basta por sí sola para producir
las bibliotecas según la receta oficial (su README no ofrece soporte Windows).
No se añade una dependencia ficticia de Maven, una clase sin JNI ni un AAR ajeno.
Se entrega diagnóstico AC3 específico y listado del registro de decodificadores;
la compatibilidad y el marcado ✓ siguen dependiendo exclusivamente de Media3.

## Evidencia aportada por el usuario

En dos emuladores Android TV, incluido Android 16:

| Pista | MIME | Idioma | Canales | Resultado |
| --- | --- | --- | --- | --- |
| Video | video/hevc | — | — | HANDLED, seleccionada |
| Surround | audio/ac3 | en | 6 | UNSUPPORTED_SUBTYPE, no seleccionada |
| Stereo | audio/ac3 | es | 2 | UNSUPPORTED_SUBTYPE, no seleccionada |
| Subtítulos | text/x-ssa | es | — | HANDLED |

Volumen de Player 1.0 y playWhenReady=true. Confirma falta de ruta AC3 utilizable
por el Player en esos entornos, no falta de datos de Telegram ni una limitación
universal de Android TV. El mismo archivo funciona en Mobile según la prueba del
usuario. No se generaliza ese soporte a otros formatos/dispositivos.

## Configuración existente auditada

- Media3 ExoPlayer y UI: **1.3.1**; minSdk 26, compileSdk/targetSdk 34.
- TV: DefaultRenderersFactory con fallback de inicialización de MediaCodec.
  Ese fallback no agrega ningún codec ausente.
- Extension renderers: modo por defecto OFF, sin módulo FFmpeg en dependencias.
- TrackSelector: el predeterminado de ExoPlayer, sin selector personalizado.
- applyDefaultAudio mantiene preferencia por español sólo entre pistas compatibles.
- selectTrack verifica el grupo actual/índice y `isTrackSupported(index)` antes de
  configurar TrackSelectionOverride. No se fuerza EXCEEDS_CAPABILITIES.
- Mobile mantiene ExoPlayer.Builder(context), su configuración y hoja inferior.

Android ofrece MediaCodec de plataforma; sus codecs dependen del sistema y el
fabricante. No podemos inventariar el emulador remoto desde aquí. La 0.1.6 consulta
`MediaCodecList.REGULAR_CODECS`, excluye encoders y registra nombres que anuncian
`audio/ac3`, al crear cada Player TV. Un nombre en el registro no demuestra que
admita el perfil/canales/AudioSink del archivo; los snapshots de Media3 son la
referencia efectiva. Las trazas no incluyen URLs, sesiones ni credenciales.

## Extensión oficial evaluada

Origen: AndroidX Media, módulo `libraries/decoder_ffmpeg`, tag **1.3.1**, commit
`d833d59124d795afc146322fe488b2c0d4b9af6a` (tag anotado
`4e0f919809b7ee7e82c8bd1391155361e0803844`). No se integra el código del branch
release actual con una versión antigua sin verificar APIs.

Archivos oficiales leídos: README, build.gradle, build_ffmpeg.sh, CMakeLists.txt,
FfmpegLibrary y DefaultRenderersFactory. FfmpegLibrary mapea audio/ac3 → ac3 y
comprueba disponibilidad real de JNI/decoder; no basta incluir las clases Java.
El README de 1.3.1 recomienda FFmpeg 6.0 y NDK r26b. Esa recomendación histórica
no acredita seguridad/compatibilidad de un build en 2026: antes de distribuir,
fijar un commit de FFmpeg con correcciones y comprobarlo con esta extensión.

La extensión no se publica por Google Maven. Requiere:

1. Fuentes oficiales Media3 1.3.1 y FFmpeg fijadas por commit/hash.
2. Compilación FFmpeg sólo con decoder ac3 (eac3 sería otro alcance).
3. NDK + CMake ≥3.21 + Ninja, SDK y JDK para construir JNI/AAR/APK.
4. libavcodec/libavutil/libswresample estáticas dentro de libffmpegJNI.so según
   CMake oficial; la APK carga JNI. Documentar ese enlace al atender LGPL.
5. Al menos arm64-v8a y x86_64; evaluar armeabi-v7a para TVs ARM de 32 bits.
   El script oficial genera además x86 y armeabi-v7a. Omitir ABI usadas por TDLib
   puede dejar un APK instalable sin audio software en ciertos dispositivos.
6. Dependencia sólo tvImplementation de un módulo/AAR reproducible con JNI;
   Mobile no debe empaquetar FFmpeg.
7. DefaultRenderersFactory TV con EXTENSION_RENDERER_MODE_ON: nativo primero,
   extensión después si el nativo no soporta formato. No usar PREFER ni seleccionar
   pistas manualmente contra el resultado de compatibilidad.
8. Verificar carga JNI, supportsFormat real, PCM/AudioSink de 2 y 6 canales,
   cambio de pista, seek, pause/play y regresiones AAC.

Para bibliotecas nuevas en ARM64/Android 15+ comprobar también ELF/empaquetado
para páginas de 16 KB. El script/CMake antiguos no acreditan ese resultado. No
se afirma que todos los emuladores Android 16 usen páginas de 16 KB ni se cambia
el empaquetado de TDLib dentro de esta versión.

## Licencias y distribución

No se incorporan nuevas dependencias/bibliotecas nativas, así que esta entrega no
redistribuye FFmpeg. El repositorio conserva su LICENSE GPLv3 existente.

Para una integración futura: wrapper AndroidX bajo Apache-2.0; FFmpeg normalmente
LGPL-2.1-or-later, con partes opcionales GPL. Construir sin enable-gpl/enable-nonfree
si se busca el conjunto LGPL; comprobar el resultado y licencias de cada componente.
Conservar avisos y textos, publicar fuentes exactamente correspondientes al binario,
parches/configuración/herramientas y material necesario para reconstruir/reemplazar
la biblioteca según el enlace elegido. La receta oficial enlaza .a en JNI: no
suponer que basta publicar un enlace al repositorio upstream. Incluir atribución
FFmpeg en Acerca de y en el punto de distribución del APK cuando se integre.

El proceso de licencia no debe prohibir la ingeniería inversa necesaria para
modificar la biblioteca. La licencia de código y eventuales patentes son temas
separados; no se hace una garantía mundial de patentes AC3. No hay impedimento
legal identificado que por sí solo vuelva imposible la investigación: el bloqueo
actual es la ausencia de un build nativo verificable y de pruebas de distribución.

## Alternativas y decisión

| Alternativa | Evaluación |
| --- | --- |
| Probar el mismo APK en un TV que anuncie y soporte AC3 | La más sencilla para validar soporte nativo; no resuelve emuladores sin codec |
| Cambiar el sistema/imagen de emulador | Podría cambiar codecs; dos imágenes ya fallaron y no hay garantía |
| Forzar HANDLED/EXCEEDS_CAPABILITIES o un override de pistas | Descartado: no agrega decoder y puede fallar |
| Actualizar sólo Media3 | No agrega por sí solo un decoder AC3; actualización transversal innecesaria |
| AAR precompilado no verificado | Descartado por procedencia, versión, ABIs y licencias no comprobadas |
| FFmpeg oficial con build reproducible exclusivo TV | Ruta recomendada para resolver software sin alterar streaming |
| Convertir archivo/agregar AAC/remux o servidor de transcodificación | Fuera del alcance autorizado: altera originales o infraestructura |
| Abrir otro reproductor externo | No sustituye automáticamente el DataSource privado por rangos de MiFlix |

## Tamaño y rendimiento

**Esta entrega:** 0 bytes de nueva biblioteca nativa. No se añadieron dependencias;
el delta de APK completo no está medido porque no se compiló. Se agrega una
consulta del registro al crear el Player, no en cada frame, y pequeños cambios UI.

**Futura extensión:** el tamaño crece por cada ABI y depende de codecs habilitados,
optimización/strip y empaquetado. Un build sólo AC3 sería menor que uno de múltiples
codecs, pero no se entrega una cifra de MB inventada sin .so/APK. Medir tamaño
comprimido y descomprimido de arm64/x86_64 y APK universal/splits antes de decidir.
La decodificación software usa CPU adicional; seis canales suelen implicar más
trabajo que dos, y el emulador no representa rendimiento/consumo de un TV ARM.
Medir underruns, sincronía, consumo y temperatura durante reproducción prolongada;
no prometer reproducción en tiempo real en todos los TVs.

## Siguiente paso recomendado para AC3

Preparar un build separado y reproducible de la extensión oficial en Linux/WSL2
con SDK/NDK/CMake/Ninja/JDK completos, fuentes y licencias fijadas. Verificar AAR
arm64/x86_64 y política de 32 bits, después integrar sólo TV y validar House real.
Es trabajo adicional pendiente; la 0.1.6 no instala estos requisitos en la PC del
usuario ni deja hooks que anuncien una extensión inexistente.

## Referencias primarias

- https://github.com/androidx/media/blob/1.3.1/libraries/decoder_ffmpeg/README.md
- https://github.com/androidx/media/blob/1.3.1/libraries/decoder_ffmpeg/src/main/jni/build_ffmpeg.sh
- https://github.com/androidx/media/blob/1.3.1/libraries/decoder_ffmpeg/src/main/jni/CMakeLists.txt
- https://github.com/androidx/media/blob/1.3.1/libraries/decoder_ffmpeg/src/main/java/androidx/media3/decoder/ffmpeg/FfmpegLibrary.java
- https://developer.android.com/media/media3/exoplayer/supported-formats
- https://developer.android.com/media/platform/supported-formats
- https://developer.android.com/guide/practices/page-sizes
- https://ffmpeg.org/legal.html

Hashes SHA-256 de la investigación (archivos obtenidos directamente del tag oficial):

```text
README.md        483cee4d879afbc17a83e33af04c220f3293ea8d3241f04a52bd2616f19f0f45
build_ffmpeg.sh  337c88b9240b67e290c361a59c39cb2812c5bfeb5c04833b5f63b227d07c9dc2
CMakeLists.txt   e8228a9eced27564d33dde3e86ca003ad86cee60e9d6bd68e53fbaa9d429bd25
```

# Historial de MiFlix TV

## 0.1.13-diagnostico / 14

- Mi Lista/Favoritos y Vistos con persistencia exclusiva TV y protección de datos.
- Marcado manual de películas/episodios, integración no destructiva con Continuar viendo.
- Cobertura de reproducción de Mobile mediante observador independiente, sin control del Player.
- fecha_agregado opcional y Últimos agregados por instante real, sin límite artificial.
- Sugerencias y pedidos con QR local exacto a https://t.me/Dario_Devia.
- Sin nuevas dependencias Android ni cambios de Worker, Admin o interfaz Mobile.
- Compilación, firma y pruebas físicas se distinguen en docs/TV_0.1.13_BIBLIOTECA.md.

## 0.1.12-diagnostico / 13

- TV conserva duración conocida cuando un guardado informa duración desconocida;
  evita que un checkpoint final vuelva a presentar un contenido completado.
- Inicio escucha cambios persistidos del progreso; no incorpora marcado Visto.
- Especiales al final del selector, usando índices originales para foco/reproducción.
- Reproductor, Hero, intro, ARM y Mobile conservados; 87 tests JVM y 46 contratos
  locales aprobados. CI: 208 tests por variante, Lint TV 0 errores/17 advertencias,
  Debug/Release compiladas y APK ARM/firma/intro verificadas. Prueba física pendiente.
- Detalles: docs/TV_0.1.12_CONTINUAR_ESPECIALES.md.

## 0.1.11-diagnostico / 12

- Segunda corrección Lint: OptIn específico en VideoPlayer para los 20 errores
  restantes del informe local. Cuerpo intacto; versión conservada.
  Rama fix/tv-0.1.11-lint-videoplayer; nueva ejecución Lint pendiente en PC.

- Corrección independiente de los 51 errores del informe local: cuatro OptIn
  específicos de Media3 y exported=true explícito en TV. Sin cambios funcionales.
- 38 contratos y 54 pruebas JVM reejecutadas pasan; Lint/compilación Android
  de esta corrección pendientes. Detalles en docs/TV_0.1.11_LINT.md.

- Recuperados los cambios locales de la tarea interrumpida desde la base exacta 0.1.10.
- MP4 original TV de 4,01 s; reproductor Media3 independiente sin controles visibles.
- Inicio se compone tras terminar/omitir, error o timeout de 10 s; liberación previa.
- OK/Enter/Atrás omiten; segundo plano cancela sin repetir al volver.
- Tema inicial negro exclusivo TV; versión Mobile y cuatro ABI TDLib intactas.
- Pruebas de política, sintaxis y contratos ejecutadas; Gradle bloqueado por descarga
  inaccesible. Sin APK ni validación física. Detalles en docs/TV_0.1.11_INTRO.md.

## 0.1.10-diagnostico / 11

- Hero TV: hasta diez películas aleatorias por sesión de proceso, priorizando
  alternativas a la sesión anterior y variedad de géneros, sin ranking por votos.
- Rotación 8 s, fade de fondos 500 ms y precarga acotada del siguiente. Pausa por
  foco del Hero, visibilidad y lifecycle; lista/índice estables al volver de ficha.
- Acciones originales, sin recrear el reproductor ni trasladar foco por rotación.
  Fondos inválidos/verticales se descartan; logo conserva sustitución por texto.
- ARM, firma, TDLib, Media3, Telegram, tráilers y pistas intactos. Mobile sin cambios.
- 44 tests JVM y 23 comprobaciones Python/contratos pasan; AAR de cuatro ABI
  verificado. Compilación Android, APK nueva/inspección ARM y UI física pendientes.
- Usuario confirmó pruebas físicas de películas/tráilers/navegación con la base;
  eso no constituye validación de 0.1.10. Sin fusión automática.

## 0.1.9-diagnostico / 10

- Preparación de empaquetado físico: ABI reales explícitas sólo en flavor TV.
  Se mantiene core:0.1.1, con cuatro ELF y bindings del mismo AAR verificados.
- Script Windows para build debug normal e inspección de APK/firma; rechazo de
  testOnly, ABI faltantes, bibliotecas inesperadas y versión/manifest incorrectos.
- APK aportada 0.1.8 inspeccionada: x86 sólo y testOnly=true; dependencia completa.
  Run/ABI inyectada es la causa probable, sin registro de invocación original.
- 33 pruebas JVM, 11 Python y 6 contratos del panel pasaron. app/src idéntico.
- APK 0.1.9, firma real, instalación y reproducción física pendientes; no se declara
  completada la compatibilidad ARM. Sin cambios funcionales ni versión nueva Mobile.

## 0.1.8-diagnostico / 9

- Etiquetas TV sólo con idioma real de Media3; localización española, ISO2/ISO3,
  desconocidos seguros y duplicados numerados por orden original. Sin nombres de
  canales, enlaces ni descripciones técnicas. Selección y compatibilidad intactas.
- Inicio: películas y series por año de estreno descendente, actual primero;
  empate estable, años inválidos al final y destacado estable durante la sesión.
  Anime excluido de la nueva lógica; Continuar viendo sin cambios.
- 12 pruebas JVM y 11 contratos de código pasan; sintaxis Kotlin comprobada.
  Android no compilado ni validado visualmente aquí. Tráilers/AC3 congelados.

## 0.1.7-diagnostico / 8

Base: `94790b811d0eb88d85457ecedfddf50ce755c148`, respaldo 0.1.6 / 7.

- Tráiler: inicio solicitado tras apertura explícita, watchdog armado en Android,
  retirada del WebView fallido y reintento con estado nuevo. Logs sanitizados de
  proveedor/layout/página/TLS; apertura externa prioriza YouTube TV y móvil.
- Controles en una sola fila con barra central flexible y tiempos visibles.
- Pósteres con indication=null y borde de foco existente, sin filtros/alpha nuevos.
- 38 pruebas JVM, 13 contratos estructurales y contrato JavaScript pasan.
  Casos instrumentados de alineación/color preparados, no ejecutados aquí.
- Compilación Android y reproducción visual/local pendientes. Tráiler real no
  confirmado. AC3, audio y streaming sin cambios.

## 0.1.6-diagnostico / 7

Base: `4d63ccdd9acd47f9fa9727ea900019addab0a968`, respaldo 0.1.5 / 6.

- Fila de controles compacta: Play/transcurrido a la izquierda, duración/engranaje
  a la derecha; barra debajo con foco DPAD y seek existente ±10 s.
- Diagnóstico de decodificadores AC3 de plataforma y mensaje específico para pistas
  AC3 no compatibles. No se altera su soporte real ni se fuerza la selección.
- Investigación de FFmpeg oficial Media3 1.3.1, ABIs, build nativo, licencia, tamaño
  y CPU. No se incorpora decoder software por ausencia de build verificable.
- 34 pruebas JVM y 6 contratos pasan; tres casos instrumentados nuevos pendientes.
  Compilación Android y validación local pendientes. Tráiler negro sigue pendiente.

## 0.1.5-diagnostico / 6

Base: `a05c6a703aecb3387e64ba696522a22ca9d9e276`, respaldo 0.1.4 / 5.

- Panel derecho sobre el Player existente, con subtítulos antes de audio, foco DPAD
  explícito, explicación de pistas no compatibles y marcas de selección efectiva.
- Back y Cerrar conservan reproducción/pausa, posición y selecciones; restauración
  del foco de Audio y subtítulos. Sin cambios de TDLib/rangos/catálogo.
- Diagnóstico TV de salida de audio, compatibilidad, selección, decodificador y errores
  de codec/sink. Fallback entre decodificadores disponibles si falla inicialización;
  no agrega soporte para formatos que el sistema no puede decodificar.
- Tráiler oficial con dimensiones numéricas, foco en Reproducir al quedar listo,
  detección de carga JavaScript/inicio fallido y mensajes de error comprensibles.
- Versión sólo en Configuración → Acerca de MiFlix.
- Pruebas JVM, contratos de código y JavaScript comprobados. Compilación Android,
  pruebas instrumentadas y reproducción real pendientes. No es una versión validada.

## 0.1.4-diagnostico / 5

Respaldo `backup/miflix-tv-0.1.4`, commit
`a05c6a703aecb3387e64ba696522a22ca9d9e276`. Conserva mejoras de menú/foco,
pósteres adaptativos, episodios y tráiler integrado. El usuario reportó
`assembleTvDebug` exitoso y pruebas en Android TV 1080p. Pendientes: House sin audio
con opciones Surround/Stereo inaccesibles; tráiler de 13 fantasmas negro.

## 0.1.3 / 4 y 0.1.2 / 3

Ver README y documentos históricos. `miflix-tv` conserva 0.1.2 / 3.

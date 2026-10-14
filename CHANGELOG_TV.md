# Historial de MiFlix TV

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

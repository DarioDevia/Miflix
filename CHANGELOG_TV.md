# Historial de MiFlix TV

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

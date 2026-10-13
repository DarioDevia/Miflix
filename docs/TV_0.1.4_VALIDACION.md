# MiFlix TV 0.1.4-diagnostico / 5 — desarrollo

Base verificada: `a71bc7baf591ac4593fe7dd2b0280715d55bfb76`,
`backup/miflix-tv-0.1.3`, árbol limpio e idéntico al respaldo local.
Rama de trabajo: `backup/miflix-tv-0.1.4`. La estable `miflix-tv` queda intacta.

## Diagnóstico inicial

- Menú: sólo dependía de búsqueda geométrica de foco; no había salida explícita
  por derecha ni memoria del último destino. Problema de código.
- Pósteres: ancho fijo 154 dp, separación 16 dp, relleno 8 dp, fondo/marco y
  título visible. Presentación de código, no problema del catálogo.
- Episodios: el modelo/parser ya conserva `imagen_url`, número, título,
  `duracion` numérica (minutos) y `sinopsis`; la vista TV sólo usaba un botón.
- Telegram: Gradle lee opcionalmente `telegram.local.properties`; si no existe,
  genera ID 0/hash vacío. TDLib conserva estados Phone, Code, Password y Ready
  (también Email/EmailCode cuando Telegram los pide). La APK sin configuración
  queda deliberadamente en NeedsApi. Es configuración local, no pérdida del flujo.
  No se lee ni se añade configuración privada para esta tarea.
- Tráiler: el catálogo usa `trailer_url`. `TrailerLink` acepta enlaces YouTube
  HTTPS y extrae ID/start. TV 0.1.3 sólo ofrecía ACTION_VIEW externo; Mobile
  tiene su propio WebView, que no se modifica.

## Checkpoint A

Salida del menú por derecha y Atrás hacia el último objetivo montado; alternativa
al punto de entrada si el elemento desapareció. Izquierda usa navegación entre
controles y, al llegar al borde, entra al menú. Memoria limitada a cada pantalla,
sin referencias retenidas a tarjetas descartadas por las listas.
Mensaje TV específico para configuración ausente, también en Configuración.

Las pruebas visuales, DPAD y reproducción real siguen PENDIENTES. No se preparan
ni ejecutan emuladores. Los tests unitarios no demuestran comportamiento visual.

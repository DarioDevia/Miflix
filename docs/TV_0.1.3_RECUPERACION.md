# Recuperación TV 0.1.3 — en validación

Base limpia clonada: `28da9ac2ecf22ce7fecfa5f0dcaf4cef6cbcf108`, rama `miflix-tv`.

Se reconstruyeron las ediciones de código disponibles en el registro de la tarea
anterior. No son archivos rescatados del contenedor: aquel entorno sigue sin acceso.
Se revisaron los comandos y se excluyeron instalación de herramientas, borrados,
credenciales y rutas del entorno anterior. Una redacción automática afectaba a la
declaración pública `password: Boolean`; se restauró el tipo, sin datos privados.

Este checkpoint NO es una entrega validada. Los resultados de 132 tests del
historial no se atribuyen a esta copia. Falta compilar y ejecutar las pruebas aquí.

Alcance: presentación TV, navegación y entrada de texto TV. Se conserva el motor
de reproducción, TDLib, persistencia, catálogo y comportamiento exclusivo Mobile.
Películas, Series y Anime comparten backdrop, identidad, tarjetas y acciones;
Series/Anime conservan selección de temporadas y episodios.

Pendientes: verificar `movie-logo`, DPAD ida/vuelta a cabecera, teclado TV,
compatibilidad Mobile, controles del player y revisión visual. El tráiler TV usa
una acción externa explícita; no se afirma corregida la superficie WebView Mobile.

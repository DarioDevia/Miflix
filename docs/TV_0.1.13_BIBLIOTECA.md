# MiFlix TV 0.1.13 — Biblioteca personal y descubrimiento

Base físicamente validada por el usuario: fix/tv-0.1.12-continue-specials,
57fca311dc06b5ad9fdcf5a02ac9daa9c08bd0f9, árbol limpio.
Rama: feature/tv-0.1.13-personal-library. Versión TV 0.1.13-diagnostico / 14.
ApplicationId ar.com.miflix.client.tv. Mobile mantiene 0.9.2 / 26 en esta línea;
no se fusiona con Mobile ni se publica una versión Mobile.

Referencia auditada: Mobile feature/mobile-0.9.13-home-discovery,
092ece114c6798d891f50e15808cd0d8250d163f. Se reutilizan las reglas de IDs,
marcado, intervalos consumidos y fechas; no su interfaz de teléfonos ni sus
funciones de identidad, recomendaciones, sincronización o filas de estrenos/géneros.

## Funciones

- Catálogo: fecha_agregado opcional, ISO 8601 con zona explícita. Se ordena por
  instante real; datos ausentes/inválidos no bloquean el catálogo ni reciben fechas
  sustitutas. No se modifica el Worker, JSON publicado o metadatos existentes.
- Inicio: Hero, Continuar viendo, Mi Lista (si tiene favoritos), Últimos agregados
  (si hay fechas válidas), filas originales. Fechas válidas de películas, series y
  anime disponibles, sin duplicados ni límite artificial. Carátulas originales.
- Fichas: agregar/quitar favoritos para los tres tipos; marcar/no marcar película
  vista. Episodios: acciones independientes e indicador ✓ Visto. La serie muestra
  el número de episodios vistos; un episodio nunca marca el título padre ni otros.
- Navegación Mi Lista: Favoritos y Vistos, con botones, foco y tarjetas TV. Volver
  desde una ficha conserva pestaña, scroll y tarjeta si aún está en la proyección.
  Si la tarjeta desaparece al desmarcarla, el foco vuelve al filtro de la sección.
- Configuración: Sugerencias y pedidos, QR local y contacto público @Dario_Devia;
  contenido exacto https://t.me/Dario_Devia. Cierre con botón o Atrás. No usa teléfono,
  sesión Telegram, API_ID/HASH, bots, servicios QR o conexión externa.

## Persistencia y reproducción

Preferencias privadas de TV: miflix_tv_library / library_v1, schemaVersion 1.
Sólo IDs estables, claves de progreso y cobertura; favoritos por Title.id. No hay
URLs Telegram como IDs de favoritos. Los IDs temporalmente ausentes del catálogo
se conservan y reaparecen cuando el catálogo vuelve a incluirlos.

Lectura inválida, error de almacenamiento, esquema futuro o campos raíz desconocidos
impiden escrituras destructivas y muestran una explicación. Una escritura manual
fallida revierte la selección visible. No se reinicia ni se elimina la biblioteca
para recuperarla. La lectura de formatos futuros puede mostrar campos reconocidos
sin permitir cambios; el JSON original permanece intacto.

El marcado manual oculta el elemento de Continuar viendo sin borrar su progreso.
Desmarcar sólo revela progreso pendiente válido. Nunca crea progreso ni el episodio
siguiente. Se limpia la cobertura al desmarcar: no se convierte progreso antiguo en
un nuevo Visto sin reproducción real.

Se adapta la regla de cobertura de Mobile: al menos 80 % realmente consumido más
alcanzar el margen de finalización existente. Saltos, pausas y huecos de segundo plano
no cuentan como consumo. Esta regla marca la biblioteca; no reemplaza la regla TV
0.1.12 de progreso min(5 minutos, 5 % de duración). Un observador independiente sólo
lee eventos/posición de Media3 y se retira al cerrar/ir a segundo plano. El reproductor
recibe únicamente el mínimo cableado para conectar ese observador; preparar, seek,
pistas, audio, liberación, TDLib y navegación de episodios permanecen iguales.

## QR y dependencias

PNG estático generado localmente mediante tools/generate_tv_requests_qr.py; se incluye
como assets/miflix_tv_requests_qr.png exclusivamente en TV. La generación es repetible
con qrcode 8.2. zxing-cpp 2.3.0 decodifica los bytes reales en las pruebas; Pillow
11.3.0 permite leer la imagen. Son herramientas de desarrollo/CI, no dependencias
Android y no se incluyen en la APK. Android no incorpora nuevas dependencias.

Fuentes: https://pypi.org/project/qrcode/8.2/ y https://github.com/zxing-cpp/zxing-cpp.
QR SHA-256: 260478e570101b569de5511987e4c8ceed5ccc5132566c72706688b1918d7c49.
No se usa generación de imágenes por IA para este recurso.

## Pruebas y alcance de validación

Las pruebas nuevas cubren favoritos de todos los tipos, persistencia/reapertura,
IDs ausentes, duplicados, marcas y desmarcas, independencia de episodios/padres,
cobertura, saltos y observador sin mutaciones del Player, errores de lectura/escritura,
esquemas futuros, fechas reales/zonas/ausencias/formatos inválidos, filtros y QR real.
Se conservan todas las pruebas de políticas de TV 0.1.12 sin modificar sus fuentes.

Los antiguos contratos que congelaban todo MainActivity o TvScreens son snapshots
históricos de versiones anteriores. Para esta versión se sustituyen por
check_tv013_contract.py, que permite la UI autorizada y exige byte identidad en
intro, Hero, progreso, streaming, controles, tráilers, especiales y manifiestos.
El contrato del reproductor sólo admite el cableado de cobertura, sin cambiar cuerpos.

GitHub Actions tv013-validation.yml enumera tareas reales, ejecuta tests TV/Mobile,
Lint TV/Mobile y compila TV Debug/Release, inspecciona APK (ABI, ELF, MP4, QR, manifest,
versiones, testOnly y firma). Los resultados exactos se registrarán tras la ejecución.
Definir estas tareas no implica que estén aprobadas. UI/DPAD y prueba física no se
consideran validadas por compilar o pasar contratos de fuentes.

## APK, firma y actualización

La APK de CI utiliza una clave debug temporal y no tiene configuración privada de
Telegram. No se presenta como actualización compatible con el TV instalado. No se
ha recibido certificado/APK de la instalación física para comparar su firmante, ni
se accede a claves privadas. La salida Release no tiene una nueva firma configurada.

Para una actualización real, conservar los archivos locales privados y la clave
que ya utilizó Android Studio para firmar 0.1.12; no crear otra identidad. En Windows:

```powershell
git fetch origin
git switch --track origin/feature/tv-0.1.13-personal-library
.\tools\build_tv_physical_debug.ps1
$tvApkPath = py -3 tools/locate_tv_apk.py
adb install -r "$tvApkPath"
```

Si la rama ya existe, git switch y git pull --ff-only. Usar JDK 17 y SDK del proyecto.
El script conserva las verificaciones y localiza la APK mediante metadatos AGP
(incluido build/intermediates/apk). No desinstalar ni borrar datos para resolver
un conflicto de firmas. No modificar las preferencias Telegram al actualizar.
Si falta la clave original, la APK CI no es una sustitución compatible: recuperar
primero esa clave. El verificador de firma comprueba validez; comparar certificados
con la APK instalada sigue siendo un paso separado cuando se dispone de ambas.

## Lista física pendiente

1. Inicio: Hero y filas previas intactos, nuevas filas en orden; carátulas sin títulos.
2. Favoritos: agregar/quitar película, serie y anime; volver a Inicio, reiniciar app/TV.
3. Mi Lista: DPAD a Favoritos/Vistos, OK a ficha, Atrás y restauración del foco.
4. Película: marcar/no marcar vista; Continuar viendo se actualiza y conserva progreso.
5. Episodios: marcar uno, comprobar que otros/padre no cambian; desmarcar y reabrir.
6. Reproducir realmente: cobertura marca visto; saltar a créditos no inventa consumo.
7. Fecha agregada: títulos con fechas válidas ordenados, antiguos sin fecha disponibles.
8. QR: escanear desde el celular, verificar contacto; cerrar por OK/Atrás sin salir de app.
9. Regresión: intro, Hero, seek, audio/subtítulos, episodios, Especiales y streaming ARM.

La validación física corresponde al usuario; no se declara realizada desde este entorno.

## Instrumentación preparada (no ejecutada físicamente)

Se amplía el runner Android nativo existente, sin nuevas librerías de pruebas:

- library-favorites-watched-dpad-back: pestañas, OK a ficha, Atrás y restauración.
- library-episode-manual-dpad: Derecha al marcado, OK alterna Visto sin reproducir,
  Izquierda vuelve al episodio.
- requests-qr-back y requests-qr-ok: foco inicial y cierre del diálogo sin cerrar app.

Estos casos usan una biblioteca de memoria y fixtures sintéticos. Sólo deben
usarse en un emulador limpio con APK sin configuración privada, como exige el
runner. CI compila la instrumentación si la tarea está disponible; no equivale a
haberla ejecutado. Ejemplo después de instalar app y test APK en ese emulador:

```powershell
.\gradlew.bat :app:assembleTvDebugAndroidTest
adb shell am instrument -w -e cases library-favorites-watched-dpad-back,library-episode-manual-dpad,requests-qr-back,requests-qr-ok ar.com.miflix.client.tv.test/ar.com.miflix.client.TvDetailInstrumentation
```

No ejecutar el runner sobre la instalación familiar ni borrar sus datos para
correr fixtures. La prueba física normal se realiza con la lista anterior.

# MiFlix TV 0.1.8-diagnostico / 9

## Base y alcance

Base local limpia y remota verificada: backup/miflix-tv-0.1.7,
94daefdaf1bebc05e8001626426fd0f1a4488b8b; Gradle 0.1.7/8.
Nueva rama backup/miflix-tv-0.1.8. Sin fusiones ni cambios a respaldos anteriores.
Sólo etiquetas del panel compartido TV y prioridad de Inicio para películas y series.
Sin modificaciones específicas ni pruebas adicionales de anime.

## Etiquetas

La implementación anterior concatenaba format.label (texto libre con posibles enlaces),
idioma, canales y Stereo/Surround. Ahora se usa exclusivamente format.language:
ISO2/ISO3 y alias bibliográficos se normalizan con Locale y se localizan al español.
Las variantes regionales usan el nombre del idioma. No se infieren idiomas del nombre,
URL, canal o ruta. Metadatos inválidos/ausentes: Idioma desconocido.

Cada sección conserva su orden y número de pistas; apariciones sucesivas del mismo
nombre reciben (2), (3), etc. Desactivados conserva su identidad. No se ocultan pistas
ni se cambian índices, grupos, soporte, overrides, preferencias, selección automática,
marcas efectivas, callbacks, OK, DPAD, Back, reproducción ni persistencia.
La numeración es estable mientras Media3 conserve su lista; si cambia la lista de
pistas, refleja el nuevo orden real. No hay caché de nombres ligada a la selección.
El único panel TV sirve a películas y episodios de todas las temporadas; Mobile
conserva sus etiquetas originales. Las explicaciones de incompatibilidad se conservan.

## Inicio

El año se obtiene del calendario del dispositivo al iniciar la composición/sesión.
Sólo datos locales de Title.year, también para series; nunca fechas de episodios,
temporadas o incorporación. Filas Películas/Series de Inicio ordenadas por año
actual y anteriores descendentes, empates en orden del catálogo. Años ausentes,
fuera de 1800..9999 o futuros quedan después de los ya estrenados. Si el reloj
es anterior a todos los años válidos, se usa el año válido descendente del catálogo.
No es posible detectar todo reloj incorrecto sin una fuente externa; no se agrega una.

El destacado TV toma el primer candidato con imagen utilizable según la validación
existente, o el primer título si ninguno tiene imagen. Se conserva su ID durante
la sesión aunque se refresque el catálogo; si desaparece, se vuelve a aplicar la
prioridad. Sin selección aleatoria TV. Las filas usan remember(titles), sin timer
ni reordenamiento por foco. Un catálogo actualizado sí puede cambiar el orden.
Anime no participa de sugerencias nuevas; su fila mantiene el catálogo original.
No se modifica Continuar viendo, categorías dedicadas, búsqueda ni episodios.
Mobile conserva FeaturedSession y su política previa.

## Comprobaciones

- Implementado: dos mejoras y versionado exclusivamente TV 0.1.8/9.
- Probado automáticamente: 12 pruebas Kotlin/JUnit (7 políticas de pistas,
  5 sugerencias); 6 contratos previos del panel y 5 contratos 0.1.8.
- Contratos comparan contra el commit base: fuentes congeladas, motor de selección,
  panel, marcas/callbacks, TvCard, Continuar viendo, manifest y versión Mobile.
- Sintaxis PSI: MainActivity, PlaybackScreen, TvScreens y TvFeaturedSession sin errores.
- git diff --check sin errores.
- No compilado Android: faltan SDK/Build Tools y JDK completo. Las pruebas puras
  se ejecutan directamente con Kotlin 1.9.22/JUnit 4.13.2; no son tests Gradle completos.
- Sin emuladores, instalación de herramientas Android, pruebas visuales o físicas.
- Tráilers y AC3 pendientes de TV físico; no investigados ni modificados aquí.
- No se abrieron archivos privados ni se agregaron permisos o dependencias.

## Actualización local y pruebas

Guardar cambios propios antes de cambiar de rama; no usar reset/clean forzados.

```powershell
git fetch origin
git switch --track -c backup/miflix-tv-0.1.8 origin/backup/miflix-tv-0.1.8
git rev-parse HEAD
.\gradlew.bat testTvDebugUnitTest testMobileDebugUnitTest assembleTvDebug assembleMobileDebug
adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk
```

Si la rama ya existe: git switch backup/miflix-tv-0.1.8 y git pull --ff-only
origin backup/miflix-tv-0.1.8. Sincronizar Android Studio; elegir tvDebug.
Conservar configuración privada local habitual y firma para mantener datos.
Consultar versión sólo en Configuración → Acerca de MiFlix.

1. Película y episodios de series de distintas temporadas: abrir panel con OK;
   revisar idiomas, duplicados, desconocidos y ausencia de URLs/nombres de canales.
2. Cambiar audio compatible/subtítulos y Desactivados: ✓ efectiva, mismo índice,
   sonido/subtítulo esperado, foco/Back y posición conservados; respetar pausa previa.
3. Inicio: títulos del año actual antes que anteriores en películas y series,
   empates estables; series según su año original. Revisar catálogo sin año actual,
   años ausentes y vacío. Regresar de ficha sin salto de destacado.
4. Continuar viendo, búsqueda y secciones conservan su orden y progreso.
5. Barra/foco de carátulas intactos; regresión habitual Mobile. No se exige prueba
   adicional de anime ni investigación de tráilers/AC3 en esta versión.

## Archivos modificados

README.md, CHANGELOG_TV.md, app/build.gradle.kts;
MainActivity.kt, PlaybackScreen.kt, TvScreens.kt, TvTrackPolicy.kt;
nuevos TvHomeSuggestions.kt y TvFeaturedSession.kt en el mismo paquete;
TvTrackPolicyTest.kt, nuevo TvHomeSuggestionsTest.kt;
tools/check_tv018_contract.py y este documento.

Estado: implementado y comprobado en políticas/contratos; compilación y validación
visual local pendientes. No considerar validada la versión sólo por estas pruebas.

package ar.com.miflix.client

internal data class InstalledVersion(val name: String, val code: Int) {
    val display: String get() = "$name · versionCode $code"
}

internal data class AppUpdate(val name: String, val code: Int, val changes: List<String>)

/** Bundled history: reading About never requires a catalog or network request. */
internal object AppUpdates {
    const val DEVELOPER = "DarioDevia"
    const val SEEN_VERSION_KEY = "updates_presented_version_code"
    fun installedVersion() = InstalledVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

    val history = listOf(
        AppUpdate("0.9.8-diagnostico", 32, listOf(
            "Mi Lista en Inicio y carruseles dinámicos por géneros del catálogo.",
            "Ver todo reutiliza Mi Lista, Secciones y los filtros de búsqueda existentes.",
            "Estado guardable de Inicio y claves estables para conservar desplazamiento al volver."
        )),
        AppUpdate("0.9.7-diagnostico", 31, listOf(
            "Inicio oculto durante la preparación de la intro, sin retrasos artificiales.",
            "Mi Lista persistente y sección Ya vistos con marcado manual por título o episodio.",
            "Vistos automáticos: cobertura reproducida mínima del 80 % y llegada a la zona final.",
            "Más títulos similares según géneros y metadatos locales del catálogo."
        )),
        AppUpdate("0.9.6-diagnostico", 30, listOf(
            "Intro oficial local con imagen y audio al iniciar MiFlix.",
            "Se muestra una vez por lanzamiento nuevo; no se repite al navegar o volver de segundo plano."
        )),
        AppUpdate("0.9.5-diagnostico", 29, listOf(
            "Acerca de MiFlix con versión instalada e historial disponible sin conexión.",
            "Aviso de novedades una vez por versión, al llegar a Inicio.",
            "Diagnóstico de segundo plano, conexión Telegram y solicitudes pendientes."
        )),
        AppUpdate("0.9.4-diagnostico", 28, listOf(
            "Corrección de la integración táctil de la barra de progreso: gestos nativos y seek final único."
        )),
        AppUpdate("0.9.3-diagnostico", 27, listOf(
            "Controles laterales de brillo temporal y volumen multimedia.",
            "Área de progreso de 48 dp; su regresión táctil motivó la revisión 0.9.4."
        )),
        AppUpdate("0.9.2-diagnostico", 26, listOf(
            "Fullscreen conservado al cambiar de episodio.",
            "Destacado aleatorio y estable durante la sesión."
        )),
        AppUpdate("0.9.1-diagnostico", 25, listOf(
            "Búsqueda y filtros locales del catálogo.",
            "Panel Audio y subtítulos, preferencia de español y subtítulos desactivados inicialmente.",
            "Subtítulos blancos con contorno y tamaño persistente Pequeño/Mediano/Grande."
        )),
        AppUpdate("0.9.0-diagnostico", 24, listOf(
            "Continuar viendo local para películas y episodios.",
            "Liberación al pasar a segundo plano y preparación al volver, conservando posición e intención."
        ))
    )
}

internal interface UpdateNoticeStorage {
    fun read(): Int
    fun write(versionCode: Int)
}

internal class UpdateNotice(private val storage: UpdateNoticeStorage) {
    /** Mark on presentation, rather than on dismissal; playback/background never consumes it. */
    fun presentIfEligible(versionCode: Int, onHome: Boolean, resumed: Boolean): Boolean {
        if (!onHome || !resumed || versionCode <= storage.read()) return false
        storage.write(versionCode)
        return true
    }
}

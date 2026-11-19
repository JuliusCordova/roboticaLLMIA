package pe.edu.upn.llmia.net

/** Selecciona transporte para la nube; no modifica red global ni rutas del robot. */
object CloudNetworkPolicy {
    fun <T> select(cellular: T?, defaultInternet: T?, robot: T?, debug: Boolean, enabled: Boolean): T? {
        if (cellular != null) return cellular
        if (!debug || !enabled) return null
        return defaultInternet?.takeIf { it != robot }
    }
}

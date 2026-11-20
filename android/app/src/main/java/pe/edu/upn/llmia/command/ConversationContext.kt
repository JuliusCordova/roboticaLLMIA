package pe.edu.upn.llmia.command

/** Solo apunta a la última respuesta válida. El historial autorizado vive en el backend. */
class ConversationContext {
    var parentId: String? = null
        private set
    var version: Long = 0
        private set
    fun reset() { version++; parentId = null }
    fun remember(id: String, expectedVersion: Long): Boolean {
        if (expectedVersion != version) return false
        parentId = id
        return true
    }
}

package pe.edu.upn.llmia.command

/** Solo permite recuperar la imagen del mismo ID; nunca usa la última foto como alternativa. */
class ObservationImages<T>(private val capacity: Int = 16) {
    init { require(capacity > 0) }
    private val images = linkedMapOf<String, T>()
    fun put(id: String, image: T) {
        images[id] = image
        while (images.size > capacity) images.remove(images.keys.first())
    }
    fun get(id: String?): T? = id?.let { images[it] }
}

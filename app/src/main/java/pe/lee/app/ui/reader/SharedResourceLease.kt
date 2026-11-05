package pe.lee.app.ui.reader

/** Independent reader/service owners release a shared publication exactly once. */
internal class SharedResourceLease<T> private constructor(private val shared: Shared<T>) : AutoCloseable {
    private class Shared<T>(val resource: T, val release: (T) -> Unit, var owners: Int = 1)

    constructor(resource: T, release: (T) -> Unit) : this(Shared(resource, release))

    private var released = false

    val resource: T
        get() = synchronized(shared) {
            check(!released) { "Resource owner already released" }
            shared.resource
        }

    fun retain(): SharedResourceLease<T> = synchronized(shared) {
        check(!released) { "Cannot retain a released resource owner" }
        shared.owners++
        SharedResourceLease(shared)
    }

    override fun close() {
        val lastOwner = synchronized(shared) {
            if (released) {
                false
            } else {
                released = true
                --shared.owners == 0
            }
        }
        if (lastOwner) shared.release(shared.resource)
    }
}

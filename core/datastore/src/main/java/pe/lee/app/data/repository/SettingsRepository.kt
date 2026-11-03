package pe.lee.app.data.repository

import android.content.Context
import android.content.SharedPreferences

/** Typed access to the existing local preference files; names and keys stay backup-compatible. */
class SettingsRepository(context: Context) {
    enum class Store(val fileName: String) {
        LIBRARY("lee_pe_library"),
        READING_STATS("lee_pe_reading_stats"),
        BUNDLED_BOOK("lee_pe_bundled_book"),
        AUDIO_PROGRESS("lee_pe_audio_progress")
    }

    private val appContext = context.applicationContext

    private fun preferences(store: Store): SharedPreferences =
        appContext.getSharedPreferences(store.fileName, Context.MODE_PRIVATE)

    fun getBoolean(store: Store, key: String, default: Boolean = false): Boolean =
        preferences(store).getBoolean(key, default)

    fun getInt(store: Store, key: String, default: Int = 0): Int =
        preferences(store).getInt(key, default)

    fun getLong(store: Store, key: String, default: Long = 0L): Long =
        preferences(store).getLong(key, default)

    fun getFloat(store: Store, key: String, default: Float = 0f): Float =
        preferences(store).getFloat(key, default)

    fun getString(store: Store, key: String, default: String? = null): String? =
        preferences(store).getString(key, default)

    fun getStringSet(store: Store, key: String, default: Set<String> = emptySet()): Set<String> =
        preferences(store).getStringSet(key, default)?.toSet().orEmpty()

    fun contains(store: Store, key: String): Boolean = preferences(store).contains(key)

    fun all(store: Store): Map<String, *> = preferences(store).all.toMap()

    fun update(store: Store, block: Editor.() -> Unit): Boolean {
        val editor = Editor(preferences(store).edit())
        editor.block()
        return editor.commit()
    }

    fun apply(store: Store, block: Editor.() -> Unit) {
        val editor = Editor(preferences(store).edit())
        editor.block()
        editor.apply()
    }

    fun removeBookScopedPreferences(uri: String): Boolean {
        val keys = all(Store.LIBRARY).keys.filter { it.endsWith(":$uri") }
        return update(Store.LIBRARY) { keys.forEach { key -> remove(key) } }
    }

    inner class Editor internal constructor(private val delegate: SharedPreferences.Editor) {
        fun putBoolean(key: String, value: Boolean) { delegate.putBoolean(key, value) }
        fun putInt(key: String, value: Int) { delegate.putInt(key, value) }
        fun putLong(key: String, value: Long) { delegate.putLong(key, value) }
        fun putFloat(key: String, value: Float) { delegate.putFloat(key, value) }
        fun putString(key: String, value: String?) { delegate.putString(key, value) }
        fun putStringSet(key: String, value: Set<String>) { delegate.putStringSet(key, value.toSet()) }
        fun remove(key: String) { delegate.remove(key) }
        fun clear() { delegate.clear() }
        internal fun commit(): Boolean = delegate.commit()
        internal fun apply() = delegate.apply()
    }
}

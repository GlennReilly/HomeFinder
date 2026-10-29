package au.homefinder.app.map

/** A small least-recently-used cache. Not thread-safe; callers confine it to one thread. */
class LruCache<K : Any, V : Any>(private val maxEntries: Int) {
    private val entries = object : LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > maxEntries
    }

    val size: Int get() = entries.size

    operator fun get(key: K): V? = entries[key]

    operator fun set(key: K, value: V) {
        entries[key] = value
    }

    operator fun contains(key: K): Boolean = key in entries

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

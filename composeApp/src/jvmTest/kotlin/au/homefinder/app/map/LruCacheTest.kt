package au.homefinder.app.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LruCacheTest {
    @Test
    fun `evicts the least recently used entry when full`() {
        val cache = LruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2
        cache["a"] // touch a so b is the oldest
        cache["c"] = 3
        assertEquals(2, cache.size)
        assertNull(cache["b"])
        assertTrue("a" in cache && "c" in cache)
    }

    @Test
    fun `overwriting a key does not grow the cache`() {
        val cache = LruCache<String, Int>(2)
        cache["a"] = 1
        cache["a"] = 2
        assertEquals(1, cache.size)
        assertEquals(2, cache["a"])
    }
}

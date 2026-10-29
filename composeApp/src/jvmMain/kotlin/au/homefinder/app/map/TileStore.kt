package au.homefinder.app.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_PARALLEL_REQUESTS = 2 // OSM tile usage policy: at most two connections

/** Loads tiles on demand into an LRU cache; reading [get] in composition re-draws when a tile arrives. */
class TileStore<T : Any>(
    private val scope: CoroutineScope,
    maxCachedTiles: Int,
    private val fetch: suspend (TileId) -> T?,
) {
    private val cache = LruCache<TileId, T>(maxCachedTiles)
    private val pending = mutableSetOf<TileId>()
    private val gate = Semaphore(MAX_PARALLEL_REQUESTS)
    private var revision by mutableIntStateOf(0)

    fun get(tile: TileId): T? {
        revision // subscribe the caller to cache changes
        return cache[tile]
    }

    fun request(tile: TileId) {
        if (tile in cache || !pending.add(tile)) return
        scope.launch {
            try {
                gate.withPermit { fetch(tile) }?.let {
                    cache[tile] = it
                    revision++
                }
            } finally {
                pending.remove(tile)
            }
        }
    }
}

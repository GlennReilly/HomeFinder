package au.homefinder.shared.model

import kotlinx.serialization.Serializable

enum class SearchResultKind { SUBURB, ADDRESS }

/** A box on the map in GDA2020 degrees: west, south, east, north. */
@Serializable
data class GeoBounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    init {
        require(west <= east && south <= north) { "Bounds must satisfy west <= east and south <= north" }
    }
}

/**
 * One search suggestion. A suburb carries [bounds] to fit the map to; an address carries the point to fly to and, when
 * G-NAF links it to a cadastral lot, the [lotId] to select (null when no lot is known, ADR 0026).
 */
@Serializable
data class SearchResult(
    val kind: SearchResultKind,
    val id: String,
    val label: String,
    val detail: String?,
    val latitude: Double,
    val longitude: Double,
    val bounds: GeoBounds? = null,
    val lotId: String? = null,
)

@Serializable
data class SearchResponse(val results: List<SearchResult>)

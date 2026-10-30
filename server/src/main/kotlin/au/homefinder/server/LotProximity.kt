package au.homefinder.server

import kotlinx.datetime.LocalDate

/** One feature near a lot: the nearest of its kind, or one of the nearest parks (rank 1 is nearest). */
data class NearFeature(val kind: String, val rank: Int, val name: String?, val distanceM: Double)

/**
 * What the OpenStreetMap pipeline (ADR 0023) found around one lot: nearest features, the park area within the pipeline's
 * radius and the date of the OSM data. [osmAsOf] is null only when the import recorded no date.
 */
data class LotProximity(val nearest: List<NearFeature>, val parkAreaM2: Double?, val osmAsOf: LocalDate?) {
    fun of(kind: String): List<NearFeature> = nearest.filter { it.kind == kind }.sortedBy { it.rank }

    companion object {
        const val BEACH: String = "beach"
        const val PARK: String = "park"
        const val SUPERMARKET: String = "supermarket"
        const val MALL: String = "mall"
    }
}

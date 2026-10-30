package au.homefinder.server

import kotlinx.datetime.LocalDate

/**
 * A suburb's roll-up of the OpenStreetMap features (ADR 0033), built by the pipeline: distances in metres in a straight line to
 * the nearest ocean beach (null when none was found), the area of parks of at least 0.5 ha inside the suburb and of the suburb
 * itself, and how many shopping centres and supermarkets are in or within the pipeline's radius of it. [osmAsOf] is the age of
 * the OSM data.
 */
data class SuburbAmenity(
    val beachFromCentreM: Double?,
    val beachFromEdgeM: Double?,
    val beachName: String?,
    val suburbAreaM2: Double,
    val parkAreaM2: Double,
    val shoppingCentres: Int,
    val supermarkets: Int,
    val osmAsOf: LocalDate?,
) {
    /** The share of the suburb that is park, from 0 to 1; null if the suburb has no area. */
    val parkShare: Double? get() = if (suburbAreaM2 > 0) (parkAreaM2 / suburbAreaM2).coerceIn(0.0, 1.0) else null
}

package au.homefinder.server

/** A suburb's counts from the 2021 Census (table G37), as the pipeline stored them (ADR 0044). */
data class SuburbCensus(
    val tenureStated: Int,
    val rented: Int,
    val houses: Int,
    val semis: Int,
    val flats: Int,
    val otherDwellings: Int,
) {
    val structureStated: Int get() = houses + semis + flats + otherDwellings

    /** Rented dwellings as a share of those with a stated tenure, 0 to 1; null when there are none. */
    val rentedShare: Double? get() = if (tenureStated > 0) (rented.toDouble() / tenureStated).coerceIn(0.0, 1.0) else null
}

package au.homefinder.server

import kotlinx.datetime.LocalDate

/** A lot's height above the stream its water reaches, and the size of that stream's catchment, as the pipeline stored them (ADR 0040). */
data class LotDrainage(val heightAboveDrainageM: Double, val streamCatchmentHa: Double)

/** What the drainage load produced: the day it ran, and this lot's figures (null when the lot has no result). */
data class LotDrainageData(
    val drainage: LotDrainage?,
    val computedOn: LocalDate,
    /** The share (0 to 1) of the suburb's lots with a result that read under 1 m above drainage; null when unknown. */
    val suburbUnderOneMetreShare: Double? = null,
    /** How many of the suburb's lots have a result, the base of that share. */
    val suburbLotsWithResult: Int = 0,
)

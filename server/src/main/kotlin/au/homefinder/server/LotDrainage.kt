package au.homefinder.server

import kotlinx.datetime.LocalDate

/** A lot's height above the stream its water reaches, and the size of that stream's catchment, as the pipeline stored them (ADR 0040). */
data class LotDrainage(val heightAboveDrainageM: Double, val streamCatchmentHa: Double)

/** What the drainage load produced: the day it ran, and this lot's figures (null when the lot has no result). */
data class LotDrainageData(val drainage: LotDrainage?, val computedOn: LocalDate)

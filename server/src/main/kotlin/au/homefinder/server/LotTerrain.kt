package au.homefinder.server

import kotlinx.datetime.LocalDate

/** A lot's height and slope as the pipeline stored them (ADR 0036). The slopes are null where the lot sits on the edges of DEM tiles. */
data class LotTerrain(val elevationM: Double, val meanSlopePercent: Double?, val maxSlopePercent: Double?, val pixels: Int)

/** What the DEM load produced: the day it ran, and this lot's figures (null when the lot has no DEM coverage). */
data class LotTerrainData(val terrain: LotTerrain?, val loadedOn: LocalDate)

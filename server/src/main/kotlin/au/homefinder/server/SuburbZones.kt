package au.homefinder.server

/**
 * One school zone's share of a suburb, as the pipeline stored it (ADR 0062): [zone] is the catchment (with the school's record when the school code is
 * in the schools data) and [lotShare] the share of the suburb's lots inside it, from 0 to 1.
 */
data class SuburbZoneShare(val zone: CatchmentZone, val lotShare: Double)

/** A suburb's zone shares, null (not this type) when the suburb catchments have not been loaded; an empty list means loaded but no zone covers 5% of the suburb. */
data class SuburbZoneData(val shares: List<SuburbZoneShare>)

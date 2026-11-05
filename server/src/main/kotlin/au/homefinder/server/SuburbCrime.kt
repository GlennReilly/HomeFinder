package au.homefinder.server

import kotlinx.datetime.LocalDate

/**
 * A suburb's recorded incidents over the latest 24 months of the BOCSAR file, as the pipeline stored them (ADR 0051), with the SEIFA usual resident
 * population to turn them into a rate (null when SEIFA has none for the suburb).
 */
data class SuburbCrime(val violent: Int, val property: Int, val windowText: String, val asOf: LocalDate, val population: Int?)

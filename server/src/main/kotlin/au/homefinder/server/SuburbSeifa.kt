package au.homefinder.server

/** A suburb's SEIFA 2021 socio-economic advantage (IRSAD) as the pipeline stored it (ADR 0049). */
data class SuburbSeifa(val irsadDecile: Int, val irsadScore: Double, val population: Int)

package au.homefinder.server

/** A suburb's SEIFA 2021 socio-economic advantage (IRSAD) as the pipeline stored it (ADR 0049). */
data class SuburbSeifa(
    val irsadDecile: Int,
    val irsadScore: Double,
    val population: Int,
    /** The deciles of the disadvantage, economic resources and education and occupation indexes; null where the ABS gives no score (ADR 0050). */
    val irsdDecile: Int? = null,
    val ierDecile: Int? = null,
    val ieoDecile: Int? = null,
)

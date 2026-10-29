package au.homefinder.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

enum class Confidence { HIGH, MEDIUM, LOW, NONE }

/**
 * A value with its provenance, so the UI can always show source, as-of date and confidence. A null [value] means
 * unknown and must carry [Confidence.NONE]: missing data is never a silent default (spec principle).
 */
@Serializable
data class Attribute<T>(
    val value: T?,
    val confidence: Confidence,
    val source: String,
    val asOf: LocalDate? = null,
    val note: String? = null,
) {
    init {
        require(value != null || confidence == Confidence.NONE) { "An unknown value must have confidence NONE" }
    }

    val isUnknown: Boolean get() = value == null

    companion object {
        fun <T> unknown(source: String, asOf: LocalDate? = null, note: String? = null): Attribute<T> =
            Attribute(null, Confidence.NONE, source, asOf, note)
    }
}

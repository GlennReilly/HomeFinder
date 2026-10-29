package au.homefinder.shared.model

import kotlinx.serialization.Serializable

/** Wall material as far as it can be inferred; "unknown" is a null attribute value, not an enum entry. */
enum class WallMaterial { BRICK, DOUBLE_BRICK, BRICK_VENEER, OTHER }

/** NOT_MAPPED means no flood study covers the lot. It is distinct from LOW and must never be scored as low. */
enum class FloodClass { NOT_MAPPED, LOW, MEDIUM, HIGH }

enum class FallDirection { TO_STREET, TO_REAR, TO_SIDE, FLAT }

/** Lot shape drives how reliable the aspect result is: anything but REGULAR is ambiguous. */
enum class LotShape { REGULAR, CORNER, BATTLE_AXE, IRREGULAR }

enum class SchoolSector { PUBLIC, CATHOLIC, INDEPENDENT }

enum class SchoolLevel { PRIMARY, HIGH, COMBINED }

enum class TransportMode { TRAIN, LIGHT_RAIL, FERRY, BUS }

/** Slope classes by mean slope in percent: flat under 5, gentle 5 to under 10, moderate 10 to 20, steep over 20. */
enum class SlopeClass {
    FLAT, GENTLE, MODERATE, STEEP;

    companion object {
        const val GENTLE_FROM_PERCENT: Double = 5.0
        const val MODERATE_FROM_PERCENT: Double = 10.0
        const val STEEP_ABOVE_PERCENT: Double = 20.0

        fun fromMeanPercent(meanPercent: Double): SlopeClass {
            require(meanPercent >= 0.0) { "Slope cannot be negative" }
            return when {
                meanPercent < GENTLE_FROM_PERCENT -> FLAT
                meanPercent < MODERATE_FROM_PERCENT -> GENTLE
                meanPercent <= STEEP_ABOVE_PERCENT -> MODERATE
                else -> STEEP
            }
        }
    }
}

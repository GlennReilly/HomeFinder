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

/**
 * How far a lot's ground stands above the nearest drainage line (ADR 0040): very close under 1 m, close 1 to under 2 m, some way above
 * 2 to under 5 m, well above from 5 m. The bands follow the council's 10% annual-chance flood corridor in ELERMORE VALE, where 43% of
 * the lots under 1 m touched it, 18% of those from 1 to 2 m, 9% from 2 to 5 m and under 1% above 5 m. This is a hint about where
 * water runs, not a flood result.
 */
enum class DrainageProximity {
    VERY_CLOSE, CLOSE, MODERATE, WELL_ABOVE;

    companion object {
        const val CLOSE_FROM_M: Double = 1.0
        const val MODERATE_FROM_M: Double = 2.0
        const val WELL_ABOVE_FROM_M: Double = 5.0

        fun fromHeightM(heightM: Double): DrainageProximity {
            require(heightM >= 0.0) { "A height above drainage cannot be negative" }
            return when {
                heightM < CLOSE_FROM_M -> VERY_CLOSE
                heightM < MODERATE_FROM_M -> CLOSE
                heightM < WELL_ABOVE_FROM_M -> MODERATE
                else -> WELL_ABOVE
            }
        }
    }
}

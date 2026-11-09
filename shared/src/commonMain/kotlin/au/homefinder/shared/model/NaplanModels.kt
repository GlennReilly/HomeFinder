package au.homefinder.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * One overall NAPLAN figure for a school, typed in by you from My School (ADR 0068): the score, the year it is from and an optional short note.
 * This is what `PUT /v1/schools/{schoolCode}/naplan` takes. Nothing is ever fetched from My School. Check it with [NaplanRules.check].
 */
@Serializable
data class NaplanEntry(val score: Int, val year: Int, val note: String? = null)

/** A NAPLAN figure as kept: what you typed and the day you last saved it. It rides on [School.naplan] in the suburb and lot reports. */
@Serializable
data class NaplanScore(val score: Int, val year: Int, val note: String? = null, val updatedOn: LocalDate? = null) {
    companion object {
        /** My School terms, clause 6.2: the attribution that must go with anything taken from the site. */
        const val ATTRIBUTION: String = "Source: Australian Curriculum, Assessment and Reporting Authority (ACARA)"

        /** What a row's Source line adds when a school on it has a NAPLAN figure. */
        const val SOURCE: String = "NAPLAN typed in by you from My School. $ATTRIBUTION"
    }
}

/** A school's NAPLAN figure, keyed by its school code: one entry of `GET /v1/schools/naplan`. */
@Serializable
data class SchoolNaplan(val schoolCode: String, val naplan: NaplanScore)

@Serializable
data class SchoolNaplanResponse(val entries: List<SchoolNaplan>)

/** Why a NAPLAN entry was refused. */
enum class NaplanProblem { SCORE_OUT_OF_RANGE, YEAR_OUT_OF_RANGE, NOTE_TOO_LONG }

/** What checking an entry came to: the entry as it will be kept (note trimmed, a blank note dropped), or the reason it is refused. */
sealed interface NaplanCheck {
    data class Valid(val entry: NaplanEntry) : NaplanCheck
    data class Invalid(val problem: NaplanProblem) : NaplanCheck
}

/** The limits on a typed-in NAPLAN figure (ADR 0068), shared by the server and the form so both refuse the same things. */
object NaplanRules {
    /** NAPLAN national scale scores run from 0 to 1000. */
    const val MIN_SCORE: Int = 0
    const val MAX_SCORE: Int = 1000

    /** NAPLAN was first sat in 2008, so no figure is older. */
    const val FIRST_YEAR: Int = 2008

    /** A note is a reminder (for example "Year 5 reading"), not a review. */
    const val MAX_NOTE_LENGTH: Int = 200

    /** Checks an entry; a year after [currentYear] is refused, as no such results exist yet. */
    fun check(entry: NaplanEntry, currentYear: Int): NaplanCheck {
        val note = entry.note?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            entry.score !in MIN_SCORE..MAX_SCORE -> NaplanCheck.Invalid(NaplanProblem.SCORE_OUT_OF_RANGE)
            entry.year !in FIRST_YEAR..currentYear -> NaplanCheck.Invalid(NaplanProblem.YEAR_OUT_OF_RANGE)
            note != null && note.length > MAX_NOTE_LENGTH -> NaplanCheck.Invalid(NaplanProblem.NOTE_TOO_LONG)
            else -> NaplanCheck.Valid(entry.copy(note = note))
        }
    }

    /** "NAPLAN 512 (2025)": how a figure reads beside a school's ICSEA. */
    fun label(naplan: NaplanScore): String = "NAPLAN ${naplan.score} (${naplan.year})"
}

package au.homefinder.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * One NAPLAN figure for a school, typed in by you (ADR 0068, 0069): [score] is the school's difference from the national average in points, above (+)
 * or below (-), for example -41; then, if you give it, the year it is from (ADR 0071). There is no note any more (ADR 0071). This is what `PUT /v1/schools/{schoolCode}/naplan` takes.
 * Nothing is ever fetched. Check it with [NaplanRules.check].
 */
@Serializable
data class NaplanEntry(val score: Int, val year: Int? = null)

/** A NAPLAN figure as kept: what you typed and the day you last saved it. It rides on [School.naplan] in the suburb and lot reports. */
@Serializable
data class NaplanScore(val score: Int, val year: Int? = null, val updatedOn: LocalDate? = null) {
    companion object {
        /**
         * What a row's Source line adds when a school on it has a NAPLAN figure. Neutral on purpose (ADR 0069): the figure may be read from another
         * site, so the app does not say it came from My School or ACARA.
         */
        const val SOURCE: String = "Your own figure, typed in. NAPLAN results are published by ACARA on My School."
    }
}

/** A school's NAPLAN figure, keyed by its school code: one entry of `GET /v1/schools/naplan`. */
@Serializable
data class SchoolNaplan(val schoolCode: String, val naplan: NaplanScore)

@Serializable
data class SchoolNaplanResponse(val entries: List<SchoolNaplan>)

/** Why a NAPLAN entry was refused. */
enum class NaplanProblem { SCORE_OUT_OF_RANGE, YEAR_OUT_OF_RANGE }

/** What checking an entry came to: the entry as it will be kept, or the reason it is refused. */
sealed interface NaplanCheck {
    data class Valid(val entry: NaplanEntry) : NaplanCheck
    data class Invalid(val problem: NaplanProblem) : NaplanCheck
}

/** The limits on a typed-in NAPLAN figure (ADR 0068), shared by the server and the form so both refuse the same things. */
object NaplanRules {
    /** The difference from the national average, in points: a loose sanity bound either side of it (ADR 0069). */
    const val MIN_SCORE: Int = -1000
    const val MAX_SCORE: Int = 1000

    /** NAPLAN was first sat in 2008, so no figure is older. */
    const val FIRST_YEAR: Int = 2008

    /** Checks an entry. The year may be missing (ADR 0071); a given year after [currentYear] is refused, as no such results exist yet. */
    fun check(entry: NaplanEntry, currentYear: Int): NaplanCheck {
        return when {
            entry.score !in MIN_SCORE..MAX_SCORE -> NaplanCheck.Invalid(NaplanProblem.SCORE_OUT_OF_RANGE)
            entry.year != null && entry.year !in FIRST_YEAR..currentYear -> NaplanCheck.Invalid(NaplanProblem.YEAR_OUT_OF_RANGE)
            else -> NaplanCheck.Valid(entry)
        }
    }

    /** "NAPLAN -41 vs national (2025)": how a figure reads beside a school's ICSEA. */
    fun label(naplan: NaplanScore): String = "NAPLAN ${signed(naplan.score)} vs national" + naplan.year?.let { " ($it)" }.orEmpty()

    /** The difference with its sign always shown: "-41", "+25", and "0" for no difference. */
    fun signed(points: Int): String = if (points > 0) "+$points" else points.toString()
}

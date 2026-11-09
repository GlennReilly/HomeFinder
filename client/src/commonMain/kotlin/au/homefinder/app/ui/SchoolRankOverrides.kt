package au.homefinder.app.ui

/**
 * SchoolRank addresses we know are right where the generated one (name + locality + nsw, ADR 0070) is wrong (ADR 0075).
 *
 * To add a school: take its code (the `School_code` column of data/raw/schools/master_dataset.csv, also `School.id` on the cards), open the school's
 * page on schoolrank.com.au in a browser, and copy the last part of the working address (everything after `https://schoolrank.com.au/school/`)
 * into [SLUGS] as `"<school code>" to "<that slug>"`, with a comment naming the school and why. The slug is used exactly as written. Nothing else is needed.
 */
object SchoolRankOverrides {
    /** School code to the full SchoolRank slug that works. */
    private val SLUGS: Map<String, String> = mapOf(
        // Kotara South Public School: our data says locality "Kotara South", SchoolRank uses "Kotara".
        "4203" to "kotara-south-public-school-kotara-nsw",
    )

    /** The known-good slug for the school with this code, or null when it has no override. */
    fun slugFor(schoolCode: String?): String? = schoolCode?.trim()?.let { SLUGS[it] }
}

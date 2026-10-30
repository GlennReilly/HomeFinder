package au.homefinder.app.ui

/**
 * Links from a card to other sites. They only point at a page; nothing from the page is fetched or copied (Homely's terms
 * forbid automated retrieval and your spec says never to scrape listing portals, ADR 0027).
 */
object ExternalLinks {
    private const val HOMELY_SUBURB_PROFILE = "https://www.homely.com.au/suburb-profile/"
    private val NOT_URL_WORD = Regex("[^a-z0-9]+")

    /**
     * Homely's profile page for a NSW suburb, written the way Homely writes it (`jesmond-nsw-2299`), or null when the
     * suburb has no postcode or no usable name.
     */
    fun homelySuburbProfile(suburbName: String, postcode: String?): String? {
        // An apostrophe is dropped ("George's" is "georges"); any other punctuation separates words.
        val words = suburbName.lowercase().replace("'", "").replace("\u2019", "").split(NOT_URL_WORD).filter { it.isNotEmpty() }
        val code = postcode?.trim()?.takeIf { it.length == POSTCODE_LENGTH && it.all(Char::isDigit) }
        if (words.isEmpty() || code == null) return null
        return HOMELY_SUBURB_PROFILE + words.joinToString("-") + "-nsw-" + code
    }

    /** My School's home page: a deep link to one school cannot be built from the data, so the card points here and names the schools. */
    const val MY_SCHOOL_HOME: String = "https://www.myschool.edu.au/"

    /** An address that may be linked from a card: http or https only, so a link can never run a script. Null otherwise. */
    fun webAddress(address: String?): String? = address?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    private const val POSTCODE_LENGTH = 4
}

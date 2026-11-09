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
        val words = words(suburbName)
        val code = validPostcode(postcode)
        if (words.isEmpty() || code == null) return null
        return HOMELY_SUBURB_PROFILE + words.joinToString("-") + "-nsw-" + code
    }

    /** One link out to a listing site: the site's name (the link text) and where it goes. */
    data class ListingLink(val site: String, val url: String)

    enum class ListingKind { HOUSES, UNITS }

    const val REALESTATE_SITE: String = "realestate.com.au"
    const val DOMAIN_SITE: String = "domain.com.au"
    private const val REALESTATE = "https://www.realestate.com.au/"
    private const val DOMAIN = "https://www.domain.com.au/"
    private const val STATE = "nsw"

    /** Words that only say what kind of dwelling a street address names ("UNIT 3, 12 MAIN ST"); the portals' address pages leave them out. */
    private val DWELLING_WORDS = setOf("unit", "flat", "apt", "apartment", "suite", "shop", "level", "lot")

    /** The street type as realestate.com.au writes it in an address page. */
    private val STREET_TYPE_ABBREVIATIONS = mapOf(
        "street" to "st", "road" to "rd", "avenue" to "ave", "drive" to "dr", "place" to "pl", "court" to "ct", "crescent" to "cres",
        "lane" to "la", "boulevard" to "bvd", "parade" to "pde", "terrace" to "tce", "close" to "cl", "highway" to "hwy",
        "circuit" to "cct", "grove" to "gr", "square" to "sq",
    )

    private fun words(text: String): List<String> =
        text.lowercase().replace("'", "").replace("\u2019", "").split(NOT_URL_WORD).filter { it.isNotEmpty() }

    private fun validPostcode(postcode: String?): String? = postcode?.trim()?.takeIf { it.length == POSTCODE_LENGTH && it.all(Char::isDigit) }

    /**
     * Where houses or units for sale in the suburb are listed on realestate.com.au and Domain (ADR 0061), or nothing when the suburb has no usable
     * name or postcode. The pages are only linked to, never fetched (ADR 0027), so whether they have listings is for the visitor to see.
     */
    fun forSaleLinks(kind: ListingKind, suburbName: String, postcode: String?): List<ListingLink> {
        val name = words(suburbName)
        val code = validPostcode(postcode)
        if (name.isEmpty() || code == null) return emptyList()
        val reaType = if (kind == ListingKind.HOUSES) "property-house" else "property-unit+apartment"
        val domainType = if (kind == ListingKind.HOUSES) "house" else "apartment-unit-flat"
        return listOf(
            ListingLink(REALESTATE_SITE, "${REALESTATE}buy/$reaType-in-${name.joinToString("+")},+$STATE+$code/list-1"),
            ListingLink(DOMAIN_SITE, "${DOMAIN}sale/${name.joinToString("-")}-$STATE-$code/$domainType/"),
        )
    }

    /**
     * The address's own page on realestate.com.au and Domain, or nothing when there is no address, suburb name or postcode. The street part is
     * everything before the last comma of "12 MAIN STREET, HAMILTON NSW 2303"; dwelling words ("UNIT") are dropped and a unit's "3/12" becomes "3-12".
     */
    fun addressLinks(address: String?, suburbName: String, postcode: String?): List<ListingLink> {
        val street = words(address?.substringBeforeLast(",").orEmpty()).filterNot { it in DWELLING_WORDS }
        val place = words(suburbName)
        val code = validPostcode(postcode)
        if (street.isEmpty() || place.isEmpty() || code == null) return emptyList()
        val tail = place.joinToString("-") + "-$STATE-$code"
        val reaStreet = street.dropLast(1) + street.last().let { STREET_TYPE_ABBREVIATIONS[it] ?: it }
        return listOf(
            ListingLink(REALESTATE_SITE, "${REALESTATE}property/${reaStreet.joinToString("-")}-$tail/"),
            ListingLink(DOMAIN_SITE, "${DOMAIN}property-profile/${street.joinToString("-")}-$tail"),
        )
    }

    /** My School's home page: a deep link to one school cannot be built from the data, so the card points here and names the schools. */
    const val MY_SCHOOL_HOME: String = "https://www.myschool.edu.au/"

    /** SchoolRank's site and the path its school pages sit under (ADR 0070). */
    const val SCHOOLRANK_SITE: String = "SchoolRank"
    const val SCHOOLRANK_HOST: String = "schoolrank.com.au"
    private const val SCHOOLRANK_SCHOOL_PATH = "/school/"
    private const val HTTPS = "https://"
    private const val AMPERSAND = "&"
    private const val AND_WORD = " and "
    private const val SLUG_SEPARATOR = "-"

    /**
     * The slug SchoolRank appears to use for a school (ADR 0070): the school's name, then the locality in its address (the Department's "Town_suburb",
     * which is not always the suburb in the name), then "nsw", all lowercase, apostrophes dropped, any other run of punctuation or spaces one hyphen.
     * "Elermore Vale Public School" in Wallsend is `elermore-vale-public-school-wallsend-nsw`, the one example we have. "&" is written "and", which is a
     * guess. Null when the name or the locality gives no words, so no link is drawn.
     */
    fun schoolRankSlug(schoolName: String, locality: String?): String? {
        val name = words(schoolName.replace(AMPERSAND, AND_WORD))
        val place = words(locality.orEmpty().replace(AMPERSAND, AND_WORD))
        if (name.isEmpty() || place.isEmpty()) return null
        return (name + place + STATE).joinToString(SLUG_SEPARATOR)
    }

    /** The school's page on SchoolRank as we build it; never fetched or checked, so it may not exist (ADR 0070). */
    fun schoolRankPage(schoolName: String, locality: String?): String? =
        schoolRankSlug(schoolName, locality)?.let { HTTPS + SCHOOLRANK_HOST + SCHOOLRANK_SCHOOL_PATH + it }

    /** An address that may be linked from a card: http or https only, so a link can never run a script. Null otherwise. */
    fun webAddress(address: String?): String? = address?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    private const val POSTCODE_LENGTH = 4
}

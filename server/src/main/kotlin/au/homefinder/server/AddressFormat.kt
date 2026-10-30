package au.homefinder.server

/** Formats the pieces of a G-NAF address the way Australian post writes them: `3/12 MAIN STREET, HAMILTON NSW 2303`. */
object AddressFormat {
    fun format(
        flatNumber: String?,
        numberFirst: String?,
        streetName: String?,
        streetType: String?,
        locality: String,
        state: String,
        postcode: String?,
    ): String {
        val number = listOfNotNull(flatNumber.clean(), numberFirst.clean()).joinToString("/")
        val street = listOfNotNull(streetName.clean(), streetType.clean()).joinToString(" ")
        val line = listOf(number, street).filter { it.isNotEmpty() }.joinToString(" ")
        val place = listOfNotNull(locality.clean(), state.clean(), postcode.clean()).joinToString(" ")
        return listOf(line, place).filter { it.isNotEmpty() }.joinToString(", ")
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

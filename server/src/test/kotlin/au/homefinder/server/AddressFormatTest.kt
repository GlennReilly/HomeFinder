package au.homefinder.server

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressFormatTest {
    @Test
    fun `a house address reads number, street, locality, state and postcode`() {
        assertEquals("12 MAIN STREET, HAMILTON NSW 2303", AddressFormat.format(null, "12", "MAIN", "STREET", "HAMILTON", "NSW", "2303"))
    }

    @Test
    fun `a unit address puts the flat first`() {
        assertEquals("3/12 MAIN STREET, HAMILTON NSW 2303", AddressFormat.format("3", "12", "MAIN", "STREET", "HAMILTON", "NSW", "2303"))
    }

    @Test
    fun `missing and blank parts are left out without stray punctuation`() {
        assertEquals("MAIN, HAMILTON NSW", AddressFormat.format(" ", "", "MAIN", null, "HAMILTON", "NSW", null))
        assertEquals("HAMILTON NSW 2303", AddressFormat.format(null, null, null, null, "HAMILTON", "NSW", "2303"))
    }
}

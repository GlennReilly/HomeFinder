package au.homefinder.shared.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttributeTest {
    private val known = Attribute(612.5, Confidence.HIGH, "NSW Digital Cadastral Database", SampleReports.asOf, "strata lot")

    @Test
    fun `a known attribute round trips`() {
        val json = ApiJson.encodeToString(known)
        assertEquals(known, ApiJson.decodeFromString<Attribute<Double>>(json))
    }

    @Test
    fun `the as-of date is written as an ISO date`() {
        assertTrue(ApiJson.encodeToString(known).contains("\"asOf\":\"2026-10-29\""))
    }

    @Test
    fun `an unknown attribute is explicit on the wire and round trips`() {
        val unknown = Attribute.unknown<Double>("OpenStreetMap", note = "no data")
        val json = ApiJson.encodeToString(unknown)
        assertTrue(json.contains("\"value\":null"), json)
        assertTrue(json.contains("\"confidence\":\"NONE\""), json)
        val back = ApiJson.decodeFromString<Attribute<Double>>(json)
        assertTrue(back.isUnknown)
        assertEquals(unknown, back)
    }

    @Test
    fun `missing note and date are written as null, not omitted`() {
        val json = ApiJson.encodeToString(Attribute(1.0, Confidence.HIGH, "src"))
        assertTrue(json.contains("\"asOf\":null") && json.contains("\"note\":null"), json)
    }

    @Test
    fun `an unknown value cannot claim confidence`() {
        assertFailsWith<IllegalArgumentException> { Attribute<Double>(null, Confidence.HIGH, "src") }
    }

    @Test
    fun `decoding an unknown value with confidence is rejected`() {
        val bad = """{"value":null,"confidence":"HIGH","source":"src","asOf":null,"note":null}"""
        assertFailsWith<Exception> { ApiJson.decodeFromString<Attribute<Double>>(bad) }
    }

    @Test
    fun `a known value may have any confidence including NONE`() {
        assertFalse(Attribute(1.0, Confidence.NONE, "src").isUnknown)
    }

    @Test
    fun `fields the reader does not know are ignored`() {
        val json = """{"value":1.5,"confidence":"LOW","source":"src","asOf":null,"note":null,"newField":42}"""
        assertEquals(1.5, ApiJson.decodeFromString<Attribute<Double>>(json).value)
    }

    @Test
    fun `an unknown confidence name is a decoding error`() {
        val json = """{"value":1.5,"confidence":"CERTAIN","source":"src"}"""
        assertFailsWith<SerializationException> { ApiJson.decodeFromString<Attribute<Double>>(json) }
    }
}

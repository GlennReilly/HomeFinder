package au.homefinder.shared.model

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportsRoundTripTest {
    @Test
    fun `a full property report round trips`() {
        val json = ApiJson.encodeToString(SampleReports.fullProperty)
        assertEquals(SampleReports.fullProperty, ApiJson.decodeFromString<PropertyReport>(json))
    }

    @Test
    fun `a property report with every attribute unknown round trips and stays unknown`() {
        val json = ApiJson.encodeToString(SampleReports.unknownProperty)
        val back = ApiJson.decodeFromString<PropertyReport>(json)
        assertEquals(SampleReports.unknownProperty, back)
        assertTrue(listOf(back.wallMaterial.isUnknown, back.flood.isUnknown, back.slope.isUnknown, back.estimatedPriceAud.isUnknown).all { it })
    }

    @Test
    fun `a full suburb report round trips`() {
        val json = ApiJson.encodeToString(SampleReports.fullSuburb)
        assertEquals(SampleReports.fullSuburb, ApiJson.decodeFromString<SuburbReport>(json))
    }

    @Test
    fun `a suburb report with every statistic unknown and no postcode round trips`() {
        val json = ApiJson.encodeToString(SampleReports.unknownSuburb)
        assertEquals(SampleReports.unknownSuburb, ApiJson.decodeFromString<SuburbReport>(json))
        assertTrue(json.contains("\"postcode\":null"), json)
    }

    @Test
    fun `flood not mapped is a known result, distinct from an unknown flood attribute`() {
        val notMapped = Attribute(FloodRisk(FloodClass.NOT_MAPPED), Confidence.MEDIUM, "Council flood study", SampleReports.asOf)
        val unknown = Attribute.unknown<FloodRisk>("Council flood study")
        val notMappedJson = ApiJson.encodeToString(notMapped)
        val unknownJson = ApiJson.encodeToString(unknown)
        assertTrue(notMappedJson.contains("NOT_MAPPED"))
        assertFalse(unknownJson.contains("NOT_MAPPED"))
        assertFalse(ApiJson.decodeFromString<Attribute<FloodRisk>>(notMappedJson).isUnknown)
        assertTrue(ApiJson.decodeFromString<Attribute<FloodRisk>>(unknownJson).isUnknown)
    }

    @Test
    fun `enum values use their names on the wire`() {
        val json = ApiJson.encodeToString(SampleReports.fullProperty)
        listOf("BRICK_VENEER", "CORNER", "HIGH", "GENTLE", "TO_REAR", "TRAIN", "PUBLIC", "PRIMARY").forEach {
            assertTrue(json.contains("\"$it\""), "missing $it in $json")
        }
    }

    @Test
    fun `computed properties are not part of the wire format`() {
        val json = ApiJson.encodeToString(SampleReports.fullProperty)
        assertFalse(json.contains("sampleSize") || json.contains("isAmbiguous") || json.contains("isUnknown"), json)
    }

    @Test
    fun `a report from a newer server with extra fields still decodes`() {
        val json = ApiJson.encodeToString(SampleReports.fullSuburb).replaceFirst("{", "{\"futureField\":{\"a\":1},")
        assertEquals(SampleReports.fullSuburb, ApiJson.decodeFromString<SuburbReport>(json))
    }
}

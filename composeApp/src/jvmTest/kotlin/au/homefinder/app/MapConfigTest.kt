package au.homefinder.app

import au.homefinder.app.map.MapConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** JVM-side checks of the pinned basemap choice (ADR 0008); camera and https checks live in commonTest. */
class MapConfigTest {
    @Test
    fun `pilot map uses the OpenFreeMap liberty style`() {
        assertEquals("https://tiles.openfreemap.org/styles/liberty", MapConfig.Pilot.styleUrl)
    }

    @Test
    fun `attribution names both data licences`() {
        assertContains(MapConfig.Pilot.attribution, "OpenMapTiles")
        assertContains(MapConfig.Pilot.attribution, "OpenStreetMap")
    }
}

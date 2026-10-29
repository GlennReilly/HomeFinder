package au.homefinder.app.map

import kotlin.test.Test
import kotlin.test.assertTrue

class MapConfigTest {
    private val pilot = MapConfig.Pilot

    @Test
    fun `pilot camera is centred on Newcastle and Lake Macquarie`() {
        assertTrue(pilot.initialCamera.latitude in -33.2..-32.8)
        assertTrue(pilot.initialCamera.longitude in 151.4..151.9)
    }

    @Test
    fun `pilot zoom is a suburb level zoom`() {
        assertTrue(pilot.initialCamera.zoom in 5.0..12.0)
    }

    @Test
    fun `attribution credits OpenStreetMap as the licence requires`() {
        assertTrue(pilot.attribution.contains("OpenStreetMap"))
        assertTrue(pilot.attribution.contains("OpenMapTiles"))
    }

    @Test
    fun `style is served over https`() {
        assertTrue(pilot.styleUrl.startsWith("https://"))
    }
}

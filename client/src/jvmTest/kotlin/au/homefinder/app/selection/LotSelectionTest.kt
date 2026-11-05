package au.homefinder.app.selection

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.HttpGet
import au.homefinder.app.api.HttpResult
import au.homefinder.app.api.LotApi
import au.homefinder.app.api.SuburbApi
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.DrainageHint
import au.homefinder.shared.model.DrainageProximity
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolInfo
import au.homefinder.shared.model.School
import au.homefinder.shared.model.NearbySchool
import au.homefinder.shared.model.ParkAccess
import au.homefinder.shared.model.Proximity
import au.homefinder.shared.model.ProximityList
import au.homefinder.shared.model.SlopeClass
import au.homefinder.shared.model.SlopeSummary
import au.homefinder.shared.model.SuburbReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LotSelectionTest {
    private fun <T> u() = Attribute.unknown<T>("src")

    private fun report(id: String, address: String) = LotReport(
        lotId = id, parcelLabel = "1//DP100", suburbId = "9001", suburbName = "HAMILTON", postcode = "2303", addresses = listOf(address),
        blockAreaM2 = Attribute(561.4, Confidence.HIGH, "cadastre"), wallMaterial = u(), backyardAspect = u(), flood = u(), slope = u(),
        estimatedPriceAud = u(), beach = u(), schools = u(), parks = u(), shopping = u(), transport = u(),
    )

    private val suburbJson = """{"suburbId":"9001","name":"HAMILTON","postcode":"2303"}"""

    private val suburbReportJson = ApiJson.encodeToString(
        SuburbReport(
            "9001", "HAMILTON", "2303", Attribute(1.0, Confidence.HIGH, "src"), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(),
        ),
    )

    private class FakeHttp(val handler: suspend (String) -> HttpResult) : HttpGet {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult = handler(url).also { urls += url }
    }

    private val config = ApiConfig("http://host:8080", "secret-key-123456")

    private val happy = FakeHttp { url ->
        when {
            url.contains("/lots/at") && url.contains("lat=-32.92") -> HttpResult.Response(200, """{"lotId":"1001","suburbId":"9001"}""")
            url.contains("/lots/at") -> HttpResult.Response(404, "")
            url.endsWith("/lots/1001") -> HttpResult.Response(200, ApiJson.encodeToString(report("1001", "12 MAIN STREET, HAMILTON NSW 2303")))
            url.contains("/suburbs/at") -> HttpResult.Response(200, suburbJson)
            url.endsWith("/suburbs/9001") -> HttpResult.Response(200, suburbReportJson)
            else -> HttpResult.Response(404, "")
        }
    }

    private fun viewModel(scope: TestScope, http: FakeHttp = happy, withLots: Boolean = true) =
        MapViewModel(SuburbApi(config, http), scope, if (withLots) LotApi(config, http) else null)

    @Test
    fun `a street-level tap selects the lot and loads its card`() = runTest {
        val vm = viewModel(this)
        vm.onMapTap(-32.92, 151.75, zoom = LOT_SELECT_MIN_ZOOM); advanceUntilIdle()
        val state = vm.state.value
        assertEquals("1001", state.selectedLotId)
        assertEquals(null, state.selectedSuburbId)
        assertIs<SuburbCardState.Hidden>(state.card)
        assertEquals("12 MAIN STREET, HAMILTON NSW 2303", assertIs<LotCardState.Loaded>(state.lotCard).report.addresses.single())
        assertTrue(happy.urls.none { "/suburbs" in it }, "a street-level tap must not look up a suburb")
    }

    @Test
    fun `below the lot zoom a tap still selects a suburb and asks for no lot`() = runTest {
        val http = FakeHttp { url ->
            when {
                "/suburbs/at" in url -> HttpResult.Response(200, suburbJson)
                url.endsWith("/suburbs/9001") -> HttpResult.Response(200, suburbReportJson)
                else -> HttpResult.Response(404, "")
            }
        }
        val vm = viewModel(this, http)
        vm.onMapTap(-32.92, 151.75, zoom = LOT_SELECT_MIN_ZOOM - 0.01); advanceUntilIdle()
        assertEquals("9001", vm.state.value.selectedSuburbId)
        assertTrue(http.urls.none { "/lots" in it })
    }

    @Test
    fun `selecting a lot clears the suburb and selecting a suburb clears the lot`() = runTest {
        val vm = viewModel(this)
        vm.onMapTap(-32.0, 151.0, zoom = 10.0); advanceUntilIdle()
        assertEquals("9001", vm.state.value.selectedSuburbId)
        vm.onMapTap(-32.92, 151.75, zoom = 17.0); advanceUntilIdle()
        assertEquals(null, vm.state.value.selectedSuburbId)
        assertEquals("1001", vm.state.value.selectedLotId)
        vm.onMapTap(-32.0, 151.0, zoom = 10.0); advanceUntilIdle()
        assertEquals(null, vm.state.value.selectedLotId)
        assertIs<LotCardState.Hidden>(vm.state.value.lotCard)
        assertEquals("9001", vm.state.value.selectedSuburbId)
    }

    @Test
    fun `a tap on no lot clears the selection and clear closes a lot`() = runTest {
        val vm = viewModel(this)
        vm.onMapTap(-32.92, 151.75, zoom = 17.0); advanceUntilIdle()
        vm.onMapTap(-34.0, 150.0, zoom = 17.0); advanceUntilIdle()
        assertEquals(MapUiState(), vm.state.value)
        vm.onMapTap(-32.92, 151.75, zoom = 17.0); advanceUntilIdle()
        vm.clear()
        assertEquals(MapUiState(), vm.state.value)
    }

    @Test
    fun `without a lot API a street-level tap does nothing`() = runTest {
        val vm = viewModel(this, withLots = false)
        vm.onMapTap(-32.92, 151.75, zoom = 18.0); advanceUntilIdle()
        assertEquals(MapUiState(), vm.state.value)
        assertTrue(happy.urls.isEmpty())
    }

    @Test
    fun `a failing lot report shows a failed card that retry can recover`() = runTest {
        var broken = true
        val http = FakeHttp { url ->
            when {
                url.contains("/lots/at") -> HttpResult.Response(200, """{"lotId":"1001","suburbId":"9001"}""")
                url.endsWith("/lots/1001") && broken -> HttpResult.Response(500, "")
                url.endsWith("/lots/1001") -> HttpResult.Response(200, ApiJson.encodeToString(report("1001", "12 MAIN STREET, HAMILTON NSW 2303")))
                else -> HttpResult.Response(404, "")
            }
        }
        val vm = viewModel(this, http)
        vm.onMapTap(-32.92, 151.75, zoom = 17.0); advanceUntilIdle()
        assertIs<LotCardState.Failed>(vm.state.value.lotCard)
        assertEquals("1001", vm.state.value.selectedLotId)
        broken = false
        vm.retry(); advanceUntilIdle()
        assertIs<LotCardState.Loaded>(vm.state.value.lotCard)
    }

    @Test
    fun `a rejected key or an unreachable API is reported without losing the selection`() = runTest {
        val vm = viewModel(this)
        vm.onMapTap(-32.92, 151.75, zoom = 17.0); advanceUntilIdle()
        val down = FakeHttp { HttpResult.Failure("offline") }
        val vm2 = viewModel(this, down)
        vm2.onMapTap(-32.92, 151.75, zoom = 17.0); advanceUntilIdle()
        assertEquals(MapViewModel.NOTICE_UNREACHABLE, vm2.state.value.notice)
        assertEquals(null, vm2.state.value.selectedLotId)
        val rejected = viewModel(this, FakeHttp { HttpResult.Response(401, "") })
        rejected.onMapTap(-32.92, 151.75, zoom = 17.0); advanceUntilIdle()
        assertEquals(MapViewModel.NOTICE_KEY_REJECTED, rejected.state.value.notice)
        assertEquals("1001", vm.state.value.selectedLotId)
    }

    @Test
    fun `the lot rows start with the measured block size and list every other factor`() {
        val rows = LotCardRows.of(report("1001", "12 MAIN STREET, HAMILTON NSW 2303"))
        assertEquals("Block size", rows.first().label)
        assertEquals("561 m²", rows.first().value)
        assertEquals(10, rows.size)
        assertEquals(listOf("Block size", "Backyard aspect", "Slope", "Height above sea level", "Height above drainage"), rows.take(5).map { it.label })
        assertTrue(rows.none { it.label in setOf("Wall material", "Flood", "Estimated price") }, "rows with no reliable source are not shown")
        assertTrue(rows.drop(1).all { it.isUnknown && it.confidence == Confidence.NONE })
        assertTrue(rows.drop(1).filter { it.label != "Height above sea level" && it.label != "Height above drainage" }.all { it.source == "src" })
    }

    @Test
    fun `height and slope read as metres and a class with the figures behind it`() {
        val base = report("1001", "12 MAIN STREET, HAMILTON NSW 2303")
        val rows = LotCardRows.of(
            base.copy(
                slope = Attribute(SlopeSummary(12.04, 21.3, SlopeClass.MODERATE), Confidence.MEDIUM, "DEM"),
                elevationM = Attribute(35.04, Confidence.MEDIUM, "DEM"),
            ),
        ).associateBy { it.label }
        assertEquals("Moderate, 12.0% on average (steepest 21.3%)", rows.getValue("Slope").value)
        assertEquals("35.0 m", rows.getValue("Height above sea level").value)
    }

    @Test
    fun `height above drainage reads as a class first, then the height and the size of the stream`() {
        val base = report("1001", "12 MAIN STREET, HAMILTON NSW 2303")
        fun row(height: Double, hectares: Double) = LotCardRows.of(
            base.copy(heightAboveDrainage = Attribute(DrainageHint(height, hectares, DrainageProximity.fromHeightM(height)), Confidence.LOW, "DEM")),
        ).associateBy { it.label }.getValue("Height above drainage")

        assertEquals("Very close to a drainage line, 0.4 m above a stream draining 21 ha", row(0.4, 20.9).value)
        assertEquals("Close to a drainage line, 1.5 m above a stream draining 5.2 ha", row(1.5, 5.2).value)
        assertEquals("Some way above a drainage line, 3.0 m above a stream draining 1,358 ha", row(3.0, 1357.9).value)
        assertEquals("Well above any drainage line, 12.3 m above a stream draining 136 ha", row(12.3, 135.9).value)
        assertEquals(Confidence.LOW, row(0.4, 20.9).confidence)
        assertFalse("flood" in row(0.4, 20.9).value.orEmpty().lowercase(), "this is where water runs, never a flood claim")
    }

    @Test
    fun `where the suburb is too flat the drainage row says it is not suitable, without figures or a confidence`() {
        val base = report("1001", "12 MAIN STREET, HAMILTON NSW 2303")
        val row = LotCardRows.of(
            base.copy(heightAboveDrainage = Attribute(DrainageHint(0.4, 20.9, DrainageProximity.FLAT_AREA), Confidence.NONE, "DEM", note = "n")),
        ).associateBy { it.label }.getValue("Height above drainage")
        assertEquals("Not suitable for flat areas", row.value)
        assertEquals(Confidence.NONE, row.confidence)
    }

    @Test
    fun `schools read as the nearest primary and high school with distance, estimated walk and ICSEA where published`() {
        val base = report("1001", "12 MAIN STREET, HAMILTON NSW 2303")
        fun row(vararg near: NearbySchool) = LotCardRows.of(
            base.copy(schools = Attribute(SchoolInfo(nearest = near.toList()), Confidence.MEDIUM, "Dept")),
        ).associateBy { it.label }.getValue("Schools").value
        val primary = NearbySchool(School("1", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, icsea = 1051, levelLabel = "Primary School"), 600, walkingMinutes = 10)
        val high = NearbySchool(School("2", "Newcastle High", SchoolSector.PUBLIC, SchoolLevel.HIGH, levelLabel = "Secondary School"), 1_400, walkingMinutes = 23)
        assertEquals(
            "Hamilton Public School (Primary School), 600 m away, about 10 min walk (estimated), ICSEA 1,051; " +
                "Newcastle High (Secondary School), 1.4 km away, about 23 min walk (estimated)",
            row(primary, high),
        )
        val far = NearbySchool(School("3", "Far", SchoolSector.PUBLIC, SchoolLevel.COMBINED), 9_000, walkingMinutes = 146)
        assertEquals("Far, 9.0 km away", row(far), "a walk over an hour is not offered, and a school with no kind or ICSEA says neither")
        assertEquals("No public school found nearby", row())
    }

    @Test
    fun `zoned schools come first, with the kind and years of the zone, and a nearest school is labelled as not zoned`() {
        val base = report("1001", "12 MAIN STREET, HAMILTON NSW 2303")
        fun row(info: SchoolInfo) = LotCardRows.of(base.copy(schools = Attribute(info, Confidence.MEDIUM, "Dept"))).associateBy { it.label }.getValue("Schools").value
        val primary = School("1", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, icsea = 1051, levelLabel = "primary, K-6")
        val high = School("2", "Newcastle High School", SchoolSector.PUBLIC, SchoolLevel.HIGH, levelLabel = "high, 7-12")
        assertEquals(
            "Zoned: Hamilton Public School (primary, K-6), ICSEA 1,051, Newcastle High School (high, 7-12)",
            row(SchoolInfo(zoned = listOf(primary, high), zonesChecked = true)),
        )
        val nearHigh = NearbySchool(School("3", "Near High", SchoolSector.PUBLIC, SchoolLevel.HIGH, levelLabel = "Secondary School"), 900, walkingMinutes = 15)
        assertEquals(
            "Zoned: Hamilton Public School (primary, K-6), ICSEA 1,051; Nearest, not zoned: Near High (Secondary School), 900 m away, about 15 min walk (estimated)",
            row(SchoolInfo(zoned = listOf(primary), nearest = listOf(nearHigh), zonesChecked = true)),
        )
        assertEquals(
            "Nearest, not zoned: Near High (Secondary School), 900 m away, about 15 min walk (estimated)",
            row(SchoolInfo(nearest = listOf(nearHigh), zonesChecked = true)),
        )
        assertEquals("No public school found nearby", row(SchoolInfo(zonesChecked = true)))
    }

    @Test
    fun `proximity factors read as distance, estimated walk and park area`() {
        val base = report("1001", "12 MAIN STREET, HAMILTON NSW 2303")
        val withProximity = base.copy(
            beach = Attribute(Proximity("Ocean Beach", "ocean beach", 1794, walkingMinutes = 29), Confidence.MEDIUM, "OSM"),
            parks = Attribute(
                ParkAccess(listOf(Proximity("Big Park", "park", 187, walkingMinutes = 3), Proximity(null, "park", 374, walkingMinutes = 6)), 50_170.0),
                Confidence.MEDIUM, "OSM",
            ),
            shopping = Attribute(
                ProximityList(listOf(Proximity("Near Supermarket", "supermarket", 1112, walkingMinutes = 18), Proximity("Hamilton Mall", "shopping centre", 561))),
                Confidence.MEDIUM, "OSM",
            ),
        )
        val rows = LotCardRows.of(withProximity).associateBy { it.label }
        assertEquals("Ocean Beach, 1.8 km away, about 29 min walk (estimated)", rows.getValue("Distance to the beach").value)
        assertEquals(
            "Big Park, 187 m away, about 3 min walk (estimated); Unnamed park, 374 m away, about 6 min walk (estimated); 50,170 m² of park within 800 m",
            rows.getValue("Parks").value,
        )
        assertEquals(
            "Supermarket: Near Supermarket, 1.1 km away, about 18 min walk (estimated); Shopping centre: Hamilton Mall, 561 m away",
            rows.getValue("Shopping").value,
        )
        // A walking time is left out beyond an hour: only the distance is said.
        val far = LotCardRows.of(base.copy(beach = Attribute(Proximity("Far Beach", "ocean beach", 10_500, walkingMinutes = 171), Confidence.MEDIUM, "OSM")))
        assertEquals("Far Beach, 10.5 km away", far.first { it.label == "Distance to the beach" }.value)
        assertEquals("999 m", Formats.distance(999))
        assertEquals("1.0 km", Formats.distance(1000))
    }

    @Test
    fun `a lot can be selected by id, as a search result does`() = runTest {
        val vm = viewModel(this)
        vm.selectLot("1001"); advanceUntilIdle()
        assertEquals("1001", vm.state.value.selectedLotId)
        assertIs<LotCardState.Loaded>(vm.state.value.lotCard)
        assertEquals(null, vm.state.value.selectedSuburbId)
        val before = happy.urls.size
        vm.selectLot("1001"); advanceUntilIdle()
        assertEquals(before, happy.urls.size, "selecting the same lot again asks for nothing")
        viewModel(this, withLots = false).also { it.selectLot("1001"); advanceUntilIdle() }.state.value.let { assertEquals(MapUiState(), it) }
    }
}

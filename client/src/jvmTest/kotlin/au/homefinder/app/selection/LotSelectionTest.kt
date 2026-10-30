package au.homefinder.app.selection

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.HttpGet
import au.homefinder.app.api.HttpResult
import au.homefinder.app.api.LotApi
import au.homefinder.app.api.SuburbApi
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.SuburbReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(11, rows.size)
        assertTrue(rows.drop(1).all { it.isUnknown && it.confidence == Confidence.NONE && it.source == "src" })
    }
}

package au.homefinder.app.selection

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.HttpGet
import au.homefinder.app.api.HttpResult
import au.homefinder.app.api.SuburbApi
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.SuburbReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private fun unknown() = Attribute.unknown<Nothing>("src")

    private fun report(id: String, name: String) = SuburbReport(
        suburbId = id, name = name, postcode = "2300", areaKm2 = Attribute(1.0, au.homefinder.shared.model.Confidence.HIGH, "src"),
        medianHousePriceAud = Attribute.unknown("s"), medianUnitPriceAud = Attribute.unknown("s"),
        priceChange12MonthsPercent = Attribute.unknown("s"), salesCount = Attribute.unknown("s"), blockSize = Attribute.unknown("s"),
        floodShare = Attribute.unknown("s"), brickShare = Attribute.unknown("s"), beach = Attribute.unknown("s"),
        schools = Attribute.unknown("s"), parkAreaShare = Attribute.unknown("s"), shopping = Attribute.unknown("s"),
        transport = Attribute.unknown("s"),
    )

    private fun refJson(id: String, name: String) = """{"suburbId":"$id","name":"$name","postcode":"2300"}"""
    private fun reportJson(id: String, name: String) = ApiJson.encodeToString(report(id, name))

    /** Answers by URL; a handler may suspend so the test can control the order of answers. */
    private class FakeHttp(val handler: suspend (String) -> HttpResult) : HttpGet {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            urls += url
            return handler(url)
        }
    }

    private val config = ApiConfig("http://host:8080", "secret-key-123456")

    private fun viewModel(scope: TestScope, http: FakeHttp) = MapViewModel(SuburbApi(config, http), scope)

    private val happy = FakeHttp { url ->
        when {
            url.contains("/suburbs/at") && url.contains("lat=-32.92") -> HttpResult.Response(200, refJson("9001", "HAMILTON"))
            url.contains("/suburbs/at") && url.contains("lat=-33.03") -> HttpResult.Response(200, refJson("9002", "BELMONT"))
            url.contains("/suburbs/at") -> HttpResult.Response(404, "")
            url.endsWith("/suburbs/9001") -> HttpResult.Response(200, reportJson("9001", "HAMILTON"))
            url.endsWith("/suburbs/9002") -> HttpResult.Response(200, reportJson("9002", "BELMONT"))
            else -> HttpResult.Response(404, "")
        }
    }

    @Test
    fun `nothing is selected to start with`() = runTest {
        assertEquals(MapUiState(), viewModel(this, happy).state.value)
    }

    @Test
    fun `a tap on a suburb selects it and loads its card`() = runTest {
        val vm = viewModel(this, happy)
        vm.onMapTap(-32.92, 151.75, zoom = 10.0)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals("9001", state.selectedSuburbId)
        assertEquals("HAMILTON", assertIs<SuburbCardState.Loaded>(state.card).report.name)
        assertNull(state.notice)
    }

    @Test
    fun `the card shows the name as soon as the tap resolves, before the report arrives`() = runTest {
        val report = CompletableDeferred<HttpResult>()
        val http = FakeHttp { url -> if (url.contains("/suburbs/at")) HttpResult.Response(200, refJson("9001", "HAMILTON")) else report.await() }
        val vm = viewModel(this, http)
        vm.onMapTap(-32.92, 151.75, 10.0)
        advanceUntilIdle()
        val loading = assertIs<SuburbCardState.Loading>(vm.state.value.card)
        assertEquals("HAMILTON", loading.ref?.name)
        assertEquals("9001", vm.state.value.selectedSuburbId)
        report.complete(HttpResult.Response(200, reportJson("9001", "HAMILTON")))
        advanceUntilIdle()
        assertIs<SuburbCardState.Loaded>(vm.state.value.card)
    }

    @Test
    fun `a tap on no suburb clears the selection`() = runTest {
        val vm = viewModel(this, happy)
        vm.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle()
        vm.onMapTap(-34.0, 150.0, 10.0); advanceUntilIdle()
        assertEquals(MapUiState(), vm.state.value)
    }

    @Test
    fun `a tap at street level does not pick a suburb`() = runTest {
        val vm = viewModel(this, happy)
        vm.onMapTap(-32.92, 151.75, zoom = LOT_SELECT_MIN_ZOOM); advanceUntilIdle()
        vm.onMapTap(-32.92, 151.75, zoom = 19.0); advanceUntilIdle()
        assertTrue(happy.urls.isEmpty())
        assertEquals(MapUiState(), vm.state.value)
        vm.onMapTap(-32.92, 151.75, zoom = LOT_SELECT_MIN_ZOOM - 0.01); advanceUntilIdle()
        assertEquals("9001", vm.state.value.selectedSuburbId)
    }

    @Test
    fun `an unreachable API keeps the selection and says so`() = runTest {
        var down = false
        val http = FakeHttp { url -> if (down) HttpResult.Failure("refused") else happy.handler(url) }
        val vm = viewModel(this, http)
        vm.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle()
        down = true
        vm.onMapTap(-33.03, 151.66, 10.0); advanceUntilIdle()
        assertEquals("9001", vm.state.value.selectedSuburbId)
        assertEquals(MapViewModel.NOTICE_UNREACHABLE, vm.state.value.notice)
        assertIs<SuburbCardState.Loaded>(vm.state.value.card)
    }

    @Test
    fun `a rejected key is reported`() = runTest {
        val vm = viewModel(this, FakeHttp { HttpResult.Response(401, "") })
        vm.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle()
        assertEquals(MapViewModel.NOTICE_KEY_REJECTED, vm.state.value.notice)
        assertNull(vm.state.value.selectedSuburbId)
    }

    @Test
    fun `a failed report shows an error on the card and retry can recover`() = runTest {
        var failing = true
        val http = FakeHttp { url ->
            if (url.endsWith("/suburbs/9001") && failing) HttpResult.Response(503, "") else happy.handler(url)
        }
        val vm = viewModel(this, http)
        vm.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle()
        val failed = assertIs<SuburbCardState.Failed>(vm.state.value.card)
        assertEquals("the server answered 503", failed.reason)
        assertEquals("9001", vm.state.value.selectedSuburbId) // the highlight stays so the user sees what failed
        failing = false
        vm.retry(); advanceUntilIdle()
        assertIs<SuburbCardState.Loaded>(vm.state.value.card)
    }

    @Test
    fun `the latest tap wins even if an earlier report arrives last`() = runTest {
        val slowHamilton = CompletableDeferred<HttpResult>()
        val http = FakeHttp { url ->
            if (url.endsWith("/suburbs/9001")) slowHamilton.await() else happy.handler(url)
        }
        val vm = viewModel(this, http)
        vm.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle()
        vm.onMapTap(-33.03, 151.66, 10.0); advanceUntilIdle()
        slowHamilton.complete(HttpResult.Response(200, reportJson("9001", "HAMILTON")))
        advanceUntilIdle()
        assertEquals("9002", vm.state.value.selectedSuburbId)
        assertEquals("BELMONT", assertIs<SuburbCardState.Loaded>(vm.state.value.card).report.name)
    }

    @Test
    fun `clearing hides the card and a late answer cannot bring it back`() = runTest {
        val slow = CompletableDeferred<HttpResult>()
        val http = FakeHttp { url -> if (url.endsWith("/suburbs/9001")) slow.await() else happy.handler(url) }
        val vm = viewModel(this, http)
        vm.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle()
        vm.clear()
        slow.complete(HttpResult.Response(200, reportJson("9001", "HAMILTON")))
        advanceUntilIdle()
        assertEquals(MapUiState(), vm.state.value)
    }

    @Test
    fun `selecting by id works without a tap and selecting the same suburb again does nothing`() = runTest {
        val vm = viewModel(this, happy)
        vm.select("9002"); advanceUntilIdle()
        assertEquals("BELMONT", assertIs<SuburbCardState.Loaded>(vm.state.value.card).report.name)
        val calls = happy.urls.size
        vm.select("9002"); advanceUntilIdle()
        assertEquals(calls, happy.urls.size)
    }

    @Test
    fun `a suburb id the API does not know clears the selection`() = runTest {
        val vm = viewModel(this, happy)
        vm.select("424242"); advanceUntilIdle()
        assertEquals(MapUiState(), vm.state.value)
    }

    @Test
    fun `the API key never appears in the requested urls`() = runTest {
        val vm = viewModel(this, happy)
        vm.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle()
        assertTrue(happy.urls.none { it.contains("secret") } && ApiContract.API_KEY_HEADER.isNotEmpty())
    }
}

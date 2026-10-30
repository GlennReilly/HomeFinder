package au.homefinder.app.selection

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.HttpGet
import au.homefinder.app.api.HttpResult
import au.homefinder.app.api.SearchApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private fun suburb(id: String, name: String) =
        """{"kind":"SUBURB","id":"$id","label":"$name","detail":"Suburb, NSW 2303","latitude":-32.92,"longitude":151.75,"bounds":{"west":151.74,"south":-32.93,"east":151.76,"north":-32.91}}"""

    private fun address(pid: String, label: String, lot: String?) =
        """{"kind":"ADDRESS","id":"$pid","label":"$label","detail":null,"latitude":-32.92,"longitude":151.75,"lotId":${lot?.let { "\"$it\"" } ?: "null"}}"""

    private fun body(vararg results: String) = """{"results":[${results.joinToString(",")}]}"""

    private class FakeHttp(val handler: suspend (String) -> HttpResult) : HttpGet {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult = handler(url).also { urls += url }
    }

    private val config = ApiConfig("http://host:8080", "secret-key-123456")

    private fun viewModel(scope: TestScope, http: FakeHttp) = SearchViewModel(SearchApi(config, http), scope, debounceMillis = 200)

    private val happy = FakeHttp { url ->
        when {
            url.endsWith("q=ham") -> HttpResult.Response(200, body(suburb("9001", "HAMILTON"), suburb("9002", "HAMILTON NORTH"), address("G1", "12 MAIN STREET, HAMILTON NSW 2303", "1001")))
            url.endsWith("q=zzz") -> HttpResult.Response(200, body())
            else -> HttpResult.Response(200, body())
        }
    }

    @Test
    fun `typing waits for the pause and searches once, highlighting the first suggestion`() = runTest {
        val http = FakeHttp { HttpResult.Response(200, body(suburb("9001", "HAMILTON"))) }
        val vm = viewModel(this, http)
        vm.onQueryChanged("h"); vm.onQueryChanged("ha"); advanceTimeBy(100); vm.onQueryChanged("ham")
        assertEquals(SearchStatus.Searching, vm.state.value.status)
        assertTrue(http.urls.isEmpty(), "nothing is sent while the user is still typing")
        advanceTimeBy(250); advanceUntilIdle()
        assertEquals(listOf("http://host:8080/v1/search?q=ham"), http.urls)
        val state = vm.state.value
        assertEquals("ham", state.query)
        assertEquals(SearchStatus.Ready, state.status)
        assertEquals(0, state.highlighted)
        assertTrue(state.isOpen)
    }

    @Test
    fun `a query that is too short sends nothing and closes the list`() = runTest {
        val vm = viewModel(this, happy)
        vm.onQueryChanged("h"); advanceUntilIdle()
        vm.onQueryChanged("  a "); advanceUntilIdle()
        assertTrue(happy.urls.isEmpty())
        assertEquals(SearchUiState(query = "  a "), vm.state.value)
    }

    @Test
    fun `no matches and failures are said plainly`() = runTest {
        val vm = viewModel(this, happy)
        vm.onQueryChanged("zzz"); advanceUntilIdle()
        assertEquals(SearchStatus.NoMatches, vm.state.value.status)
        assertTrue(vm.state.value.isOpen)
        val rejected = viewModel(this, FakeHttp { HttpResult.Response(401, "") })
        rejected.onQueryChanged("ham"); advanceUntilIdle()
        assertEquals(SearchStatus.Failed(SearchViewModel.MESSAGE_KEY_REJECTED), rejected.state.value.status)
        val down = viewModel(this, FakeHttp { HttpResult.Failure("offline") })
        down.onQueryChanged("ham"); advanceUntilIdle()
        assertEquals(SearchStatus.Failed(SearchViewModel.MESSAGE_FAILED), down.state.value.status)
        val unavailable = viewModel(this, FakeHttp { HttpResult.Response(503, "") })
        unavailable.onQueryChanged("ham"); advanceUntilIdle()
        assertEquals(SearchStatus.Failed(SearchViewModel.MESSAGE_FAILED), unavailable.state.value.status)
    }

    @Test
    fun `a slow earlier answer can never replace a newer one`() = runTest {
        val slow = CompletableDeferred<HttpResult>()
        val http = FakeHttp { url -> if (url.endsWith("q=ham")) slow.await() else HttpResult.Response(200, body(address("G9", "9 OTHER STREET, WARATAH NSW 2298", null))) }
        val vm = viewModel(this, http)
        vm.onQueryChanged("ham"); advanceTimeBy(250)
        vm.onQueryChanged("hamx"); advanceTimeBy(250); advanceUntilIdle()
        assertEquals("9 OTHER STREET, WARATAH NSW 2298", vm.state.value.results.single().label)
        slow.complete(HttpResult.Response(200, body(suburb("9001", "HAMILTON")))); advanceUntilIdle()
        assertEquals("9 OTHER STREET, WARATAH NSW 2298", vm.state.value.results.single().label)
        assertEquals("hamx", vm.state.value.query)
    }

    @Test
    fun `the highlight moves with the arrows, wraps round and can be set by the mouse`() = runTest {
        val vm = viewModel(this, happy)
        vm.onQueryChanged("ham"); advanceUntilIdle()
        assertEquals(0, vm.state.value.highlighted)
        vm.moveHighlight(1); assertEquals(1, vm.state.value.highlighted)
        vm.moveHighlight(1); assertEquals(2, vm.state.value.highlighted)
        vm.moveHighlight(1); assertEquals(0, vm.state.value.highlighted)
        vm.moveHighlight(-1); assertEquals(2, vm.state.value.highlighted)
        vm.highlight(1); assertEquals(1, vm.state.value.highlighted)
        vm.highlight(99); assertEquals(1, vm.state.value.highlighted)
        viewModel(this, happy).also { it.moveHighlight(1) }.state.value.let { assertEquals(SearchUiState(), it) }
    }

    @Test
    fun `choosing returns the suggestion, shows its label and closes the list`() = runTest {
        val vm = viewModel(this, happy)
        vm.onQueryChanged("ham"); advanceUntilIdle()
        val chosen = vm.choose()
        assertEquals("HAMILTON", chosen?.label)
        assertEquals(SearchUiState(query = "HAMILTON"), vm.state.value)
        vm.onQueryChanged("ham"); advanceUntilIdle()
        assertEquals("12 MAIN STREET, HAMILTON NSW 2303", vm.choose(2)?.label)
        assertNull(vm.choose(), "nothing is left to choose after choosing")
    }

    @Test
    fun `choosing cancels a search that is still waiting`() = runTest {
        val http = FakeHttp { HttpResult.Response(200, body(suburb("9001", "HAMILTON"))) }
        val vm = viewModel(this, http)
        vm.onQueryChanged("ham"); advanceUntilIdle()
        vm.onQueryChanged("hami"); advanceTimeBy(50)
        vm.choose()
        advanceUntilIdle()
        assertEquals(listOf("http://host:8080/v1/search?q=ham"), http.urls)
    }

    @Test
    fun `close keeps the text and the suggestions, and open brings them back`() = runTest {
        val vm = viewModel(this, happy)
        vm.onQueryChanged("ham"); advanceUntilIdle()
        vm.close()
        assertEquals(false, vm.state.value.isOpen)
        assertEquals("ham", vm.state.value.query)
        assertEquals(3, vm.state.value.results.size)
        vm.open()
        assertTrue(vm.state.value.isOpen)
        assertIs<SearchStatus.Ready>(vm.state.value.status)
    }
}

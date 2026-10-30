package au.homefinder.app.selection

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.HttpGet
import au.homefinder.app.api.HttpResult
import au.homefinder.app.api.HttpWrite
import au.homefinder.app.api.RatingApi
import au.homefinder.app.api.SuburbApi
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.SuburbReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RatingFlowTest {
    private fun <T> u() = Attribute.unknown<T>("src")

    private fun report(rating: HomelyRating?) = SuburbReport(
        "9001", "HAMILTON", "2303", Attribute(1.0, Confidence.HIGH, "src"), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(),
        homelyRating = rating?.let { Attribute(it, Confidence.LOW, HomelyRating.SOURCE, LocalDate(2026, 10, 30), "typed in by you") } ?: u(),
    )

    private val config = ApiConfig("http://host:8080", "secret-key-123456")

    /** A tiny server in memory: one suburb whose rating can be set and removed. */
    private class World {
        var stored: HomelyRating? = null
        var writeFails = false
        val writes = mutableListOf<String>()
    }

    private fun build(scope: TestScope, world: World): MapViewModel {
        val reads = HttpGet { url, _ ->
            when {
                url.contains("/suburbs/at") -> HttpResult.Response(200, """{"suburbId":"9001","name":"HAMILTON","postcode":"2303"}""")
                url.endsWith("/suburbs/9001") -> HttpResult.Response(200, ApiJson.encodeToString(report(world.stored)))
                else -> HttpResult.Response(404, "")
            }
        }
        val writes = HttpWrite { method, url, _, body ->
            world.writes += "$method $url $body"
            when {
                world.writeFails -> HttpResult.Response(503, "")
                method == "PUT" -> { world.stored = ApiJson.decodeFromString<HomelyRating>(body!!); HttpResult.Response(200, body) }
                else -> { world.stored = null; HttpResult.Response(204, "") }
            }
        }
        return MapViewModel(SuburbApi(config, reads), scope, null, RatingApi(config, reads, writes))
    }

    private fun TestScope.selected(world: World): MapViewModel = build(this, world).also { it.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle() }

    @Test
    fun `saving a score updates the card in place and tells the map`() = runTest {
        val world = World()
        val vm = selected(world)
        val versionBefore = vm.ratingsVersion.value
        val outcome = vm.saveRating("2,1", "7")
        assertEquals(RatingOutcome.Saved, outcome)
        assertEquals(HomelyRating(2.1, 7), world.stored)
        val card = assertIs<SuburbCardState.Loaded>(vm.state.value.card)
        assertEquals(HomelyRating(2.1, 7), card.report.homelyRating.value)
        assertEquals(versionBefore + 1, vm.ratingsVersion.value)
        assertEquals("9001", vm.state.value.selectedSuburbId)
    }

    @Test
    fun `removing the score clears it from the card and tells the map`() = runTest {
        val world = World().apply { stored = HomelyRating(2.1, 7) }
        val vm = selected(world)
        assertEquals(HomelyRating(2.1, 7), assertIs<SuburbCardState.Loaded>(vm.state.value.card).report.homelyRating.value)
        assertEquals(RatingOutcome.Removed, vm.removeRating())
        assertNull(world.stored)
        assertTrue(assertIs<SuburbCardState.Loaded>(vm.state.value.card).report.homelyRating.isUnknown)
        assertEquals(1, vm.ratingsVersion.value)
    }

    @Test
    fun `bad input is rejected with the reason and nothing is sent`() = runTest {
        val world = World()
        val vm = selected(world)
        assertEquals(RatingOutcome.Rejected(RatingInput.MESSAGE_STARS_RANGE), vm.saveRating("9", ""))
        assertEquals(RatingOutcome.Rejected(RatingInput.MESSAGE_STARS_MISSING), vm.saveRating("", ""))
        assertEquals(RatingOutcome.Rejected(RatingInput.MESSAGE_REVIEWS), vm.saveRating("3", "x"))
        assertTrue(world.writes.isEmpty())
        assertEquals(0, vm.ratingsVersion.value)
    }

    @Test
    fun `a server failure is reported and the card and labels stay as they were`() = runTest {
        val world = World().apply { writeFails = true }
        val vm = selected(world)
        assertIs<RatingOutcome.Failed>(vm.saveRating("2.1", "7"))
        assertIs<RatingOutcome.Failed>(vm.removeRating())
        assertEquals(0, vm.ratingsVersion.value)
        assertTrue(assertIs<SuburbCardState.Loaded>(vm.state.value.card).report.homelyRating.isUnknown)
    }

    @Test
    fun `without a selected suburb or the ratings API there is nothing to save`() = runTest {
        val world = World()
        assertEquals(RatingOutcome.Failed(MapViewModel.MESSAGE_SELECT_FIRST), build(this, world).saveRating("2.1", ""))
        val noApi = MapViewModel(SuburbApi(config) { _, _ -> HttpResult.Response(404, "") }, this)
        assertEquals(RatingOutcome.Failed(MapViewModel.MESSAGE_RATINGS_UNAVAILABLE), noApi.saveRating("2.1", ""))
        assertEquals(RatingOutcome.Failed(MapViewModel.MESSAGE_RATINGS_UNAVAILABLE), noApi.removeRating())
    }

    @Test
    fun `the row shows stars and reviews, and is absent when nothing was entered`() {
        assertEquals("2.1 ★ (7 reviews)", SuburbCardRows.homely(report(HomelyRating(2.1, 7)))?.value)
        assertEquals("4.0 ★ (1 review)", SuburbCardRows.homely(report(HomelyRating(4.0, 1)))?.value)
        assertEquals("3.5 ★", SuburbCardRows.homely(report(HomelyRating(3.5, null)))?.value)
        assertEquals("5.0 ★ (1,200 reviews)", SuburbCardRows.homely(report(HomelyRating(5.0, 1200)))?.value)
        assertNull(SuburbCardRows.homely(report(null)))
        val row = SuburbCardRows.homely(report(HomelyRating(2.1, 7)))!!
        assertEquals(HomelyRating.SOURCE, row.source)
        assertEquals("2026-10-30", row.asOf)
        assertEquals(Confidence.LOW, row.confidence)
    }
}

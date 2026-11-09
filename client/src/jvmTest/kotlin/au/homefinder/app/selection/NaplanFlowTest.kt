package au.homefinder.app.selection

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.HttpGet
import au.homefinder.app.api.HttpResult
import au.homefinder.app.api.HttpVerb
import au.homefinder.app.api.HttpWrite
import au.homefinder.app.api.LotApi
import au.homefinder.app.api.NaplanApi
import au.homefinder.app.api.SuburbApi
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanScore
import au.homefinder.shared.model.NearbySchool
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolInfo
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaplanFlowTest {
    private fun <T> u() = Attribute.unknown<T>("src")
    private val day = LocalDate(2026, 11, 8)

    /** A tiny server in memory: school 1234 is listed by suburb 9001 and lot 1001, and its figure can be set and cleared. */
    private class World {
        var stored: NaplanScore? = null
        var writeFails = false
        val writes = mutableListOf<String>()
    }

    private fun school(world: World) = School("1234", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, 1051, naplan = world.stored)

    private fun suburb(world: World) = SuburbReport(
        "9001", "HAMILTON", "2303", Attribute(1.0, Confidence.HIGH, "src"), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(),
    ).copy(zonedSchools = Attribute(SuburbZonedSchools(listOf(SuburbZone(school(world), 1.0))), Confidence.MEDIUM, "src"))

    private fun lot(world: World) = LotReport("1001", null, "9001", "HAMILTON", "2303", emptyList(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u())
        .copy(schools = Attribute(SchoolInfo(nearest = listOf(NearbySchool(school(world), 600))), Confidence.MEDIUM, "src"))

    private val config = ApiConfig("http://host:8080", "secret-key-123456")

    private fun build(world: World, scope: TestScope): MapViewModel {
        val reads = HttpGet { url, _ ->
            when {
                url.contains("/suburbs/at") -> HttpResult.Response(200, """{"suburbId":"9001","name":"HAMILTON","postcode":"2303"}""")
                url.contains("/lots/at") -> HttpResult.Response(200, """{"lotId":"1001","suburbId":"9001"}""")
                url.endsWith("/suburbs/9001") -> HttpResult.Response(200, ApiJson.encodeToString(suburb(world)))
                url.endsWith("/lots/1001") -> HttpResult.Response(200, ApiJson.encodeToString(lot(world)))
                else -> HttpResult.Response(404, "")
            }
        }
        val writes = HttpWrite { method, url, _, body ->
            world.writes += "$method $url"
            when {
                world.writeFails -> HttpResult.Response(503, "")
                url.contains("/schools/9999/") -> HttpResult.Response(404, "")
                method == HttpVerb.PUT -> {
                    val entry = ApiJson.decodeFromString<NaplanEntry>(body!!)
                    val kept = NaplanScore(entry.score, day).also { world.stored = it }
                    HttpResult.Response(200, ApiJson.encodeToString(kept))
                }
                else -> { world.stored = null; HttpResult.Response(204, "") }
            }
        }
        return MapViewModel(SuburbApi(config, reads), scope, LotApi(config, reads), null, NaplanApi(config, writes))
    }

    private fun suburbSchool(vm: MapViewModel) = assertIs<SuburbCardState.Loaded>(vm.state.value.card).report.zonedSchools.value!!.zones.single().school

    @Test
    fun `saving a figure reloads the suburb card in place with it`() = runTest {
        val world = World()
        val vm = build(world, this).also { it.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle() }
        assertEquals(NaplanOutcome.Saved, vm.saveNaplan("1234", "-41"))
        assertEquals(NaplanScore(-41, day), suburbSchool(vm).naplan)
        assertEquals("9001", vm.state.value.selectedSuburbId)
    }

    @Test
    fun `clearing a figure reloads the card without it`() = runTest {
        val world = World().apply { stored = NaplanScore(512, LocalDate(2026, 1, 1)) }
        val vm = build(world, this).also { it.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle() }
        assertEquals(NaplanOutcome.Removed, vm.removeNaplan("1234"))
        assertEquals(null, suburbSchool(vm).naplan)
    }

    @Test
    fun `on a lot card the lot report is reloaded instead`() = runTest {
        val world = World()
        val vm = build(world, this).also { it.onMapTap(-32.92, 151.75, 17.0); advanceUntilIdle() }
        assertEquals(NaplanOutcome.Saved, vm.saveNaplan("1234", "530"))
        val lot = assertIs<LotCardState.Loaded>(vm.state.value.lotCard).report
        assertEquals(NaplanScore(530, day), lot.schools.value!!.nearest.single().school.naplan)
    }

    @Test
    fun `bad input is rejected with the reason and nothing is sent`() = runTest {
        val world = World()
        val vm = build(world, this).also { it.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle() }
        assertEquals(NaplanOutcome.Rejected(NaplanInput.MESSAGE_SCORE_RANGE), vm.saveNaplan("1234", "-1200"))
        assertTrue(world.writes.isEmpty())
    }

    @Test
    fun `a failing server, an unknown school and no NAPLAN api are reported`() = runTest {
        val world = World()
        val vm = build(world, this).also { it.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle() }
        assertEquals(NaplanOutcome.Failed(MapViewModel.MESSAGE_SCHOOL_UNKNOWN), vm.saveNaplan("9999", "512"))
        world.writeFails = true
        assertIs<NaplanOutcome.Failed>(vm.saveNaplan("1234", "512"))
        assertIs<NaplanOutcome.Failed>(vm.removeNaplan("1234"))
        assertEquals(null, suburbSchool(vm).naplan)
        val bare = MapViewModel(SuburbApi(config) { _, _ -> HttpResult.Response(404, "") }, this)
        assertEquals(NaplanOutcome.Failed(MapViewModel.MESSAGE_NAPLAN_UNAVAILABLE), bare.saveNaplan("1234", "512"))
    }

    @Test
    fun `a saved or cleared figure bumps the NAPLAN version so the filters reload, and a refused or failed one does not`() = runTest {
        val world = World()
        val vm = build(world, this).also { it.onMapTap(-32.92, 151.75, 10.0); advanceUntilIdle() }
        assertEquals(0, vm.naplanVersion.value)
        vm.saveNaplan("1234", "-1200")
        vm.saveNaplan("9999", "12")
        assertEquals(0, vm.naplanVersion.value, "nothing was saved")
        vm.saveNaplan("1234", "-41")
        assertEquals(1, vm.naplanVersion.value)
        vm.removeNaplan("1234")
        assertEquals(2, vm.naplanVersion.value)
        world.writeFails = true
        vm.saveNaplan("1234", "5")
        assertEquals(2, vm.naplanVersion.value)
    }
}

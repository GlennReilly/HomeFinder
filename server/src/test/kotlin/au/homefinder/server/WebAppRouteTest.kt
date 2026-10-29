package au.homefinder.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebAppRouteTest {
    private val key = "0123456789abcdef"

    private fun site(dir: Path): Path {
        dir.resolve("index.html").writeText("<!doctype html><title>Home Finder</title>")
        dir.resolve("homefinder.js").writeText("console.log('app')")
        dir.resolve("maplibre-gl-worker.mjs").writeText("export {}")
        dir.resolve("styles.css").writeText("body{}")
        // A file next to the web root that must never be reachable.
        dir.resolveSibling("secret.txt").writeText("not for the web")
        return dir
    }

    private fun withSite(dir: Path, block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        val root = dir.resolve("web").also { it.createDirectories() }
        site(root)
        application { module(ServerConfig("127.0.0.1", 8080, key, webRoot = root.toString())) }
        block()
    }

    @Test
    fun `the page is served at the root and by name`(@TempDir dir: Path) = withSite(dir) {
        val root = client.get("/")
        assertEquals(HttpStatusCode.OK, root.status)
        assertTrue("Home Finder" in root.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/index.html").status)
    }

    @Test
    fun `assets are served with the right content types`(@TempDir dir: Path) = withSite(dir) {
        assertTrue(client.get("/homefinder.js").headers[HttpHeaders.ContentType].orEmpty().contains("javascript"))
        assertTrue(client.get("/styles.css").headers[HttpHeaders.ContentType].orEmpty().startsWith("text/css"))
        // Browsers refuse to start a module worker unless it is served as JavaScript.
        assertTrue(client.get("/maplibre-gl-worker.mjs").headers[HttpHeaders.ContentType].orEmpty().contains("javascript"))
    }

    @Test
    fun `the bundle must be revalidated on every load`(@TempDir dir: Path) = withSite(dir) {
        assertTrue(client.get("/homefinder.js").headers[HttpHeaders.CacheControl].orEmpty().contains("no-cache"))
    }

    @Test
    fun `files outside the web root cannot be reached`(@TempDir dir: Path) = withSite(dir) {
        listOf("/../secret.txt", "/%2e%2e/secret.txt", "/..%2fsecret.txt", "/%2e%2e%2fsecret.txt").forEach {
            val response = client.get(it)
            assertFalse("not for the web" in response.bodyAsText(), it)
        }
    }

    @Test
    fun `an unknown file is a 404 and the API is untouched`(@TempDir dir: Path) = withSite(dir) {
        assertEquals(HttpStatusCode.NotFound, client.get("/nothing-here.js").status)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        // The API still needs its key even though a static route sits at "/".
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/status").status)
        assertEquals(HttpStatusCode.OK, client.get("/v1/status") { header("X-API-Key", key) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/tiles/suburbs/10/943/611.pbf").status)
    }

    @Test
    fun `without a web root the root is not served`() = testApplication {
        application { module(ServerConfig("127.0.0.1", 8080, key)) }
        assertEquals(HttpStatusCode.NotFound, client.get("/").status)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }
}

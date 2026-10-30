plugins {
    alias(libs.plugins.kotlinJvm)
}

// Browser smoke tests (ADR 0032): Playwright drives the installed Chrome through the built web app, which talks to a small
// fake of the API. They are not part of `./gradlew build` (they need a browser); run `./gradlew :e2e:e2eTest`.
kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(project(":shared"))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.playwright)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val webBundle = project(":webApp").layout.buildDirectory.dir("dist/js/productionExecutable")

// The ordinary test task stays off so `build` does not need a browser.
tasks.test { enabled = false }

val e2eTest by tasks.registering(Test::class) {
    description = "Runs the browser smoke tests against the built web app."
    group = "verification"
    dependsOn(":webApp:jsBrowserDistribution")
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    systemProperty("homefinder.web.root", webBundle.get().asFile.absolutePath)
    // The tests use the Chrome that is already installed; do not let Playwright download its own browsers.
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = false }
    outputs.upToDateWhen { false }
}

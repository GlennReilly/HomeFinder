plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

// The web client (ADR 0019): Kotlin/JS, a MapLibre GL JS map and a DOM UI built with kotlinx-html.
kotlin {
    js {
        useEsModules() // maplibre-gl 6 is ESM-only
        browser {
            commonWebpackConfig {
                outputFileName = "homefinder.js"
                cssSupport { enabled.set(true) }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":client"))
            implementation(libs.kotlinx.html)
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.coroutines.core)
            implementation(npm("maplibre-gl", libs.versions.maplibreGl.get()))
        }
    }
}

// maplibre-gl 6 starts its web worker from separate files that a bundler does not emit, so they are copied next to the
// page and the map is pointed at them (SuburbMap).
val copyMaplibreWorker by tasks.registering(Copy::class) {
    dependsOn(rootProject.tasks.named("kotlinNpmInstall"))
    from(rootProject.layout.buildDirectory.dir("js/node_modules/maplibre-gl/dist")) {
        include("maplibre-gl-worker.mjs")
    }
    into(layout.buildDirectory.dir("generated/maplibre"))
}

kotlin.sourceSets.named("jsMain") { resources.srcDir(copyMaplibreWorker.map { it.destinationDir }) }

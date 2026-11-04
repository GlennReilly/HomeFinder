plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin { jvmToolchain(17) }

// Run by pipeline/scripts/load-drainage.sh (ADR 0040): `./gradlew :drainage:run --args="<dem dir> <lots csv> <out csv>"`.
application {
    mainClass = "au.homefinder.drainage.MainKt"
    applicationDefaultJvmArgs = listOf("-Xmx3g")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

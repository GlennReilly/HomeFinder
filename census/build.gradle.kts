plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin { jvmToolchain(17) }

// Run by pipeline/scripts/load-census.sh (ADR 0044): `./gradlew :census:run --args="<census dir> <output csv>"`.
application {
    mainClass = "au.homefinder.census.MainKt"
    applicationDefaultJvmArgs = listOf("-Xmx1g")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

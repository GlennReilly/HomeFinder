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

// Run by pipeline/scripts/load-seifa.sh (ADR 0049): `./gradlew :census:seifa --args="<workbook> <output csv>"`.
tasks.register<JavaExec>("seifa") {
    group = "application"
    mainClass = "au.homefinder.census.SeifaMainKt"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Xmx1g")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

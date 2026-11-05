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

// Run by pipeline/scripts/load-crime.sh (ADR 0051): `./gradlew :census:crime --args="<SuburbData.csv> <output csv>"`.
tasks.register<JavaExec>("crime") {
    group = "application"
    mainClass = "au.homefinder.census.CrimeMainKt"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Xmx512m")
}

// Run by pipeline/scripts/load-catchments.sh (ADR 0056): `./gradlew :census:catchments --args="<catchments dir> <output csv>"`.
tasks.register<JavaExec>("catchments") {
    group = "application"
    mainClass = "au.homefinder.census.CatchmentsMainKt"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Xmx1g")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

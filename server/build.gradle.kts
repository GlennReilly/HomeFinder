plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin { jvmToolchain(17) }

application { mainClass = "au.homefinder.server.MainKt" }

// `./gradlew :server:run` reads server/.env (KEY=VALUE lines) into the server's environment, like the database's .env.
tasks.named<JavaExec>("run") {
    val envFile = layout.projectDirectory.file(".env").asFile
    if (envFile.exists()) {
        envFile.readLines().map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .forEach { line -> line.split("=", limit = 2).let { environment(it[0].trim(), it[1].trim()) } }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktorServerTestHost)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

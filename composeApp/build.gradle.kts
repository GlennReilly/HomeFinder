plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// maplibre-compose desktop needs Java 25 or later (ADR 0008).
val desktopJavaVersion = 25

kotlin {
    jvmToolchain(desktopJavaVersion)

    androidLibrary {
        namespace = "au.homefinder.app"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.maplibre.compose)
        }
        androidMain.dependencies {
            runtimeOnly(libs.maplibre.runtime.opengl.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            runtimeOnly(libs.maplibre.runtime.opengl.linux.x64)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit.jupiter)
            implementation(libs.mockk)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

compose.desktop {
    application {
        mainClass = "au.homefinder.app.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        providers.gradleProperty("mapRenderer").orNull?.let { jvmArgs += "-Dhomefinder.map.renderer=$it" }
        javaHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(desktopJavaVersion) }
            .get().metadata.installationPath.asFile.absolutePath
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

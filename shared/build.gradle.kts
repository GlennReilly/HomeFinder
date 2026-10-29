plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)

    jvm()
    js { nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime) // LocalDate is part of the public model
            api(libs.kotlinx.serialization.json) // ApiJson exposes its Json type
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.mockk)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

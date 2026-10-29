plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

// Client logic with no UI: API client, suburb selection and the Suburb Card rows. Shared by the Compose apps and the
// web app (ADR 0019). Tests run on the JVM; the JS target is built and its common tests run under Node.
kotlin {
    jvmToolchain(17)

    jvm()
    js { nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.ktor.client.core)
            api(libs.kotlinx.html)
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

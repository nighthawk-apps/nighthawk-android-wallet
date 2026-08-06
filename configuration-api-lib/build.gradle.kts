plugins {
    kotlin("multiplatform")
    id("stealth.kotlin-multiplatform-build-conventions")
    id("stealth.dependency-conventions")

    id("org.jetbrains.kotlinx.kover")
    id("stealth.kover-conventions")
}

kotlin {
    jvm()
    sourceSets {
        getByName("commonMain") {
            dependencies {

                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.immutable)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                api(libs.kotlinx.coroutines.test)
            }
        }
        getByName("jvmMain") {
            dependencies {
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

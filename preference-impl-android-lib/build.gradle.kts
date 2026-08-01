plugins {
    id("com.android.library")
    id("stealth.android-build-conventions")
//    id("wtf.emulator.gradle")
//    id("stealth.emulator-wtf-conventions")
    id("stealth.jacoco-conventions")
}

// Force orchestrator to be used for this module, because we need the preference files
// to be purged between tests
val isOrchestratorEnabled = true

android {
    namespace = "com.nighthawkapps.lib.android.preference"

    if (isOrchestratorEnabled) {
        defaultConfig {
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
        }

        testOptions {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
    }
}

dependencies {
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.crypto.tink:tink-android:1.8.0")
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(projects.preferenceApiLib)

    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    androidTestUtil(libs.androidx.test.services) {
        artifact {
            type = "apk"
        }
    }

    if (isOrchestratorEnabled) {
        androidTestUtil(libs.androidx.test.orchestrator) {
            artifact {
                type = "apk"
            }
        }
    }
}

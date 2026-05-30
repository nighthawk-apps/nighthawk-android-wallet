plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.plugin.compose")
    id("stealth.android-build-conventions")
    id("stealth.compose-conventions")
//    id("wtf.emulator.gradle")
//    id("stealth.emulator-wtf-conventions")
}

// Force orchestrator to be used for this module, because we need cleared state to generate screenshots
val isOrchestratorEnabled = true

android {
    namespace = "com.nighthawkapps.lib.android.ui.screenshot"
    // Target needs to be set to com.android.application type module
    targetProjectPath = ":${projects.app.name}"
    // Run tests in this module
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        if (isOrchestratorEnabled) {
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
        }

        testInstrumentationRunner = "com.nighthawkapps.lib.android.test.NighthawkUiTestRunner"
    }

    // Define the same flavors as in app module
    flavorDimensions.add("network")
    productFlavors {
        create("darkfitestnet") {
            dimension = "network"
        }
        create("darkfimainnet") {
            dimension = "network"
        }
    }
    buildTypes {
        create("release") {
            // To provide compatibility with other modules
        }
    }

    if (isOrchestratorEnabled) {
        testOptions {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.configurationApiLib)
    implementation(projects.configurationImplAndroidLib)
    implementation(projects.spackleAndroidLib)
    implementation(projects.testLib)
    implementation(projects.uiLib)

    implementation(libs.bundles.androidx.test)
    implementation(libs.bundles.androidx.compose.core)

    implementation(libs.androidx.compose.test.junit)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.uiAutomator)

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

/*emulatorwtf {
    directoriesToPull.set(listOf("/sdcard/googletest/test_outputfiles"))

    // Because screenshot tests can be flaky, allow this module to always re-run
    // which is helpful on GitHub Actions.  Once the tests are fully stabilized, this can be
    // removed.
    sideEffects.set(true)
}*/

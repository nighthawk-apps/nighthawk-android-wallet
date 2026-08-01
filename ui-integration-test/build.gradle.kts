plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.plugin.compose")
    id("stealth.android-build-conventions")
    id("stealth.compose-conventions")
//    id("wtf.emulator.gradle")
//    id("stealth.emulator-wtf-conventions")
    id("stealth.jacoco-conventions")
}

// Force orchestrator to be used for this module, because we need cleared state before each test
val isOrchestratorEnabled = false

android {
    namespace = "com.nighthawkapps.lib.android.ui.integration"
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
            // to align with the benchmark module requirement - run against minified application
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
    implementation(projects.uiLib)
    implementation(projects.uiDesignLib)
    implementation(projects.testLib)
    implementation(projects.spackleAndroidLib)
    implementation(projects.darkfiAndroidSdk)

    implementation(libs.bundles.androidx.test)
    implementation(libs.bundles.androidx.compose.core)

    implementation(libs.androidx.compose.test.junit)
    implementation(libs.androidx.navigation.compose)
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

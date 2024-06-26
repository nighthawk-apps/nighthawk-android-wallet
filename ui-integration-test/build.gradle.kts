plugins {
    id("com.android.test")
    kotlin("android")
    id("secant.android-build-conventions")
//    id("wtf.emulator.gradle")
//    id("secant.emulator-wtf-conventions")
    id("secant.jacoco-conventions")
}

// Force orchestrator to be used for this module, because we need cleared state before each test
val isOrchestratorEnabled = true

android {
    namespace = "co.electriccoin.zcash.ui.integration"
    // Target needs to be set to com.android.application type module
    targetProjectPath = ":${projects.app.name}"
    // Run tests in this module
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        if (isOrchestratorEnabled) {
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
        }

        testInstrumentationRunner = "co.electriccoin.zcash.test.ZcashUiTestRunner"
    }

    // Define the same flavors as in app module
    flavorDimensions.add("network")
    productFlavors {
        create("zcashtestnet") {
            dimension = "network"
        }
        create("zcashmainnet") {
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

    composeOptions {
        kotlinCompilerExtensionVersion = libs.androidx.compose.compiler.get().versionConstraint.displayName
    }
}

dependencies {
    implementation(projects.uiLib)
    implementation(projects.uiDesignLib)
    implementation(projects.testLib)
    implementation(projects.spackleAndroidLib)

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
plugins {
    id("com.android.test")
    id("stealth.android-build-conventions")
}

android {
    namespace = "com.nighthawkapps.lib.android.ui.benchmark"
    targetProjectPath = ":${projects.app.name}"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        testInstrumentationRunner = "com.nighthawkapps.lib.android.test.NighthawkUiTestRunner"
        // to enable benchmarking for emulators, although only a physical device gives real results
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
        // To simplify module variants, we assume to run benchmarking against mainnet only
        missingDimensionStrategy("network", "darkfimainnet")
    }

    buildTypes {
        create("release") {
            // To provide compatibility with other modules
        }
        create("benchmark") {
            // We provide the extra benchmark build variants for benchmarking. We still need to support debug
            // variants to be compatible with debug variants in other modules, although benchmarking does not allow
            // not minified build variants - benchmarking with the debug build variants will fail.
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(projects.testLib)

    implementation(libs.bundles.androidx.test)
    implementation(libs.androidx.test.macrobenchmark)

    androidTestUtil(libs.androidx.test.services) {
        artifact {
            type = "apk"
        }
    }

    if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
        androidTestUtil(libs.androidx.test.orchestrator) {
            artifact {
                type = "apk"
            }
        }
    }
}
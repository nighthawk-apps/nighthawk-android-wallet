import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ResValue
import java.util.Locale

plugins {
    id("com.android.application")
    id("stealth.android-build-conventions")
    id("com.mikepenz.aboutlibraries.plugin")
}

val packageName = project.property("WALLET_RELEASE_PACKAGE_NAME").toString()

val testnetNetworkName = "Testnet"

android {
    namespace = "com.nighthawkwallet.android"

    defaultConfig {
        applicationId = packageName

        // If Google Play deployment is triggered, then these are placeholders which are overwritten
        // when the deployment runs
        versionCode = project.property("WALLET_VERSION_CODE").toString().toInt()
        versionName = project.property("WALLET_VERSION_NAME").toString()

        // S8: SHA-256 of lightwalletd leaf cert DER (64 hex). Empty = loopback-only / unset.
        // Override: -PLIGHTWALLET_TLS_PIN_SHA256=<hex> or gradle.properties.
        val tlsPin = (project.findProperty("LIGHTWALLET_TLS_PIN_SHA256") as? String)?.trim().orEmpty()
        manifestPlaceholders["LIGHTWALLET_TLS_PIN_SHA256"] = tlsPin
        // Release builds must ship a pin (fail closed at configure time).
        val isReleaseTask = gradle.startParameter.taskNames.any {
            it.contains("Release", ignoreCase = true) && !it.contains("UnitTest", ignoreCase = true)
        }
        if (isReleaseTask && (tlsPin.isEmpty() || tlsPin == "0".repeat(64) || tlsPin.equals("PLACEHOLDER", true))) {
            throw GradleException(
                "LIGHTWALLET_TLS_PIN_SHA256 must be set to a real 64-hex leaf pin for release builds"
            )
        }

        if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
        }

        testInstrumentationRunner = "com.nighthawkapps.lib.android.test.NighthawkUiTestRunner"
    }

    if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
        testOptions {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Omit Google Play SDK dependency metadata from APK/AAB (F-Droid / sideload).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    flavorDimensions.add("network")

    val testNetFlavorName = "darkfitestnet"
    productFlavors {
        // would rather name them "testnet" and "mainnet" but product flavor names cannot start with the word "test"
        create(testNetFlavorName) {
            dimension = "network"
            applicationId = "$packageName.testnet" // allow to be installed alongside mainnet
            matchingFallbacks.addAll(listOf("darkfitestnet", "debug"))
        }
        create("darkfimainnet") {
            dimension = "network"
            applicationId = packageName
            matchingFallbacks.addAll(listOf("darkfimainnet", "release"))
            // Extra shrinking when minifying mainnet release: drop android.util.Log from merged code paths.
            proguardFile("proguard-mainnet-strip-log.pro")
        }
    }

    val releaseKeystorePath = project.property("WALLET_RELEASE_KEYSTORE_PATH").toString()
    val releaseKeystorePassword = project.property("WALLET_RELEASE_KEYSTORE_PASSWORD").toString()
    val releaseKeyAlias = project.property("WALLET_RELEASE_KEY_ALIAS").toString()
    val releaseKeyAliasPassword =
        project.property("WALLET_RELEASE_KEY_ALIAS_PASSWORD").toString()
    val isReleaseSigningConfigured = listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyAliasPassword
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (isReleaseSigningConfigured) {
            // If this block doesn't execute, the output will be unsigned
            create("release").apply {
                storeFile = File(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyAliasPassword
            }
        }
    }

    buildTypes {
        getByName("debug").apply {
            // Note that the build-conventions defines the res configs
            isPseudoLocalesEnabled = true

            // Suffixing app package name and version to avoid collisions with other installed
            // wallet variants installed side-by-side
            // versionNameSuffix = "-debug"
            // applicationIdSuffix = ".debug"
        }
        getByName("release").apply {
            isMinifyEnabled = project.property("IS_MINIFY_ENABLED").toString().toBoolean()
            isShrinkResources = project.property("IS_MINIFY_ENABLED").toString().toBoolean()
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-project.txt"
            )

            val isSignReleaseBuildWithDebugKey = project.property("IS_SIGN_RELEASE_BUILD_WITH_DEBUG_KEY")
                .toString().toBoolean()

            if (isReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            } else if (isSignReleaseBuildWithDebugKey) {
                // Warning: in this case is the release build signed with the debug key
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    /*playConfigs {
        register(testNetFlavorName) {
            enabled.set(false)
        }
    }*/

    testCoverage {
        jacocoVersion = project.property("JACOCO_VERSION").toString()
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    // just to support baseline profile installation needed by benchmark tests
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.about.libraries)
    implementation(projects.darkfiAndroidSdk)
    implementation(projects.preferenceApiLib)
    implementation(projects.preferenceImplAndroidLib)
    implementation(projects.spackleAndroidLib)
    implementation(projects.uiLib)

    // Force BouncyCastle >= 1.78 to fix TrustAllX509TrustManager (pdfbox-android pulls 1.72).
    constraints {
        implementation("org.bouncycastle:bcprov-jdk15to18:1.78.1")
        implementation("org.bouncycastle:bcpkix-jdk15to18:1.78.1")
        implementation("org.bouncycastle:bcutil-jdk15to18:1.78.1")
    }

    androidTestImplementation(projects.testLib)

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

androidComponents {
    onVariants { variant ->
        val defaultAppName = project.property("WALLET_RELEASE_APP_NAME").toString()
        val debugAppNameSuffix = project.property("WALLET_DEBUG_APP_NAME_SUFFIX").toString()
        val supportEmailAddress = project.property("WALLET_SUPPORT_EMAIL_ADDRESS").toString()
        val appDisplayName =
            when (variant.name) {
                "darkfitestnetDebug" -> "$defaultAppName ($testnetNetworkName)$debugAppNameSuffix"
                "darkfimainnetDebug" -> "$defaultAppName$debugAppNameSuffix"
                "darkfitestnetRelease" -> "$defaultAppName ($testnetNetworkName)"
                "darkfimainnetRelease" -> defaultAppName
                else -> null
            }
        if (appDisplayName != null) {
            variant.resValues.put(variant.makeResValueKey("string", "app_name"), ResValue(appDisplayName, ""))
        }
        variant.resValues.put(
            variant.makeResValueKey("string", "support_email_address"),
            ResValue(supportEmailAddress, ""),
        )

        variant.buildConfigFields!!.put(
            "LOGCAT_ENABLED",
            BuildConfigField(
                type = "boolean",
                value = (variant.name != "darkfimainnetRelease").toString(), // Default true for testnet
                comment = "Production mainnet APK must not emit diagnostics to logcat.",
            ),
        )

        variant.buildConfigFields!!.put(
            "IS_STRICT_MODE_CRASH_ENABLED",
            BuildConfigField(
                type = "boolean",
                value = project.property("IS_CRASH_ON_STRICT_MODE_VIOLATION").toString(),
                comment = "Whether is the strict mode enabled"
            )
        )

        variant.packaging.resources.excludes.addAll(listOf(
            ".readme",
        ))

        if (variant.name.lowercase(Locale.US).contains("release")) {
            variant.packaging.resources.excludes.addAll(listOf(
                "**/*.kotlin_metadata",
                "DebugProbesKt.bin",
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/android.arch**",
                "META-INF/androidx**",
                "META-INF/com.android**",
                "META-INF/com.google.android.material_material.version",
                "META-INF/com.google.dagger_dagger.version",
                "build-data.properties",
                "core.properties",
                "firebase-**.properties",
                "kotlin-tooling-metadata.json",
                "kotlin/**",
                "play-services-**.properties",
                "protolite-well-known-types.properties",
                "transport-api.properties",
                "transport-backend-cct.properties",
                "transport-runtime.properties"
            ))
        }
    }
}

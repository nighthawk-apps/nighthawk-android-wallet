import com.android.build.api.variant.BuildConfigField

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("stealth.android-build-conventions")
    id("stealth.compose-conventions")
//    id("wtf.emulator.gradle")
//    id("stealth.emulator-wtf-conventions")
    id("stealth.jacoco-conventions")
    id("com.mikepenz.aboutlibraries.plugin")
}

android {
    namespace = "com.nighthawkapps.lib.android.ui"

    defaultConfig {
        testInstrumentationRunner = "com.nighthawkapps.lib.android.test.NighthawkUiTestRunner"
        multiDexEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").apply {
            res.directories.apply {
                clear()
                addAll(
                    listOf(
                        "src/main/res-drawable",
                        "src/main/res/ui/about",
                        "src/main/res/ui/backup",
                        "src/main/res/ui/common",
                        "src/main/res/ui/history",
                        "src/main/res/ui/home",
                        "src/main/res/ui/onboarding",
                        "src/main/res/ui/receive",
                        "src/main/res/ui/request",
                        "src/main/res/ui/restore",
                        "src/main/res/ui/scan",
                        "src/main/res/ui/seed",
                        "src/main/res/ui/send",
                        "src/main/res/ui/settings",
                        "src/main/res/ui/support",
                        "src/main/res/ui/update",
                        "src/main/res/ui/wallet_address",
                        "src/main/res/ui/warning",
                    )
                )
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        // Configure SecureScreen for protecting screens with sensitive data in runtime
        variant.buildConfigFields!!.put(
                "IS_SECURE_SCREEN_ENABLED",
                BuildConfigField(
                        type = "boolean",
                        value = project.property("IS_SECURE_SCREEN_PROTECTION_ACTIVE").toString(),
                        comment = "Whether is the SecureScreen sensitive data protection enabled"
                )
        )
    }
}

dependencies {
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.livedata)
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.crypto.tink:tink-android:1.8.0")
    implementation(libs.androidx.splash)
    implementation(libs.androidx.workmanager)
    implementation(libs.bundles.androidx.camera)
    implementation(libs.bundles.androidx.compose.core)
    implementation(libs.bundles.androidx.compose.extended)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.immutable)
    implementation(projects.darkfiAndroidSdk)
    implementation(libs.zxing)
    implementation(libs.pdfbox)
    implementation(libs.lottie)
    implementation(libs.androidx.biometric)
    implementation(libs.about.libraries)
    implementation(libs.square.retrofit)
    implementation(libs.square.retrofit.gson)
    implementation(libs.square.okhttp)
    implementation(libs.square.okhttp.logging.interceptor)
    implementation(libs.secure.storage)

    implementation(projects.buildInfoLib)
    implementation(projects.configurationApiLib)
    implementation(projects.configurationImplAndroidLib)
    implementation(projects.preferenceApiLib)
    implementation(projects.preferenceImplAndroidLib)
    implementation(projects.spackleAndroidLib)
    api(projects.uiDesignLib)

    androidTestImplementation(projects.testLib)
    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.androidx.compose.test.junit)
    androidTestImplementation(libs.androidx.compose.test.manifest)
    androidTestImplementation(libs.kotlin.reflect)
    androidTestImplementation(libs.kotlin.test)

    androidTestUtil(libs.androidx.test.services) {
        artifact {
            type = "apk"
        }
    }

    if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
        androidTestUtil(libs.androidx.test.services)
        androidTestUtil(libs.androidx.test.orchestrator) {
            artifact {
                type = "apk"
            }
        }
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}


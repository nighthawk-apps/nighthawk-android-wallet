
plugins {
    id("com.android.library")
    id("stealth.android-build-conventions")
    id("stealth.jacoco-conventions")
    kotlin("plugin.serialization")
}

val darkircBundleAssetsDir =
    objects.directoryProperty().apply {
        set(layout.buildDirectory.dir("generated-darkirc-bundle-assets"))
    }

/**
 * Copies `darkirc_exec` from the repo-root [artifacts/darkirc] tree into merged assets (`darkirc/<abi>/`).
 * CI/local builds populate that folder via [scripts/build-darkirc-android.sh] or extracted artifacts.
 */
val syncDarkircArtifacts =
    tasks.register<Sync>("syncDarkircArtifacts") {
        group = "build"
        description =
            "Bundles darkirc_exec from root artifacts/darkirc into APK assets."
        into(darkircBundleAssetsDir)
        into("darkirc") {
            from(rootProject.layout.projectDirectory.dir("artifacts/darkirc"))
            include("**/darkirc_exec")
        }
    }

val mobileFfiJniLibsDir =
    objects.directoryProperty().apply {
        set(layout.buildDirectory.dir("generated-mobile-ffi-jniLibs"))
    }

/**
 * Copies `libdarkfi_mobile_ffi.so` from [artifacts/mobile-ffi] when present
 * (see [scripts/build-darkfi-mobile-ffi-android.sh]).
 */
val syncMobileFfiArtifacts =
    tasks.register<Sync>("syncMobileFfiArtifacts") {
        group = "build"
        description =
            "Bundles libdarkfi_mobile_ffi.so from root artifacts/mobile-ffi into merged jniLibs."
        into(mobileFfiJniLibsDir)
        from(rootProject.layout.projectDirectory.dir("artifacts/mobile-ffi")) {
            include("**/libdarkfi_mobile_ffi.so")
        }
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncDarkircArtifacts,
        ) { darkircBundleAssetsDir }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            syncMobileFfiArtifacts,
        ) { mobileFfiJniLibsDir }
    }
}

android {
    namespace = "com.nighthawkapps.lib.android.sdk"

    lint {
        lintConfig = file("lint.xml")
    }

    defaultConfig {
        consumerProguardFiles("proguard-consumer.txt")
    }

    androidResources {
        noCompress += "darkirc_exec"
    }

    buildFeatures {
        buildConfig = false
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("src/main/jniLibs")
        }
    }
}

dependencies {
    // Embedded Tor (Guardian Project) — SOCKS on loopback.
    implementation("info.guardianproject:tor-android:0.4.9.8")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("com.jaredrummler:android-shell:1.0.0")

    // UniFFI-generated Kotlin bindings (0.31+) use JNA to load `libdarkfi_mobile_ffi`.
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.immutable)

    implementation(projects.spackleAndroidLib)
    implementation(libs.androidx.workmanager)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("org.json:json:20260522")
    testImplementation(libs.kotlin.test)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(kotlin("test-junit"))
}

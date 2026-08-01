
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

val darkfidBundleAssetsDir =
    objects.directoryProperty().apply {
        set(layout.buildDirectory.dir("generated-darkfid-bundle-assets"))
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

val syncDarkfidArtifacts =
    tasks.register<Sync>("syncDarkfidArtifacts") {
        group = "build"
        description =
            "Bundles darkfid_exec from root artifacts/darkfid into APK assets."
        into(darkfidBundleAssetsDir)
        into("darkfid") {
            from(rootProject.layout.projectDirectory.dir("artifacts/darkfid"))
            include("**/darkfid_exec")
        }
    }

val mobileFfiJniLibsDir =
    objects.directoryProperty().apply {
        set(layout.buildDirectory.dir("generated-mobile-ffi-jniLibs"))
    }

val embeddedDaemonJniLibsDir =
    objects.directoryProperty().apply {
        set(layout.buildDirectory.dir("generated-embedded-daemon-jniLibs"))
    }

/**
 * Copies packaged daemons into jniLibs as `.so` so Android 10+ can exec them from
 * [android.content.pm.ApplicationInfo.nativeLibraryDir] (see [EmbeddedPackagedExecutable]).
 */
val syncEmbeddedDaemonJniLibs =
    tasks.register<Sync>("syncEmbeddedDaemonJniLibs") {
        group = "build"
        description =
            "Bundles darkirc_exec / darkfid_exec into jniLibs as lib*_embedded.so."
        into(embeddedDaemonJniLibsDir)
        from(rootProject.layout.projectDirectory.dir("artifacts/darkirc")) {
            include("**/darkirc_exec")
            rename("darkirc_exec", "libdarkirc_embedded.so")
        }
        from(rootProject.layout.projectDirectory.dir("artifacts/darkfid")) {
            include("**/darkfid_exec")
            rename("darkfid_exec", "libdarkfid_embedded.so")
        }
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
        from(rootProject.layout.projectDirectory.dir("rust/target/aarch64-linux-android/release")) {
            include("libdarkfi_mobile_ffi.so")
            into("arm64-v8a")
        }
        from(rootProject.layout.projectDirectory.dir("rust/target/x86_64-linux-android/release")) {
            include("libdarkfi_mobile_ffi.so")
            into("x86_64")
        }
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncDarkircArtifacts,
        ) { darkircBundleAssetsDir }
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncDarkfidArtifacts,
        ) { darkfidBundleAssetsDir }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            syncMobileFfiArtifacts,
        ) { mobileFfiJniLibsDir }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            syncEmbeddedDaemonJniLibs,
        ) { embeddedDaemonJniLibsDir }
    }
}

tasks.configureEach {
    if (name.endsWith("JniLibFolders") && name.startsWith("merge")) {
        dependsOn(syncEmbeddedDaemonJniLibs)
    }
}

android {
    namespace = "com.nighthawkapps.lib.android.sdk"

    lint {
        lintConfig = file("lint.xml")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    defaultConfig {
        consumerProguardFiles("proguard-consumer.txt")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    androidResources {
        noCompress += "darkirc_exec"
        noCompress += "darkfid_exec"
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
    // `@aar` bundles per-ABI `libjnidispatch.so` (required on Android; plain `jna` jar does not).
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.crypto.tink:tink-android:1.8.0")
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

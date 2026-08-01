plugins {
    id("com.android.library")
    id("stealth.android-build-conventions")
    id("stealth.jacoco-conventions")
}

android {
    namespace = "com.nighthawkapps.lib.android.test"
    resourcePrefix = "darkfi_wallet_test_"
}

dependencies {
    api(libs.bundles.androidx.test)
}

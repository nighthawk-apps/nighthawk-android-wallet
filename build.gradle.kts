buildscript {
    dependencyLocking {
        // This property is treated specially, as it is not defined by default in the root gradle.properties
        // and declaring it in the root gradle.properties is ignored by included builds. This only picks up
        // a value declared as a system property, a command line argument, or a an environment variable.
        val isDependencyLockingEnabled = if (project.hasProperty("ZCASH_IS_DEPENDENCY_LOCKING_ENABLED")) {
            project.property("ZCASH_IS_DEPENDENCY_LOCKING_ENABLED").toString().toBoolean()
        } else {
            true
        }

        if (isDependencyLockingEnabled) {
            lockAllConfigurations()
        }
    }

    repositories {
        val isRepoRestrictionEnabled = true

//        val googleGroups = listOf<String>(
//            "com.google.firebase",
//            "com.google.gms",
//            "com.google.android.gms"
//        )

//        google {
//            if (isRepoRestrictionEnabled) {
//                content {
//                    googleGroups.forEach { includeGroup(it) }
//                }
//            }
//        }
        // We don't use mavenCentral now, but in the future we may want to use it for some dependencies
        // mavenCentral {
        //     if (isRepoRestrictionEnabled) {
        //         content {
        //             googleGroups.forEach { excludeGroup(it) }
        //         }
        //     }
        // }
        gradlePluginPortal {
            if (isRepoRestrictionEnabled) {
                content {
//                    googleGroups.forEach { excludeGroup(it) }
                }
            }
        }
    }

    dependencies {
//        classpath("com.google.gms:google-services:${project.property("GOOGLE_PLAY_SERVICES_GRADLE_PLUGIN_VERSION")}")
    }
}

plugins {
    id("com.github.ben-manes.versions")
    id("secant.detekt-conventions")
    id("secant.ktlint-conventions")
    id("secant.rosetta-conventions")
}

val uiIntegrationModuleName: String = projects.uiIntegrationTest.name
val uiScreenshotModuleName: String = projects.uiScreenshotTest.name

tasks {
    withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
        gradleReleaseChannel = "current"

        resolutionStrategy {
            componentSelection {
                all {
                    if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
                        reject("Unstable")
                    }
                }
            }
        }
    }

    register("checkProperties") {
        // Ensure that developers do not change default values of certain properties directly
        // in the repo, but instead set them in their local ~/.gradle/gradle.properties file
        // (or use command line arguments)
        val expectedPropertyValues = mapOf(
            "ZCASH_IS_TREAT_WARNINGS_AS_ERRORS" to "true",
            "IS_KOTLIN_TEST_COVERAGE_ENABLED" to "true",
            "IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED" to "false",
            "IS_USE_TEST_ORCHESTRATOR" to "false",
            "IS_CRASH_ON_STRICT_MODE_VIOLATION" to "false",

            "ZCASH_FIREBASE_TEST_LAB_API_KEY_PATH" to "",
            "ZCASH_FIREBASE_TEST_LAB_PROJECT" to "",

            "ZCASH_EMULATOR_WTF_API_KEY" to "",

            "IS_MINIFY_ENABLED" to "true",

            "ZCASH_RELEASE_APP_NAME" to "Nighthawk",
            "ZCASH_RELEASE_PACKAGE_NAME" to "com.nighthawkapps.wallet.android",
            "ZCASH_SUPPORT_EMAIL_ADDRESS" to "nighthawkwallet@protonmail.com",
            "IS_SECURE_SCREEN_PROTECTION_ACTIVE" to "true",
            "IS_DARK_MODE_ENABLED" to "false",

            "ZCASH_DEBUG_KEYSTORE_PATH" to "",
            "ZCASH_RELEASE_KEYSTORE_PATH" to "${rootProject.projectDir}/nighthawkpublic.keystore",
            "ZCASH_RELEASE_KEYSTORE_PASSWORD" to "android",
            "ZCASH_RELEASE_KEY_ALIAS" to "key0",
            "ZCASH_RELEASE_KEY_ALIAS_PASSWORD" to "android",

            "IS_SIGN_RELEASE_BUILD_WITH_DEBUG_KEY" to "false",

            "ZCASH_GOOGLE_PLAY_SERVICE_KEY_FILE_PATH" to "",
            "ZCASH_GOOGLE_PLAY_DEPLOY_MODE" to "build",

            "SDK_INCLUDED_BUILD_PATH" to "",
            "BIP_39_INCLUDED_BUILD_PATH" to ""
        )

        val actualPropertyValues = project.properties.filterKeys { it in expectedPropertyValues.keys }

        doLast {
            val warnings = expectedPropertyValues.filter { (key, value) ->
                actualPropertyValues[key].toString() != value
            }.map { "Property ${it.key} does not have expected value \"${it.value}\"" }

            if (warnings.isNotEmpty()) {
                throw GradleException(warnings.joinToString(separator = "\n"))
            }
        }
    }
}

val unstableKeywords = listOf("alpha", "beta", "rc", "m", "ea", "build")

fun isNonStable(version: String): Boolean {
    val versionLowerCase = version.lowercase()

    return unstableKeywords.any { versionLowerCase.contains(it) }
}

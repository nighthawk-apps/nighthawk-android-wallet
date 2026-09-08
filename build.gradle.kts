import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentSelectionWithCurrent
import org.gradle.api.artifacts.ComponentSelection

buildscript {
    dependencyLocking {
        // This property is treated specially, as it is not defined by default in the root gradle.properties
        // and declaring it in the root gradle.properties is ignored by included builds. This only picks up
        // a value declared as a system property, a command line argument, or a an environment variable.
        val isDependencyLockingEnabled = if (project.hasProperty("WALLET_IS_DEPENDENCY_LOCKING_ENABLED")) {
            project.property("WALLET_IS_DEPENDENCY_LOCKING_ENABLED").toString().toBoolean()
        } else {
            true
        }

        if (isDependencyLockingEnabled) {
            lockAllConfigurations()
        }
    }
}

plugins {
    id("com.github.ben-manes.versions")
    id("stealth.detekt-conventions")
    id("stealth.ktlint-conventions")
    id("stealth.rosetta-conventions")
}

val uiIntegrationModuleName: String = projects.uiIntegrationTest.name
val uiScreenshotModuleName: String = projects.uiScreenshotTest.name

tasks {
    withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
        gradleReleaseChannel = "current"

        resolutionStrategy {
            componentSelection {
                all { selection: ComponentSelectionWithCurrent ->
                    val isCandidateNonStable = isNonStable(selection.candidate.version)
                    val isCurrentNonStable = isNonStable(selection.currentVersion)
                    if (isCandidateNonStable && !isCurrentNonStable) {
                        selection.reject("Unstable")
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
            "WALLET_IS_TREAT_WARNINGS_AS_ERRORS" to "true",
            "IS_KOTLIN_TEST_COVERAGE_ENABLED" to "true",
            "IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED" to "false",
            "IS_USE_TEST_ORCHESTRATOR" to "false",
            "IS_CRASH_ON_STRICT_MODE_VIOLATION" to "false",

            "WALLET_FIREBASE_TEST_LAB_API_KEY_PATH" to "",
            "WALLET_FIREBASE_TEST_LAB_PROJECT" to "",

            "WALLET_EMULATOR_WTF_API_KEY" to "",

            "IS_MINIFY_ENABLED" to "true",

            "WALLET_RELEASE_APP_NAME" to "Nighthawk",
            "WALLET_RELEASE_PACKAGE_NAME" to "com.nighthawkwallet.android",
            "WALLET_SUPPORT_EMAIL_ADDRESS" to "nighthawkwallet@protonmail.com",
            "IS_SECURE_SCREEN_PROTECTION_ACTIVE" to "true",
            "IS_DARK_MODE_ENABLED" to "true",

            "WALLET_DEBUG_KEYSTORE_PATH" to "",
            "WALLET_RELEASE_KEYSTORE_PATH" to "${rootProject.projectDir}/nighthawkpublic.keystore",
            "WALLET_RELEASE_KEYSTORE_PASSWORD" to "android",
            "WALLET_RELEASE_KEY_ALIAS" to "key0",
            "WALLET_RELEASE_KEY_ALIAS_PASSWORD" to "android",

            "IS_SIGN_RELEASE_BUILD_WITH_DEBUG_KEY" to "false",

            "WALLET_GOOGLE_PLAY_SERVICE_KEY_FILE_PATH" to "",
            "WALLET_GOOGLE_PLAY_DEPLOY_MODE" to "build",

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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

private val stealthAndroidLocaleFilters =
    listOf(
        "en",
        "en-rUS",
        "en-rGB",
        "en-rAU",
        "ar",
        "bg",
        "bn",
        "da",
        "de",
        "el",
        "es",
        "fi",
        "fr",
        "he",
        "hi",
        "id",
        "is",
        "it",
        "ja",
        "kn",
        "ko",
        "mr",
        "ne",
        "nl",
        "no",
        "pa",
        "pt",
        "ro",
        "ru",
        "sa",
        "ta",
        "te",
        "th",
        "ug",
        "uk",
        "ur",
        "zh",
    )

/** Desugaring is enabled in stealth below; AGP expects this artifact on [Configuration] coreLibraryDesugaring. */
fun Project.ensureStealthAndroidCoreLibraryDesugaringDependency() {
    val version = project.property("CORE_LIBRARY_DESUGARING_VERSION").toString()
    dependencies.add(
        "coreLibraryDesugaring",
        "com.android.tools:desugar_jdk_libs:$version",
    )
}

/** AGP 9 compiles Kotlin without [org.jetbrains.kotlin.android]; tweak flags on JVM compile tasks. */
fun Project.configureStealthKotlinJvmCompileTasksFromAgpBuiltInKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(project.property("ANDROID_JVM_TARGET").toString()))
            allWarningsAsErrors.set(project.property("WALLET_IS_TREAT_WARNINGS_AS_ERRORS").toString().toBoolean())
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }
}

/** compileSdk 37 + minor 1 ⇒ platform API 37.1 (additive APIs; targetSdk stays 37). */
fun Project.applyStealthCompileSdk(android: CommonExtension) {
    android.compileSdk = property("ANDROID_COMPILE_SDK_VERSION").toString().toInt()
    findProperty("ANDROID_COMPILE_SDK_MINOR")?.toString()?.toIntOrNull()?.takeIf { it > 0 }?.let {
        android.compileSdkMinor = it
    }
}

pluginManager.withPlugin("com.android.application") {
    project.extensions.configure<ApplicationExtension>("android") {
        applyStealthCompileSdk(this)
        ndkVersion = project.property("ANDROID_NDK_VERSION").toString()

        defaultConfig {
            minSdk = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()
            targetSdk = project.property("ANDROID_TARGET_SDK_VERSION").toString().substringBefore('.').toInt()

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            testInstrumentationRunnerArguments["useTestStorageService"] = "true"
            if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
                testInstrumentationRunnerArguments["clearPackageData"] = "true"
            }
        }

        lint {
            disable.add("MissingTranslation")
            disable.add("ExtraTranslation")
        }

        androidResources {
            localeFilters += stealthAndroidLocaleFilters
        }

        compileOptions {
            isCoreLibraryDesugaringEnabled = true

            val javaVersion = JavaVersion.toVersion(project.property("ANDROID_JVM_TARGET").toString())
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }

        buildTypes {
            getByName("debug").apply {
                val coverageEnabled =
                    project.property("IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED").toString().toBoolean()
                enableAndroidTestCoverage = coverageEnabled
                enableUnitTestCoverage = coverageEnabled
            }
        }

        signingConfigs {
            val debugKeystorePath = project.property("WALLET_DEBUG_KEYSTORE_PATH").toString()
            if (!debugKeystorePath.isNullOrBlank()) {
                getByName("debug").apply {
                    storeFile = File(debugKeystorePath)
                }
            }
        }

        testOptions {
            animationsDisabled = true
            if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
                execution = "ANDROIDX_TEST_ORCHESTRATOR"
            }
            @Suppress("UnstableApiUsage")
            managedDevices {
                localDevices {
                    create("pixel2Min") {
                        device = "Pixel 2"
                        apiLevel = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt().coerceAtLeast(27)
                        systemImageSource = "google"
                    }
                    create("pixel2Target") {
                        device = "Pixel 2"
                        apiLevel = project.property("ANDROID_TARGET_SDK_VERSION").toString().substringBefore('.').toInt()
                        systemImageSource = "google"
                    }
                }
                groups {
                    create("defaultDevices") {
                        targetDevices.addAll(allDevices.toList())
                    }
                }
            }
        }

        packaging {
            resources {
                excludes.addAll(
                    listOf(
                        "META-INF/AL2.0",
                        "META-INF/ASL2.0",
                        "META-INF/DEPENDENCIES",
                        "META-INF/LGPL2.1",
                        "META-INF/LICENSE",
                        "META-INF/LICENSE-notice.md",
                        "META-INF/LICENSE.md",
                        "META-INF/LICENSE.txt",
                        "META-INF/NOTICE",
                        "META-INF/NOTICE.txt",
                        "META-INF/license.txt",
                        "META-INF/notice.txt"
                    )
                )
            }
        }
    }
}

pluginManager.withPlugin("com.android.library") {
    project.extensions.configure<LibraryExtension>("android") {
        applyStealthCompileSdk(this)
        ndkVersion = project.property("ANDROID_NDK_VERSION").toString()

        defaultConfig {
            minSdk = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            File(project.projectDir, "proguard-consumer.txt").takeIf { it.exists() }
                ?.let { consumerProguardFiles(it) }

            testInstrumentationRunnerArguments["useTestStorageService"] = "true"
            if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
                testInstrumentationRunnerArguments["clearPackageData"] = "true"
            }
        }

        lint {
            disable.add("MissingTranslation")
            disable.add("ExtraTranslation")
        }

        compileOptions {
            isCoreLibraryDesugaringEnabled = true

            val javaVersion = JavaVersion.toVersion(project.property("ANDROID_JVM_TARGET").toString())
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }

        buildTypes {
            getByName("debug").apply {
                val coverageEnabled =
                    project.property("IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED").toString().toBoolean()
                enableAndroidTestCoverage = coverageEnabled
                enableUnitTestCoverage = coverageEnabled
            }
        }

        signingConfigs {
            val debugKeystorePath = project.property("WALLET_DEBUG_KEYSTORE_PATH").toString()
            if (!debugKeystorePath.isNullOrBlank()) {
                getByName("debug").apply {
                    storeFile = File(debugKeystorePath)
                }
            }
        }

        testOptions {
            animationsDisabled = true
            if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
                execution = "ANDROIDX_TEST_ORCHESTRATOR"
            }
        }

        packaging {
            resources {
                excludes.addAll(
                    listOf(
                        "META-INF/AL2.0",
                        "META-INF/ASL2.0",
                        "META-INF/DEPENDENCIES",
                        "META-INF/LGPL2.1",
                        "META-INF/LICENSE",
                        "META-INF/LICENSE-notice.md",
                        "META-INF/LICENSE.md",
                        "META-INF/LICENSE.txt",
                        "META-INF/NOTICE",
                        "META-INF/NOTICE.txt",
                        "META-INF/license.txt",
                        "META-INF/notice.txt"
                    )
                )
            }
        }
    }
}

pluginManager.withPlugin("com.android.test") {
    project.extensions.configure<TestExtension>("android") {
        applyStealthCompileSdk(this)
        ndkVersion = project.property("ANDROID_NDK_VERSION").toString()

        defaultConfig {
            minSdk = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            testInstrumentationRunnerArguments["useTestStorageService"] = "true"
            if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
                testInstrumentationRunnerArguments["clearPackageData"] = "true"
            }
        }

        lint {
            disable.add("MissingTranslation")
            disable.add("ExtraTranslation")
        }

        compileOptions {
            isCoreLibraryDesugaringEnabled = true

            val javaVersion = JavaVersion.toVersion(project.property("ANDROID_JVM_TARGET").toString())
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }

        buildTypes {
            getByName("debug").apply {
                val coverageEnabled =
                    project.property("IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED").toString().toBoolean()
                enableAndroidTestCoverage = coverageEnabled
                enableUnitTestCoverage = coverageEnabled
            }
        }

        testOptions {
            animationsDisabled = true
            if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
                execution = "ANDROIDX_TEST_ORCHESTRATOR"
            }
        }

        packaging {
            resources {
                excludes.addAll(
                    listOf(
                        "META-INF/AL2.0",
                        "META-INF/ASL2.0",
                        "META-INF/DEPENDENCIES",
                        "META-INF/LGPL2.1",
                        "META-INF/LICENSE",
                        "META-INF/LICENSE-notice.md",
                        "META-INF/LICENSE.md",
                        "META-INF/LICENSE.txt",
                        "META-INF/NOTICE",
                        "META-INF/NOTICE.txt",
                        "META-INF/license.txt",
                        "META-INF/notice.txt"
                    )
                )
            }
        }
    }
}

ensureStealthAndroidCoreLibraryDesugaringDependency()
configureStealthKotlinJvmCompileTasksFromAgpBuiltInKotlin()

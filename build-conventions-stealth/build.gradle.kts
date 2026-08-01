plugins {
    `kotlin-dsl`
}

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

dependencies {
    implementation(libs.gradle)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlinx.kover.gradle.plugin)
    val kotlinVersion = project.findProperty("KOTLIN_VERSION")?.toString() ?: "2.2.10"
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
}

plugins {
    `kotlin-dsl-base`
}

buildscript {
    dependencyLocking {
        // If configuring buildSrc fails after a Gradle wrapper upgrade (embedded Kotlin vs locked kotlin-dsl),
        // regenerate locks:
        // ./gradlew help -PWALLET_IS_DEPENDENCY_LOCKING_ENABLED=false --write-locks && ./gradlew help --write-locks
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

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.org.eclipse.jgit)
}

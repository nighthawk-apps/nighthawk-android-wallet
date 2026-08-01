dependencyLocking {
    val isDependencyLockingEnabled = if (project.hasProperty("WALLET_IS_DEPENDENCY_LOCKING_ENABLED")) {
        project.property("WALLET_IS_DEPENDENCY_LOCKING_ENABLED").toString().toBoolean()
    } else {
        true
    }

    if (isDependencyLockingEnabled) {
        lockAllConfigurations()
    }
}

tasks {
    register("resolveAll") {
        doLast {
            configurations.filter {
                // Add any custom filtering on the configurations to be resolved
                it.isCanBeResolved
            }.forEach { it.resolve() }
        }
    }
}

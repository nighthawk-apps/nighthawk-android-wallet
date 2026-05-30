@file:Suppress("DEPRECATION")

import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
    extensions.configure<ComposeCompilerGradlePluginExtension>("composeCompiler") {
        generateFunctionKeyMetaClasses.set(false)

        if (project.providers.gradleProperty("IS_ENABLE_COMPOSE_COMPILER_METRICS").orNull == "true") {
            metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        }
        if (project.providers.gradleProperty("IS_ENABLE_COMPOSE_COMPILER_REPORTS").orNull == "true") {
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        }
    }
}

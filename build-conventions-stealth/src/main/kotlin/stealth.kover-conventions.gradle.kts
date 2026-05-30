import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

pluginManager.withPlugin("org.jetbrains.kotlinx.kover") {
    extensions.findByType<KoverProjectExtension>()?.apply {
        if (!project.property("IS_KOTLIN_TEST_COVERAGE_ENABLED").toString().toBoolean()) {
            disable()
        }

        reports {
            total {
                html {
                    onCheck.set(true)
                    title.set("Kover Report")
                }
                xml {
                    onCheck.set(true)
                }
            }
        }
    }
}
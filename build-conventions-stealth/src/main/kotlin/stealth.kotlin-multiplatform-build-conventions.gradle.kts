pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.findByType<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>()?.apply {
//        jvmToolchain(project.property("JVM_TOOLCHAIN").toString().toInt())
        jvm()

        targets.all {
            compilations.all {
                compileTaskProvider.configure {
                    compilerOptions {
                        allWarningsAsErrors.set(
                            project.property("WALLET_IS_TREAT_WARNINGS_AS_ERRORS").toString().toBoolean()
                        )
                        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn", "-opt-in=kotlin.time.ExperimentalTime")
                    }
                }
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(project.property("KOTLIN_JVM_TARGET").toString().toInt())
}

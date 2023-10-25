package ru.raysmith.setupapp

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.the
import java.io.File

abstract class SetupApplicationsTask : DefaultTask() {

    @get:Input
    abstract val envs: SetProperty<String>

    @get:Input
    abstract val env: Property<String>

    @get:Optional
    @get:Input
    abstract val prod: Property<Boolean>

    @get:Internal
    val regex by lazy {
        "(commonMain|jvmMain)([\\\\/])resources([\\\\/])(${envs.get().filter { it != env.get() }.joinToString("|")})".also {
            logger.info("regex: $it, ${envs.get().size}, ${envs.getOrElse(setOf("dev", "prod")).size}")
        }.toRegex()
    }

    init {
        project.the<SourceSetContainer>().findByName("main")?.apply {
            val config = project.configurations.getByName("compileClasspath")

            config.allDependencies.forEach { dependency ->
                if (dependency is ProjectDependency) {
                    dependency.dependencyProject.tasks.findByName("jvmJar")?.also {
                        dependsOn(it)
                    }
                }
            }
        }
    }

    @TaskAction
    fun run() {
        with(project) {
            if (pluginManager.hasPlugin("org.gradle.application")) {
                logger.info("Start setup application for '$name' in environment '${env.get()}'")

                fun SourceSet.copyResources(resources: SourceDirectorySet?) {
                    resources?.forEach {
                        if (!it.path.contains(regex)) {
                            logger.info("Add ${it.path} -> ${output.resourcesDir!!.path} for ${project.name} [${this.name}]")
                            copy {
                                from(it.path)
                                include { true }
                                into(output.resourcesDir!!.path)
                                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                            }
                        }
                    }
                }

                val useProdWebpack = prod.orElse(false).get() || project.gradle.startParameter.taskNames.contains("installDist")
                val jsBrowserTaskName = if (useProdWebpack) "jsBrowserProductionWebpack" else "jsBrowserDevelopmentWebpack"

                project.the<SourceSetContainer>().findByName("main")?.apply {
                    val config = project.configurations.getByName("compileClasspath")

                    config.allDependencies.forEach { dependency ->
                        if (dependency is ProjectDependency) {

                            copyResources(dependency.dependencyProject.kotlin.sourceSets.named("commonMain").orNull?.resources)
                            copyResources(dependency.dependencyProject.kotlin.sourceSets.named("jvmMain").orNull?.resources)

                            val webpackTask = dependency.dependencyProject.tasks.findByName(jsBrowserTaskName)
                            if (webpackTask != null) {
                                check(webpackTask is org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack)
                                val file = File(webpackTask.outputDirectory.asFile.get(), webpackTask.mainOutputFileName.get())
                                if (file.exists()) {
                                    logger.info("Add ${file.path} -> ${output.resourcesDir!!.path} for ${project.name} [${this.name}]")
                                    copy {
                                        from(file)
                                        include { true }
                                        into(output.resourcesDir!!.path)
                                        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val Project.kotlin: org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension get() =
    (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("kotlin")
            as org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

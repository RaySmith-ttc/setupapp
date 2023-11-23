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
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import java.io.File

abstract class SetupApplicationsTask : DefaultTask() {

    @get:Input
    abstract val envs: SetProperty<String>

    @get:Input
    abstract val sourceSets: SetProperty<String>

    @get:Input
    abstract val env: Property<String>

    @get:Optional
    @get:Input
    abstract val prod: Property<Boolean>

    @get:Internal
    val regex by lazy {
        "(${sourceSets.get().joinToString("|")})([\\\\/])resources([\\\\/])(${envs.get().filter { it != env.get() }.joinToString("|")})".toRegex()
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
                        if (!it.path.contains(regex) && output.resourcesDir != null) {
                            if (!output.resourcesDir!!.resolve(it.name).exists()) {
                                logger.info("Add ${it.path} -> ${output.resourcesDir!!.path} for ${project.name} [${this.name}]")
                                copy {
                                    from(it.path)
                                    include { true }
                                    into(output.resourcesDir!!.path)
                                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                                }
                            } else {
                                logger.info("Skip ${it.path}: already exists")
                            }
                        }
                    }
                }

                val useProdWebpack = prod.orElse(false).get() || project.gradle.startParameter.taskNames.contains("installDist")
                val jsBrowserTaskName = if (useProdWebpack) "jsBrowserProductionWebpack" else "jsBrowserDevelopmentWebpack"

                project.the<SourceSetContainer>().findByName("main")?.apply {
                    val config = project.configurations.getByName("compileClasspath")

                    sourceSets.get().forEach { sourceSet ->
                        copyResources(project.kotlinExtension.sourceSets.firstOrNull { it.name == sourceSet }?.resources)
                    }

                    config.allDependencies.forEach { dependency ->
                        if (dependency is ProjectDependency) {
                            sourceSets.get().forEach { sourceSet ->
                                copyResources(dependency.dependencyProject.kotlinExtension.sourceSets.firstOrNull { it.name == sourceSet }?.resources)
                            }

                            val webpackTask = dependency.dependencyProject.tasks.findByName(jsBrowserTaskName)
                            if (webpackTask != null && output.resourcesDir != null) {
                                check(webpackTask is org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack)
                                val file = File(webpackTask.outputDirectory.asFile.get(), webpackTask.mainOutputFileName.get())

                                if (file.exists()) {
                                    if (!output.resourcesDir!!.resolve(file.name).exists()) {
                                        logger.info("1 Add ${file.path} -> ${output.resourcesDir!!.path} for ${project.name} [${this.name}]")
                                        copy {
                                            from(file)
                                            include { true }
                                            into(output.resourcesDir!!.path)
                                            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                                        }
                                    } else {
                                        logger.info("Skip ${file.path}: already exists")
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
    extensions.getByName("kotlin") as org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

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
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

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

    private fun Path.findParent(target: String): Path? {
        logger.info(this.absolutePathString())
        if (name == target) return this
        if (parent == null) return null

        return parent.findParent(target)
    }

    private val useProdWebpack by lazy { prod.orElse(false).get() || project.gradle.startParameter.taskNames.contains("installDist") }
    private val jsBrowserTaskName by lazy { if (useProdWebpack) "jsBrowserProductionWebpack" else "jsBrowserDevelopmentWebpack" }
    private fun Project.copyAllResources(sourceSet: SourceSet, from: Project) {
        sourceSets.get().forEach { ss ->
            copyResources(sourceSet, from.kotlinExtension.sourceSets.firstOrNull { it.name == ss }?.resources, ss)
        }

        val webpackTask = from.tasks.findByName(jsBrowserTaskName)
        if (webpackTask != null && sourceSet.output.resourcesDir != null) {
            check(webpackTask is org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack)
            val file = File(webpackTask.outputDirectory.asFile.get(), webpackTask.mainOutputFileName.get())

            if (file.exists()) {
                if (!sourceSet.output.resourcesDir!!.resolve(file.name).exists()) {
                    logger.info("Add ${file.path} -> ${sourceSet.output.resourcesDir!!.path} for ${project.name} [${this.name}]")
                    copy {
                        from(file)
                        include { true }
                        into(sourceSet.output.resourcesDir!!.path)
                        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    }
                } else {
                    logger.info("Skip ${file.path}: already exists")
                }
            }
        }
    }

    private fun Project.copyResources(projectSourceSet: SourceSet, resources: SourceDirectorySet?, sourceSet: String) {
        resources?.forEach {
            if (!it.path.contains(regex) && projectSourceSet.output.resourcesDir != null) {
                if (!projectSourceSet.output.resourcesDir!!.resolve(it.name).exists()) {
                    val resourcesFolder = "src\\${sourceSet}\\resources\\"

                    val isParentResourcesRoot = it.parent.indexOf(resourcesFolder) != -1

                    // resolving folders
                    val resourcePath = if (isParentResourcesRoot) {
                        // resources\foo\bar\image.jpg -> \foo\bar
                        it.path.substring(it.path.indexOf(resourcesFolder) + resourcesFolder.length).substringBeforeLast('\\')
                    } else {
                        // resources\image.jpg -> \image.jpg
                        it.path
                    }

                    val target = if (!isParentResourcesRoot || resourcePath.startsWith(env.get())) {
                        projectSourceSet.output.resourcesDir!!.path
                    } else {
                        "${projectSourceSet.output.resourcesDir!!.path}\\$resourcePath"
                    }

                    logger.info("Add ${it.path} -> $target for ${this.name} [$sourceSet]")

                    copy {
                        from(it.path)
                        include { true }
                        into(target)
                        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    }
                } else {
                    logger.info("Skip ${it.path}: already exists")
                }
            }
        }
    }

    @TaskAction
    fun run() {
        with(project) {
            if (pluginManager.hasPlugin("org.gradle.application")) {
                logger.info("Start setup application for '$name' in environment '${env.get()}'")

                project.the<SourceSetContainer>().findByName("main")?.apply {
                    output.resourcesDir?.deleteRecursively()
                    val config = project.configurations.getByName("compileClasspath")

                    copyAllResources(this, project)

                    config.allDependencies.forEach { dependency ->
                        if (dependency is ProjectDependency) {
                            copyAllResources(this, dependency.dependencyProject)
                        }
                    }
                }
            }
        }
    }
}
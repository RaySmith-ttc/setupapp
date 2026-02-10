package ru.raysmith.setupapp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import java.io.File
import java.util.LinkedHashSet

abstract class SetupApplicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(ApplicationPlugin::class.java)
        val extension = project.extensions.create<SetupApplicationPluginExtension>("setupapp")

        val task = project.tasks.register<SetupApplicationsTask>("setupapp") {
            envs.set(extension.envs)
            env.set(extension.env)
            prod.set(extension.prod)
            sourceSets.set(extension.sourceSets)
            outputDir.set(project.layout.buildDirectory.dir("setupapp/resources"))
        }

        // Настраиваем processResources сразу (источник подключится позже)
        project.tasks.named<ProcessResources>("processResources") {
            exclude(extension.envs.get())
            dependsOn(task)
            from(task.flatMap { it.outputDir })
        }

        // Откладываем вычисление зависимостей до момента, когда все проекты сконфигурированы
        project.gradle.projectsEvaluated {
            val depProjects = collectProjectDependencies(project)

            // Подключаем корни ресурсов зависимостей как входы (src/<ss>/resources)
            task.configure {
                logger.info("[SetupApplicationPlugin] Found dependency projects: ${depProjects.map { it.name }} for project ${project.name}")

                val depDirs = depProjects.map { it.projectDir }
                resourceRoots.from(
                    extension.sourceSets.map { ssets ->
                        depDirs.flatMap { dir ->
                            ssets.map { ss -> File(dir, "src/$ss/resources") }
                        }
                    }.also {
                        logger.info("[SetupApplicationPlugin] Configured resource roots: ${it.map { it.map { it.path } }}")
                    }
                )
            }

            // Решаем, какой webpack использовать
            val useProdWebpack =
                extension.prod.get() || project.gradle.startParameter.taskNames.contains("installDist")
            val jsBrowserTaskName = if (useProdWebpack) {
                "jsBrowserProductionWebpack"
            } else {
                "jsBrowserDevelopmentWebpack"
            }

            // Добавляем выходы webpack (текущий проект + зависимости), если задачи существуют
            (depProjects + project).forEach { p ->
                val webpackTask = p.tasks.findByName(jsBrowserTaskName)
                if (webpackTask is KotlinWebpack) {
                    task.configure {
                        dependsOn(webpackTask)
                        val fileProvider = project.layout.file(project.provider {
                            File(
                                webpackTask.outputDirectory.asFile.get(),
                                webpackTask.mainOutputFileName.get()
                            )
                        })
                        webpackFiles.from(fileProvider)
                    }
                }
            }
        }
    }

    private fun collectProjectDependencies(project: Project): Set<Project> {
        val result = LinkedHashSet<Project>()
        fun visit(p: Project) {
            val cfg = p.configurations.findByName("compileClasspath")
                ?: p.configurations.findByName("jvmCompileClasspath")
                ?: return
            cfg.allDependencies.forEach { d ->
                if (d is ProjectDependency) {
                    val dp = project.project(d.path)
                    if (result.add(dp)) {
                        // Гарантируем, что зависимый проект уже сконфигурирован
                        project.evaluationDependsOn(dp.path)
                        visit(dp)
                    }
                }
            }
        }
        visit(project)
        return result
    }
}
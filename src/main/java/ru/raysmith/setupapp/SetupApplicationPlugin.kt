package ru.raysmith.setupapp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

abstract class SetupApplicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(ApplicationPlugin::class.java)
        val extension = project.extensions.create<SetupApplicationPluginExtension>("setupapp")

        val task = project.tasks.register<SetupApplicationsTask>("setupapp") {
            envs.set(extension.envs)
            env.set(extension.env)
            prod.set(extension.prod)
            sourceSets.set(extension.sourceSets)
        }

        project.tasks.getByName("processResources").dependsOn(task)
    }
}
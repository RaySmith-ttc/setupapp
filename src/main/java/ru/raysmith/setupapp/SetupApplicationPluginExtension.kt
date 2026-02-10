package ru.raysmith.setupapp

import org.gradle.api.Project
import org.gradle.api.provider.Property

abstract class SetupApplicationPluginExtension(project: Project) {
    private fun env() = System.getProperty("env") ?: System.getenv("env") ?: "dev"

    val envs = project.objects.setProperty(String::class.java).convention(setOf("dev", "prod"))
    val sourceSets = project.objects.setProperty(String::class.java).convention(setOf("main", "commonMain", "jvmMain"))
    val env = project.property(defaultValue = env())
    val prod = project.property(defaultValue = env() == "prod")
}

inline fun <reified T : Any> Project.property(defaultValue: T?): Property<T> =
    objects.property(T::class.java).convention(defaultValue)
@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    `kotlin-dsl`
    kotlin("jvm") version "2.0.0"
    id("com.gradle.plugin-publish") version "1.2.1"
    `java-gradle-plugin`
}

group = "ru.raysmith"
version = "1.7"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin", version = "2.0.0"))
    implementation(gradleApi())
}

gradlePlugin {
    website.set("https://github.com/RaySmith-ttc/setupapp.git")
    vcsUrl.set("https://github.com/RaySmith-ttc/setupapp.git")

    plugins {
        create("setupapp") {
            id = "ru.raysmith.setupapp"
            displayName = "setupapp"
            description = "Inheritance of dependent modules resources with env filtering"
            tags.set(setOf("env", "resources", "multiplatform", "KMP"))
            implementationClass = "ru.raysmith.setupapp.SetupApplicationPlugin"
        }
    }
}

publishing {

}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}
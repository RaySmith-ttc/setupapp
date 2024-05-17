@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    `kotlin-dsl`
    kotlin("jvm") version "1.9.21"
    id("com.gradle.plugin-publish") version "1.2.1"
    `java-gradle-plugin`
}

group = "ru.raysmith"
version = "1.7-rc.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin", version = "2.0.0-Beta3"))
    implementation(gradleApi())
}

gradlePlugin {
    website.set("https://github.com/RaySmith-ttc")
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

tasks {
    withType<KotlinCompile> {
        kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
    }
}
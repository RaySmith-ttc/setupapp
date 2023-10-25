@file:Suppress("UnstableApiUsage")

plugins {
    `kotlin-dsl`
    kotlin("jvm") version "1.9.10"
    id("com.gradle.plugin-publish") version "1.2.1"
    `java-gradle-plugin`
}

group = "ru.raysmith"
version = "1.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin", version = "1.9.10"))
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
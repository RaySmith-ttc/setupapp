@file:Suppress("UnstableApiUsage")

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.0"
}

group = "ru.raysmith"
version = "1.9"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin", version = "2.1.0"))
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
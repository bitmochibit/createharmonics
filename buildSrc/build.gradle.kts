import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.`kotlin-dsl`
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.neoforged.net/releases")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

dependencies {
    add("implementation", "org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    add("implementation", "net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:2.0.141")
    add("implementation", "com.gradleup.shadow:shadow-gradle-plugin:8.3.6")
}

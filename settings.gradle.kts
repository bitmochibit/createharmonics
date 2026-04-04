pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        // NeoForge
        maven { url = uri("https://maven.neoforged.net/releases") }
        // SpongePowered — MixinGradle plugin
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        // Kotlin for Forge plugin repo
        maven {
            name = "Kotlin for Forge"
            url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("com.gradleup.shadow") version "8.3.6" apply false
}

rootProject.name = "createharmonics"

include("common")
// include("forge")
include("neoforge")

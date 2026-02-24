pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        // ForgeGradle
        maven {
            name = "MinecraftForge"
            url = uri("https://maven.minecraftforge.net/")
        }
        // NeoForge
        maven { url = uri("https://maven.neoforged.net/releases") }
        // SpongePowered — MixinGradle plugin
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        // Architectury plugin + Loom
        maven {
            name = "Architectury"
            url = uri("https://maven.architectury.dev")
        }
        // Kotlin for Forge plugin repo
        maven {
            name = "Kotlin for Forge"
            url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "createharmonics"

include("common")
include("forge")
include("neoforge")

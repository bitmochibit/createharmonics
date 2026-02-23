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
        // Parchment
        maven { url = uri("https://maven.parchmentmc.org") }
        // NeoForge
        maven { url = uri("https://maven.neoforged.net/releases") }
        // Mixin (MixinGradle is applied as classpath in forge build script, but resolve here too)
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        // Architectury plugin
        maven {
            name = "Architectury"
            url = uri("https://maven.architectury.dev")
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

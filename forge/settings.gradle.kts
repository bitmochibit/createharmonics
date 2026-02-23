// This file is used when running `gradle` directly inside the forge/ folder
// (standalone build).  When building from the repository root, the root
// settings.gradle.kts takes precedence and this file is ignored.
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "MinecraftForge"
            url = uri("https://maven.minecraftforge.net/")
        }
        maven { url = uri("https://maven.parchmentmc.org") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

rootProject.name = "createharmonics-forge"

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("architectury-plugin") version "3.4.162" apply false
    id("dev.architectury.loom") version "1.13.467" apply false
}

allprojects {
    group = rootProject.property("mod_group_id").toString()
    version = rootProject.property("mod_version").toString()

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        maven { url = uri("https://maven.createmod.net") }
        maven { url = uri("https://maven.ithundxr.dev/mirror") }
        maven { url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") }
        maven {
            name = "Kotlin for Forge"
            url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        }
        maven {
            name = "Architectury"
            url = uri("https://maven.architectury.dev")
        }
        maven {
            name = "Progwml6 maven (JEI)"
            url = uri("https://dvs1.progwml6.com/files/maven/")
        }
        maven {
            name = "ModMaven (JEI mirror)"
            url = uri("https://modmaven.dev")
        }
        maven {
            name = "Valkyrien Skies"
            url = uri("https://maven.valkyrienskies.org")
        }
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
    }
}

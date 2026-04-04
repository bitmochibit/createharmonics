import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.4.1"
}

subprojects {
    group = rootProject.property("mod_group_id").toString()
    version = "${rootProject.property("version_major")}.${rootProject.property("version_minor")}.${
        rootProject.property("version_patch")
    }"

    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        maven("https://maven.createmod.net") // Create, Ponder, Flywheel
        maven("https://maven.ithundxr.dev/snapshots") // Registrate
        maven { url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") }

        maven {
            name = "Kotlin for Forge"
            url = uri("https://thedarkcolour.github.io/KotlinForForge/")
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
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
        maven {
            name = "ParchmentMC"
            url = uri("https://maven.parchmentmc.org")
        }
    }

    val modAuthor = rootProject.property("mod_authors").toString()
    val minecraftVersion = rootProject.property("minecraft_version").toString()

    tasks.withType<Jar>().configureEach {
        manifest {
            attributes(
                mapOf(
                    "Specification-Title" to project.name,
                    "Specification-Vendor" to modAuthor,
                    "Specification-Version" to project.version,
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version,
                    "Implementation-Vendor" to modAuthor,
                    "Implementation-Timestamp" to
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date()),
                    "Timestamp" to System.currentTimeMillis(),
                    "Built-On-Java" to "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})",
                    "Built-On-Minecraft" to minecraftVersion,
                ),
            )
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}

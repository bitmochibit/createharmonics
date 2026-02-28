import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0" apply false
    id("xyz.wagyourtail.unimined") version "1.4.1"
}

subprojects {
    group = rootProject.property("mod_group_id").toString()
    version = "${rootProject.property("version_major")}.${rootProject.property("version_minor")}.${
        rootProject.property("version_patch")
    }"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        maven { url = uri("https://maven.createmod.net") }
        maven { url = uri("https://maven.ithundxr.dev/mirror") }
        maven { url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") }

        maven { url = uri("https://maven.firstdark.dev/releases") }
        maven { url = uri("https://mcentral.firstdark.dev/releases") }

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
            name = "Valkyrien Skies"
            url = uri("https://maven.valkyrienskies.org")
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

    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }

    tasks.named("clean") {
        delete("$rootDir/artifacts")
    }

    if (project.name != "common") {
        tasks.register("delDevJar") {
            doLast {
                val tree = fileTree("build/libs")
                tree.include("**/*-dev-shadow.jar")
                tree.include("**/*-dev.jar")
                tree.include("**/*-all.jar")
                tree.include("**/*-slim.jar")
                tree.forEach { it.delete() }
            }
        }

        tasks.named("build") {
            finalizedBy("delDevJar")
        }

        tasks.register<Copy>("copyAllArtifacts") {
            from(layout.buildDirectory.dir("libs"))
            into(rootProject.layout.projectDirectory.dir("artifacts"))
            include("*.jar")
        }

        tasks.named("build") {
            finalizedBy("copyAllArtifacts")
        }
    }
}

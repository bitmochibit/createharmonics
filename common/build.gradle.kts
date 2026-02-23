buildscript {
    repositories {
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        mavenCentral()
    }
    dependencies {
        classpath("org.spongepowered:mixingradle:0.7-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${property("kotlin_version")}")
    }
}

plugins {
    idea
    id("net.minecraftforge.gradle") version "[6.0.16,6.2)"
    id("org.parchmentmc.librarian.forgegradle") version "1.+"
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
    id("architectury-plugin") version "3.4.162"
    `java-library`
}

apply(plugin = "org.spongepowered.mixin")

// ── Properties read from common/gradle.properties ────────────────────────────
val minecraftVersion = project.property("minecraft_version") as String
val forgeVersion = project.property("forge_version") as String
val mcMappingChannel = project.property("mapping_channel") as String
val mcMappingVersion = project.property("mapping_version") as String

val modId = rootProject.property("mod_id") as String
val modGroupId = rootProject.property("mod_group_id") as String
val modVersion = rootProject.property("mod_version") as String
val kotlinVersion = rootProject.property("kotlin_version") as String

val createVersion = project.property("create_version") as String
val ponderVersion = project.property("ponder_version") as String
val flywheelVersion = project.property("flywheel_version") as String
val registrateVersion = project.property("registrate_version") as String
val architecturyApiVersion = project.property("architectury_api_version") as String

group = modGroupId
version = modVersion

// ── Java / Kotlin toolchain ────────────────────────────────────────────────────
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    // Expose compiled sources and resources to consumer subprojects
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

// ── ForgeGradle — needed only to get a deobfuscated Minecraft jar ──────────────
fun Project.minecraft(configure: net.minecraftforge.gradle.userdev.UserDevExtension.() -> Unit) =
    extensions.configure("minecraft", configure)

minecraft {
    mappings(mcMappingChannel, mcMappingVersion)

    // No run configurations — the common module is never run directly.
    runs {}
}

configure<org.spongepowered.asm.gradle.plugins.MixinExtension> {
    add(sourceSets.main.get(), "$modId.refmap.json")
    config("$modId.common.mixins.json")
}

sourceSets {
    main {
        ext["refMap"] = "$modId.refmap.json"
    }
}

// ── Repositories ──────────────────────────────────────────────────────────────
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
}

val fg = extensions.getByType<net.minecraftforge.gradle.userdev.DependencyManagementExtension>()

// ── Dependencies ──────────────────────────────────────────────────────────────
dependencies {
    // Minecraft — brings in the obfuscated jar so our code can reference MC APIs.
    // Marked compileOnly so it is NOT re-exported; each loader provides Minecraft at runtime.
    "minecraft"("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    // Mixin annotation processor (needed by IDEs for refmap generation)
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:0.4.1")!!)

    // Kotlin stdlib — exported to consumers via `api` so they don't need to re-declare it
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")

    // kotlinx libraries — consumed by the shared code; loaders inherit these transitively
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Architectury API — common/platform-agnostic module for multiplatform utilities
    compileOnly(fg.deobf("dev.architectury:architectury:$architecturyApiVersion"))

    // Create — only the API surface is needed for common code.
    // Loader-specific Create artifacts are added in forge/neoforge subprojects.
    compileOnly(fg.deobf("com.simibubi.create:create-$minecraftVersion:$createVersion:slim"))
    compileOnly(fg.deobf("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion"))
    compileOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersion:$flywheelVersion"))
    compileOnly(fg.deobf("com.tterrag.registrate:Registrate:$registrateVersion"))
}

// ── Encoding ──────────────────────────────────────────────────────────────────
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-AreobfSrgFile=${project.file("build/createSrgToMcp/output.srg").absolutePath}",
            "-AoutSrgFile=${project.file("build/createSrgToMcp/output.srg").absolutePath}",
            "-AoutRefMapFile=${project.file("build/resources/main/$modId.refmap.json").absolutePath}",
        ),
    )
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.gradleup.shadow")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
    id("net.neoforged.moddev") version "2.0.141"
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

base.archivesName.set("${rootProject.property("mod_name")}-Common-${rootProject.property("minecraft_version")}")

val minecraftVersionProp = rootProject.property("minecraft_version").toString()
val parchmentMinecraftProp = rootProject.property("parchment_minecraft").toString()
val parchmentVersionProp = rootProject.property("parchment_version").toString()
val kotlinVersion = rootProject.property("kotlin_version").toString()
val kotlinCoroutinesVersion = rootProject.property("kotlin_coroutines_version").toString()
val kotlinSerializationVersion = rootProject.property("kotlin_serialization_version").toString()

val createVersion = rootProject.property("create_version").toString()
val ponderVersion = rootProject.property("ponder_version").toString()
val flywheelVersion = rootProject.property("flywheel_version").toString()
val registrateVersion = rootProject.property("registrate_version").toString()

val vs2Version = rootProject.property("vs2_version").toString()
val vsCoreVersion = rootProject.property("vs_core_version").toString()

val neoForgeVersion = rootProject.property("neo_version").toString()

// ── BuildConfig generation ────────────────────────────────────────────────────

val generateBuildConfigTask =
    tasks.register("generateBuildConfig") {
        print("Generating BuildConfig.kt")
        val outputDir =
            layout.buildDirectory
                .dir("generated/sources/buildConfig")
                .get()
                .asFile
        outputs.dir(outputDir)

        doLast {
            val isCurseForge = project.hasProperty("curseforge")
            val file = File("$outputDir/me/mochibit/createharmonics/BuildConfig.kt")
            file.parentFile.mkdirs()
            file.writeText(
                """
                @file:Suppress("MayBeConstant")

                package me.mochibit.createharmonics
                  
                object BuildConfig {
                    val IS_CURSEFORGE = $isCurseForge
                    val PLATFORM = "${if (isCurseForge) "CurseForge" else "Modrinth"}"
                }
                """.trimIndent(),
            )
        }
    }

sourceSets["main"].kotlin.srcDir(
    generateBuildConfigTask.map {
        layout.buildDirectory.dir("generated/sources/buildConfig").get()
    },
)

tasks.named("compileKotlin") {
    dependsOn(generateBuildConfigTask)
}

// ── NeoForge setup ────────────────────────────────────────────────────────────
// Using NeoForge as the compile base for common gives us deobfuscated Minecraft
// + NeoForge APIs without any extra shims. Nothing loader-specific should be
// written here — just use it for compilation of shared code.

neoForge {
    version = neoForgeVersion

    if (file("src/main/resources/META-INF/accesstransformer.cfg").exists()) {
        accessTransformers.from(file("src/main/resources/META-INF/accesstransformer.cfg"))
    }

    parchment {
        enabled = true
        minecraftVersion = parchmentMinecraftProp
        mappingsVersion = parchmentVersionProp
    }
}

// ── Dependencies ──────────────────────────────────────────────────────────────

dependencies {
    shadow("org.tukaani:xz:1.11")
    compileOnly("org.tukaani:xz:1.11")

    // Mixin annotation processor
    compileOnly("org.spongepowered:mixin:0.8.5")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation(kotlin("reflect"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")

    // Create — NeoForge 1.21.1 coordinates
    compileOnly("com.simibubi.create:create-$minecraftVersionProp:$createVersion:slim") { isTransitive = false }
    compileOnly("net.createmod.ponder:ponder-neoforge:$ponderVersion+mc$minecraftVersionProp") { isTransitive = false }
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-$minecraftVersionProp:$flywheelVersion")
    compileOnly("com.tterrag.registrate:Registrate:$registrateVersion") { isTransitive = false }

    // VS2 — Unavailable for 1.21
//    modCompileOnly("org.valkyrienskies:valkyrienskies-121-neoforge:$vs2Version") { isTransitive = false }
//    modCompileOnly("org.valkyrienskies.core:api:$vsCoreVersion") {
//        exclude(group = "org.joml")
//    }
//    modCompileOnly("org.valkyrienskies.core:internal:$vsCoreVersion") {
//        exclude(group = "org.joml")
//    }
//    modCompileOnly("org.valkyrienskies.core:util:$vsCoreVersion") {
//        exclude(group = "org.joml")
//    }
}

// ── Artifact publication for submodules ───────────────────────────────────────

project.configurations.create("commonJava").apply {
    isCanBeResolved = false
    isCanBeConsumed = true
}

project.configurations.create("commonResources").apply {
    isCanBeResolved = false
    isCanBeConsumed = true
}

artifacts {
    add("commonJava", sourceSets["main"].java.sourceDirectories.singleFile)
    add("commonResources", sourceSets["main"].resources.sourceDirectories.singleFile)
}

// ── Shadow jar ────────────────────────────────────────────────────────────────

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations["shadow"])
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.named("jar") {
    finalizedBy("shadowJar")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+WhenGuards"))
}

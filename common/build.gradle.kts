import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("net.neoforged.moddev") version "2.0.141"
}

val isCurseForge = project.hasProperty("curseforge")

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

base.archivesName.set("${rootProject.property("mod_id")}-common-${rootProject.property("minecraft_version")}")

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

val sableVersion = rootProject.property("sable_version").toString()
val veilVersion = rootProject.property("veil_version").toString()

val neoForgeVersion = rootProject.property("neo_version").toString()
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
    compileOnly("org.tukaani:xz:1.11")

    // Mixin annotation processor
    compileOnly("org.spongepowered:mixin:0.8.5")

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    compileOnly(kotlin("reflect"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")

    compileOnly("com.simibubi.create:create-$minecraftVersionProp:$createVersion:slim") { isTransitive = false }
    compileOnly("net.createmod.ponder:ponder-neoforge:$ponderVersion+mc$minecraftVersionProp")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-$minecraftVersionProp:$flywheelVersion")
    compileOnly("com.tterrag.registrate:Registrate:$registrateVersion")

    implementation("dev.eriksonn.aeronautics:aeronautics-neoforge-1.21.1:1.2.1") {
        exclude("foundry.veil")
        exclude("com.tterrag.registrate")
        exclude("cc.tweaked")
        exclude("maven.modrinth")
    }

    implementation("dev.simulated_team.simulated:simulated-neoforge-1.21.1:1.2.1") {
        exclude("foundry.veil")
        exclude("com.tterrag.registrate")
        exclude("cc.tweaked")
        exclude("maven.modrinth")
    }

//    api("dev.ryanhcode.sable:sable-common-$minecraftVersionProp:$sableVersion") {
//        exclude("foundry.veil")
//        exclude("com.tterrag.registrate")
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

// Shared exclusion patterns so forge/neoforge build scripts can reuse them
val curseforgeExcludes =
    listOf(
        "me/mochibit/createharmonics/audio/bin/BackgroundBinInstaller.class",
        "me/mochibit/createharmonics/audio/bin/BackgroundBinInstaller$*.class",
        "me/mochibit/createharmonics/audio/bin/BinInstaller.class",
        "me/mochibit/createharmonics/audio/bin/BinInstaller$*.class",
    )
// Expose them so platform scripts can consume via project(":common").extra
extra["curseforgeExcludes"] = curseforgeExcludes

tasks.named<Jar>("jar") {
    if (isCurseForge) {
        curseforgeExcludes.forEach { exclude(it) }
    }
}

tasks.register<GradleBuild>("buildForCurseforge") {
    startParameter.projectProperties = mapOf("curseforge" to "true")
    group = "build"
    tasks = listOf("build")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+WhenGuards"))
}

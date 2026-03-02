plugins {
    id("com.gradleup.shadow")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
    id("net.neoforged.moddev.legacyforge") version "2.0.140"
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
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

legacyForge {
    mcpVersion = "$minecraftVersionProp"
    if (file("src/main/resources/META-INF/accesstransformer.cfg").exists()) {
        accessTransformers.from(file("src/main/resources/META-INF/accesstransformer.cfg"))
    }
    parchment {
        minecraftVersion = parchmentMinecraftProp
        mappingsVersion = parchmentVersionProp
    }
}

dependencies {
    shadow("org.tukaani:xz:1.11")
    compileOnly("org.tukaani:xz:1.11")

    // Mixin (for annotation processing in common module)
    compileOnly("org.spongepowered:mixin:0.8.5")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")

    // Create
    compileOnly("com.simibubi.create:create-$minecraftVersionProp:$createVersion:slim") { isTransitive = false }
    compileOnly("net.createmod.ponder:Ponder-Forge-$minecraftVersionProp:$ponderVersion") { isTransitive = false }
    compileOnly("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersionProp:$flywheelVersion")
    compileOnly("com.tterrag.registrate:Registrate:$registrateVersion") { isTransitive = false }

    // VS2
    compileOnly("org.valkyrienskies:valkyrienskies-120-forge:$vs2Version") { isTransitive = false }
    compileOnly("org.valkyrienskies.core:api:$vsCoreVersion") {
        exclude(group = "org.joml")
    }
    compileOnly("org.valkyrienskies.core:internal:$vsCoreVersion") {
        exclude(group = "org.joml")
    }
    compileOnly("org.valkyrienskies.core:util:$vsCoreVersion") {
        exclude(group = "org.joml")
    }
}

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

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations["shadow"])
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.named("jar") {
    finalizedBy("shadowJar")
}

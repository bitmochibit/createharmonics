plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

architectury {
    common(rootProject.property("enabled_platforms").toString().split(","))
}

loom {
    silentMojangMappingsLicense()
}

val minecraftVersion = rootProject.property("minecraft_version").toString()
val kotlinVersion = rootProject.property("kotlin_version").toString()

val createVersion = rootProject.property("create_version").toString()
val ponderVersion = rootProject.property("ponder_version").toString()
val flywheelVersion = rootProject.property("flywheel_version").toString()
val registrateVersion = rootProject.property("registrate_version").toString()

val generateBuildConfigTask =
    tasks.register("generateBuildConfig") {
        val outputDir = file("$buildDir/generated/sources/buildConfig")
        outputs.dir(outputDir)

        doLast {
            val isCurseForge = project.hasProperty("curseforge")
            val file = file("$outputDir/me/mochibit/createharmonics/BuildConfig.kt")
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

dependencies {
    minecraft("net.minecraft:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    implementation("org.tukaani:xz:1.11")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    modImplementation("com.simibubi.create:create-$minecraftVersion:$createVersion:slim")
    modImplementation("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion")
    compileOnly("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersion:$flywheelVersion")
    runtimeOnly("dev.engine-room.flywheel:flywheel-forge-$minecraftVersion:$flywheelVersion")
    modImplementation("com.tterrag.registrate:Registrate:$registrateVersion")

    // We depend on Fabric Loader here to use the Fabric @Environment annotations,
    // which get remapped to the correct annotations on each platform.
    // Do NOT use other classes from Fabric Loader.
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    modImplementation("dev.architectury:architectury:${rootProject.property("architectury_api_version")}")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir("$buildDir/generated/sources/buildConfig")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.compileKotlin {
    dependsOn(generateBuildConfigTask)
}

tasks.compileJava {
    dependsOn(generateBuildConfigTask)
}

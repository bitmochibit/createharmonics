plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

architectury {
    platformSetupLoomIde()
    forge()
}

loom {
    silentMojangMappingsLicense()
    forge {
        mixinConfig("createharmonics.mixins.json")
    }
}

val minecraftVersion = rootProject.property("minecraft_version").toString()
val createVersion = rootProject.property("create_version").toString()
val ponderVersion = rootProject.property("ponder_version").toString()
val flywheelVersion = rootProject.property("flywheel_version").toString()
val registrateVersion = rootProject.property("registrate_version").toString()
val jeiMinecraftVersion = rootProject.property("jei_minecraft_version").toString()
val jeiVersion = rootProject.property("jei_version").toString()
val vs2Version = rootProject.property("vs2_version").toString()
val vsCoreVersion = rootProject.property("vs_core_version").toString()

val common: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
val shadowBundle: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    named("developmentForge").get().extendsFrom(common)
}

dependencies {
    minecraft("net.minecraft:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    "forge"("net.minecraftforge:forge:$minecraftVersion-${rootProject.property("forge_version")}")

    modImplementation("dev.architectury:architectury-forge:${rootProject.property("architectury_api_version")}")

    // Kotlin for Forge
    implementation("thedarkcolour:kotlinforforge:${rootProject.property("kotlin_for_forge_version")}")

    // Create mod
    modImplementation("com.simibubi.create:create-$minecraftVersion:$createVersion:slim")
    modImplementation("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion")
    compileOnly("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersion:$flywheelVersion")
    runtimeOnly("dev.engine-room.flywheel:flywheel-forge-$minecraftVersion:$flywheelVersion")
    modImplementation("com.tterrag.registrate:Registrate:$registrateVersion")

    // VS2
    modImplementation("org.valkyrienskies:valkyrienskies-120-forge:$vs2Version")
    modImplementation("org.valkyrienskies.core:api:$vsCoreVersion") {
        exclude(group = "org.joml")
    }
    modImplementation("org.valkyrienskies.core:internal:$vsCoreVersion") {
        exclude(group = "org.joml")
    }
    modImplementation("org.valkyrienskies.core:util:$vsCoreVersion") {
        exclude(group = "org.joml")
    }

    // JEI
    runtimeOnly("mezz.jei:jei-$jeiMinecraftVersion-forge:$jeiVersion")

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionForge"))
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to project.version))
    }
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.get().archiveFile)
}

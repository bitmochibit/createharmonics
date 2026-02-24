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

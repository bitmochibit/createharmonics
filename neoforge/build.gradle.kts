plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    silentMojangMappingsLicense()
    neoForge { }
}

val minecraftVersion = project.property("minecraft_version").toString()

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
    named("developmentNeoForge").get().extendsFrom(common)
}

dependencies {
    minecraft("net.minecraft:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    "neoForge"("net.neoforged:neoforge:${rootProject.property("neoforge_version")}")

    modImplementation("dev.architectury:architectury-neoforge:${rootProject.property("architectury_api_version")}")

    // Kotlin for NeoForge
    implementation("thedarkcolour:kotlinforforge-neoforge:${rootProject.property("kotlin_for_forge_version")}")

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionNeoForge"))
    shadowBundle("org.tukaani:xz:1.11")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

sourceSets {
    main {
        resources {
            srcDir("src/main/templates")
        }
    }
}

tasks.processResources {
    val props =
        mapOf(
            "version" to project.version,
            "mod_version" to project.version,
            "mod_id" to rootProject.property("mod_id"),
            "mod_name" to rootProject.property("mod_name"),
            "mod_license" to rootProject.property("mod_license"),
            "mod_authors" to rootProject.property("mod_authors"),
            "mod_description" to rootProject.property("mod_description"),
            "minecraft_version_range" to "[${project.property("minecraft_version")},)",
            "neo_version_range" to "[${rootProject.property("neoforge_version")},)",
            "loader_version_range" to "[1,)",
        )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"

    relocate("org.tukaani.xz", "me.mochibit.createharmonics.libs.xz")

    if (project.hasProperty("curseforge")) {
        exclude("me/mochibit/createharmonics/audio/bin/BackgroundBinInstaller.class")
        exclude("me/mochibit/createharmonics/audio/bin/BackgroundBinInstaller\$*.class")
        exclude("me/mochibit/createharmonics/audio/bin/BinInstaller.class")
        exclude("me/mochibit/createharmonics/audio/bin/BinInstaller\$*.class")
    }
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.get().archiveFile)
    archiveClassifier = ""
}

tasks.register("buildCurseForge") {
    group = "build"
    description = "Builds the NeoForge jar for CurseForge distribution"
    dependsOn(tasks.remapJar)
}

gradle.taskGraph.whenReady {
    if (gradle.taskGraph.hasTask(":neoforge:buildCurseForge")) {
        project.ext.set("curseforge", true)
    }
}

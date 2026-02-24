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

dependencies {
    minecraft("net.minecraft:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

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
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

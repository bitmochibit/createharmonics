import buildsrc.chVersions

plugins {
    id("createharmonics.neoforge-base")
}

val v = chVersions

base.archivesName = "${v.modId}-common-${v.minecraft}"

legacyForge {
    mcpVersion = v.minecraft
    if (file("src/main/resources/META-INF/accesstransformer.cfg").exists()) {
        accessTransformers.from(file("src/main/resources/META-INF/accesstransformer.cfg"))
    }
}

dependencies {
    compileOnly("org.tukaani:xz:1.11")

    modCompileOnly("net.minecraftforge:forge:1.20.1-47.1.0")
    compileOnly("net.minecraftforge:fmlcore:1.20.1-47.1.0")

    compileOnly("org.spongepowered:mixin:0.8.5")

    modCompileOnly("com.simibubi.create:create-${v.minecraft}:${v.create}:slim") { isTransitive = false }
    modCompileOnly("net.createmod.ponder:Ponder-Forge-${v.minecraft}:${v.ponder}") { isTransitive = false }
    modCompileOnly("dev.engine-room.flywheel:flywheel-forge-api-${v.minecraft}:${v.flywheel}")
    modCompileOnly("com.tterrag.registrate:Registrate:${v.registrate}") { isTransitive = false }

    modCompileOnly("org.valkyrienskies:valkyrienskies-120-forge:${v.vs2}") { isTransitive = false }
    modCompileOnly("org.valkyrienskies.core:api:${v.vs2Core}") { exclude(group = "org.joml") }
    modCompileOnly("org.valkyrienskies.core:internal:${v.vs2Core}") { exclude(group = "org.joml") }
    modCompileOnly("org.valkyrienskies.core:util:${v.vs2Core}") { exclude(group = "org.joml") }

    modCompileOnly("mezz.jei:jei-${v.jeiMc}-common-api:${v.jei}")
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

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
}

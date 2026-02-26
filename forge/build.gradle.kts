plugins {
    id("xyz.wagyourtail.unimined")
}

version =
    "${rootProject.property("version_major")}.${rootProject.property("version_minor")}.${rootProject.property("version_patch")}"
group = rootProject.property("mod_group_id").toString()
base.archivesName.set("${rootProject.property("mod_name")}-Forge-${rootProject.property("minecraft_version")}")

val minecraftVersion = rootProject.property("minecraft_version").toString()

val jeiMinecraftVersion = rootProject.property("jei_minecraft_version").toString()
val jeiVersion = rootProject.property("jei_version").toString()
val vs2Version = rootProject.property("vs2_version").toString()
val vsCoreVersion = rootProject.property("vs_core_version").toString()

val createVersion = rootProject.property("create_version").toString()
val ponderVersion = rootProject.property("ponder_version").toString()
val flywheelVersion = rootProject.property("flywheel_version").toString()
val registrateVersion = rootProject.property("registrate_version").toString()

val modId = rootProject.property("mod_id").toString()
val forgeVersion = rootProject.property("forge_version").toString()

dependencies {
    // Kotlin for Forge
    implementation("thedarkcolour:kotlinforforge:${rootProject.property("kotlin_for_forge_version")}")

    // Create for Forge
    implementation("com.simibubi.create:create-$minecraftVersion:$createVersion:slim")
    implementation("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion")
    compileOnly("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersion:$flywheelVersion")
    runtimeOnly("dev.engine-room.flywheel:flywheel-forge-$minecraftVersion:$flywheelVersion")
    implementation("com.tterrag.registrate:Registrate:$registrateVersion")

    // VS2
    implementation("org.valkyrienskies:valkyrienskies-120-forge:$vs2Version")
    implementation("org.valkyrienskies.core:api:$vsCoreVersion") {
        exclude(group = "org.joml")
    }
    implementation("org.valkyrienskies.core:internal:$vsCoreVersion") {
        exclude(group = "org.joml")
    }
    implementation("org.valkyrienskies.core:util:$vsCoreVersion") {
        exclude(group = "org.joml")
    }

    // JEI
    runtimeOnly("mezz.jei:jei-$jeiMinecraftVersion-forge:$jeiVersion")

    implementation(project(":common"))
}

unimined.minecraft(sourceSets["main"]) {
    version(minecraftVersion)
    mappings {
        mojmap()
        devNamespace("mojmap")
    }
    minecraftForge {
        loader(forgeVersion)
        mixinConfig("$modId.mixins.json", "$modId-forge.mixins.json")
    }
}

tasks.named<ProcessResources>("processResources") {
    from(project(":common").sourceSets["main"].resources)
    val buildProps = project.properties.toMap()

    filesMatching("META-INF/mods.toml") {
        expand(buildProps)
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    enabled = false
}

tasks.withType<JavaCompile>().configureEach {
    source(project(":common").sourceSets["main"].allSource)
}

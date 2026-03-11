import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
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

base.archivesName.set("${rootProject.property("mod_name")}-Forge-${rootProject.property("minecraft_version")}")

val minecraftVersionProp = rootProject.property("minecraft_version").toString()
val parchmentMinecraftProp = rootProject.property("parchment_minecraft").toString()
val parchmentVersionProp = rootProject.property("parchment_version").toString()

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

val forgeProject = project
val commonProject = project(":common")

mixin {
    add(sourceSets["main"], "$modId.refmap.json")
    config("$modId.mixins.json")
    config("createharmonics.common.mixins.json")
}

legacyForge {
    version = "$minecraftVersionProp-$forgeVersion"

    validateAccessTransformers = true

    val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at)
    }

    parchment {
        enabled = true
        minecraftVersion = parchmentMinecraftProp
        mappingsVersion = parchmentVersionProp
    }

    runs {
        register("client") {
            client()
            jvmArguments.add("-XX:TieredStopAtLevel=1")
        }
        register("clientDebug") {
            client()
            jvmArguments.add("-XX:TieredStopAtLevel=1")
            jvmArguments.add("-XX:+AllowEnhancedClassRedefinition")
            // Disable FML early display window to prevent jdwp.dll crash when debugger is attached on Windows + Java 21
            jvmArguments.add("-Dfml.earlyWindowProvider=dummyprovider")
        }
        register("data") {
            data()
            programArguments.addAll(
                "--mod",
                modId,
                "--all",
                "--output",
                forgeProject.file("src/generated/resources/").absolutePath,
                "--existing",
                forgeProject.file("src/main/resources/").absolutePath,
                "--existing",
                commonProject.file("src/main/resources/").absolutePath,
            )
        }
        register("server") {
            server()
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets["main"])
        }
    }
}

sourceSets.main {
    resources.srcDir("src/generated/resources")
    java.srcDir("src/generated/java")
    kotlin.srcDir("src/generated/kotlin")
}

dependencies {
    // Kotlin for Forge
    implementation("thedarkcolour:kotlinforforge:${rootProject.property("kotlin_for_forge_version")}")

    // Create for Forge
    modImplementation("com.simibubi.create:create-$minecraftVersionProp:$createVersion:slim")
    modImplementation("net.createmod.ponder:Ponder-Forge-$minecraftVersionProp:$ponderVersion")
    modCompileOnly("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersionProp:$flywheelVersion")
    modRuntimeOnly("dev.engine-room.flywheel:flywheel-forge-$minecraftVersionProp:$flywheelVersion")
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
    modRuntimeOnly("mezz.jei:jei-$jeiMinecraftVersion-forge:$jeiVersion")

    compileOnly(project(":common"))
    compileOnly("org.tukaani:xz:1.11")
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT:processor")
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    source(project(":common").sourceSets["main"].kotlin)
}

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
    manifest.attributes(
        "MixinConfigs" to "$modId.mixins.json,createharmonics.common.mixins.json",
    )
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+WhenGuards"))
}

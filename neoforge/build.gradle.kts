import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
    id("net.neoforged.moddev") version "2.0.78"
}

kotlin {
    jvmToolchain {
        // NeoForge 1.21.1 requires Java 21
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

base.archivesName.set("${rootProject.property("mod_name")}-NeoForge-${rootProject.property("minecraft_version")}")

val minecraftVersionProp = rootProject.property("minecraft_version").toString() // e.g. "1.21.1"
val parchmentMinecraftProp = rootProject.property("parchment_minecraft").toString()
val parchmentVersionProp = rootProject.property("parchment_version").toString()

val jeiMinecraftVersion = rootProject.property("jei_minecraft_version").toString()
val jeiVersion = rootProject.property("jei_version").toString()

val createVersion = rootProject.property("create_version").toString()
val ponderVersion = rootProject.property("ponder_version").toString()
val flywheelVersion = rootProject.property("flywheel_version").toString()
val registrateVersion = rootProject.property("registrate_version").toString()

val modId = rootProject.property("mod_id").toString()
val neoForgeVersion = rootProject.property("neo_version").toString() // e.g. "21.1.86"

val neoProject = project
val commonProject = project(":common")

neoForge {
    version = neoForgeVersion

    validateAccessTransformers = true

    val at = commonProject.file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at)
    }

    parchment {
        enabled = true
        minecraftVersion = parchmentMinecraftProp
        mappingsVersion = parchmentVersionProp
    }

    // In the non-legacy plugin, mixin config lives inside neoForge { }
//    mixin {
//        add(sourceSets["main"], "$modId.refmap.json")
//        config("$modId.mixins.json")
//        config("createharmonics.common.mixins.json")
//    }

    runs {
        register("client") {
            client()
            jvmArguments.add("-XX:TieredStopAtLevel=1")
        }
        register("clientDebug") {
            client()
            jvmArguments.add("-XX:TieredStopAtLevel=1")
            jvmArguments.add("-XX:+AllowEnhancedClassRedefinition")
        }
        register("data") {
            data()
            programArguments.addAll(
                "--mod",
                modId,
                "--all",
                "--output",
                neoProject.file("src/generated/resources/").absolutePath,
                "--existing",
                neoProject.file("src/main/resources/").absolutePath,
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
    // Kotlin for NeoForge
    implementation("thedarkcolour:kotlinforforge-neoforge:${rootProject.property("kotlin_for_neoforge_version")}")

    // Create for NeoForge
    implementation("com.simibubi.create:create-$minecraftVersionProp:$createVersion:slim")
    implementation("net.createmod.ponder:ponder-neoforge:$ponderVersion+mc$minecraftVersionProp")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-$minecraftVersionProp:$flywheelVersion")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-$minecraftVersionProp:$flywheelVersion")
    implementation("com.tterrag.registrate:Registrate:$registrateVersion")
    // JEI
    runtimeOnly("mezz.jei:jei-$jeiMinecraftVersion-neoforge:$jeiVersion")

    compileOnly(project(":common"))
    compileOnly("org.tukaani:xz:1.11")
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT:processor")
}

tasks.named<ProcessResources>("processResources") {
    from(commonProject.sourceSets["main"].resources)
    val buildProps = project.properties.toMap()

    // NeoForge uses neoforge.mods.toml instead of META-INF/mods.toml
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(buildProps)
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    enabled = false
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    enabled = false
}

tasks.withType<JavaCompile>().configureEach {
    source(commonProject.sourceSets["main"].allSource)
}

tasks.withType<KotlinCompile>().configureEach {
    source(commonProject.sourceSets["main"].kotlin)
}

// No reobfJar on NeoForge — it ships Mojang-mapped at runtime natively.
tasks.named<Jar>("jar") {
    manifest.attributes(
        "MixinConfigs" to "$modId.mixins.json,createharmonics.common.mixins.json",
    )
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+WhenGuards"))
}

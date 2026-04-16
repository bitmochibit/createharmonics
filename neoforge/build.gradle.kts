import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    id("net.neoforged.moddev") version "2.0.78"
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

base.archivesName.set("${rootProject.property("mod_name")}-neoforge-${rootProject.property("minecraft_version")}")

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

val kotlinVersion = rootProject.property("kotlin_version").toString()
val kotlinCoroutinesVersion = rootProject.property("kotlin_coroutines_version").toString()
val kotlinSerializationVersion = rootProject.property("kotlin_serialization_version").toString()

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
    resources.srcDirs("src/generated/resources", "templates")
    java.srcDir("src/generated/java")
    kotlin.srcDir("src/generated/kotlin")
}

dependencies {
    // Kotlin for NeoForge
    implementation("thedarkcolour:kotlinforforge-neoforge:${rootProject.property("kotlin_for_neoforge_version")}")

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    compileOnly(kotlin("reflect"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")

    // Create for NeoForge
    implementation("com.simibubi.create:create-$minecraftVersionProp:$createVersion:slim") { isTransitive = false }
    implementation("net.createmod.ponder:ponder-neoforge:$ponderVersion+mc$minecraftVersionProp")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-$minecraftVersionProp:$flywheelVersion")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-$minecraftVersionProp:$flywheelVersion")
    implementation("com.tterrag.registrate:Registrate:$registrateVersion")
    // JEI
    runtimeOnly("mezz.jei:jei-$jeiMinecraftVersion-neoforge:$jeiVersion")

    compileOnly(project(":common"))
    compileOnly("org.tukaani:xz:1.11")
}

tasks.named<ProcessResources>("processResources") {
    from(commonProject.sourceSets["main"].resources)
    val buildProps = project.properties.toMap()

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

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

tasks.register<GradleBuild>("buildForCurseforge") {
    startParameter.projectProperties = mapOf("curseforge" to "true")
    group = "build"
    tasks = listOf("build")
}

tasks.register<GradleBuild>("cleanAll") {
    group = "build"
    tasks = listOf(":common:clean", ":forge:clean")
}

val curseforgeExcludes = commonProject.extra["curseforgeExcludes"] as List<*>

tasks.named<Jar>("jar") {
    manifest.attributes(
        "MixinConfigs" to "$modId.mixins.json,createharmonics.common.mixins.json",
    )
    if (project.hasProperty("curseforge")) {
        curseforgeExcludes.forEach { exclude(it.toString()) }
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+WhenGuards"))
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

val prodModsDir: String =
    localProperties.getProperty("prodModsDir")
        ?: providers.gradleProperty("prodModsDir").orNull
        ?: "build/deploy"

tasks.register<Copy>("deployToProd") {
    group = "build"

    dependsOn("build")

    from(layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-dev.jar")
    }

    into(file(prodModsDir))
}

tasks.register<GradleBuild>("buildAndDeployToProd") {
    group = "build"
    tasks = listOf("build", ":forge:deployToProd")
}

tasks.register<GradleBuild>("buildCfAndDeployToProd") {
    group = "build"
    tasks = listOf("buildForCurseforge", ":forge:deployToProd")
}

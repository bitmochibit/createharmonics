import buildsrc.chVersions
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    id("createharmonics.neoforge-base")
    id("com.gradleup.shadow")
}

val v = chVersions
val commonProject = project(":common")

base.archivesName = "${v.modId}-forge-${v.minecraft}"

mixin {
    add(sourceSets["main"], "${v.modId}.refmap.json")
    config("${v.modId}.mixins.json")
    config("createharmonics.common.mixins.json")
}

legacyForge {
    version = "${v.minecraft}-${v.forge}"

    validateAccessTransformers = true

    val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at)
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
                v.modId,
                "--all",
                "--output",
                project.file("src/generated/resources/").absolutePath,
                "--existing",
                project.file("src/main/resources/").absolutePath,
                "--existing",
                commonProject.file("src/main/resources/").absolutePath,
            )
        }
        register("server") {
            server()
        }
    }

    mods {
        create(v.modId) {
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
    implementation("thedarkcolour:kotlinforforge:${v.kotlinForForge}")

    // Create for Forge
    modImplementation("com.simibubi.create:create-${v.minecraft}:${v.create}:slim")
    modImplementation("net.createmod.ponder:Ponder-Forge-${v.minecraft}:${v.ponder}")
    modCompileOnly("dev.engine-room.flywheel:flywheel-forge-api-${v.minecraft}:${v.flywheel}")
    modRuntimeOnly("dev.engine-room.flywheel:flywheel-forge-${v.minecraft}:${v.flywheel}")
    modImplementation("com.tterrag.registrate:Registrate:${v.registrate}")

    // VS2
    modImplementation("org.valkyrienskies:valkyrienskies-120-forge:${v.vs2}")
    modImplementation("org.valkyrienskies.core:api:${v.vs2Core}") {
        exclude(group = "org.joml")
    }
    modImplementation("org.valkyrienskies.core:internal:${v.vs2Core}") {
        exclude(group = "org.joml")
    }
    modImplementation("org.valkyrienskies.core:util:${v.vs2Core}") {
        exclude(group = "org.joml")
    }

    // JEI
    modRuntimeOnly("mezz.jei:jei-${v.jeiMc}-forge:${v.jei}")
    modCompileOnly("mezz.jei:jei-${v.jeiMc}-forge:${v.jei}")

    compileOnly(project(":common"))
    shadow("org.tukaani:xz:1.11")
    compileOnly("org.tukaani:xz:1.11")
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT:processor")
}

tasks.named<ProcessResources>("processResources") {
    from(project(":common").sourceSets["main"].resources)
    val buildProps = project.properties.toMap()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesMatching("META-INF/mods.toml") {
        expand(buildProps)
    }
}

tasks.withType<JavaCompile>().configureEach {
    source(project(":common").sourceSets["main"].allSource)
}

tasks.withType<KotlinCompile>().configureEach {
    source(project(":common").sourceSets["main"].kotlin)
}

tasks.named<Jar>("jar") {
    dependsOn("shadowJar")
    from(tasks.named<ShadowJar>("shadowJar").map { zipTree(it.archiveFile) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes(
        "MixinConfigs" to "${v.modId}.mixins.json,createharmonics.common.mixins.json",
    )
}
tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "shadow"

    configurations = listOf(project.configurations.getByName("shadow"))
    dependencies {
        include(dependency("org.tukaani:xz:1.11"))
    }

    relocate("org.tukaani.xz", "me.mochibit.createharmonics.libs.tukaani.xz")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}


tasks.register<GradleBuild>("cleanAll") {
    group = "build"
    tasks = listOf(":common:clean", ":forge:clean")
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
    dependsOn("reobfJar")
    from(tasks.named("reobfJar"))

    into(file(prodModsDir))
}

tasks.register<GradleBuild>("buildAndDeployToProd") {
    group = "build"
    tasks = listOf("build", ":forge:deployToProd")
}


import net.minecraftforge.gradle.userdev.UserDevExtension
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.internal.impldep.org.junit.experimental.categories.Categories.CategoryFilter.exclude
import java.text.SimpleDateFormat
import java.util.Date


fun Project.minecraft(configure: UserDevExtension.() -> Unit) = extensions.configure("minecraft", configure)

buildscript {
    repositories {
        // These repositories are only for Gradle plugins, put any other repositories in the repository block further below
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        mavenCentral()
    }
    dependencies {
        classpath("org.spongepowered:mixingradle:0.7-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    }
}

plugins {
    eclipse
    idea
    id("net.minecraftforge.gradle") version "[6.0.16,6.2)"
    id("org.parchmentmc.librarian.forgegradle") version "1.+"
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
}

apply(plugin = "org.spongepowered.mixin")
apply(plugin = "kotlin")

val modId = property("mod_id") as String
val modGroupId = property("mod_group_id") as String
val modVersion = property("mod_version") as String
val modName = property("mod_name") as String
val modLicense = property("mod_license") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String

val minecraftVersion = property("minecraft_version") as String
val minecraftVersionRange = property("minecraft_version_range") as String
val forgeVersion = property("forge_version") as String
val forgeVersionRange = property("forge_version_range") as String
val loaderVersionRange = property("loader_version_range") as String
val mcMappingChannel = property("mapping_channel") as String
val mcMappingVersion = property("mapping_version") as String

val createVersion = property("create_version") as String
val ponderVersion = property("ponder_version") as String
val flywheelVersion = property("flywheel_version") as String
val registrateVersion = property("registrate_version") as String
val jeiMinecraftVersion = property("jei_minecraft_version") as String
val jeiVersion = property("jei_version") as String
val kotlinVersion = property("kotlin_version") as String

val vs2Version = property("vs2_version") as String
val vsCoreVersion = property("vs_core_version") as String

group = modGroupId
version = modVersion

base {
    archivesName.set(modId)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

minecraft {
    mappings(mcMappingChannel, mcMappingVersion)
    copyIdeResources.set(true)

    runs {
        all {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")

            mods {
                create(modId) {
                    source(sourceSets.main.get())
                    source(sourceSets.test.get())
                }
            }
        }

        register("client") {
            property("forge.enabledGameTestNamespaces", modId)
            property("forge.enableGameTest", "true")
        }

        register("debug") {
            parent(this@runs.getByName("client"))
            jvmArg("-XX:+AllowEnhancedClassRedefinition")
        }

        register("clientDummy") {
            parent(this@runs.getByName("client"))
            args("--username", "Dummy")
        }

        register("server") {
            property("forge.enabledGameTestNamespaces", modId)
            args("--nogui")
        }

        register("gameTestServer") {
            property("forge.enabledGameTestNamespaces", modId)
        }

        register("data") {
            workingDirectory(project.file("run-data"))
            args(
                "--mod",
                modId,
                "--all",
                "--output",
                file("src/generated/resources/"),
                "--existing",
                file("src/main/resources/"),
            )
        }
    }
}

// Mixin configuration
configure<org.spongepowered.asm.gradle.plugins.MixinExtension> {
    add(sourceSets.main.get(), "$modId.refmap.json")
}

sourceSets {
    main {
        ext["refMap"] = "$modId.refmap.json"
        resources.srcDir("src/generated/resources")
    }

    test {
        java.srcDir("src/test/java")
        kotlin.srcDir("src/test/kotlin")
        resources.srcDir("src/test/resources")

        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
    maven { url = uri("https://maven.createmod.net") }
    maven { url = uri("https://maven.ithundxr.dev/mirror") }
    maven { url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") }
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
    }
    maven {
        // location of the maven that hosts JEI files
        name = "Progwml6 maven"
        url = uri("https://dvs1.progwml6.com/files/maven/")
    }
    maven {
        // location of a maven mirror for JEI files, as a fallback
        name = "ModMaven"
        url = uri("https://modmaven.dev")
    }
    maven {
        name = "Valkyrien Skies"
        url = uri("https://maven.valkyrienskies.org")
    }
}

val fg = extensions.getByType<net.minecraftforge.gradle.userdev.DependencyManagementExtension>()

dependencies {
    "minecraft"("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("thedarkcolour:kotlinforforge:4.12.0")

    implementation(fg.deobf("com.simibubi.create:create-$minecraftVersion:$createVersion:slim"))
    configurations.getByName("implementation").dependencies.last().apply {
        (this as ExternalModuleDependency).isTransitive = false
    }
    implementation(fg.deobf("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion"))
    compileOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersion:$flywheelVersion"))
    runtimeOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-$minecraftVersion:flywheelVersion"))
    implementation(fg.deobf("com.tterrag.registrate:Registrate:$registrateVersion"))

    // VS2
    implementation(fg.deobf("org.valkyrienskies:valkyrienskies-120-forge:$vs2Version"))
    implementation("org.valkyrienskies.core:api:$vsCoreVersion") {
        exclude(group = "org.joml")
    }
    implementation("org.valkyrienskies.core:internal:$vsCoreVersion") {
        exclude(group = "org.joml")
    }
    implementation("org.valkyrienskies.core:util:$vsCoreVersion") {
        exclude(group = "org.joml")
    }

    // test
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("org.junit.platform:junit-platform-suite-api:1.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-suite-engine:1.10.1")

    testImplementation("io.mockk:mockk:1.13.8")

    // MixinExtras with JarJar
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:0.4.1")!!)
    implementation(fg.deobf("io.github.llamalad7:mixinextras-forge:0.4.1"))
    jarJar(group = "io.github.llamalad7", name = "mixinextras-forge", version = "[0.4.1,)")

    runtimeOnly(fg.deobf("mezz.jei:jei-$jeiMinecraftVersion-forge:$jeiVersion"))
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties =
        mapOf(
            "minecraft_version" to minecraftVersion,
            "minecraft_version_range" to minecraftVersionRange,
            "forge_version" to forgeVersion,
            "forge_version_range" to forgeVersionRange,
            "loader_version_range" to loaderVersionRange,
            "mod_id" to modId,
            "mod_name" to modName,
            "mod_license" to modLicense,
            "mod_version" to modVersion,
            "mod_authors" to modAuthors,
            "mod_description" to modDescription,
        )

    inputs.properties(replaceProperties)

    // Handle duplicate files by preferring files from src/main/resources
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(replaceProperties + mapOf("project" to project))
    }
}

// Enable JarJar and make build depend on it
tasks.named("build") {
    dependsOn(tasks.named("jarJar"))
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to modId,
                "Specification-Vendor" to modAuthors,
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to archiveVersion,
                "Implementation-Vendor" to modAuthors,
                "Implementation-Timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date()),
                "MixinConfigs" to "$modId.mixins.json",
            ),
        )
    }
    finalizedBy("reobfJar")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-AreobfSrgFile=${project.file("build/createSrgToMcp/output.srg").absolutePath}",
            "-AoutSrgFile=${project.file("build/createSrgToMcp/output.srg").absolutePath}",
            "-AoutRefMapFile=${project.file("build/resources/main/$modId.refmap.json").absolutePath}",
        ),
    )
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

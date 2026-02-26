plugins {
    id("xyz.wagyourtail.unimined")
}

version =
    "${rootProject.property("version_major")}.${rootProject.property("version_minor")}.${rootProject.property("version_patch")}"
group = rootProject.property("mod_group_id").toString()

val minecraftVersion = project.property("minecraft_version").toString()
val modId = rootProject.property("mod_id").toString()
val neoforgeVersion = rootProject.property("neoforge_version").toString()

dependencies {
    implementation(project(":common"))
}

unimined.minecraft(sourceSets["main"]) {
    version(minecraftVersion)
    mappings {
        mojmap()
        devNamespace("mojmap")
    }
    neoForged {
        loader(neoforgeVersion)
        mixinConfig("$modId.mixins.json", "$modId-neoforge.mixins.json")
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

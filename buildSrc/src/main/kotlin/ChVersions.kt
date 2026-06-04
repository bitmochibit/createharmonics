package buildsrc

import org.gradle.api.Project

val Project.chVersions: ChVersions get() = ChVersions(rootProject)

class ChVersions(
    private val root: Project,
) {
    val minecraft get() = root.property("minecraft_version").toString()
    val forge get() = root.property("forge_version").toString()

    val parchmentMc get() = root.property("parchment_minecraft").toString()
    val parchmentVer get() = root.property("parchment_version").toString()

    val kotlin get() = root.property("kotlin_version").toString()
    val kotlinCoroutines get() = root.property("kotlin_coroutines_version").toString()
    val kotlinSerialization get() = root.property("kotlin_serialization_version").toString()
    val kotlinForForge get() = root.property("kotlin_for_forge_version").toString()

    val create get() = root.property("create_version").toString()
    val ponder get() = root.property("ponder_version").toString()
    val flywheel get() = root.property("flywheel_version").toString()
    val registrate get() = root.property("registrate_version").toString()

    val jeiMc get() = root.property("jei_minecraft_version").toString()
    val jei get() = root.property("jei_version").toString()

    val vs2 get() = root.property("vs2_version").toString()
    val vs2Core get() = root.property("vs_core_version").toString()

    val modId get() = root.property("mod_id").toString()
    val modVersion get() = "${root.property("version_major")}.${root.property("version_minor")}.${root.property("version_patch")}"
}

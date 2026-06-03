package buildsrc

import org.gradle.api.Project

val Project.isCurseForge: Boolean get() = hasProperty("curseforge")


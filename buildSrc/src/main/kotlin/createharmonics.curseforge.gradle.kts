import buildsrc.ChVersions

val excludes =
    listOf(
        "me/mochibit/createharmonics/audio/bin/BackgroundBinInstaller.class",
        "me/mochibit/createharmonics/audio/bin/BackgroundBinInstaller\$*.class",
        "me/mochibit/createharmonics/audio/bin/BinInstaller.class",
        "me/mochibit/createharmonics/audio/bin/BinInstaller\$*.class",
    )

extra["curseforgeExcludes"] = excludes

if (project.hasProperty("curseforge")) {
    tasks.withType<Jar>().configureEach {
        excludes.forEach { exclude(it) }
    }
}

import buildsrc.chVersions

plugins {
    id("net.neoforged.moddev.legacyforge")
    id("createharmonics.kotlin-base")
}

val v = chVersions

legacyForge {
    parchment {
        enabled = true
        minecraftVersion = v.parchmentMc
        mappingsVersion = v.parchmentVer
    }
}

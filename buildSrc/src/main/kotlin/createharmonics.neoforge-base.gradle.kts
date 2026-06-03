import buildsrc.chVersions

plugins {
    id("net.neoforged.moddev")
    id("createharmonics.kotlin-base")
}

val v = chVersions

neoForge {
    parchment {
        enabled = true
        minecraftVersion = v.parchmentMc
        mappingsVersion = v.parchmentVer
    }
}

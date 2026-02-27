package me.mochibit.createharmonics

import me.mochibit.createharmonics.foundation.err

object CreateHarmonicsMod {
    const val MOD_ID = "createharmonics"
    private var initialized = false

    fun commonSetup() {
        if (initialized) {
            return "CreateHarmonicsMod is already initialized.".err()
        }
        initialized = true
    }
}

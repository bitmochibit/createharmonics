package me.mochibit.createharmonics.foundation.network.packet

import me.mochibit.createharmonics.foundation.services.contentService

class ConfigureRecordPressBasePacket(
    val audioUrls: MutableList<String>,
    val urlWeights: MutableList<Float>,
    val randomMode: Boolean = false,
    val newIndex: Int = 0,
) : ModPacket,
    C2SPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        contentService
            .return true
    }
}

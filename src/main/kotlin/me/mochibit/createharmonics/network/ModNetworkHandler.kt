package me.mochibit.createharmonics.network

import me.mochibit.createharmonics.CreateHarmonicsMod
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel

object ModNetworkHandler {
    private const val PROTOCOL_VERSION = "1"
    private var packetId = 0

    val channel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "main"),
        { PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION }
    )

    fun register() {

    }
}
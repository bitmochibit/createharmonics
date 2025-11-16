package me.mochibit.createharmonics.network

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.registry.AbstractModRegistry
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel

object ModNetworkHandler : AbstractModRegistry {
    private const val PROTOCOL_VERSION = "1"
    private var packetId = 0

    val channel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "main"),
        { PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION }
    )

    override fun register(eventBus: IEventBus) {
        Logger.info("Registering Mod Network Channel")
    }
}
package me.mochibit.createharmonics.registry

import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.kinetics.recordPlayer.AudioNameDisplaySource
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

object ModDisplaySources : AutoRegistrable {
    val AUDIO_NAME =
        cRegistrate()
            .displaySource("audio_name", ::AudioNameDisplaySource)
            .register()

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        Logger.info("Registering display sources")
    }
}

package me.mochibit.createharmonics.command

import me.mochibit.createharmonics.CreateHarmonicsMod.Companion.MOD_ID
import me.mochibit.createharmonics.Logger
import net.minecraftforge.client.event.RegisterClientCommandsEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
object CommandRegistry {

    @SubscribeEvent
    @JvmStatic
    fun registerCommands(event: RegisterCommandsEvent) {
        Logger.info("Registering commands...")
        SetRecordUrlCommand.register(event.dispatcher)
    }

    @SubscribeEvent
    @JvmStatic
    fun registerClientCommands(event: RegisterClientCommandsEvent) {
        Logger.info("Registering client commands...")
    }
}
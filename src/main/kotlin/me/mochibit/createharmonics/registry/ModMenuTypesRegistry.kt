package me.mochibit.createharmonics.registry

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox.AndesiteJukeboxMenu
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.minecraftforge.common.extensions.IForgeMenuType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object ModMenuTypesRegistry : AbstractModRegistry {
    private val MENUS = DeferredRegister.create(Registries.MENU, CreateHarmonicsMod.MOD_ID)

    val ANDESITE_JUKEBOX: RegistryObject<MenuType<AndesiteJukeboxMenu>> = MENUS.register("andesite_jukebox") {
        IForgeMenuType.create { id, inv, buf -> AndesiteJukeboxMenu(id, inv, buf) }
    }

    override fun register(eventBus: IEventBus) {
        Logger.info("Registering menu types for ${CreateHarmonicsMod.MOD_ID}")
        MENUS.register(eventBus)
    }
}


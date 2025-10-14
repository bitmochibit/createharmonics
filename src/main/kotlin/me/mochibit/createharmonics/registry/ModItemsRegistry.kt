package me.mochibit.createharmonics.registry

import com.tterrag.registrate.util.entry.ItemEntry
import me.mochibit.createharmonics.CreateHarmonics
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.item.EtherealDiscItem
import net.minecraftforge.eventbus.api.IEventBus

object ModItemsRegistry : AbstractModRegistry {

    val etherealDisc: ItemEntry<EtherealDiscItem> = cRegistrate().item<EtherealDiscItem>("ethereal_disc") { _ ->
        EtherealDiscItem()
    }.properties { prop ->
        prop.stacksTo(1)
    }.model { c, p ->
        p.generated(c, p.modLoc("item/ethereal_disc"))
    }.register()


    override fun register(eventBus: IEventBus) {
        info("Registering items for ${CreateHarmonicsMod.MOD_ID}")
    }
}
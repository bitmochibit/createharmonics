package me.mochibit.createharmonics.registry

import com.tterrag.registrate.util.entry.ItemEntry
import me.mochibit.createharmonics.Config
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.item.EtherealDiscItem
import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus

object ModItemsRegistry : AbstractModRegistry {
    
    private fun registerDiskVariants() {
        // Use the default variants here for registration
        Config.variants.forEach { (type, maxUses) ->
            registerEtherealDiscVariant(type, maxUses)
        }
    }

    fun registerEtherealDiscVariant(suffix: Config.DiscType, maxUses: Int?): ItemEntry<EtherealDiscItem> {
        val name = "ethereal_disc_${suffix.name.lowercase()}"
        
        return cRegistrate().item(name) { EtherealDiscItem(maxUses == null, props) }
            .properties { prop: Item.Properties -> prop.stacksTo(1) }
            .model { ctx, prov ->
                prov.generated(ctx, prov.modLoc("item/$name"))
            }
            .register()
    }


    override fun register(eventBus: IEventBus) {
        info("Registering items for ${CreateHarmonicsMod.MOD_ID}")
        registerDiskVariants()
    }
}
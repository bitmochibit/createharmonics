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
        Config.diskVariants.forEach { (type, maxUses) ->
            registerEtherealDiscVariant(type, maxUses)
        }
    }
    
    fun registerEtherealDiscVariant(discType: Config.DiscType, maxUses: Int?): ItemEntry<EtherealDiscItem> {
        val name = "ethereal_disc_${discType.name.lowercase()}"
        return cRegistrate().item(name) { 
            val properties = Item.Properties().stacksTo(1)
            // If maxUses is not null, set durability. Otherwise, item is unbreakable.
            if (maxUses != null) {
                properties.durability(maxUses)
            }
            EtherealDiscItem(discType, properties)
        }
            .model { ctx , prov ->
                prov.generated(ctx, prov.modLoc("item/$name"))
            }
            .register()
    }


    override fun register(eventBus: IEventBus) {
        info("Registering items for ${CreateHarmonicsMod.MOD_ID}")
        registerDiskVariants()
    }
}
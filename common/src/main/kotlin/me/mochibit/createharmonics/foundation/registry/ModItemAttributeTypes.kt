package me.mochibit.createharmonics.foundation.registry

import com.simibubi.create.api.registry.CreateBuiltInRegistries
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType
import com.simibubi.create.content.logistics.item.filter.attribute.SingletonItemAttribute
import me.mochibit.createharmonics.content.records.hasAssignedUrl
import me.mochibit.createharmonics.content.records.isBrokenEtherealRecord
import me.mochibit.createharmonics.content.records.isEtherealRecord
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.info
import net.minecraft.core.Registry
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.function.BiPredicate

object ModItemAttributeTypes : PreFreezeCommonRegistry {
    val ETHEREAL_RECORD_DESTROYED =
        singleton("record_is_broken") { stack, _ ->
            stack.isBrokenEtherealRecord()
        }

    val ETHEREAL_RECORD_URL_ASSIGNED =
        singleton("has_url_assigned") { stack, _ ->
            stack.isEtherealRecord() &&
                !stack.isBrokenEtherealRecord() &&
                stack.hasAssignedUrl()
        }

    private fun singleton(
        id: String,
        predicate: BiPredicate<ItemStack, Level>,
    ): ItemAttributeType =
        register(
            id,
            SingletonItemAttribute.Type { type ->
                SingletonItemAttribute(
                    type,
                    predicate,
                    id,
                )
            },
        )

    private fun singleton(
        id: String,
        predicate: (ItemStack) -> Boolean,
    ): ItemAttributeType =
        singleton(id) { stack, _ ->
            predicate(stack)
        }

    private fun register(
        id: String,
        type: ItemAttributeType,
    ): ItemAttributeType =
        Registry.register(
            CreateBuiltInRegistries.ITEM_ATTRIBUTE_TYPE,
            id.asResource(),
            type,
        )

    override fun register() {
        "Registering item attributes".info()
    }
}

package me.mochibit.createharmonics.content.item

import me.mochibit.createharmonics.content.item.record.RecordType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries

class EtherealRecordItem(
    val recordType: RecordType,
    props: Properties,
) : Item(props) {
    companion object {
        const val AUDIO_URL_TAG_KEY = "audio_url"

        fun getAudioUrl(stack: ItemStack): String? {
            if (stack.item !is EtherealRecordItem) return null
            return stack.tag?.getString(AUDIO_URL_TAG_KEY)
        }

        fun setAudioUrl(
            stack: ItemStack,
            url: String,
        ) {
            if (stack.item !is EtherealRecordItem) return

            if (stack.tag == null) {
                stack.tag = net.minecraft.nbt.CompoundTag()
            }
            stack.tag?.putString(AUDIO_URL_TAG_KEY, url)
        }
    }

    override fun getDescriptionId(): String =
        "item.${ForgeRegistries.ITEMS.getKey(this)?.namespace}.${ForgeRegistries.ITEMS.getKey(this)?.path}"
}

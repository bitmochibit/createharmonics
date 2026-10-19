package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.content.processing.DepotLikeBehaviour
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordUtilities
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack

/**
 *
 * This behaviour handles:
 * - Accepting Ethereal Records from belt conveyors and other sources
 * - Assigning audio URLs to Ethereal Records
 * - Ejecting processed records back onto conveyors
 */
class RecordPressBaseBehaviour(
    private val be: RecordPressBaseBlockEntity,
) : DepotLikeBehaviour(be) {
    companion object {
        @JvmStatic
        val BEHAVIOUR_TYPE = BehaviourType<RecordPressBaseBehaviour>()
    }

    var config = RecordPressBaseConfiguration()

    /**
     * Assigns an audio URL to an Ethereal Record item after processing is complete.
     * This is called when the press finishes processing the item.
     * The URL is selected from the list based on the selection mode:
     * - Random mode: selects a random URL from the list
     * - Ordered mode: cycles through URLs in order
     *
     * @param stack The item stack to assign the URL to
     */
    private fun assignUrlToItem(stack: ItemStack) {
        val entries = config.entries
        if (entries.isEmpty()) return

        val index =
            if (config.randomMode) selectWeightedRandomIndex(entries)
            else config.currentUrlIndex.mod(entries.size)

        config = config.copy(
            currentUrlIndex = if (config.randomMode) index else (index + 1) % entries.size,
        )
        RecordUtilities.setAudioUrl(stack, entries[index].url)
    }

    /**
     * Selects a random index from the audioUrls list using weighted probability.
     * If weights are not properly initialized, defaults to uniform distribution.
     */
    private fun selectWeightedRandomIndex(entries: List<RecordPressBaseConfiguration.AudioEntry>): Int {
        val total = entries.sumOf { it.weight.toDouble() }
        if (total <= 0.0) return entries.indices.random()
        var r = Math.random() * total
        entries.forEachIndexed { i, e ->
            r -= e.weight
            if (r <= 0.0) return i
        }
        return entries.lastIndex
    }

    override fun addSubBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        DirectBeltInputBehaviour(be)
            .allowingBeltFunnels()
            .setInsertionHandler(this::tryInsertingFromSide)
            .considerOccupiedWhen { side: Direction -> this.isOccupied(side) }
            .also {
                behaviours.add(it)
            }

        transportedHandler =
            TransportedItemStackHandlerBehaviour(
                be,
                this::applyRecipeProcessing,
            ).withStackPlacement { this.getWorldPositionOf() }.also {
                behaviours.add(it)
            }
    }

    override fun processOnlyData(input: TransportedItemStack): Boolean =
        (input.stack.item as? EtherealRecordItem)?.isRecordBroken()?.not() == true

    override fun processData(input: TransportedItemStack): ItemStack {
        input.stack.let {
            if (it.item is EtherealRecordItem) {
                assignUrlToItem(it)
            }
        }
        return input.stack
    }

    override fun write(compound: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(compound, registries, clientPacket)
        compound.putBoolean("RandomMode", config.randomMode)
        compound.putInt("CurrentUrlIndex", config.currentUrlIndex)
        compound.put("Entries", ListTag().apply {
            config.entries.forEach { e ->
                add(CompoundTag().apply {
                    putString("Url", e.url)
                    putFloat("Weight", e.weight)
                    e.name?.let { putString("Name", it) }
                })
            }
        })
    }


    override fun read(compound: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(compound, registries, clientPacket)

        val entries = when {
            compound.contains("Entries", Tag.TAG_LIST.toInt()) ->
                compound.getList("Entries", Tag.TAG_COMPOUND.toInt()).map { tag ->
                    val c = tag as CompoundTag
                    RecordPressBaseConfiguration.AudioEntry(
                        url = c.getString("Url"),
                        weight = c.getFloat("Weight"),
                        name = if (c.contains("Name")) c.getString("Name") else null,
                    )
                }

            // legacy
            compound.contains("AudioUrls") -> {
                val urls = compound.getList("AudioUrls", Tag.TAG_STRING.toInt())
                val weights = compound.getList("UrlWeights", Tag.TAG_FLOAT.toInt())
                (0 until urls.size).map { i ->
                    RecordPressBaseConfiguration.AudioEntry(
                        url = urls.getString(i),
                        weight = if (i < weights.size) weights.getFloat(i) else 1f,
                    )
                }
            }
            else -> config.entries
        }

        config = RecordPressBaseConfiguration(
            randomMode = if (compound.contains("RandomMode")) compound.getBoolean("RandomMode") else config.randomMode,
            currentUrlIndex = if (compound.contains("CurrentUrlIndex")) compound.getInt("CurrentUrlIndex") else config.currentUrlIndex,
            entries = entries,
        )
    }

    override fun getType(): BehaviourType<*> = BEHAVIOUR_TYPE
}


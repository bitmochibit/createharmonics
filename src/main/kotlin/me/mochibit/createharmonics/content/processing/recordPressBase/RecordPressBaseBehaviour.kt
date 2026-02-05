package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.content.processing.DepotLikeBehaviour
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import net.createmod.catnip.math.VecHelper
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.IItemHandler

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

    /** List of audio URLs to be assigned to processed Ethereal Records */
    var audioUrls: MutableList<String> = mutableListOf()

    /** Weights for each URL in random mode (0.0 to 1.0, default 1.0 for equal probability) */
    var urlWeights: MutableList<Float> = mutableListOf()

    /** Selection mode: true = random, false = ordered */
    var randomMode: Boolean = false

    /** Current index for ordered mode */
    var currentUrlIndex: Int = 0

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
        if (audioUrls.isEmpty()) return

        val selectedUrl =
            if (randomMode) {
                // Random mode: pick a random URL using weighted selection
                val randomIndex = selectWeightedRandomIndex()
                currentUrlIndex = randomIndex
                audioUrls[randomIndex]
            } else {
                // Ordered mode: cycle through URLs
                val url = audioUrls[currentUrlIndex % audioUrls.size]
                currentUrlIndex++
                if (currentUrlIndex >= audioUrls.size) currentUrlIndex = 0
                url
            }

        EtherealRecordItem.setAudioUrl(stack, selectedUrl)
    }

    /**
     * Selects a random index from the audioUrls list using weighted probability.
     * If weights are not properly initialized, defaults to uniform distribution.
     */
    private fun selectWeightedRandomIndex(): Int {
        // Ensure weights list is same size as URLs
        ensureWeightsInitialized()

        // Calculate total weight
        val totalWeight = urlWeights.sum()

        // If no valid weights, return random index
        if (totalWeight <= 0f) {
            return audioUrls.indices.random()
        }

        // Select random value between 0 and totalWeight
        val randomValue = Math.random().toFloat() * totalWeight

        // Find the index that corresponds to this random value
        var cumulativeWeight = 0f
        for (i in audioUrls.indices) {
            cumulativeWeight += urlWeights[i]
            if (randomValue <= cumulativeWeight) {
                return i
            }
        }

        // Fallback (should never reach here)
        return audioUrls.indices.last()
    }

    /**
     * Ensures the weights list has the same size as URLs list.
     * Fills missing weights with 1.0 (equal probability).
     */
    private fun ensureWeightsInitialized() {
        while (urlWeights.size < audioUrls.size) {
            urlWeights.add(1f)
        }
        while (urlWeights.size > audioUrls.size) {
            urlWeights.removeAt(urlWeights.size - 1)
        }
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
            TransportedItemStackHandlerBehaviour(be, this::applyRecipeProcessing)
                .withStackPlacement { this.getWorldPositionOf() }
                .also {
                    behaviours.add(it)
                }
    }

    override fun transformHeldOutput(
        heldItem: TransportedItemStack?,
        heldOutput: TransportedItemStack?,
    ): TransportedItemStack? {
        heldItem?.let {
            if (it.stack.item is EtherealRecordItem) {
                assignUrlToItem(it.stack)
                return it
            }
        }
        return heldOutput
    }

    override fun write(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        super.write(compound, clientPacket)

        val urlsTag = net.minecraft.nbt.ListTag()
        for (url in audioUrls) {
            urlsTag.add(
                net.minecraft.nbt.StringTag
                    .valueOf(url),
            )
        }
        compound.put("AudioUrls", urlsTag)

        val weightsTag = net.minecraft.nbt.ListTag()
        ensureWeightsInitialized()
        for (weight in urlWeights) {
            weightsTag.add(
                net.minecraft.nbt.FloatTag
                    .valueOf(weight),
            )
        }
        compound.put("UrlWeights", weightsTag)

        compound.putBoolean("RandomMode", randomMode)
        compound.putInt("CurrentUrlIndex", currentUrlIndex)
    }

    override fun read(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        super.read(compound, clientPacket)

        // Load the list of URLs
        if (compound.contains("AudioUrls")) {
            audioUrls.clear()
            val urlsTag = compound.getList("AudioUrls", 8) // 8 = String tag type
            for (i in 0 until urlsTag.size) {
                audioUrls.add(urlsTag.getString(i))
            }
        }

        // Load the list of weights
        if (compound.contains("UrlWeights")) {
            urlWeights.clear()
            val weightsTag = compound.getList("UrlWeights", 5) // 5 = Float tag type
            for (i in 0 until weightsTag.size) {
                urlWeights.add(weightsTag.getFloat(i))
            }
        }

        // Ensure weights are initialized if missing
        ensureWeightsInitialized()

        if (compound.contains("RandomMode")) {
            randomMode = compound.getBoolean("RandomMode")
        }
        if (compound.contains("CurrentUrlIndex")) {
            currentUrlIndex = compound.getInt("CurrentUrlIndex")
        }
    }

    override fun getType(): BehaviourType<*> = BEHAVIOUR_TYPE
}

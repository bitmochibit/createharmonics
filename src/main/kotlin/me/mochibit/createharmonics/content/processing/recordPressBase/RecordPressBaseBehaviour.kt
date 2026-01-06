package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import net.createmod.catnip.math.VecHelper
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.IItemHandler

/**
 *
 * This behaviour handles:
 * - Accepting items from belt conveyors and other sources
 * - Holding items while they are being processed by the press above
 * - Ejecting processed items back onto conveyors
 * - Assigning audio URLs to processed Ethereal Records
 */
// TODO Maybe for less repeated code, inherit from DepotBehaviour and override necessary methods?
class RecordPressBaseBehaviour(
    private val be: RecordPressBaseBlockEntity,
) : BlockEntityBehaviour(be) {
    companion object {
        @JvmStatic
        val BEHAVIOUR_TYPE = BehaviourType<RecordPressBaseBehaviour>()

        /** Speed at which items move on the base (1/8 block per tick) */
        private const val MOVEMENT_SPEED = 1f / 8f

        /** Position constants for item animation */
        private const val POSITION_EDGE = 1.0f // Item at the edge (entering)
        private const val POSITION_CENTER = 0.5f // Item at center (held)
        private const val POSITION_FAR_EDGE = 0.0f // Item at far edge (exiting)
    }

    /** The item currently being held/processed on the base */
    var heldItem: TransportedItemStack? = null

    /** Queue of items that have arrived but haven't been placed on the base yet */
    val incoming = mutableListOf<TransportedItemStack>()

    /** Queue of items that are leaving the base (for exit animation) */
    val outgoing = mutableListOf<TransportedItemStack>()

    /** List of audio URLs to be assigned to processed Ethereal Records */
    var audioUrls: MutableList<String> = mutableListOf()

    /** Weights for each URL in random mode (0.0 to 1.0, default 1.0 for equal probability) */
    var urlWeights: MutableList<Float> = mutableListOf()

    /** Selection mode: true = random, false = ordered */
    var randomMode: Boolean = false

    /** Current index for ordered mode */
    var currentUrlIndex: Int = 0

    /** Handles the transportation state for items on conveyor belts */
    private lateinit var transportedHandler: TransportedItemStackHandlerBehaviour

    /** Convenience property to get the actual ItemStack being held */
    val heldItemStack: ItemStack
        get() = heldItem?.stack ?: ItemStack.EMPTY

    /**
     * Internal inventory handler that exposes items for external extraction.
     * Slot 0: held item (being processed)
     * Slots 1-8: outgoing items (ready to leave but blocked)
     *
     * Incoming items are not exposed as they're still in transit animation.
     * This allows mechanical arms, funnels, and chutes to extract stuck items.
     */
    private val processingInventory =
        object : IItemHandler {
            override fun getSlots(): Int = 9 // 1 held + 8 outgoing slots (matching Depot's output buffer)

            override fun getStackInSlot(slot: Int): ItemStack =
                when {
                    slot == 0 -> heldItemStack
                    slot in 1..8 -> outgoing.getOrNull(slot - 1)?.stack ?: ItemStack.EMPTY
                    else -> ItemStack.EMPTY
                }

            override fun insertItem(
                slot: Int,
                stack: ItemStack,
                simulate: Boolean,
            ): ItemStack {
                // Only allow insertion into slot 0 (held item)
                if (slot != 0) return stack

                // Reject if already holding an item
                if (!heldItemStack.isEmpty) {
                    return stack
                }

                if (!simulate) {
                    // Create a new transported stack and place it in the center of the base
                    val newHeldItem = TransportedItemStack(stack)
                    newHeldItem.prevBeltPosition = POSITION_CENTER
                    newHeldItem.beltPosition = POSITION_CENTER
                    newHeldItem.insertedFrom = Direction.UP
                    heldItem = newHeldItem
                    be.notifyUpdate()
                }
                return ItemStack.EMPTY
            }

            override fun extractItem(
                slot: Int,
                amount: Int,
                simulate: Boolean,
            ): ItemStack {
                // Extract from held item (slot 0)
                if (slot == 0) {
                    val held = heldItem ?: return ItemStack.EMPTY

                    // Prevent extraction when item is locked (being processed)
                    if (held.locked) {
                        return ItemStack.EMPTY
                    }

                    val stack = held.stack.copy()
                    val extracted = stack.split(amount)

                    if (!simulate) {
                        held.stack = stack
                        if (stack.isEmpty) {
                            heldItem = null
                        }
                        be.notifyUpdate()
                    }
                    return extracted
                }

                // Extract from outgoing items (slots 1-8)
                if (slot in 1..8) {
                    val outgoingIndex = slot - 1
                    if (outgoingIndex >= outgoing.size) return ItemStack.EMPTY

                    val item = outgoing[outgoingIndex]
                    val stack = item.stack.copy()
                    val extracted = stack.split(amount)

                    if (!simulate) {
                        item.stack = stack
                        if (stack.isEmpty) {
                            outgoing.removeAt(outgoingIndex)
                        }
                        be.notifyUpdate()
                    }
                    return extracted
                }

                return ItemStack.EMPTY
            }

            override fun getSlotLimit(slot: Int): Int = 64

            override fun isItemValid(
                slot: Int,
                stack: ItemStack,
            ): Boolean = slot == 0 // Only allow insertion into the held item slot
        }

    /** Lazy provider for the processing inventory, used by external systems */
    val invProvider: LazyOptional<IItemHandler> = LazyOptional.of { processingInventory }

    /**
     * Attempts to insert an item from a belt or conveyor into the base.
     * Items are added to the incoming queue and will be placed on the base
     * when the current held item is removed.
     *
     * @param transportedStack The item being inserted
     * @param side The direction from which the item is being inserted
     * @param simulate If true, only checks if insertion is possible without modifying state
     * @return The remaining items that couldn't be inserted (empty if successful)
     */
    private fun tryInsertingFromSide(
        transportedStack: TransportedItemStack,
        side: Direction,
        simulate: Boolean,
    ): ItemStack {
        // Reject if already holding an item, or if there are items in the queues
        if (!heldItemStack.isEmpty || incoming.isNotEmpty() || outgoing.isNotEmpty()) {
            return transportedStack.stack
        }

        if (simulate) {
            return ItemStack.EMPTY
        }

        // Create a copy and animate it from the edge to the center
        val newStack = transportedStack.copy()
        newStack.insertedFrom = side
        newStack.prevSideOffset = newStack.sideOffset
        // Start from the edge and animate to center
        newStack.prevBeltPosition = POSITION_EDGE
        newStack.beltPosition = POSITION_EDGE

        incoming.add(newStack)

        be.setChanged()
        be.sendData()

        return ItemStack.EMPTY
    }

    /**
     * Updates the animation state of a transported item by moving it from edge to center.
     *
     * @return true when the item has reached the center and is ready to be placed
     */
    private fun tick(transportedStack: TransportedItemStack): Boolean {
        transportedStack.prevBeltPosition = transportedStack.beltPosition
        transportedStack.prevSideOffset = transportedStack.sideOffset

        // Animate from edge to center
        if (transportedStack.beltPosition > POSITION_CENTER) {
            transportedStack.beltPosition =
                (transportedStack.beltPosition - MOVEMENT_SPEED).coerceAtLeast(POSITION_CENTER)
        }

        return transportedStack.beltPosition <= POSITION_CENTER
    }

    /**
     * Updates only the previous positions for rendering interpolation, without animating.
     * Used for items that are stationary (like the held item).
     */
    private fun tickStationary(transportedStack: TransportedItemStack) {
        transportedStack.prevBeltPosition = transportedStack.beltPosition
        transportedStack.prevSideOffset = transportedStack.sideOffset
    }

    /**
     * Updates the animation state of an outgoing item, moving it from center to edge.
     *
     * @return true when the item has reached the edge and is ready to be ejected
     */
    private fun tickOutgoing(transportedStack: TransportedItemStack): Boolean {
        transportedStack.prevBeltPosition = transportedStack.beltPosition
        transportedStack.prevSideOffset = transportedStack.sideOffset

        // Animate from center to far edge
        if (transportedStack.beltPosition > POSITION_FAR_EDGE) {
            transportedStack.beltPosition =
                (transportedStack.beltPosition - MOVEMENT_SPEED).coerceAtLeast(POSITION_FAR_EDGE)
        }

        return transportedStack.beltPosition <= POSITION_FAR_EDGE
    }

    /**
     * Main tick method that runs every game tick. Manages the entire lifecycle of items.
     */
    override fun tick() {
        super.tick()

        processIncomingItems()
        processOutgoingItems()
        processHeldItem()
    }

    /**
     * Processes items in the incoming queue, animating them towards the center.
     * When an item reaches the center, only ONE item is taken from the stack
     * and placed as the held item. The rest remains in the incoming queue.
     */
    private fun processIncomingItems() {
        // Only animate incoming items if the held slot is empty
        val shouldAnimate = heldItem == null

        for (item in incoming) {
            if (shouldAnimate) {
                // Tick the item animation only when held slot is empty
                val reachedCenter = tick(item)

                // Skip processing if not at center yet
                if (!reachedCenter) continue

                // Only place items on server side
                if (world.isClientSide && !be.isVirtual) continue

                // Split off one item from the stack
                val singleItem = item.stack.split(1)
                val heldTransported = TransportedItemStack(singleItem)
                heldTransported.insertedFrom = item.insertedFrom
                heldTransported.beltPosition = POSITION_CENTER
                heldTransported.prevBeltPosition = POSITION_CENTER
                heldItem = heldTransported

                // If there are more items in the stack, reset animation to edge
                if (!item.stack.isEmpty) {
                    item.beltPosition = POSITION_EDGE
                    item.prevBeltPosition = POSITION_EDGE
                }

                be.notifyUpdate()
                // Break after placing one item - next item will be processed next tick
                break
            } else {
                // Held slot is occupied - keep items stationary at the edge
                tickStationary(item)
                if (item.beltPosition != POSITION_EDGE) {
                    item.beltPosition = POSITION_EDGE
                    item.prevBeltPosition = POSITION_EDGE
                }
            }
        }

        // Remove empty stacks
        incoming.removeIf { it.stack.isEmpty }
    }

    /**
     * Processes items in the outgoing queue, animating them towards the edge.
     * When an item reaches the edge, it tries to eject in the original direction.
     * If blocked, the item stays at the edge until the blockage is cleared.
     * Items inserted from above/below cannot be ejected to belts and must be extracted by other means.
     */
    private fun processOutgoingItems() {
        for (item in outgoing) {
            // Continue animation if item hasn't reached edge
            if (!tickOutgoing(item)) continue

            // Only try to eject on server side
            if (world.isClientSide && !be.isVirtual) continue

            // Items from vertical directions cannot eject to belts
            if (item.insertedFrom.axis.isVertical) {
                // Keep item at edge, waiting for mechanical arm/funnel extraction
                item.beltPosition = POSITION_FAR_EDGE
                item.prevBeltPosition = POSITION_FAR_EDGE
                continue
            }

            // Item is at the edge - try to eject in the original direction only
            val ejected = tryEjectToNextBelt(item, item.insertedFrom)

            if (ejected) {
                // Successfully ejected, mark for removal
                item.locked = true // Use locked as a flag to mark for removal
            } else {
                // Blocked! Keep item at edge position (don't animate further)
                item.beltPosition = POSITION_FAR_EDGE
                item.prevBeltPosition = POSITION_FAR_EDGE
            }
        }

        // Remove successfully ejected items
        val iterator = outgoing.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.locked) { // Item was successfully ejected
                iterator.remove()
                be.notifyUpdate()
            }
        }
    }

    /**
     * Processes the held item by communicating with the press above.
     * Since incoming items are split into singles, this always processes one item at a time.
     */
    private fun processHeldItem() {
        val currHeldItem = heldItem ?: return

        // Update positions for smooth rendering
        tickStationary(currHeldItem)

        // Only process on server side
        if (world.isClientSide) return

        // Look for the record press 2 blocks above this base
        val processingBehaviour =
            get(
                world,
                pos.above(2),
                BeltProcessingBehaviour.TYPE,
            )

        // If no processing behavior exists, just eject the item
        if (processingBehaviour == null) {
            if (!currHeldItem.locked) {
                ejectItem(currHeldItem, currHeldItem.insertedFrom)
            }
            return
        }

        // Check if processing is blocked (e.g., output is full)
        if (!currHeldItem.locked && BeltProcessingBehaviour.isBlocked(world, pos)) {
            return
        }

        // Track if the item was previously locked (being processed)
        val wasLocked = currHeldItem.locked

        // Let the press handle the item based on its current state
        val result =
            if (wasLocked) {
                // Item is already being processed, continue processing
                processingBehaviour.handleHeldItem(currHeldItem, transportedHandler)
            } else {
                // Item just arrived, start processing
                processingBehaviour.handleReceivedItem(currHeldItem, transportedHandler)
            }

        // Verify item wasn't extracted by chute/funnel during processing
        // This prevents processing "air" if the item was removed mid-operation
        if (heldItem == null) {
            return
        }

        // Handle the processing result
        when (result) {
            BeltProcessingBehaviour.ProcessingResult.REMOVE -> {
                // Item was consumed by the process, remove it
                heldItem = null
                be.sendData()
                return
            }

            BeltProcessingBehaviour.ProcessingResult.HOLD -> {
                // Item needs to stay for processing
                currHeldItem.locked = true
            }

            BeltProcessingBehaviour.ProcessingResult.PASS -> {
                // Processing is complete, allow item to pass through
                currHeldItem.locked = false
            }

            else -> {}
        }

        // If processing just completed (was locked, now passing), assign the audio URL
        if (result == BeltProcessingBehaviour.ProcessingResult.PASS && wasLocked) {
            assignUrlToItem(currHeldItem.stack)
            be.notifyUpdate()
        }

        // If item is not locked, eject it from the base
        if (!currHeldItem.locked) {
            ejectItem(currHeldItem, currHeldItem.insertedFrom)
        }
    }

    /**
     * Prepares an item for ejection by adding it to the outgoing animation queue.
     * Items exit in the same direction they entered from.
     *
     * @param item The item to eject
     * @param direction The direction to eject the item (same as insertion direction)
     */
    private fun ejectItem(
        item: TransportedItemStack,
        direction: Direction,
    ) {
        // Prepare item for exit animation (starts at center)
        val outgoingItem = item.copy()
        outgoingItem.insertedFrom = direction
        outgoingItem.prevBeltPosition = POSITION_CENTER
        outgoingItem.beltPosition = POSITION_CENTER

        // Add to outgoing queue for animation
        outgoing.add(outgoingItem)

        // Clear the held item
        heldItem = null
        be.notifyUpdate()
    }

    /**
     * Attempts to eject an item to an adjacent belt or conveyor in the specified direction.
     *
     * @param item The item to eject
     * @param direction The direction to try ejecting to
     * @return true if the item was successfully ejected, false otherwise
     */
    private fun tryEjectToNextBelt(
        item: TransportedItemStack,
        direction: Direction,
    ): Boolean {
        val nextPos = pos.relative(direction)
        val nextBehaviour = get(world, nextPos, DirectBeltInputBehaviour.TYPE)

        if (nextBehaviour != null && nextBehaviour.canInsertFromSide(direction)) {
            val returned = nextBehaviour.handleInsertion(item.copy(), direction, false)
            return returned.isEmpty
        }

        return false
    }

    /**
     * Drops an item into the world at the specified position.
     */
    private fun dropItemInWorld(
        stack: ItemStack,
        position: Vec3,
    ) {
        val entity = ItemEntity(world, position.x, position.y + 0.5, position.z, stack)
        world.addFreshEntity(entity)
    }

    /**
     * Handles player interaction with the press base.
     * - If pressing shift: picks up all items and allows placing an item
     * - If not pressing shift: opens GUI
     *
     * @return true if the interaction was handled
     */
    fun onPlayerInteract(
        player: net.minecraft.world.entity.player.Player,
        hand: InteractionHand,
    ): Boolean {
        fun playSound() {
            world.playSound(
                null,
                pos,
                net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                net.minecraft.sounds.SoundSource.PLAYERS,
                0.2f,
                1f + world.random.nextFloat(),
            )
        }

        // Pick up the held item if present
        heldItem?.let {
            player.inventory.placeItemBackInInventory(it.stack)
            heldItem = null
            playSound()
        }

        // Pick up all outgoing items
        if (outgoing.isNotEmpty()) {
            outgoing.forEach { player.inventory.placeItemBackInInventory(it.stack) }
            playSound()
        }
        outgoing.clear()

        if (incoming.isNotEmpty()) {
            incoming.forEach { player.inventory.placeItemBackInInventory(it.stack) }
            playSound()
        }
        incoming.clear()

        be.notifyUpdate()
        return true
    }

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

    /**
     * Registers the sub-behaviours that enable this block to interact with Create's systems.
     *
     * This sets up:
     * - DirectBeltInputBehaviour: Allows belts to insert items into this block
     * - TransportedItemStackHandlerBehaviour: Manages the visual and state representation of transported items
     *
     * @param behaviours The list to add behaviours to
     */
    fun addSubBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        behaviours.add(
            DirectBeltInputBehaviour(be)
                .setInsertionHandler(this::tryInsertingFromSide)
                .considerOccupiedWhen { !heldItemStack.isEmpty },
        )

        // Handle recipe processing for non-Ethereal Record items
        transportedHandler =
            TransportedItemStackHandlerBehaviour(be, this::applyRecipeProcessing)
                .withStackPlacement { VecHelper.getCenterOf(pos) }
        behaviours.add(transportedHandler)
    }

    /**
     * Applies recipe processing to items on the base.
     * This is called by the TransportedItemStackHandlerBehaviour to handle recipe outputs.
     *
     * For Ethereal Records, we skip recipe processing since they use custom URL assignment.
     * For other items, this allows normal pressing recipes to work.
     */
    private fun applyRecipeProcessing(
        maxDistanceFromCentre: Float,
        processFunction: java.util.function.Function<TransportedItemStack, TransportedItemStackHandlerBehaviour.TransportedResult>,
    ) {
        val held = heldItem ?: return

        // Only process items that are centered
        if (POSITION_CENTER - held.beltPosition > maxDistanceFromCentre) return

        // Skip recipe processing for Ethereal Records (they use custom URL system)
        if (held.stack.item is EtherealRecordItem) return

        // Apply the recipe processing function
        val stackBefore = held.stack.copy()
        val result = processFunction.apply(held)

        // Check if anything changed
        if (result.didntChangeFrom(stackBefore)) return

        // Clear the held item slot
        heldItem = null

        // Set the new held output if present
        if (result.hasHeldOutput()) {
            val output = result.heldOutput
            output?.let {
                it.beltPosition = POSITION_CENTER
                it.prevBeltPosition = POSITION_CENTER
                heldItem = it
            }
        }

        // Handle additional outputs (if recipe produces multiple items)
        for (additionalOutput in result.outputs) {
            if (heldItem == null) {
                // If no held item, place this as the held item
                additionalOutput.beltPosition = POSITION_CENTER
                additionalOutput.prevBeltPosition = POSITION_CENTER
                heldItem = additionalOutput
            } else {
                // Otherwise drop into world
                dropItemInWorld(additionalOutput.stack, VecHelper.getCenterOf(pos))
            }
        }

        be.notifyUpdate()
    }

    /**
     * Serializes the behaviour's state to NBT for saving and network synchronization.
     * Saves the currently held item if present.
     */
    override fun write(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        super.write(compound, clientPacket)
        heldItem?.let { compound.put("HeldItem", it.serializeNBT()) }

        // Save incoming items for client animation
        val incomingTag = net.minecraft.nbt.ListTag()
        for (item in incoming) {
            incomingTag.add(item.serializeNBT())
        }
        compound.put("Incoming", incomingTag)

        // Save outgoing items for client animation
        val outgoingTag = net.minecraft.nbt.ListTag()
        for (item in outgoing) {
            outgoingTag.add(item.serializeNBT())
        }
        compound.put("Outgoing", outgoingTag)

        // Save the list of URLs
        val urlsTag = net.minecraft.nbt.ListTag()
        for (url in audioUrls) {
            urlsTag.add(
                net.minecraft.nbt.StringTag
                    .valueOf(url),
            )
        }
        compound.put("AudioUrls", urlsTag)

        // Save the list of weights
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

    /**
     * Deserializes the behaviour's state from NBT when loading or receiving updates.
     * Restores the held item if it was saved.
     */
    override fun read(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        super.read(compound, clientPacket)
        heldItem =
            if (compound.contains("HeldItem")) {
                TransportedItemStack.read(compound.getCompound("HeldItem"))
            } else {
                null
            }

        // Load incoming items for client animation
        incoming.clear()
        if (compound.contains("Incoming")) {
            val incomingTag = compound.getList("Incoming", 10) // 10 = Compound tag type
            for (i in 0 until incomingTag.size) {
                incoming.add(TransportedItemStack.read(incomingTag.getCompound(i)))
            }
        }

        // Load outgoing items for client animation
        outgoing.clear()
        if (compound.contains("Outgoing")) {
            val outgoingTag = compound.getList("Outgoing", 10) // 10 = Compound tag type
            for (i in 0 until outgoingTag.size) {
                outgoing.add(TransportedItemStack.read(outgoingTag.getCompound(i)))
            }
        }

        // Load the list of URLs
        audioUrls.clear()
        if (compound.contains("AudioUrls")) {
            val urlsTag = compound.getList("AudioUrls", 8) // 8 = String tag type
            for (i in 0 until urlsTag.size) {
                audioUrls.add(urlsTag.getString(i))
            }
        }

        // Load the list of weights
        urlWeights.clear()
        if (compound.contains("UrlWeights")) {
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

    /**
     * Called when the block is destroyed. Drops any held item into the world
     * so it isn't lost when the block breaks.
     */
    override fun destroy() {
        super.destroy()

        val dropPos = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)

        // Drop held item
        heldItem?.let {
            world.addFreshEntity(ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, it.stack))
        }

        // Drop all incoming items
        incoming.forEach {
            world.addFreshEntity(ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, it.stack))
        }

        // Drop all outgoing items
        outgoing.forEach {
            world.addFreshEntity(ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, it.stack))
        }
    }

    /**
     * Called when the block entity is unloaded (e.g., chunk unload).
     * Invalidates the inventory capability to prevent memory leaks.
     */
    override fun unload() {
        super.unload()
        invProvider.invalidate()
    }

    override fun getType(): BehaviourType<*> = BEHAVIOUR_TYPE
}

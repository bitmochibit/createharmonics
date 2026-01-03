package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.utility.BlockHelper
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
 * Manages the behavior of the Record Press Base block, which acts as a platform
 * for processing items with the Record Press machinery above it.
 *
 * This behaviour handles:
 * - Accepting items from belt conveyors and other sources
 * - Holding items while they are being processed by the press above
 * - Ejecting processed items back onto conveyors
 * - Assigning audio URLs to processed Ethereal Records
 */
class RecordPressBaseBehaviour(
    private val be: RecordPressBaseBlockEntity,
) : BlockEntityBehaviour(be) {
    companion object {
        @JvmStatic
        val BEHAVIOUR_TYPE = BehaviourType<RecordPressBaseBehaviour>()

        /** Speed at which items are ejected from the press base */
        private const val MOVEMENT_SPEED = 1f / 8f
    }

    /** The item currently being held/processed on the base */
    var heldItem: TransportedItemStack? = null

    /** Queue of items that have arrived but haven't been placed on the base yet */
    private val incoming = mutableListOf<TransportedItemStack>()

    /** List of audio URLs to be assigned to processed Ethereal Records */
    var audioUrls: MutableList<String> = mutableListOf()

    /** Selection mode: true = random, false = ordered */
    var randomMode: Boolean = false

    /** Current index for ordered mode */
    private var currentUrlIndex: Int = 0

    /** Handles the transportation state for items on conveyor belts */
    private lateinit var transportedHandler: TransportedItemStackHandlerBehaviour

    /** Convenience property to get the actual ItemStack being held */
    val heldItemStack: ItemStack
        get() = heldItem?.stack ?: ItemStack.EMPTY

    /**
     * Internal inventory handler that exposes the held item as a single-slot inventory.
     * This allows other systems (like hoppers or pipes) to interact with the base.
     */
    private val processingInventory =
        object : IItemHandler {
            override fun getSlots(): Int = 1

            override fun getStackInSlot(slot: Int): ItemStack = heldItemStack

            override fun insertItem(
                slot: Int,
                stack: ItemStack,
                simulate: Boolean,
            ): ItemStack {
                // Reject if already holding an item
                if (!heldItemStack.isEmpty) {
                    return stack
                }

                if (!simulate) {
                    // Create a new transported stack and place it in the center of the base
                    val newHeldItem = TransportedItemStack(stack)
                    newHeldItem.prevBeltPosition = 0f
                    newHeldItem.beltPosition = 0.5f
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
                val held = heldItem ?: return ItemStack.EMPTY
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

            override fun getSlotLimit(slot: Int): Int = 64

            override fun isItemValid(
                slot: Int,
                stack: ItemStack,
            ): Boolean = true
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
        // Reject if already holding an item
        if (!heldItemStack.isEmpty) {
            return transportedStack.stack
        }

        if (simulate) {
            return ItemStack.EMPTY
        }

        // Create a copy and position it at the center of the base
        val newStack = transportedStack.copy()
        newStack.beltPosition = 0.5f
        newStack.insertedFrom = side
        newStack.prevSideOffset = newStack.sideOffset
        newStack.prevBeltPosition = 0.5f

        incoming.add(newStack)

        be.setChanged()
        be.sendData()

        return ItemStack.EMPTY
    }

    /**
     * Updates the animation state of a transported item by copying current positions
     * to previous positions. This enables smooth interpolation for rendering.
     */
    private fun tick(transportedStack: TransportedItemStack): Boolean {
        transportedStack.prevBeltPosition = transportedStack.beltPosition
        transportedStack.prevSideOffset = transportedStack.sideOffset
        return true
    }

    /**
     * Main tick method that runs every game tick. Manages the entire lifecycle of items:
     * 1. Processes incoming items and places them on the base
     * 2. Communicates with the press above to process held items
     * 3. Ejects completed items back onto belts or into the world
     */
    override fun tick() {
        super.tick()

        // Process incoming items from the queue
        val iterator = incoming.iterator()
        while (iterator.hasNext()) {
            val ts = iterator.next()
            if (!tick(ts)) continue
            if (world.isClientSide && !be.isVirtual) continue

            if (heldItem == null) {
                // Place the incoming item on the base
                heldItem = ts
            } else {
                // If base is occupied, drop the item into the world
                val vec = VecHelper.getCenterOf(pos)
                val entity = ItemEntity(world, vec.x, vec.y + 0.5, vec.z, ts.stack)
                world.addFreshEntity(entity)
            }
            iterator.remove()
            be.notifyUpdate()
        }

        val currHeldItem = heldItem ?: return
        if (!tick(currHeldItem)) return

        // Only process on server side
        if (world.isClientSide) return

        // Look for the record press 2 blocks above this base
        val processingBehaviour =
            get(
                world,
                pos.above(2),
                BeltProcessingBehaviour.TYPE,
            )

        if (processingBehaviour != null) {
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
    }

    /**
     * Attempts to eject a processed item from the base in the specified direction.
     *
     * The ejection process follows this priority:
     * 1. Try to insert into an adjacent belt or conveyor
     * 2. If that fails or is blocked, drop the item into the world with velocity
     *
     * @param item The item to eject
     * @param direction The direction to eject the item (typically the direction it came from)
     */
    private fun ejectItem(
        item: TransportedItemStack,
        direction: Direction,
    ) {
        val side = direction
        val nextPos = pos.relative(side)
        val nextBehaviour = get(world, nextPos, DirectBeltInputBehaviour.TYPE)

        // Try to insert into an adjacent belt or conveyor
        if (nextBehaviour != null && nextBehaviour.canInsertFromSide(side)) {
            val returned = nextBehaviour.handleInsertion(item.copy(), side, false)

            // Successfully inserted all items
            if (returned.isEmpty) {
                heldItem = null
                be.notifyUpdate()
                return
            }

            // Partially inserted, update the held item with remaining items
            if (returned.count != item.stack.count) {
                item.stack = returned
                be.notifyUpdate()
                return
            }
        }

        // If can't insert into adjacent block and there's no solid surface, drop into world
        if (!BlockHelper.hasBlockSolidSide(world.getBlockState(nextPos), world, nextPos, side.opposite)) {
            // Calculate ejection position (slightly offset from center)
            val outPos = VecHelper.getCenterOf(pos).add(Vec3.atLowerCornerOf(direction.normal).scale(0.75))

            // Calculate ejection velocity (move in direction with slight upward arc)
            val outMotion = Vec3.atLowerCornerOf(direction.normal).scale(MOVEMENT_SPEED.toDouble()).add(0.0, 0.125, 0.0)

            // Create and configure the item entity
            val entity = ItemEntity(world, outPos.x, outPos.y + 6.0 / 16.0, outPos.z, item.stack)
            entity.deltaMovement = outMotion
            entity.setDefaultPickUpDelay()
            entity.hurtMarked = true
            world.addFreshEntity(entity)
        }

        // Clear the held item
        heldItem = null
        be.notifyUpdate()
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
                // Random mode: pick a random URL
                audioUrls.random()
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

        // TODO: Make this handler process other crafts normally, except for Ethereal records
        transportedHandler = TransportedItemStackHandlerBehaviour(be) { _, _ -> }
        behaviours.add(transportedHandler)
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

        // Save the list of URLs
        val urlsTag = net.minecraft.nbt.ListTag()
        for (url in audioUrls) {
            urlsTag.add(
                net.minecraft.nbt.StringTag
                    .valueOf(url),
            )
        }
        compound.put("AudioUrls", urlsTag)

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

        // Load the list of URLs
        audioUrls.clear()
        if (compound.contains("AudioUrls")) {
            val urlsTag = compound.getList("AudioUrls", 8) // 8 = String tag type
            for (i in 0 until urlsTag.size) {
                audioUrls.add(urlsTag.getString(i))
            }
        }

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
        heldItem?.let {
            world.addFreshEntity(
                ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, it.stack),
            )
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

package me.mochibit.createharmonics.content.processing

import com.simibubi.create.AllSoundEvents
import com.simibubi.create.content.kinetics.belt.BeltHelper
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.item.ItemHelper
import me.mochibit.createharmonics.extension.onServer
import net.createmod.catnip.math.VecHelper
import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.Containers
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemHandlerHelper
import net.minecraftforge.items.ItemStackHandler
import kotlin.math.min

abstract class DepotLikeBehaviour(
    be: SmartBlockEntity,
) : BlockEntityBehaviour(be) {
    class DepotLikeItemHandler(
        private val behaviour: DepotLikeBehaviour,
    ) : IItemHandler {
        override fun getSlots(): Int = 9

        override fun getStackInSlot(slot: Int): ItemStack =
            if (slot == MAIN_SLOT) {
                behaviour.heldItemStack
            } else {
                behaviour.processingOutputBuffer.getStackInSlot(slot - 1)
            }

        override fun insertItem(
            slot: Int,
            stack: ItemStack,
            simulate: Boolean,
        ): ItemStack {
            if (slot != MAIN_SLOT) return stack
            if (!behaviour.heldItemStack.isEmpty && !behaviour.canMergeItems()) return stack
            if (!behaviour.isOutputEmpty && !behaviour.canMergeItems()) return stack

            val remainder = behaviour.insert(TransportedItemStack(stack), simulate)
            if (!simulate && remainder != stack) behaviour.blockEntity.notifyUpdate()
            return remainder
        }

        override fun extractItem(
            slot: Int,
            amount: Int,
            simulate: Boolean,
        ): ItemStack {
            if (slot != MAIN_SLOT) {
                return behaviour.processingOutputBuffer.extractItem(slot - 1, amount, simulate)
            }

            val held = behaviour.heldItem ?: return ItemStack.EMPTY
            val stack = held.stack.copy()
            val extracted = stack.split(amount)
            if (!simulate) {
                held.stack = stack
                if (stack.isEmpty) {
                    behaviour.heldItem = null
                }
                behaviour.blockEntity.notifyUpdate()
            }
            return extracted
        }

        override fun getSlotLimit(slot: Int): Int = if (slot == MAIN_SLOT) behaviour.maxStackSize() else 64

        override fun isItemValid(
            slot: Int,
            stack: ItemStack,
        ): Boolean = slot == MAIN_SLOT && behaviour.isItemValid(stack)

        companion object {
            private const val MAIN_SLOT = 0
        }
    }

    var heldItem: TransportedItemStack? = null
    var incoming: MutableList<TransportedItemStack> = mutableListOf()
    var processingOutputBuffer: ItemStackHandler
    var itemHandler: DepotLikeItemHandler
    var lazyItemHandler: LazyOptional<DepotLikeItemHandler>
    var transportedHandler: TransportedItemStackHandlerBehaviour? = null
    var maxStackSize: () -> Int = { heldItem?.stack?.maxStackSize ?: 64 }
    var canAcceptItems: () -> Boolean = { true }
    var canFunnelsPullFrom: (Direction) -> Boolean = { true }
    var onHeldInserted: (ItemStack) -> Unit
    var acceptedItems: (ItemStack) -> Boolean
    var allowMerge: Boolean = false

    init {
        acceptedItems = { true }
        onHeldInserted = { }
        itemHandler = DepotLikeItemHandler(this)
        lazyItemHandler = LazyOptional.of { itemHandler }
        processingOutputBuffer =
            object : ItemStackHandler(8) {
                override fun onContentsChanged(slot: Int) {
                    be.notifyUpdate()
                }
            }
    }

    fun enableMerging(): DepotLikeBehaviour {
        allowMerge = true
        return this
    }

    fun withCallback(changeListener: (ItemStack) -> Unit): DepotLikeBehaviour {
        onHeldInserted = changeListener
        return this
    }

    fun onlyAccepts(filter: (ItemStack) -> Boolean): DepotLikeBehaviour {
        acceptedItems = filter
        return this
    }

    override fun tick() {
        super.tick()

        val world = blockEntity.level ?: return

        val iterator = incoming.iterator()
        while (iterator.hasNext()) {
            val ts = iterator.next()
            if (!tick(ts)) {
                continue
            }
            if (world.isClientSide && !blockEntity.isVirtual) {
                continue
            }
            if (heldItem == null) {
                heldItem = ts
            } else {
                val currentHeld = heldItem!!
                if (!ItemHelper.canItemStackAmountsStack(currentHeld.stack, ts.stack)) {
                    val vec = VecHelper.getCenterOf(blockEntity.blockPos)
                    Containers.dropItemStack(world, vec.x, vec.y + .5f, vec.z, ts.stack)
                } else {
                    currentHeld.stack.grow(ts.stack.count)
                }
            }
            iterator.remove()
            blockEntity.notifyUpdate()
        }

        world.onServer {
            tryEjectOutputToBelts()
        }

        val currentHeldItem = heldItem ?: return
        if (!tick(currentHeldItem)) return

        val pos = blockEntity.blockPos

        if (world.isClientSide) return
        if (handleBeltFunnelOutput()) return

        val processingBehaviour = get(world, pos.above(2), BeltProcessingBehaviour.TYPE) ?: return
        if (!currentHeldItem.locked && BeltProcessingBehaviour.isBlocked(world, pos)) return

        val previousItem = currentHeldItem.stack
        val wasLocked = currentHeldItem.locked
        val result =
            if (wasLocked) {
                processingBehaviour.handleHeldItem(heldItem, transportedHandler)
            } else {
                processingBehaviour.handleReceivedItem(heldItem, transportedHandler)
            }

        if (heldItem == null || result == ProcessingResult.REMOVE) {
            heldItem = null
            blockEntity.sendData()
            return
        }

        heldItem!!.locked = result == ProcessingResult.HOLD
        if (heldItem!!.locked != wasLocked || !previousItem.equals(heldItem!!.stack, false)) {
            blockEntity.sendData()
        }
    }

    protected fun tick(heldItem: TransportedItemStack): Boolean {
        heldItem.prevBeltPosition = heldItem.beltPosition
        heldItem.prevSideOffset = heldItem.sideOffset
        val diff = .5f - heldItem.beltPosition
        if (diff > 1 / 512f) {
            if (diff > 1 / 32f && !BeltHelper.isItemUpright(heldItem.stack)) heldItem.angle += 1
            heldItem.beltPosition += diff / 4f
        }
        return diff < 1 / 16f
    }

    private fun handleBeltFunnelOutput(): Boolean {
        val funnel = world.getBlockState(pos.above())
        val funnelFacing = AbstractFunnelBlock.getFunnelFacing(funnel)
        if (funnelFacing == null || !canFunnelsPullFrom(funnelFacing.opposite)) return false

        for (slot in 0..<processingOutputBuffer.slots) {
            val previousItem = processingOutputBuffer.getStackInSlot(slot)
            if (previousItem.isEmpty) continue
            val afterInsert =
                blockEntity
                    .getBehaviour(DirectBeltInputBehaviour.TYPE)
                    ?.tryExportingToBeltFunnel(previousItem, null, false)
            if (afterInsert == null) return false
            if (previousItem.count != afterInsert.count) {
                processingOutputBuffer.setStackInSlot(slot, afterInsert)
                blockEntity.notifyUpdate()
                return true
            }
        }

        val previousItem = heldItem!!.stack
        val afterInsert =
            blockEntity
                .getBehaviour(DirectBeltInputBehaviour.TYPE)
                ?.tryExportingToBeltFunnel(previousItem, null, false)
        if (afterInsert == null) return false
        if (previousItem.count != afterInsert.count) {
            if (afterInsert.isEmpty) {
                heldItem = null
            } else {
                heldItem!!.stack = afterInsert
            }
            blockEntity.notifyUpdate()
            return true
        }

        return false
    }

    private fun tryEjectOutputToBelts(): Boolean {
        for (slot in 0..<processingOutputBuffer.slots) {
            val previousItem = processingOutputBuffer.getStackInSlot(slot)
            if (previousItem.isEmpty) continue

            for (direction in Direction.Plane.HORIZONTAL) {
                val ejected = tryEjectToBelt(previousItem, direction)
                if (ejected.count != previousItem.count) {
                    processingOutputBuffer.setStackInSlot(slot, ejected)
                    blockEntity.notifyUpdate()
                    return true
                }
            }
        }

        return false
    }

    private fun tryEjectToBelt(
        stack: ItemStack,
        direction: Direction,
    ): ItemStack {
        val nextPos = pos.relative(direction)
        val nextBehaviour = get(world, nextPos, DirectBeltInputBehaviour.TYPE) ?: return stack

        if (!nextBehaviour.canInsertFromSide(direction)) return stack

        val transportedStack = TransportedItemStack(stack.copy())
        val returned = nextBehaviour.handleInsertion(transportedStack, direction, false)
        return returned
    }

    override fun destroy() {
        super.destroy()
        val level = getWorld()
        val pos = getPos()
        ItemHelper.dropContents(level, pos, processingOutputBuffer)
        for (transportedItemStack in incoming) {
            Block.popResource(level, pos, transportedItemStack.stack)
        }
        if (!heldItemStack.isEmpty) {
            Block.popResource(level, pos, heldItemStack)
        }
    }

    override fun unload() {
        lazyItemHandler.invalidate()
    }

    override fun write(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        heldItem?.let { compound.put("HeldItem", it.serializeNBT()) }
        compound.put("OutputBuffer", processingOutputBuffer.serializeNBT())
        if (canMergeItems() && incoming.isNotEmpty()) {
            compound.put(
                "Incoming",
                NBTHelper.writeCompoundList(incoming) { obj -> obj.serializeNBT() },
            )
        }
    }

    override fun read(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        heldItem =
            if (compound.contains("HeldItem")) {
                TransportedItemStack.read(compound.getCompound("HeldItem"))
            } else {
                null
            }
        processingOutputBuffer.deserializeNBT(compound.getCompound("OutputBuffer"))
        if (canMergeItems()) {
            val list = compound.getList("Incoming", Tag.TAG_COMPOUND.toInt())
            incoming = NBTHelper.readCompoundList(list) { nbt -> TransportedItemStack.read(nbt) }.toMutableList()
        }
    }

    abstract fun addSubBehaviours(behaviours: MutableList<BlockEntityBehaviour>)

    val heldItemStack: ItemStack
        get() = heldItem?.stack ?: ItemStack.EMPTY

    fun canMergeItems(): Boolean = allowMerge

    val presentStackSize: Int
        get() {
            var cumulativeStackSize = heldItemStack.count
            for (slot in 0..<processingOutputBuffer.slots) {
                cumulativeStackSize +=
                    processingOutputBuffer
                        .getStackInSlot(
                            slot,
                        ).count
            }
            return cumulativeStackSize
        }

    val remainingSpace: Int
        get() {
            var cumulativeStackSize = presentStackSize
            for (transportedItemStack in incoming) {
                cumulativeStackSize += transportedItemStack.stack.count
            }
            val fromGetter =
                min(
                    if (maxStackSize() == 0) 64 else maxStackSize(),
                    heldItemStack.maxStackSize,
                )
            return fromGetter - cumulativeStackSize
        }

    fun insert(
        heldItem: TransportedItemStack,
        simulate: Boolean,
    ): ItemStack {
        var heldItem = heldItem
        if (!canAcceptItems()) return heldItem.stack
        if (!acceptedItems(heldItem.stack)) return heldItem.stack

        if (canMergeItems()) {
            val remainingSpace = this.remainingSpace
            val inserted = heldItem.stack
            if (remainingSpace <= 0) return inserted
            if (this.heldItem != null &&
                !ItemHelper.canItemStackAmountsStack(
                    this.heldItem!!.stack,
                    inserted,
                )
            ) {
                return inserted
            }

            var returned = ItemStack.EMPTY
            if (remainingSpace < inserted.count) {
                returned = ItemHandlerHelper.copyStackWithSize(heldItem.stack, inserted.count - remainingSpace)
                if (!simulate) {
                    val copy = heldItem.copy()
                    copy.stack.count = remainingSpace
                    if (this.heldItem != null) {
                        incoming.add(copy)
                    } else {
                        this.heldItem = copy
                    }
                }
            } else {
                if (!simulate) {
                    if (this.heldItem != null) {
                        incoming.add(heldItem)
                    } else {
                        this.heldItem = heldItem
                    }
                }
            }
            return returned
        }

        var returned = ItemStack.EMPTY
        val maxCount = heldItem.stack.maxStackSize
        val stackTooLarge = maxCount < heldItem.stack.count
        if (stackTooLarge) {
            returned = ItemHandlerHelper.copyStackWithSize(heldItem.stack, heldItem.stack.count - maxCount)
        }

        if (simulate) return returned

        if (isEmpty) {
            if (heldItem.insertedFrom.axis.isHorizontal) {
                AllSoundEvents.DEPOT_SLIDE.playOnServer(getWorld(), getPos())
            } else {
                AllSoundEvents.DEPOT_PLOP.playOnServer(getWorld(), getPos())
            }
        }

        if (stackTooLarge) {
            heldItem = heldItem.copy()
            heldItem.stack.count = maxCount
        }

        this.heldItem = heldItem
        onHeldInserted(heldItem.stack)
        return returned
    }

    fun removeHeldItem() {
        this.heldItem = null
    }

    fun setCenteredHeldItem(heldItem: TransportedItemStack?) {
        this.heldItem = heldItem
        heldItem?.let {
            it.beltPosition = 0.5f
            it.prevBeltPosition = 0.5f
        }
    }

    fun <T> getItemCapability(
        cap: Capability<T?>?,
        side: Direction?,
    ): LazyOptional<T?> = lazyItemHandler.cast()

    fun isOccupied(side: Direction?): Boolean {
        if (!heldItemStack.isEmpty && !canMergeItems()) return true
        if (!isOutputEmpty && !canMergeItems()) return true
        if (!canAcceptItems()) return true
        return false
    }

    fun tryInsertingFromSide(
        transportedStack: TransportedItemStack,
        side: Direction,
        simulate: Boolean,
    ): ItemStack {
        var transportedStack = transportedStack
        val inserted = transportedStack.stack

        if (isOccupied(side)) return inserted

        val size = transportedStack.stack.count
        transportedStack = transportedStack.copy()
        transportedStack.beltPosition = if (side.axis.isVertical) 0.5f else 0f
        transportedStack.insertedFrom = side
        transportedStack.prevSideOffset = transportedStack.sideOffset
        transportedStack.prevBeltPosition = transportedStack.beltPosition
        val remainder = insert(transportedStack, simulate)
        if (remainder.count != size) {
            blockEntity.notifyUpdate()
        }

        return remainder
    }

    open fun transformHeldOutput(
        heldItem: TransportedItemStack?,
        heldOutput: TransportedItemStack?,
    ): TransportedItemStack? = heldOutput

    open fun processOnlyData(input: TransportedItemStack): Boolean = false

    open fun processData(input: TransportedItemStack): ItemStack = input.stack

    fun applyRecipeProcessing(
        maxDistanceFromCentre: Float,
        processFunction: java.util.function.Function<TransportedItemStack, TransportedResult>,
    ) {
        val currentHeldItem = heldItem ?: return
        if (0.5f - currentHeldItem.beltPosition > maxDistanceFromCentre) return

        val stackBefore = currentHeldItem.stack.copy()
        val result = processFunction.apply(currentHeldItem)

        if (processOnlyData(currentHeldItem)) {
            val processedStack = processData(currentHeldItem).copy()
            heldItem = null
            val remainder = ItemHandlerHelper.insertItemStacked(processingOutputBuffer, processedStack, false)
            val vec = VecHelper.getCenterOf(blockEntity.blockPos)
            Containers.dropItemStack(blockEntity.level, vec.x, vec.y + 0.5f, vec.z, remainder)
            blockEntity.notifyUpdate()
            return
        }

        if (result.didntChangeFrom(stackBefore)) return

        heldItem = null
        if (result.hasHeldOutput()) {
            setCenteredHeldItem(
                transformHeldOutput(currentHeldItem, result.heldOutput),
            )
        }

        for (added in result.outputs) {
            if (heldItemStack.isEmpty) {
                setCenteredHeldItem(
                    transformHeldOutput(currentHeldItem, added),
                )
                continue
            }
            val remainder = ItemHandlerHelper.insertItemStacked(processingOutputBuffer, added.stack, false)
            val vec = VecHelper.getCenterOf(blockEntity.blockPos)
            Containers.dropItemStack(blockEntity.level, vec.x, vec.y + 0.5f, vec.z, remainder)
        }

        blockEntity.notifyUpdate()
    }

    val isEmpty: Boolean
        get() = heldItem == null && this.isOutputEmpty

    val isOutputEmpty: Boolean
        get() {
            for (i in 0..<processingOutputBuffer.slots) {
                if (!processingOutputBuffer.getStackInSlot(i).isEmpty) {
                    return false
                }
            }
            return true
        }

    fun getWorldPositionOf(): Vec3 = VecHelper.getCenterOf(blockEntity.blockPos)

    fun isItemValid(stack: ItemStack): Boolean = acceptedItems(stack)
}

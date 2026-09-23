package me.mochibit.createharmonics.content.uploader

import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerTrait
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.foundation.extension.onServer
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.items.ItemStackHandler

class AmethystCatalystBehaviour(
    val be: AmethystCatalystBlockEntity
): BlockEntityBehaviour(be) {
    companion object {
        @JvmStatic
        val BEHAVIOUR_TYPE = BehaviourType<AmethystCatalystBehaviour>()
    }

    private val randomSource = RandomSource.create()

    override fun getType(): BehaviourType<*> = BEHAVIOUR_TYPE

    val itemHandler = AmethystCatalystItemHandler(this)

    val hasCrystal: Boolean
        get() = !itemHandler.getStackInSlot(AmethystCatalystItemHandler.CRYSTAL_SLOT).isEmpty

    fun getCrystal(): ItemStack = itemHandler.getStackInSlot(AmethystCatalystItemHandler.CRYSTAL_SLOT).copy()

    fun insertCrystal(crystalItem: ItemStack): Boolean {
        if (hasCrystal) return false
        if (!crystalItem.`is`(Items.AMETHYST_SHARD)) return false
        itemHandler.setStackInSlot(AmethystCatalystItemHandler.CRYSTAL_SLOT, crystalItem.copy())
        this.be.level?.playSound(
            null,
            pos,
            SoundEvents.AMETHYST_BLOCK_PLACE,
            SoundSource.PLAYERS,
            0.2f,
            1f + RandomSource.create().nextFloat(),
        )
        return true
    }

    fun popCrystal(): ItemStack {
        val stack = getCrystal()
        itemHandler.setStackInSlot(AmethystCatalystItemHandler.CRYSTAL_SLOT, ItemStack.EMPTY)
        if (!stack.isEmpty) {
            this.be.level?.playSound(
                null,
                pos,
                SoundEvents.AMETHYST_CLUSTER_FALL,
                SoundSource.PLAYERS,
                0.2f,
                1f + RandomSource.create().nextFloat(),
            )
        }
        return stack
    }

    fun onCrystalChange(outcome: CrystalChangeOutcome) {
        when(outcome) {
            CrystalChangeOutcome.INSERTED -> {
                this.be.level?.playSound(
                    null,
                    pos,
                    SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS,
                    2f,
                    RandomSource.create().nextFloat().coerceIn(0.0f..0.5f),
                )
                this.be.level?.playSound(
                    null,
                    pos,
                    SoundEvents.BELL_RESONATE,
                    SoundSource.PLAYERS,
                    2f,
                    1+RandomSource.create().nextFloat().coerceIn(0.0f..0.5f),
                )
            }

            CrystalChangeOutcome.REMOVED -> {
                this.be.level?.playSound(
                    null,
                    pos,
                    SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS,
                    2f,
                    RandomSource.create().nextFloat().coerceIn(0.0f..0.5f),
                )
                this.be.level?.playSound(
                    null,
                    pos,
                    SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS,
                    2f,
                    RandomSource.create().nextFloat().coerceIn(0.0f..0.5f),
                )
            }
        }
    }

}

enum class CrystalChangeOutcome {
    INSERTED,
    REMOVED
}


class AmethystCatalystItemHandler(
    val behaviour: AmethystCatalystBehaviour,
    private val targetSlotCount: Int = 1,
) : ItemStackHandler(targetSlotCount) {
    companion object {
        const val CRYSTAL_SLOT = 0
    }

    override fun insertItem(
        slot: Int,
        stack: ItemStack,
        simulate: Boolean,
    ): ItemStack {
        if (slot != CRYSTAL_SLOT) return stack
        return super.insertItem(slot, stack, simulate)
    }

    override fun onLoad() {
        behaviour.be.onServer {
            behaviour.blockEntity.notifyUpdate()
        }
    }

    override fun onContentsChanged(slot: Int) {
        behaviour.be.onServer {
            val hasCrystal = !getStackInSlot(CRYSTAL_SLOT).isEmpty
            val be = behaviour.blockEntity
            if (hasCrystal) {
                behaviour.onCrystalChange(CrystalChangeOutcome.INSERTED)
            } else {
                behaviour.onCrystalChange(CrystalChangeOutcome.REMOVED)
            }

            be.level?.setBlockAndUpdate(
                be.blockPos,
                be.blockState.setValue(AmethystCatalystBlock.POWERED, hasCrystal),
            )
            be.notifyUpdate()
        }
    }

    override fun isItemValid(
        slot: Int,
        stack: ItemStack,
    ): Boolean {
        return stack.isEmpty || stack.`is`(Items.AMETHYST_SHARD)
    }

    override fun deserializeNBT(
        provider: HolderLookup.Provider,
        nbt: CompoundTag,
    ) {
        super.deserializeNBT(provider, nbt)
        if (stacks.size < targetSlotCount) {
            val expanded = NonNullList.withSize(targetSlotCount, ItemStack.EMPTY)
            for (i in stacks.indices) expanded[i] = stacks[i]
            stacks = expanded
        }
    }
}
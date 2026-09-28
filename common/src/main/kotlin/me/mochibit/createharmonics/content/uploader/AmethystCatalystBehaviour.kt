package me.mochibit.createharmonics.content.uploader

import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.foundation.extension.onServer
import me.mochibit.createharmonics.foundation.inventory.GenericInventory
import me.mochibit.createharmonics.foundation.inventory.InventorySpec
import me.mochibit.createharmonics.foundation.inventory.StorageInventory
import me.mochibit.createharmonics.foundation.inventory.clearContent
import me.mochibit.createharmonics.foundation.inventory.dropContents
import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.Clearable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class AmethystCatalystBehaviour(
    val be: AmethystCatalystBlockEntity
) : BlockEntityBehaviour(be), Clearable {
    companion object {
        @JvmStatic
        val BEHAVIOUR_TYPE = BehaviourType<AmethystCatalystBehaviour>()

        const val CRYSTAL_SLOT = 0
    }

    private val randomSource = RandomSource.create()

    override fun getType(): BehaviourType<*> = BEHAVIOUR_TYPE

    val itemHandler: StorageInventory = contentService.createInventoryFromSpec(
        InventorySpec(
            size = 1,
            isItemValid = { _, stack -> stack.isEmpty || stack.`is`(Items.AMETHYST_SHARD) },
            slotLimit = { _ -> 1 },
        ), onChange = { _ ->
            val hasCrystal = !getStackInSlot(CRYSTAL_SLOT).isEmpty
            if (hasCrystal) {
                be.behaviour.onCrystalChange(CrystalChangeOutcome.INSERTED)
            } else {
                be.behaviour.onCrystalChange(CrystalChangeOutcome.REMOVED)
            }

            be.level?.setBlockAndUpdate(
                be.blockPos,
                be.blockState.setValue(AmethystCatalystBlock.POWERED, hasCrystal),
            )
            be.notifyUpdate()
        }, onLoad = {
            blockEntity.notifyUpdate()
        }
    )

    val hasCrystal: Boolean
        get() = !itemHandler.getStackInSlot(CRYSTAL_SLOT).isEmpty

    fun getCrystal(): ItemStack = itemHandler.getStackInSlot(CRYSTAL_SLOT).copy()

    fun insertCrystal(crystalItem: ItemStack): Boolean {
        if (hasCrystal) return false
        if (!crystalItem.`is`(Items.AMETHYST_SHARD)) return false
        itemHandler.setStackInSlot(CRYSTAL_SLOT, crystalItem.copyWithCount(1))
        this.be.level?.playSound(
            null,
            pos,
            SoundEvents.AMETHYST_BLOCK_PLACE,
            SoundSource.PLAYERS,
            0.5f,
            1f + RandomSource.create().nextFloat(),
        )
        return true
    }

    fun popCrystal(): ItemStack {
        val stack = getCrystal()
        itemHandler.setStackInSlot(CRYSTAL_SLOT, ItemStack.EMPTY)
        if (!stack.isEmpty) {
            this.be.level?.playSound(
                null,
                pos,
                SoundEvents.AMETHYST_CLUSTER_FALL,
                SoundSource.PLAYERS,
                0.5f,
                1f + RandomSource.create().nextFloat(),
            )
        }
        return stack
    }

    fun onCrystalChange(outcome: CrystalChangeOutcome) {
        when (outcome) {
            CrystalChangeOutcome.INSERTED -> {
                this.be.level?.playSound(
                    null,
                    pos,
                    SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS,
                    1.2f,
                    RandomSource.create().nextFloat().coerceIn(0.0f..0.5f),
                )
                this.be.level?.playSound(
                    null,
                    pos,
                    SoundEvents.BELL_RESONATE,
                    SoundSource.PLAYERS,
                    1.2f,
                    1 + RandomSource.create().nextFloat().coerceIn(0.0f..0.5f),
                )
            }

            CrystalChangeOutcome.REMOVED -> {
                this.be.level?.playSound(
                    null,
                    pos,
                    SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS,
                    .8f,
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

    override fun clearContent() {
        this.itemHandler.clearContent()
    }

    override fun destroy() {
        this.be.level?.onServer { serverLevel ->
            this.itemHandler.dropContents(serverLevel, be.blockPos)
            onCrystalChange(CrystalChangeOutcome.REMOVED)
        }
        super.destroy()
    }

    override fun unload() {
        contentService.invalidateBlockEntityStorage(be)
        super.unload()
    }

    override fun write(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(nbt, registries, clientPacket)
        nbt.put("Inventory", itemHandler.serializeNBT(registries))
    }

    override fun read(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        if (nbt.contains("Inventory")) {
            itemHandler.deserializeNBT(registries, nbt.getCompound("Inventory"))
        }
        super.read(nbt, registries, clientPacket)
    }

}

enum class CrystalChangeOutcome {
    INSERTED, REMOVED
}

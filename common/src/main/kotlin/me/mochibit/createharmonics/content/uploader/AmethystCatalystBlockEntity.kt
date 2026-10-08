package me.mochibit.createharmonics.content.uploader

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.audio.soundscape.ModSoundScapes
import me.mochibit.createharmonics.audio.upload.UploadSession
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBehaviour
import me.mochibit.createharmonics.foundation.extension.onClient
import me.mochibit.createharmonics.foundation.extension.onServer
import me.mochibit.createharmonics.foundation.inventory.InventorySpec
import me.mochibit.createharmonics.foundation.inventory.StorageInventory
import me.mochibit.createharmonics.foundation.inventory.clearContent
import me.mochibit.createharmonics.foundation.inventory.dropContents
import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.Clearable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class AmethystCatalystBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
): SmartBlockEntity(type, pos, state), Clearable {
    companion object {
        const val CRYSTAL_SLOT = 0

        // beam params
        const val BEAM_SPEED = 8f
        const val BEAM_MIN_TICKS = 60
    }

    class BeamFrame(val grow: Float, val retract: Float, val length: Float, val descending: Boolean)

    enum class CrystalChangeOutcome { INSERTED, REMOVED }

    enum class Phase(val hasBeam: Boolean) {
        IDLE(false), UPLOADING(true), DELETING(true)
    }


    val itemHandler: StorageInventory = contentService.createInventoryFromSpec(
        InventorySpec(
            size = 1,
            isItemValid = { _, stack -> stack.isEmpty || stack.`is`(Items.AMETHYST_SHARD) },
            slotLimit = { _ -> 1 },
        ), onChange = { _ ->
            val hasCrystal = !getStackInSlot(CRYSTAL_SLOT).isEmpty
            if (hasCrystal) {
                this@AmethystCatalystBlockEntity.onCrystalChange(CrystalChangeOutcome.INSERTED)
            } else {
                this@AmethystCatalystBlockEntity.onCrystalChange(CrystalChangeOutcome.REMOVED)
            }

            this@AmethystCatalystBlockEntity.level?.setBlockAndUpdate(
                this@AmethystCatalystBlockEntity.blockPos,
                this@AmethystCatalystBlockEntity.blockState.setValue(AmethystCatalystBlock.POWERED, hasCrystal),
            )
            this@AmethystCatalystBlockEntity.notifyUpdate()
        }, onLoad = {
            this@AmethystCatalystBlockEntity.notifyUpdate()
        }
    )

    var phase = Phase.IDLE
        private set
    val isUploading get() = phase == Phase.UPLOADING
    val isDeleting get() = phase == Phase.DELETING
    val isBusy get() = phase != Phase.IDLE
    var uploadingFileName: String? = null
        private set
    var uploadingProgress = 0f
        private set

    private var effectNonce = 0
    private var nonceSynced = false

    // Server only data
    @Transient
    private var session: UploadSession? = null
    private var deleteTicksLeft = 0

    // Client only data
    private var beamStart = -1L
    private var retractStart = -1L
    private var beamDescending = false

    // Block API
    val hasCrystal: Boolean
        get() = !itemHandler.getStackInSlot(CRYSTAL_SLOT).isEmpty

    fun getCrystal(): ItemStack = itemHandler.getStackInSlot(CRYSTAL_SLOT).copy()

    fun insertCrystal(crystalItem: ItemStack): Boolean {
        if (hasCrystal) return false
        if (!crystalItem.`is`(Items.AMETHYST_SHARD)) return false
        itemHandler.setStackInSlot(CRYSTAL_SLOT, crystalItem.copyWithCount(1))
        this.level?.playSound(
            null,
            this.blockPos,
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
            this.level?.playSound(
                null,
                this.blockPos,
                SoundEvents.AMETHYST_CLUSTER_FALL,
                SoundSource.PLAYERS,
                0.5f,
                1f + RandomSource.create().nextFloat(),
            )
        }
        return stack
    }

    /**
     * Reserves the block entity to the current upload session
     * @return an [UploadSession] if it can be reserved, otherwise if already busy it will return null
     */
    fun tryReserve(fileName: String): UploadSession? {
        if (isBusy) return null
        val s = UploadSession(fileName)
        session = s
        uploadingFileName = s.fileName
        uploadingProgress = 0f
        changePhase(Phase.UPLOADING)
        return s
    }

    /**
     * Attempts to start a delete file process
     * @return false if the block is already busy doing something
     */
    fun tryStartDelete(): Boolean {
        if (isBusy) return false
        deleteTicksLeft = beamTicks.toInt() + 1
        changePhase(Phase.DELETING)
        return true
    }

    // Phases
    private fun changePhase(next: Phase) {
        if (next.hasBeam) effectNonce++
        enterPhase(next)
        sendData()
    }

    private fun enterPhase(next: Phase) {
        val prev = phase
        if (prev == next) return
        phase = next
        onPhaseEnd(prev)
        onPhaseStart(next)
    }

    private fun onPhaseStart(phase: Phase) = when (phase) {
        Phase.UPLOADING -> sound(SoundEvents.BEACON_ACTIVATE, 1f, 1.5f)
        Phase.DELETING -> sound(SoundEvents.BEACON_DEACTIVATE, 1f, 1.2f)
        Phase.IDLE -> Unit
    }

    private fun onPhaseEnd(phase: Phase) = when (phase) {
        Phase.UPLOADING -> sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f)
        Phase.DELETING -> sound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1f)
        Phase.IDLE -> Unit
    }

    private fun onPhaseTick(phase: Phase) {
        val time = level?.gameTime ?: return
        when (phase) {
            Phase.UPLOADING -> if (time % 10L == 0L) sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4f, 1.8f)
            Phase.DELETING -> if (time % 10L == 0L) sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4f, 0.8f)
            Phase.IDLE -> Unit
        }
    }

    // Client beam

    private val beamLength: Float
        get() = level?.let { (it.maxBuildHeight - blockPos.y - 1).coerceAtLeast(0).toFloat() } ?: 0f

    private val beamTicks: Float
        get() = (beamLength / BEAM_SPEED).coerceAtLeast(1f)

    fun beamFrame(partialTicks: Float): BeamFrame? {
        if (beamStart < 0) return null
        val level = level ?: return null
        val now = level.gameTime
        val ticks = beamTicks
        val grow = ((now - beamStart) + partialTicks) / ticks
        val retract = if (retractStart < 0) 0f else ((now - retractStart) + partialTicks) / ticks
        return BeamFrame(grow.coerceIn(0f, 1f), retract.coerceIn(0f, 1f), beamLength, beamDescending)
    }

    private fun startBeam(phase: Phase) {
        beamDescending = phase == Phase.DELETING
        beamStart = level?.gameTime ?: -1L
        retractStart = -1L
    }

    private fun tickBeam(now: Long) {
        if (beamStart < 0) return
        if (retractStart < 0) {
            if (!isBusy && now - beamStart >= BEAM_MIN_TICKS) retractStart = now
        } else if (now - retractStart >= beamTicks) {
            beamStart = -1L
            retractStart = -1L
        }
    }


    override fun destroy() {
        this.level?.onServer { serverLevel ->
            if (hasCrystal) {
                this.itemHandler.dropContents(serverLevel, this.blockPos)
                onCrystalChange(CrystalChangeOutcome.REMOVED)
            }
        }
        super.destroy()
    }

    override fun invalidate() {
        contentService.invalidateBlockEntityStorage(this)
        super.invalidate()
    }

    override fun tick() {
        super.tick()

        this.level?.onClient { level, _ ->
            tickBeam(level.gameTime)
            if (hasCrystal) ModSoundScapes.play(ModSoundScapes.AmbienceGroup.RESONATING, worldPosition, 0.5f)
        }
        this.level?.onServer { tickServer() }

        onPhaseTick(phase)
    }

    private fun tickServer() {
        when (phase) {
            Phase.UPLOADING -> {
                val s = session ?: return
                if (s.finished) {
                    session = null
                    uploadingFileName = null
                    uploadingProgress = 0f
                    changePhase(Phase.IDLE)
                } else if (s.progress != uploadingProgress) {
                    uploadingProgress = s.progress
                    sendData()
                }
            }
            Phase.DELETING -> if (--deleteTicksLeft <= 0) changePhase(Phase.IDLE)
            Phase.IDLE -> Unit
        }
    }

    override fun write(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(nbt, registries, clientPacket)
        nbt.put("Inventory", itemHandler.serializeNBT(registries))

        if (clientPacket) {
            nbt.putString("Phase", phase.name)
            nbt.putInt("EffectNonce", effectNonce)
            if (isUploading) {
                nbt.putString("File", uploadingFileName.orEmpty())
                nbt.putFloat("Progress", uploadingProgress)
            }
        }
    }

    override fun read(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(nbt, registries, clientPacket)
        if (nbt.contains("Inventory")) {
            itemHandler.deserializeNBT(registries, nbt.getCompound("Inventory"))
        }
        if (!clientPacket) return

        val newPhase = Phase.entries.firstOrNull { it.name == nbt.getString("Phase") } ?: Phase.IDLE
        uploadingFileName = if (newPhase == Phase.UPLOADING) nbt.getString("File") else null
        uploadingProgress = if (newPhase == Phase.UPLOADING) nbt.getFloat("Progress") else 0f

        val nonce = nbt.getInt("EffectNonce")
        if (nonceSynced) {
            if (nonce != effectNonce && newPhase.hasBeam) startBeam(newPhase)
            enterPhase(newPhase)
        } else {
            phase = newPhase
        }
        effectNonce = nonce
        nonceSynced = true
    }

    override fun clearContent() {
        this.itemHandler.clearContent()
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {}

    private fun sound(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) {
        level?.playSound(null, blockPos, event, SoundSource.PLAYERS, volume, pitch)
    }

    private fun randomPitch(base: Float, spread: Float) = base + RandomSource.create().nextFloat() * spread

    private fun onCrystalChange(outcome: CrystalChangeOutcome) = when (outcome) {
        CrystalChangeOutcome.INSERTED -> {
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, randomPitch(0f, 0.5f))
            sound(SoundEvents.BELL_RESONATE, 1.2f, randomPitch(1f, 0.5f))
        }
        CrystalChangeOutcome.REMOVED -> {
            sound(SoundEvents.BEACON_DEACTIVATE, 0.8f, randomPitch(0f, 0.5f))
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, randomPitch(0f, 0.5f))
        }
    }

}
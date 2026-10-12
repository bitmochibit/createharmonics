package me.mochibit.createharmonics.content.uploader

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.audio.soundscape.ModSoundScapes
import me.mochibit.createharmonics.audio.upload.UploadSession
import me.mochibit.createharmonics.foundation.extension.onClient
import me.mochibit.createharmonics.foundation.extension.onServer
import me.mochibit.createharmonics.foundation.inventory.InventorySpec
import me.mochibit.createharmonics.foundation.inventory.StorageInventory
import me.mochibit.createharmonics.foundation.inventory.clearContent
import me.mochibit.createharmonics.foundation.inventory.dropContents
import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.Clearable
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.max

class AmethystCatalystBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : SmartBlockEntity(type, pos, state), Clearable {

    companion object {
        const val CRYSTAL_SLOT = 0
        private const val TICK_SOUND_INTERVAL = 10L
        private const val SHOCK_DAMAGE = 2f
        private const val SHOCK_KNOCKBACK = 0.6
        private const val RAIN_CHANCE = 0.15f
    }

    enum class CrystalChangeOutcome { INSERTED, REMOVED }

    class PhaseSound(val event: SoundEvent, val volume: Float = 1f, val pitch: Float = 1f)

    // beam state machine
    enum class Phase(
        val hasBeam: Boolean = false,
        val onStart: PhaseSound? = null,
        val onEnd: PhaseSound? = null,
        val onTick: PhaseSound? = null,
    ) {
        IDLE,
        UPLOADING(
            hasBeam = true,
            onStart = PhaseSound(SoundEvents.BEACON_ACTIVATE, 1f, 1.5f),
            onEnd = PhaseSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f),
            onTick = PhaseSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4f, 1.8f),
        ),
        DELETING(
            hasBeam = true,
            onStart = PhaseSound(SoundEvents.BEACON_DEACTIVATE, 1f, 1.2f),
            onEnd = PhaseSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1f),
            onTick = PhaseSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4f, 0.8f),
        ),
        COOLDOWN
    }

    val itemHandler: StorageInventory = contentService.createInventoryFromSpec(
        InventorySpec(
            size = 1,
            isItemValid = { _, stack -> stack.isEmpty || stack.`is`(Items.AMETHYST_SHARD) },
            slotLimit = { _ -> 1 },
            canExtract = { _ -> !this@AmethystCatalystBlockEntity.isBusy },
        ),
        onChange = { _ ->
            val hasCrystal = !getStackInSlot(CRYSTAL_SLOT).isEmpty
            this@AmethystCatalystBlockEntity.onCrystalChange(
                if (hasCrystal) CrystalChangeOutcome.INSERTED else CrystalChangeOutcome.REMOVED,
            )
            this@AmethystCatalystBlockEntity.level?.setBlockAndUpdate(
                this@AmethystCatalystBlockEntity.blockPos,
                this@AmethystCatalystBlockEntity.blockState.setValue(AmethystCatalystBlock.POWERED, hasCrystal),
            )
            this@AmethystCatalystBlockEntity.notifyUpdate()
        },
        onLoad = { this@AmethystCatalystBlockEntity.notifyUpdate() },
    )

    // State (synced)
    var phase = Phase.IDLE
        private set
    val isUploading get() = phase == Phase.UPLOADING
    val isDeleting get() = phase == Phase.DELETING
    val isBusy get() = phase != Phase.IDLE
    val isBeamPhase get() = phase.hasBeam
    var uploadingFileName: String? = null
        private set
    var uploadingProgress = 0f
        private set

    private var effectNonce = 0
    private var nonceSynced = false

    // Server only
    @Transient
    private var session: UploadSession? = null
    private var deleteTicksLeft = 0
    private var cooldownTicksLeft = 0
    private var beamStartTime = 0L

    // Client only
    private val beam = BeamAnimator()

    private val beamLength: Float
        get() = level?.let { (it.maxBuildHeight - blockPos.y - 1).coerceAtLeast(0).toFloat() } ?: 0f

    /** Called by the renderer every frame. */
    fun beamFrame(partialTicks: Float): BeamFrame? {
        val level = level ?: return null
        return beam.frame(level.gameTime + partialTicks, beamLength)
    }

    // Crystal API
    val hasCrystal: Boolean
        get() = !itemHandler.getStackInSlot(CRYSTAL_SLOT).isEmpty

    fun getCrystal(): ItemStack = itemHandler.getStackInSlot(CRYSTAL_SLOT).copy()

    fun insertCrystal(crystalItem: ItemStack): Boolean {
        if (hasCrystal) return false
        if (!crystalItem.`is`(Items.AMETHYST_SHARD)) return false
        itemHandler.setStackInSlot(CRYSTAL_SLOT, crystalItem.copyWithCount(1))
        level?.playSound(
            null, blockPos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS,
            0.5f, 1f + RandomSource.create().nextFloat(),
        )
        return true
    }

    fun popCrystal(): ItemStack {
        val stack = getCrystal()
        itemHandler.setStackInSlot(CRYSTAL_SLOT, ItemStack.EMPTY)
        if (!stack.isEmpty) {
            level?.playSound(
                null, blockPos, SoundEvents.AMETHYST_CLUSTER_FALL, SoundSource.PLAYERS,
                0.5f, 1f + RandomSource.create().nextFloat(),
            )
        }
        return stack
    }

    fun shock(player: Player) {
        val sl = level as? ServerLevel ?: return
        if (player.isCreative) return

        player.hurt(sl.damageSources().lightningBolt(), SHOCK_DAMAGE)

        player.knockback(
            SHOCK_KNOCKBACK,
            blockPos.x + 0.5 - player.x,
            blockPos.z + 0.5 - player.z,
        )
        player.hurtMarked = true

        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, blockPos.x + 0.5, blockPos.y + 0.8, blockPos.z + 0.5, 12, 0.25, 0.2, 0.25, 0.1)
        sl.playSound(null, blockPos, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.5f, randomPitch(0.6f, 0.3f))
        sl.playSound(null, blockPos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1f, 2f)
    }

    // Session API

    /**
     * Reserves the block entity for an upload session.
     * @return an [UploadSession], or null if the block is already busy
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

    /** @return false if the block is already busy */
    fun tryStartDelete(): Boolean {
        if (isBusy) return false
        deleteTicksLeft = BeamTiming.extendTicks(beamLength).toInt() + 1
        changePhase(Phase.DELETING)
        return true
    }


    private fun changePhase(next: Phase) {
        if (next.hasBeam) {
            effectNonce++
            beamStartTime = level?.gameTime ?: 0L
        }
        enterPhase(next)
        if (next == Phase.UPLOADING) maybeStartRain()
        sendData()
    }

    private fun enterPhase(next: Phase) {
        val prev = phase
        if (prev == next) return
        phase = next
        play(prev.onEnd)
        play(next.onStart)
    }

    private fun onPhaseTick() {
        val tickSound = phase.onTick ?: return
        val time = level?.gameTime ?: return
        if (time % TICK_SOUND_INTERVAL == 0L) play(tickSound)
    }

    private fun startCooldown() {
        val now = level?.gameTime ?: return changePhase(Phase.IDLE)
        val len = beamLength
        val retractStart = max(now.toFloat(), beamStartTime + max(BeamTiming.MIN_HOLD_TICKS, BeamTiming.extendTicks(len)))
        cooldownTicksLeft = ceil(retractStart - now).toInt()
        changePhase(Phase.COOLDOWN)
    }


    override fun tick() {
        super.tick()

        level?.onClient { level, _ ->
            beam.tick(level.gameTime, beamLength, keepAlive = isBeamPhase)
            if (hasCrystal) ModSoundScapes.play(ModSoundScapes.AmbienceGroup.RESONATING, worldPosition, 0.5f)
        }
        level?.onServer { tickServer() }

        onPhaseTick()
    }

    private fun tickServer() {
        when (phase) {
            Phase.UPLOADING -> {
                val s = session ?: return
                if (s.finished) {
                    session = null
                    uploadingFileName = null
                    uploadingProgress = 0f
                    startCooldown()
                } else if (s.progress != uploadingProgress) {
                    uploadingProgress = s.progress
                    sendData()
                }
            }
            Phase.DELETING -> {
                deleteTicksLeft--
                if (deleteTicksLeft <= 0) startCooldown()
            }
            Phase.COOLDOWN -> if (--cooldownTicksLeft <= 0) changePhase(Phase.IDLE)
            Phase.IDLE -> Unit
        }
    }

    override fun destroy() {
        level?.onServer { serverLevel ->
            if (hasCrystal) {
                itemHandler.dropContents(serverLevel, blockPos)
                onCrystalChange(CrystalChangeOutcome.REMOVED)
            }
            if (isBusy) {
                session?.cancel()
                session = null
                sound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.5f, 0.6f)
                spawnShatterParticles(serverLevel)
            }
        }
        super.destroy()
    }

    override fun invalidate() {
        contentService.invalidateBlockEntityStorage(this)
        super.invalidate()
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
            if (nonce != effectNonce && newPhase.hasBeam) {
                level?.let { beam.start(descending = newPhase == Phase.DELETING, now = it.gameTime) }
            }
            enterPhase(newPhase)
        } else {
            phase = newPhase
        }
        effectNonce = nonce
        nonceSynced = true
    }

    override fun clearContent() {
        itemHandler.clearContent()
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {}

    // Sounds
    private fun sound(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) {
        level?.playSound(null, blockPos, event, SoundSource.PLAYERS, volume, pitch)
    }

    private fun play(s: PhaseSound?) {
        if (s != null) sound(s.event, s.volume, s.pitch)
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

    private fun spawnShatterParticles(level: ServerLevel) {
        val elapsed = (level.gameTime - beamStartTime).toFloat()
        val progress = (elapsed / BeamTiming.extendTicks(beamLength)).coerceIn(0f, 1f)
        val cx = blockPos.x + 0.5
        val cz = blockPos.z + 0.5
        val yFrom = blockPos.y + 1.0
        val yTo = yFrom + progress * beamLength

        val step = max(1.5, (yTo - yFrom) / 60.0)
        var y = yFrom
        while (y <= yTo) {
            level.sendParticles(ParticleTypes.WITCH, cx, y, cz, 1, 0.2, 0.0, 0.2, 0.02)
            y += step
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, blockPos.y + 0.6, cz, 20, 0.2, 0.15, 0.2, 0.2)
    }

    private fun maybeStartRain() {
        val sl = level as? ServerLevel ?: return
        if (!sl.dimensionType().hasSkyLight() || sl.dimensionType().hasCeiling()) return
        if (sl.isRaining || sl.random.nextFloat() >= RAIN_CHANCE) return
        sl.setWeatherParameters(0, sl.random.nextInt(6000, 12000), true, false)
    }

}
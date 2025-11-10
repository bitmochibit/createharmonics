package me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox

import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.registry.ModPartialModels
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.AngleHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraftforge.items.ItemStackHandler

class RecordPlayerActorVisual(
    context: VisualizationContext,
    val world: VirtualRenderWorld,
    val movementContext: MovementContext
) : ActorVisual(
    context,
    world,
    movementContext
) {

    private var rotation: Double = 0.0
    private var previousRotation: Double = 0.0

    private val disc: TransformedInstance =
        instancerProvider.instancer(
            InstanceTypes.TRANSFORMED,
            Models.partial(ModPartialModels.ETHEREAL_RECORD)
        ).createInstance()

    private val facing = movementContext.state.getValue(DirectionalKineticBlock.FACING)

    val inventoryHandler = object : ItemStackHandler(1) {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return stack.item is EtherealRecordItem
        }
    }.apply {
        movementContext.blockEntityData.contains("inventory").let { hasInventory ->
            if (hasInventory) {
                val nbt = movementContext.blockEntityData.getCompound("inventory")
                this.deserializeNBT(nbt)
            }
        }
    }

    private fun hasRecord(): Boolean {
        val record = inventoryHandler.getStackInSlot(0)
        return !record.isEmpty && record.item is EtherealRecordItem
    }

    override fun tick() {
        if (!hasRecord()) {
            disc.setVisible(false)
        } else {
            disc.setVisible(true)
        }

        if (context.disabled) return

        previousRotation = rotation

        val speed = movementContext.animationSpeed

        rotation += speed.toDouble()/20
        rotation %= 360.0
    }

    override fun beginFrame() {
        disc
            .setIdentityTransform()
            .translate(context.localPos)
//            .translate(
//                facing.opposite.normal.x * .9f,
//                facing.opposite.normal.y * .9f,
//                facing.opposite.normal.z * .9f
//            )
            .center()
            .rotateToFace(facing.opposite)
            .rotateZDegrees(getRotation())
            .uncenter()
            .setChanged()
    }

    private fun getRotation(): Float {
        return AngleHelper.angleLerp(AnimationTickHolder.getPartialTicks().toDouble(), previousRotation, rotation)
    }

    override fun _delete() {
        disc.delete()
    }
}
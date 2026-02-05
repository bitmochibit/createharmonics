package me.mochibit.createharmonics.foundation.ponder

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity
import com.simibubi.create.content.kinetics.press.PressingBehaviour
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlock
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity
import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.registry.ModItems
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import thedarkcolour.kotlinforforge.forge.vectorutil.v3d.toVec3
import java.util.function.Consumer
import java.util.function.UnaryOperator

object PonderScenes {
    fun recordPressBase(
        builder: SceneBuilder,
        util: SceneBuildingUtil,
    ) {
        // Ponder scene size 9 x 6 x 9
        val scene = CreateSceneBuilder(builder)
        scene.title(
            "record_press_base",
            "Using Record Press Bases",
        )

        val plateSize = 7
        val pressBase = util.grid().at(4, 1, 4)
        val pressTop = util.vector().topOf(pressBase)

        val plateOffsetX = pressBase.x - plateSize / 2
        val plateOffsetZ = pressBase.z - plateSize / 2

        scene.configureBasePlate(plateOffsetX, plateOffsetZ, plateSize)
        scene.addKeyframe()
        scene.showBasePlate()
        scene.idle(5)
        scene.world().showSection(util.select().position(pressBase), Direction.DOWN)
        scene
            .overlay()
            .showText(60)
            .text("Record Press Bases are depot-like blocks used for setting urls on Ethereal Records")
            .placeNearTarget()
            .pointAt(pressTop)
        scene.idle(70)

        scene.addKeyframe()
        val fallingItem = ItemStack(ModItems.getEtherealRecordItem(RecordType.EMERALD).get())
        val fallingItemLink =
            scene.world().createItemEntity(util.vector().centerOf(4, 4, 4), util.vector().of(.0, -0.1, .0), fallingItem)
        scene.idle(9)
        scene.world().modifyEntity(fallingItemLink, Entity::discard)
        scene.world().createItemOnBeltLike(pressBase, Direction.UP, fallingItem)
        scene.idle(20)
        scene
            .overlay()
            .showText(40)
            .text("Ethereal Records can be dropped on top of the press base")
            .placeNearTarget()
            .pointAt(pressTop)
        scene.idle(50)
        val beltSelection1 = util.select().fromTo(2, 1, 4, 3, 1, 4)
        val beltSelection2 = util.select().fromTo(5, 1, 4, 8, 1, 4)
        scene.world().showSection(beltSelection1, Direction.DOWN)
        scene.idle(5)
        scene.world().showSection(beltSelection2, Direction.DOWN)
        scene
            .overlay()
            .showText(40)
            .text("Or be sledded in using conveyor belts")
            .placeNearTarget()
            .pointAt(pressTop)
        scene.idle(10)
//        scene.world().modifyBlockEntity(pressBase, RecordPressBaseBlockEntity::class.java) { be ->
//            be.behaviour.heldItem?.let { be.behaviour.ejectItem(it) }
//        }
        scene.idle(50)
        scene.addKeyframe()
        scene
            .overlay()
            .showControls(pressTop, Pointing.DOWN, 20)
            .rightClick()
        scene
            .overlay()
            .showText(50)
            .text("Right-Click to open the configuration interface")
            .placeNearTarget()
            .pointAt(pressTop)
        scene.idle(60)
        scene
            .overlay()
            .showText(50)
            .text("You can create a list of urls to be set, and choose the mode of selection")
            .placeNearTarget()
            .pointAt(pressTop)
        scene.idle(60)
        val pressPos = util.grid().at(4, 3, 4)
        scene.addKeyframe()
        scene.world().showSection(util.select().position(pressPos), Direction.DOWN)
        scene.idle(10)
        scene.world().modifyBlockEntity(pressPos, MechanicalPressBlockEntity::class.java) { be ->
            be.pressingBehaviour.start(PressingBehaviour.Mode.BELT)
        }
        scene.idle(10)
        scene.world().modifyBlockEntity(
            pressPos,
            MechanicalPressBlockEntity::class.java,
        ) { pte: MechanicalPressBlockEntity ->
            pte
                .getPressingBehaviour()
                .makePressingParticleEffect(
                    util.vector().centerOf(pressBase).add(0.0, (8 / 16f).toDouble(), 0.0),
                    ItemStack(ModItems.getEtherealRecordItem(RecordType.GOLD).get()),
                )
        }
        scene
            .overlay()
            .showText(70)
            .text("When a record is present, a mechanical press must be used to inprint the url onto it")
            .placeNearTarget()
            .pointAt(pressTop)
        scene.idle(80)

        scene.markAsFinished()
    }

    fun andesiteJukebox(
        builder: SceneBuilder,
        util: SceneBuildingUtil,
    ) {
        val scene = CreateSceneBuilder(builder)
        scene.title(
            "andesite_jukebox",
            "Using Andesite Jukeboxes",
        )

        val recordPlayer = util.grid().at(16, 2, 16)
        val recordLever = util.grid().at(16, 2, 15)

        val topOfPlayer = util.vector().topOf(recordPlayer)
        val plateSize = 8
        scene.configureBasePlate(recordPlayer.x - (plateSize / 2), recordPlayer.z - (plateSize / 2), plateSize)
        scene.addKeyframe()
        scene.showBasePlate()
        scene.idle(5)

        scene.world().showSection(util.select().fromTo(16, 1, 15, 16, 1, 21), Direction.DOWN)
        scene.world().showSection(util.select().position(16, 2, 16), Direction.DOWN)
        scene
            .overlay()
            .showText(60)
            .text("Andesite jukeboxes can play audio from Ethereal Records")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.idle(70)

        // Record insertion feature
        scene.addKeyframe()
        val brassRecord = ItemStack(ModItems.getEtherealRecordItem(RecordType.BRASS).get())
        scene
            .overlay()
            .showControls(topOfPlayer, Pointing.DOWN, 20)
            .rightClick()
            .withItem(brassRecord)
        scene.idle(7)

        scene.world().modifyBlockEntity(recordPlayer, AndesiteJukeboxBlockEntity::class.java) { be ->
            be.playerBehaviour.insertRecord(brassRecord)
        }

        scene.idle(10)
        scene
            .overlay()
            .showText(60)
            .text("Right-Click to manually insert or pop Ethereal Records from it")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.idle(50)

        scene
            .overlay()
            .showText(70)
            .text("Each Ethereal Record has some intrinsic audio effects")
            .placeNearTarget()
        scene.idle(70)

        scene
            .overlay()
            .showControls(topOfPlayer, Pointing.DOWN, 20)
            .whileSneaking()
            .rightClick()
        scene.idle(7)
        scene.world().modifyBlockEntity(recordPlayer, AndesiteJukeboxBlockEntity::class.java) { be ->
            be.playerBehaviour.popRecord()
        }
        scene.effects().indicateSuccess(recordPlayer)
        scene.idle(20)

        val diamondRecord = ItemStack(ModItems.getEtherealRecordItem(RecordType.DIAMOND).get())
        scene
            .overlay()
            .showControls(topOfPlayer, Pointing.DOWN, 20)
            .rightClick()
            .withItem(diamondRecord)
        scene.idle(7)

        scene.world().modifyBlockEntity(recordPlayer, AndesiteJukeboxBlockEntity::class.java) { be ->
            be.playerBehaviour.insertRecord(diamondRecord)
        }

        // Pitch change feature
        scene.addKeyframe()
        scene.world().showSection(util.select().fromTo(16, 2, 17, 16, 2, 20), Direction.DOWN)
        scene.idle(10)
        scene
            .overlay()
            .showText(90)
            .text("Based on the rotational speed supplied, the pitch of the audio will change")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.rotateCameraY(-90f)
        scene.idle(40)
        scene.world().multiplyKineticSpeed(util.select().everywhere(), 1 / 4f)
        scene.idle(70)

        // Jukebox modes
        scene.addKeyframe()
        scene.rotateCameraY(90f)
        scene.world().hideSection(util.select().fromTo(16, 2, 17, 16, 2, 20), Direction.UP)
        scene.idle(5)
        scene.world().multiplyKineticSpeed(util.select().everywhere(), 2f)
        scene
            .overlay()
            .showText(60)
            .text("Andesite jukeboxes have 2 modes, play and pause")
            .pointAt(topOfPlayer)
        scene.idle(70)

        scene
            .overlay()
            .showText(60)
            .colored(PonderPalette.GREEN)
            .text("When in play mode, the audio will play once and stop when finished")
            .pointAt(topOfPlayer)

        scene.idle(80)

        scene
            .overlay()
            .showText(60)
            .colored(PonderPalette.BLUE)
            .text("In pause mode the playback will be simply manually paused, and can be resumed resetting to play mode")
            .pointAt(topOfPlayer)
        scene.idle(80)

        // Redstone loop mode
        scene.addKeyframe()
        val redstoneBB =
            AABB(recordPlayer)
                .inflate((-1 / 32f).toDouble(), (-1 / 32f).toDouble(), (-1 / 32f).toDouble())

        scene.world().showSection(util.select().position(recordLever), Direction.DOWN)
        scene.idle(10)
        scene.world().modifyBlock(recordLever, { s -> s.cycle(LeverBlock.POWERED) }, false)
        scene.effects().indicateRedstone(recordLever)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, recordPlayer, redstoneBB, 80)
        scene
            .overlay()
            .showText(60)
            .colored(PonderPalette.RED)
            .text("When powered they behave differently depending on the mode")
            .pointAt(topOfPlayer)
        scene.idle(70)

        scene
            .overlay()
            .showText(70)
            .colored(PonderPalette.GREEN)
            .text("In play mode, they will loop the playback until unpowered")
            .pointAt(topOfPlayer)
        scene.idle(80)

        scene
            .overlay()
            .showText(70)
            .colored(PonderPalette.BLUE)
            .text("In pause mode, redstone controls the playback, so power to play, unpower to pause")
            .pointAt(topOfPlayer)
        scene.idle(80)
        scene.addKeyframe()
        scene.world().hideSection(util.select().position(recordLever), Direction.UP)
        scene.idle(20)
        scene.world().setBlock(
            recordLever,
            AllBlocks.ANALOG_LEVER.get().defaultBlockState().setValue(
                AnalogLeverBlock.FACING,
                Direction.WEST,
            ),
            false,
        )
        scene.world().showSection(util.select().position(recordLever), Direction.DOWN)
        scene
            .overlay()
            .showText(70)
            .text("When the redstone signal is analog, volume and play mode can be controlled")
            .pointAt(topOfPlayer)
        scene.idle(40)

        scene.idle(7)
        for (i in 0..6) {
            scene.idle(2)
            val state = i + 1
            scene.world().modifyBlockEntityNBT(
                util.select().position(recordLever),
                AnalogLeverBlockEntity::class.java,
            ) { nbt: CompoundTag -> nbt.putInt("State", state) }
        }
        scene.idle(40)

        scene
            .overlay()
            .showText(70)
            .colored(PonderPalette.GREEN)
            .text("In play mode, it will only loop when fully powered, scaling volume with power level")
            .pointAt(topOfPlayer)
        scene.idle(80)

        scene
            .overlay()
            .showText(70)
            .colored(PonderPalette.BLUE)
            .text("In pause mode, audio will always loop, with volume scaled by power level, pausing only when fully unpowered")
            .pointAt(topOfPlayer)
        scene.idle(80)

        // Mechanical Arm Feature
        val extractingArm = util.grid().at(18, 2, 15)
        val insertingArm = util.grid().at(18, 2, 17)
        val depot = util.grid().at(18, 2, 16)

        scene.addKeyframe()
        scene.world().showSection(util.select().fromTo(18, 1, 15, 18, 2, 17), Direction.DOWN)
        scene
            .overlay()
            .showText(50)
            .text("Mechanical arms works on Andesite jukeboxes and it has special behaviours based on the mode")
            .pointAt(topOfPlayer)
            .placeNearTarget()
        scene.idle(60)

        scene
            .overlay()
            .showText(70)
            .colored(PonderPalette.GREEN)
            .text("In play mode, when unpowered or not fully powered, arms will extract the record only if the playback ends")
            .placeNearTarget()
        scene.idle(24)
        scene.world().instructArm(extractingArm, ArmBlockEntity.Phase.MOVE_TO_INPUT, ItemStack.EMPTY, 0)
        scene.idle(30)
        scene.world().modifyBlockEntity(recordPlayer, AndesiteJukeboxBlockEntity::class.java) { be ->
            be.playerBehaviour.popRecord()
        }
        scene.world().instructArm(extractingArm, ArmBlockEntity.Phase.SEARCH_OUTPUTS, diamondRecord, -1)
        scene.idle(30)
        scene.world().instructArm(extractingArm, ArmBlockEntity.Phase.MOVE_TO_OUTPUT, diamondRecord, 0)
        scene.idle(24)
        scene.world().createItemOnBeltLike(depot, Direction.UP, diamondRecord)
        scene.world().instructArm(extractingArm, ArmBlockEntity.Phase.SEARCH_INPUTS, ItemStack.EMPTY, -1)
        scene
            .overlay()
            .showText(70)
            .colored(PonderPalette.GREEN)
            .text("In play mode if powered fully, arms can't extract the record")
            .placeNearTarget()

        scene.idle(80)

        scene.idle(15)
        scene
            .overlay()
            .showText(70)
            .colored(PonderPalette.BLUE)
            .text("In pause mode, arms will extract the record when fully unpowered")
            .placeNearTarget()

        scene.idle(24)
        scene.world().instructArm(insertingArm, ArmBlockEntity.Phase.MOVE_TO_INPUT, ItemStack.EMPTY, 0)
        scene.idle(24)
        scene.world().removeItemsFromBelt(depot)
        scene.world().instructArm(insertingArm, ArmBlockEntity.Phase.SEARCH_OUTPUTS, diamondRecord, -1)
        scene.idle(5)
        scene.world().instructArm(insertingArm, ArmBlockEntity.Phase.MOVE_TO_OUTPUT, diamondRecord, 0)
        scene.idle(24)
        scene.world().modifyBlockEntity(recordPlayer, AndesiteJukeboxBlockEntity::class.java) { be ->
            be.playerBehaviour.insertRecord(diamondRecord)
        }
        scene.world().instructArm(insertingArm, ArmBlockEntity.Phase.SEARCH_INPUTS, ItemStack.EMPTY, -1)
        scene.idle(5)
        scene.idle(80)
        scene.world().hideSection(util.select().position(recordLever), Direction.UP)

        // Contraption scene
        val bearingPos = util.grid().at(16, 3, 16)
        val jukeboxContraption = util.grid().at(15, 3, 14)
        scene.addKeyframe()
        scene.world().hideSection(util.select().fromTo(18, 1, 14, 18, 2, 17), Direction.UP)
        scene.world().hideSection(util.select().fromTo(16, 1, 15, 16, 1, 21), Direction.UP)
        scene.world().hideSection(util.select().position(16, 2, 16), Direction.UP)
        scene.idle(30)
        scene.world().showSection(util.select().position(bearingPos), Direction.WEST)

        val rotatingStructure =
            scene.world().showIndependentSection(
                util.select().fromTo(15, 3, 14, 15, 4, 16),
                Direction.WEST,
            )
        scene
            .world()
            .configureCenterOfRotation(rotatingStructure, util.vector().blockSurface(bearingPos, Direction.EAST))
        scene.world().setKineticSpeed(util.select().position(jukeboxContraption), 32f)
        scene.world().rotateBearing(bearingPos, 360f, 80)
        scene.world().rotateSection(rotatingStructure, 360.0, 0.0, 0.0, 80)
        scene
            .overlay()
            .showText(80)
            .text("Ethereal jukeboxes work on contraptions too")
            .placeNearTarget()
            .pointAt(topOfPlayer)

        scene.idle(90)
        scene
            .overlay()
            .showText(60)
            .text("The Andesite Jukebox pauses audio when the contraption stops, by default")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.world().setKineticSpeed(util.select().position(jukeboxContraption), 0f)
        scene.idle(70)

        scene
            .overlay()
            .showText(80)
            .text("Playback is resumed when the contraption starts moving again")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.world().setKineticSpeed(util.select().position(jukeboxContraption), 32f)
        scene.world().rotateBearing(bearingPos, 360f, 80)
        scene.world().rotateSection(rotatingStructure, 360.0, 0.0, 0.0, 80)
        scene.idle(90)

        scene
            .overlay()
            .showText(80)
            .text("Jukeboxes affects audio pitch relative to its position speed")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.world().setKineticSpeed(util.select().position(jukeboxContraption), 16f)
        scene.world().rotateBearing(bearingPos, 360f, 120)
        scene.world().rotateSection(rotatingStructure, 360.0, 0.0, 0.0, 120)
        scene.idle(130)

        scene.addKeyframe()
        val rotation: Float = 360 * 3f
        val duration = 120 * 3
        val contraptionLeverPosition = util.grid().at(15, 3, 15)
        val contraptionLeverSel = util.select().position(15, 3, 15)

        scene
            .overlay()
            .showText(80)
            .text("Modes set before assembly works on contraptions, allowing some special cases and locking the pitch")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.world().rotateBearing(bearingPos, rotation, duration)
        scene.world().rotateSection(rotatingStructure, rotation.toDouble(), 0.0, 0.0, duration)
        scene.idle(90)

        scene
            .overlay()
            .showText(80)
            .text("Redstone level is also preserved, allowing for volume regulation both on play and pause modes")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.world().setBlock(
            contraptionLeverPosition,
            Blocks.REDSTONE_BLOCK.defaultBlockState(),
            true,
        )

        scene.idle(7)
        for (i in 0..9) {
            scene.idle(2)
            val state = i + 1
            scene.world().modifyBlockEntityNBT(
                contraptionLeverSel,
                AnalogLeverBlockEntity::class.java,
            ) { nbt: CompoundTag -> nbt.putInt("State", state) }
        }

        scene.effects().indicateRedstone(contraptionLeverPosition)
        scene.idle(90)

        scene
            .overlay()
            .showText(80)
            .text("If the jukebox is on pause mode, and powered, it will play audio even if the contraption is stopped")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.idle(90)

        scene
            .overlay()
            .showText(80)
            .text("If in play mode, redstone will only control the volume, but playback will stop when the contraption stops")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.idle(90)

        scene.markAsFinished()
    }
}

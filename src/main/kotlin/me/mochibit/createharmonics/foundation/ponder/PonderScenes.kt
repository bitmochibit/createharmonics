package me.mochibit.createharmonics.foundation.ponder

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity
import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.registry.ModItems
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.createmod.ponder.foundation.element.WorldSectionElementImpl
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.AABB
import kotlin.jvm.optionals.getOrNull

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

        val plateSize = 5
        val pressBase = util.grid().at(4, 1, 4)
        val pressTop = util.vector().topOf(pressBase)

        scene.configureBasePlate(pressBase.x - plateSize / 2, pressBase.z - plateSize / 2, plateSize)
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

        // Redstone loop mode
        scene.addKeyframe()
        val redstoneBB =
            AABB(recordPlayer)
                .inflate((-1 / 32f).toDouble(), (-1 / 32f).toDouble(), (-1 / 32f).toDouble())

        scene.rotateCameraY(90f)
        scene.world().hideSection(util.select().fromTo(16, 2, 17, 16, 2, 20), Direction.UP)
        scene.idle(5)
        scene.world().multiplyKineticSpeed(util.select().everywhere(), 2f)
        scene.world().showSection(util.select().position(recordLever), Direction.DOWN)
        scene.idle(10)
        scene.world().modifyBlock(recordLever, { s -> s.cycle(LeverBlock.POWERED) }, false)
        scene.effects().indicateRedstone(recordLever)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, recordPlayer, redstoneBB, 80)
        scene
            .overlay()
            .showText(60)
            .colored(PonderPalette.RED)
            .text("Andesite jukeboxes can be put to loop-mode when redstone powered")
            .pointAt(topOfPlayer)
        scene.idle(70)
        scene.world().modifyBlock(recordLever, { s -> s.cycle(LeverBlock.POWERED) }, false)

        // Mechanical Arm Feature
        val extractingArm = util.grid().at(18, 2, 15)
        val insertingArm = util.grid().at(18, 2, 17)
        val depot = util.grid().at(18, 2, 16)

        scene.addKeyframe()
        scene.world().showSection(util.select().fromTo(18, 1, 15, 18, 2, 17), Direction.DOWN)
        scene
            .overlay()
            .showText(50)
            .text("Mechanical arms works on Andesite jukeboxes")
            .pointAt(topOfPlayer)
            .placeNearTarget()
        scene.idle(60)

        scene
            .overlay()
            .showText(50)
            .text("When not powered, after the playback ends, mechanical arms will extract the record")
            .pointAt(topOfPlayer)
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

        // Contraption scene
        val bearingPos = util.grid().at(16, 3, 16)
        val jukeboxContraption = util.grid().at(15, 3, 14)
        scene.addKeyframe()
        scene.world().hideSection(util.select().position(recordLever), Direction.UP)
        scene.world().hideSection(util.select().fromTo(18, 1, 14, 18, 2, 17), Direction.UP)
        scene.world().hideSection(util.select().fromTo(16, 1, 15, 16, 1, 21), Direction.UP)
        scene.world().hideSection(util.select().position(16, 2, 16), Direction.UP)
        scene.idle(30)
        scene.world().showSection(util.select().position(bearingPos), Direction.WEST)

        val rotatingStructure =
            scene.world().showIndependentSection(
                util.select().fromTo(15, 3, 14, 15, 3, 16),
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
            .text("The Andesite Jukebox pauses audio when the contraption stops")
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

        scene.markAsFinished()
    }
}

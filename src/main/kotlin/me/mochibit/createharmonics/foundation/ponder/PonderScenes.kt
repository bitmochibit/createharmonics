package me.mochibit.createharmonics.foundation.ponder

import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.registry.ModItems
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.AABB

object PonderScenes {
    fun andesiteJukebox(
        builder: SceneBuilder,
        util: SceneBuildingUtil,
    ) {
        // Ponder scene size 5 x 7 x 5
        val scene = CreateSceneBuilder(builder)
        scene.title(
            "andesite_jukebox",
            "Using Andesite Jukeboxes",
        )

        val recordPlayer = util.grid().at(16, 2, 16)
        val recordLever = util.grid().at(16, 2, 15)

        val topOfPlayer = util.vector().topOf(recordPlayer)
        val plateSize = 7
        scene.configureBasePlate(recordPlayer.x - (plateSize / 2), recordPlayer.z - (plateSize / 2), plateSize)
        scene.showBasePlate()
        scene.idle(5)

        scene.world().showSection(util.select().fromTo(16, 1, 15, 16, 1, 21), Direction.DOWN)
        scene.world().showSection(util.select().position(16, 2, 16), Direction.DOWN)
        scene
            .overlay()
            .showText(60)
            .attachKeyFrame()
            .text("Andesite jukeboxes can play audio from Ethereal Records")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.idle(70)

        // INSERTING THE RECORD

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
            .showText(70)
            .attachKeyFrame()
            .text("Right-Click to manually insert or pop Ethereal Records from it")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.idle(80)

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
        scene.world().showSection(util.select().fromTo(16, 2, 17, 16, 2, 20), Direction.DOWN)
        scene.idle(10)
        scene
            .overlay()
            .showText(90)
            .attachKeyFrame()
            .text("Based on the rotational speed supplied, the pitch of the audio will change")
            .placeNearTarget()
            .pointAt(topOfPlayer)
        scene.rotateCameraY(-90f)
        scene.idle(40)
        scene.world().multiplyKineticSpeed(util.select().everywhere(), 1 / 4f)
        scene.effects().rotationSpeedIndicator(recordPlayer.below())
        scene.idle(70)

        // Redstone loop mode
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
            .showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text("Andesite jukeboxes can be put to loop-mode when redstone powered")
            .pointAt(topOfPlayer)
        scene.idle(60)
        scene.world().modifyBlock(recordLever, { s -> s.cycle(LeverBlock.POWERED) }, false)
        scene.idle(10)

        scene.markAsFinished()
    }
}

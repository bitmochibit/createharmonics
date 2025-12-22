package me.mochibit.createharmonics.foundation.ponder

import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction

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
        scene.configureBasePlate(0, 0, 5)
        scene.setSceneOffsetY(3f)
        scene.showBasePlate()
        scene.idle(5)

        val recordPlayer = util.grid().at(3, 5, 3)
        scene.world().showSection(util.select().position(2, 1, 2), Direction.DOWN)
        val topOf = util.vector().topOf(recordPlayer)
        scene
            .overlay()
            .showText(60)
            .attachKeyFrame()
            .text("Andesite jukeboxes can play audio from Ethereal Records")
            .placeNearTarget()
            .pointAt(topOf)
        scene.idle(70)

        scene.markAsFinished()
    }
}

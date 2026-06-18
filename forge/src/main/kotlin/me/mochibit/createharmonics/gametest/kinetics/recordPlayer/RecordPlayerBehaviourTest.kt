package me.mochibit.createharmonics.gametest.kinetics.recordPlayer

import com.simibubi.create.AllBlocks
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.content.kinetics.recordPlayer.PlaybackState
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBehaviour
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.registry.ModBlocks
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.foundation.registry.ModItems.etherealRecord
import me.mochibit.createharmonics.handler.RecordCraftingHandler
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder

@GameTestHolder(MOD_ID)
class RecordPlayerBehaviourTest {

    @GameTest(template = "andesite_player")
    fun `(andesite record) record insertion test`(helper: GameTestHelper) {
        val pos = BlockPos(0,2,0)
        val blockEntity = helper.getBlockEntity(pos) as? RecordPlayerBlockEntity
            ?: return helper.fail("Record Player Behaviour not found! ${helper.getBlockState(pos)}")

        val behaviour = blockEntity.playerBehaviour

        val recordStack = ModItems etherealRecord RecordType.BRASS
        val result = behaviour.insertRecord(ItemStack(recordStack))

        helper.assertTrue(result, "Record insertion should succeed")
        helper.assertTrue(behaviour.hasRecord(), "Should have a record after insertion")
        helper.succeed()
    }
}

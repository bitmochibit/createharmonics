package me.mochibit.createharmonics.gametest.kinetics.recordPlayer

import com.simibubi.create.content.logistics.depot.DepotBlockEntity
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.foundation.registry.ModItems.etherealRecord
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraftforge.gametest.GameTestHolder

@GameTestHolder(MOD_ID)
class RecordPlayerBehaviourTest {

    @GameTest(template = "andesite")
    fun `(andesite player) record insertion test`(helper: GameTestHelper) {
        val pos = fromOrigin(0, 1, 0)
        val blockEntity = helper.getBlockEntity(pos) as? RecordPlayerBlockEntity
            ?: return helper.fail("Record Player Behaviour not found! ${helper.getBlockState(pos)}")

        val behaviour = blockEntity.playerBehaviour

        val recordStack = ModItems etherealRecord RecordType.BRASS
        val result = behaviour.insertRecord(ItemStack(recordStack))

        helper.assertTrue(result, "Record insertion should succeed")
        helper.assertTrue(behaviour.hasRecord(), "Should have a record after insertion")
        helper.succeed()
    }

    @GameTest(template = "andesite")
    fun `(andesite player) record removal test`(helper: GameTestHelper) {
        val pos = fromOrigin(0, 1, 0)
        val blockEntity = helper.getBlockEntity(pos) as? RecordPlayerBlockEntity
            ?: return helper.fail("Record Player Behaviour not found! ${helper.getBlockState(pos)}")

        val behaviour = blockEntity.playerBehaviour
        val recordStack = ModItems etherealRecord RecordType.BRASS
        behaviour.insertRecord(ItemStack(recordStack))

        val poppedStack = behaviour.popRecord()
        if (poppedStack == null || poppedStack.isEmpty) {
            return helper.fail("Invalid record was popped out!")
        }

        val poppedItem =
            poppedStack.item as? EtherealRecordItem ?: return helper.fail("A non record item was popped out!")

        if (poppedItem.recordType != RecordType.BRASS) {
            return helper.fail("Record type popped(${poppedStack}) is different than the one inserted(${recordStack})")
        }

        helper.assertTrue(!behaviour.hasRecord(), "Should not have a record after removal")
        helper.succeed()
    }

    @GameTest(template = "andesite_arm_insert", setupTicks = 100, timeoutTicks = 600)
    fun `(andesite player) mechanical arm insertion in player`(helper: GameTestHelper) {
        val depotPosition = fromOrigin(1, 1, 1)
        val depotBe = helper.getBlockEntity(depotPosition) as? DepotBlockEntity
            ?: return helper.fail("Depot behaviour not found! ${helper.getBlockState(depotPosition)}")

        val recordItem = ModItems etherealRecord RecordType.BRASS
        depotBe.heldItem = ItemStack(recordItem)

        val playerPos = fromOrigin(0, 1, 0)

        helper.runAfterDelay(500) {
            val playerBe = helper.getBlockEntity(playerPos) as? RecordPlayerBlockEntity
                ?: return@runAfterDelay helper.fail("Record Player not found!")
            val playerBehaviour = playerBe.playerBehaviour
            helper.assertTrue(playerBehaviour.hasRecord(), "Should have record, inserted from arms!!")
            helper.succeed()
        }
    }

    /**
     * While in pause mode the arm can extract the record whenever redstone power isn't there
     */
    @GameTest(template = "andesite_arm_extract", setupTicks = 100, timeoutTicks = 600)
    fun `(andesite player) mechanical arm extraction from player, pause mode`(helper: GameTestHelper) {
        val depotPosition = fromOrigin(1, 1, 1)
        val depotBe = helper.getBlockEntity(depotPosition) as? DepotBlockEntity
            ?: return helper.fail("Depot behaviour not found! ${helper.getBlockState(depotPosition)}")

        val recordItem = ModItems etherealRecord RecordType.BRASS
        val playerPos = fromOrigin(0, 1, 0)
        val playerBe = helper.getBlockEntity(playerPos) as? RecordPlayerBlockEntity
            ?: return helper.fail("Record Player not found!")
        val playerBehaviour = playerBe.playerBehaviour
        playerBehaviour.be.playbackMode.value = RecordPlayerBlockEntity.PlaybackMode.PAUSE.ordinal
        playerBehaviour.insertRecord(ItemStack(recordItem))

        helper.runAfterDelay(600) {
            val depotHeldItem = depotBe.heldItem
            if (depotHeldItem == ItemStack.EMPTY || depotHeldItem.isEmpty) {
                return@runAfterDelay helper.fail("Depot not received any items, arm failed to extract maybe?")
            }

            val depotItem = depotHeldItem.item
            if (depotItem !is EtherealRecordItem) {
                return@runAfterDelay helper.fail("Depot has a non ethereal record item!")
            }

            if (depotItem.recordType != RecordType.BRASS) {
                return@runAfterDelay helper.fail("Record type ${RecordType.BRASS} is different than the one inserted into depot ${depotItem.recordType}")
            }

            helper.assertTrue(!playerBehaviour.hasRecord(), "Should not have a record since removed from arms!")
            helper.succeed()
        }
    }

    /**
     * While in play mode the arm can extract the record only if the song finishes
     */
    @GameTest(template = "andesite_arm_extract", setupTicks = 100, timeoutTicks = 600)
    fun `(andesite player) mechanical arm extraction from player, play mode`(helper: GameTestHelper) {
        val depotPosition = fromOrigin(1, 1, 1)
        val depotBe = helper.getBlockEntity(depotPosition) as? DepotBlockEntity
            ?: return helper.fail("Depot behaviour not found! ${helper.getBlockState(depotPosition)}")

        val recordItem = ModItems etherealRecord RecordType.BRASS
        val playerPos = fromOrigin(0, 1, 0)
        val playerBe = helper.getBlockEntity(playerPos) as? RecordPlayerBlockEntity
            ?: return helper.fail("Record Player not found!")
        val playerBehaviour = playerBe.playerBehaviour
        playerBehaviour.insertRecord(ItemStack(recordItem))
        playerBehaviour.onPlaybackEnd(false)

        helper.runAfterDelay(600) {
            val depotHeldItem = depotBe.heldItem
            if (depotHeldItem == ItemStack.EMPTY || depotHeldItem.isEmpty) {
                return@runAfterDelay helper.fail("Depot not received any items, arm failed to extract maybe?")
            }

            val depotItem = depotHeldItem.item
            if (depotItem !is EtherealRecordItem) {
                return@runAfterDelay helper.fail("Depot has a non ethereal record item!")
            }

            if (depotItem.recordType != RecordType.BRASS) {
                return@runAfterDelay helper.fail("Record type ${RecordType.BRASS} is different than the one inserted into depot ${depotItem.recordType}")
            }

            helper.assertTrue(!playerBehaviour.hasRecord(), "Should not have a record since removed from arms!")
            helper.succeed()
        }
    }
}

internal fun origin(): BlockPos = BlockPos(0, 1, 0)
internal fun fromOrigin(x: Int, y: Int, z: Int): BlockPos = origin().offset(x, y, z)
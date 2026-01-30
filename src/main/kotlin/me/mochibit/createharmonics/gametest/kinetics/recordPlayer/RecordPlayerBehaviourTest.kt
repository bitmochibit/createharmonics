package me.mochibit.createharmonics.gametest.kinetics.recordPlayer

import com.simibubi.create.AllBlocks
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBehaviour
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.records.RecordCraftingHandler
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.registry.ModBlocks
import me.mochibit.createharmonics.registry.ModIcons
import me.mochibit.createharmonics.registry.ModItems
import me.mochibit.createharmonics.registry.ModItems.etherealRecord
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder(CreateHarmonicsMod.MOD_ID)
class RecordPlayerBehaviourTest {
    private fun GameTestHelper.setupRecordPlayer(): Pair<RecordPlayerBlockEntity, RecordPlayerBehaviour> {
        val pos = BlockPos(1, 1, 1)
        setBlock(pos, ModBlocks.ANDESITE_JUKEBOX.get())

        val blockEntity =
            getBlockEntity(pos) as? RecordPlayerBlockEntity
                ?: throw IllegalStateException("BlockEntity is not a RecordPlayerBlockEntity at $pos")

        return blockEntity to blockEntity.playerBehaviour
    }

    @GameTest(template = "record_player")
    fun testInsertRecordSuccess(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val recordStack = ModItems etherealRecord RecordType.BRASS

        val result = behaviour.insertRecord(ItemStack(recordStack))

        helper.assertTrue(result, "Record insertion should succeed")
        helper.assertTrue(behaviour.hasRecord(), "Should have a record after insertion")
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testInsertRecordFailure(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val firstRecord = ItemStack(ModItems etherealRecord RecordType.BRASS)
        val secondRecord = ItemStack(ModItems etherealRecord RecordType.DIAMOND)

        behaviour.insertRecord(firstRecord)
        val result = behaviour.insertRecord(secondRecord)

        helper.assertFalse(result, "Second insertion should fail")
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testHasRecord(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val recordStack = ModItems etherealRecord RecordType.BRASS

        behaviour.insertRecord(ItemStack(recordStack))

        helper.assertTrue(behaviour.hasRecord(), "Should detect the presence of a record")
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testHasNoRecord(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        helper.assertFalse(behaviour.hasRecord(), "Should not detect any record initially")
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testPopRecord(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val recordStack = ModItems etherealRecord RecordType.BRASS

        behaviour.insertRecord(ItemStack(recordStack))
        val popped = behaviour.popRecord()

        helper.assertTrue(popped != null && !popped.isEmpty, "Popped record should not be null or empty")
        helper.assertFalse(behaviour.hasRecord(), "Should not have a record after popping")
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testPopRecordEmpty(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val popped = behaviour.popRecord()

        helper.assertTrue(popped == null || popped.isEmpty, "Should return null/empty when there is no record")
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testInitialState(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        helper.assertTrue(
            behaviour.playbackState == RecordPlayerBehaviour.PlaybackState.STOPPED,
            "Initial state should be STOPPED. Found: ${behaviour.playbackState}",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testStartPlayer(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val recordStack = ItemStack(ModItems etherealRecord RecordType.BRASS)

        // Place behind a creative motor to provide power
        val motorPos = BlockPos(1, 1, 2)
        helper.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.get())
        RecordCraftingHandler.setCraftedWithDisc(recordStack, ItemStack(Items.MUSIC_DISC_FAR))

        behaviour.insertRecord(recordStack)

        helper.runAfterDelay(20) {
            helper.assertTrue(
                behaviour.playbackState == RecordPlayerBehaviour.PlaybackState.PLAYING,
                "Should be in PLAYING state after starting",
            )

            helper.succeed()
        }
    }

    @GameTest(template = "record_player")
    fun testStopPlayer(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val recordStack = ModItems etherealRecord RecordType.BRASS

        behaviour.insertRecord(ItemStack(recordStack))
        behaviour.startPlayer()
        behaviour.stopPlayer()

        helper.assertTrue(
            behaviour.playbackState == RecordPlayerBehaviour.PlaybackState.STOPPED,
            "Should be in STOPPED state",
        )
        helper.assertTrue(
            behaviour.playTime == 0L,
            "Playback time should reset to 0",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testPausePlayer(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val recordStack = ModItems etherealRecord RecordType.BRASS

        behaviour.insertRecord(ItemStack(recordStack))
        behaviour.startPlayer()
        behaviour.pausePlayer()

        helper.assertTrue(
            behaviour.playbackState == RecordPlayerBehaviour.PlaybackState.MANUALLY_PAUSED,
            "Should be in MANUALLY_PAUSED state",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testUniqueUuidGeneration(helper: GameTestHelper) {
        val pos1 = BlockPos(1, 1, 1)
        val pos2 = BlockPos(1, 1, 2)

        helper.setBlock(pos1, ModBlocks.ANDESITE_JUKEBOX.get())
        helper.setBlock(pos2, ModBlocks.ANDESITE_JUKEBOX.get())

        val behaviour1 = (helper.getBlockEntity(pos1) as RecordPlayerBlockEntity).playerBehaviour
        val behaviour2 = (helper.getBlockEntity(pos2) as RecordPlayerBlockEntity).playerBehaviour

        helper.assertTrue(
            behaviour1.recordPlayerUUID != behaviour2.recordPlayerUUID,
            "Each behaviour should have a unique UUID",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testUuidPersistence(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val originalUUID = behaviour.recordPlayerUUID
        val tag = CompoundTag()

        behaviour.write(tag, false)

        val pos = BlockPos(1, 1, 1)
        helper.setBlock(pos, Blocks.AIR)
        helper.setBlock(pos, ModBlocks.ANDESITE_JUKEBOX.get())

        val newBlockEntity = helper.getBlockEntity(pos) as RecordPlayerBlockEntity
        newBlockEntity.playerBehaviour.read(tag, false)

        helper.assertTrue(
            newBlockEntity.playerBehaviour.recordPlayerUUID == originalUUID,
            "UUID should persist through save/load",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testPlaybackStateSerialization(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val recordStack = ModItems etherealRecord RecordType.BRASS
        behaviour.insertRecord(ItemStack(recordStack))
        behaviour.startPlayer()

        val originalState = behaviour.playbackState
        val tag = CompoundTag()
        behaviour.write(tag, false)

        val pos = BlockPos(1, 1, 1)
        helper.setBlock(pos, Blocks.AIR)
        helper.setBlock(pos, ModBlocks.ANDESITE_JUKEBOX.get())

        val newBehaviour = (helper.getBlockEntity(pos) as RecordPlayerBlockEntity).playerBehaviour
        newBehaviour.read(tag, false)

        helper.assertTrue(
            newBehaviour.playbackState == originalState,
            "Playback state should persist",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testAudioPlayCountSerialization(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val recordStack = ModItems etherealRecord RecordType.BRASS
        behaviour.insertRecord(ItemStack(recordStack))
        behaviour.startPlayer()

        val originalCount = behaviour.audioPlayCount
        val tag = CompoundTag()
        behaviour.write(tag, false)

        val pos = BlockPos(1, 1, 1)
        helper.setBlock(pos, Blocks.AIR)
        helper.setBlock(pos, ModBlocks.ANDESITE_JUKEBOX.get())

        val newBehaviour = (helper.getBlockEntity(pos) as RecordPlayerBlockEntity).playerBehaviour
        newBehaviour.read(tag, false)

        helper.assertTrue(
            newBehaviour.audioPlayCount == originalCount,
            "Audio play count should persist",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testEmptyNbtRead(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val emptyTag = CompoundTag()
        try {
            behaviour.read(emptyTag, false)
            helper.succeed()
        } catch (e: Exception) {
            helper.fail("Should not throw exceptions with empty NBT: ${e.message}")
        }
    }

    @GameTest(template = "record_player")
    fun testPlaybackEndNoLoop(helper: GameTestHelper) {
        val pos = BlockPos(1, 1, 1)
        helper.setBlock(pos, ModBlocks.ANDESITE_JUKEBOX.get())
        val behaviour = (helper.getBlockEntity(pos) as RecordPlayerBlockEntity).playerBehaviour

        val absolutePos = helper.absolutePos(pos)

        helper.assertFalse(
            helper.level.hasNeighborSignal(absolutePos),
            "There should be no redstone signal initially",
        )

        val recordStack = ModItems etherealRecord RecordType.BRASS
        behaviour.insertRecord(ItemStack(recordStack))
        behaviour.startPlayer()

        behaviour.onPlaybackEnd(behaviour.recordPlayerUUID.toString())

        helper.assertTrue(
            behaviour.playbackState == RecordPlayerBehaviour.PlaybackState.STOPPED,
            "Should stop after playback ends without redstone",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testAudioTitleUpdate(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val title = "Test Track"
        behaviour.onAudioTitleUpdate(title)

        helper.assertTrue(
            behaviour.audioPlayingTitle == title,
            "Audio title should be updated. Expected: $title, Got: ${behaviour.audioPlayingTitle}",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testIgnoreUnknownTitle(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        behaviour.onAudioTitleUpdate("Unknown")

        helper.assertTrue(
            behaviour.audioPlayingTitle == null,
            "Should ignore the title 'Unknown'",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testAudioTitlePersistence(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val title = "Persistent Track"
        behaviour.onAudioTitleUpdate(title)

        val tag = CompoundTag()
        behaviour.write(tag, false)

        val pos = BlockPos(1, 1, 1)
        helper.setBlock(pos, Blocks.AIR)
        helper.setBlock(pos, ModBlocks.ANDESITE_JUKEBOX.get())

        val newBehaviour = (helper.getBlockEntity(pos) as RecordPlayerBlockEntity).playerBehaviour
        newBehaviour.read(tag, false)

        helper.assertTrue(
            newBehaviour.audioPlayingTitle == title,
            "Audio title should persist",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testDefaultSoundRadius(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        helper.assertTrue(
            behaviour.soundRadius == 32,
            "Default sound radius should be 32. Found: ${behaviour.soundRadius}",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testItemHandlerExists(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        helper.assertTrue(
            behaviour.itemHandler != null,
            "Item handler should exist",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testLazyItemHandler(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        helper.assertTrue(
            behaviour.lazyItemHandler != null && behaviour.lazyItemHandler.isPresent,
            "Lazy item handler should exist and be present",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testItemHandlerSlotCount(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        helper.assertTrue(
            behaviour.itemHandler.slots == 1,
            "Should have 1 slot for records. Found: ${behaviour.itemHandler.slots}",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testBehaviourType(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        val type = behaviour.type

        helper.assertTrue(type != null, "Behaviour type should not be null")
        helper.assertTrue(
            type === RecordPlayerBehaviour.Companion.BEHAVIOUR_TYPE,
            "Should return the correct behaviour type",
        )
        helper.succeed()
    }

    @GameTest(template = "record_player")
    fun testUnload(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        try {
            behaviour.unload()
            helper.succeed()
        } catch (e: Exception) {
            helper.fail("Unload should not throw exceptions: ${e.message}")
        }
    }

    @GameTest(template = "record_player")
    fun testDestroy(helper: GameTestHelper) {
        val (_, behaviour) = helper.setupRecordPlayer()
        try {
            behaviour.destroy()
            helper.succeed()
        } catch (e: Exception) {
            helper.fail("Destroy should not throw exceptions: ${e.message}")
        }
    }
}

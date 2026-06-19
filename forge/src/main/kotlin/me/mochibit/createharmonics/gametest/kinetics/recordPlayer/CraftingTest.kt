package me.mochibit.createharmonics.gametest.kinetics.recordPlayer

import com.simibubi.create.content.logistics.depot.DepotBlockEntity
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.foundation.registry.ModItems.etherealRecord
import me.mochibit.createharmonics.handler.RecordCraftingHandler
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraftforge.gametest.GameTestHolder

@GameTestHolder(CreateHarmonicsMod.MOD_ID)
class CraftingTest {
    @GameTest(template = "complete_setup", setupTicks = 100, timeoutTicks = 1000)
    fun `crafting chain and original disc check`(helper: GameTestHelper) {
        val discDepotPos = fromOrigin(4, 1, 4)

        // The `complete setup` template uses a diamond ethereal record, check which source has and randomize
        val referenceRecordItem = ModItems etherealRecord RecordType.DIAMOND
        val referenceBaseStack = referenceRecordItem.defaultInstance

        val defaultedDiscName = RecordCraftingHandler.getCraftedWithDisc(referenceBaseStack).displayName
        val uniqueDisc = EtherealRecordItem.JUKEBOX_DISCS.filter { it.defaultInstance.displayName != defaultedDiscName }.random().defaultInstance

        val discDepotBe = helper.getBlockEntity(discDepotPos) as? DepotBlockEntity ?: return helper.fail("Disc depot was not found")
        discDepotBe.heldItem = uniqueDisc
        // It should craft a disc and place it in the result depot that is diamond and has the same uniqueDisc source
        helper.runAfterDelay(800) {
            val resultDepotPos = fromOrigin(4, 1, 0)
            val resultDepotBe = helper.getBlockEntity(resultDepotPos) as? DepotBlockEntity ?: return@runAfterDelay helper.fail("Result depot was not found")
            val resultStack = resultDepotBe.heldItem
            val resultItem = resultStack.item

            if (resultItem !is EtherealRecordItem) {
                return@runAfterDelay helper.fail("Expected an Ethereal Record Item, got ${resultItem}")
            }

            val craftedWithDisc = RecordCraftingHandler.getCraftedWithDisc(resultStack)
            if (craftedWithDisc.isEmpty) {
                return@runAfterDelay helper.fail("Result expected to source with ${uniqueDisc.displayName}, but empty!")
            }

            if (craftedWithDisc.displayName != uniqueDisc.displayName) {
                return@runAfterDelay helper.fail("Result expected to source with ${uniqueDisc.displayName}, but instead got ${craftedWithDisc.displayName}")
            }

            helper.succeed()
        }
    }
}
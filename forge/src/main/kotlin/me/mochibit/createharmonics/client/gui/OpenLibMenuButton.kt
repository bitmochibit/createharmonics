package me.mochibit.createharmonics.client.gui

import me.mochibit.createharmonics.client.gui.OpenLibMenuButton.MenuRows.leftTextKeys
import me.mochibit.createharmonics.client.gui.OpenLibMenuButton.MenuRows.rightTextKeys
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.registry.ModConfigurations
import me.mochibit.createharmonics.registry.ModItems
import net.createmod.catnip.gui.ScreenOpener
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.CommonComponents
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import org.apache.commons.lang3.mutable.MutableObject
import java.util.function.Consumer

class OpenLibMenuButton(
    x: Int,
    y: Int,
) : Button(
        x,
        y,
        20,
        20,
        CommonComponents.EMPTY,
        OnPress { b: Button? -> click(b) },
        DEFAULT_NARRATION,
    ) {
    override fun renderString(
        graphics: GuiGraphics,
        pFont: Font,
        pColor: Int,
    ) {
        val icon = ModItems.getEtherealRecordItem(RecordType.BRASS).asStack()
        val bakedModel =
            Minecraft
                .getInstance()
                .itemRenderer
                .getModel(icon, Minecraft.getInstance().level, Minecraft.getInstance().player, 0)
        if (bakedModel == null) return

        graphics.renderItem(icon, x + 2, y + 2)
    }

    data object MenuRows {
        data class SingleMenuRow(
            val leftTextKey: String,
            val rightTextKey: String = "",
        )

        val MAIN_MENU =
            listOf(
                SingleMenuRow("menu.singleplayer"),
                SingleMenuRow("menu.multiplayer"),
                SingleMenuRow("fml.menu.mods", "menu.online"),
                SingleMenuRow("narrator.button.language", "narrator.button.accessibility"),
            )

        val INGAME_MENU =
            listOf(
                SingleMenuRow("menu.returnToGame"),
                SingleMenuRow("gui.advancements", "gui.stats"),
                SingleMenuRow("menu.sendFeedback", "menu.reportBugs"),
                SingleMenuRow("menu.options", "menu.shareToLan"),
                SingleMenuRow("menu.returnToMenu"),
            )

        fun List<SingleMenuRow>.leftTextKeys(): List<String> =
            this.map {
                it.leftTextKey
            }

        fun List<SingleMenuRow>.rightTextKeys(): List<String> =
            this.map {
                it.rightTextKey
            }
    }

    @EventBusSubscriber(value = [Dist.CLIENT])
    object OpenLibButtonHandler {
        @JvmStatic
        @SubscribeEvent
        fun onGuiInit(event: ScreenEvent.Init) {
            val screen = event.screen

            val menu: List<MenuRows.SingleMenuRow>
            val rowIdx: Int
            val offsetX: Int
            when (screen) {
                is TitleScreen -> {
                    menu = MenuRows.MAIN_MENU
                    rowIdx = ModConfigurations.client.mainMenuLibButtonRow.get()
                    offsetX = ModConfigurations.client.mainMenuLibButtonOffsetX.get()
                }

                is PauseScreen -> {
                    menu = MenuRows.INGAME_MENU
                    rowIdx = ModConfigurations.client.ingameMenuLibButtonRow.get()
                    offsetX = ModConfigurations.client.ingameMenuLibButtonOffsetX.get()
                }

                else -> {
                    return
                }
            }

            if (rowIdx == 0) {
                return
            }

            val onLeft = offsetX < 0
            val targetMessage = I18n.get((if (onLeft) menu.leftTextKeys() else menu.rightTextKeys())[rowIdx - 1])

            val toAdd = MutableObject<GuiEventListener>(null)
            event
                .listenersList
                .stream()
                .filter { w: GuiEventListener -> w is AbstractWidget }
                .map { w: GuiEventListener -> w as AbstractWidget }
                .filter { w: AbstractWidget ->
                    (
                        w.message.string == targetMessage
                    )
                }.findFirst()
                .ifPresent(
                    Consumer { w: AbstractWidget ->
                        toAdd.value =
                            OpenLibMenuButton(
                                w.x + offsetX + (if (onLeft) -20 else w.getWidth()),
                                w.y,
                            )
                    },
                )
            if (toAdd.getValue() != null) event.addListener(toAdd.getValue())
        }
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun click(b: Button?) {
            ScreenOpener.open(HarmonicsMenuScreen(Minecraft.getInstance().screen))
        }
    }
}

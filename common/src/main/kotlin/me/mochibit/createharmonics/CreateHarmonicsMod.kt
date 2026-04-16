package me.mochibit.createharmonics

import com.simibubi.create.foundation.data.CreateRegistrate
import com.simibubi.create.foundation.item.ItemDescription
import com.simibubi.create.foundation.item.KineticStats
import com.simibubi.create.foundation.item.TooltipModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.mochibit.createharmonics.audio.bin.BinStatusManager
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.foundation.async.ModCoroutineScope
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.eventbus.autoHandler
import me.mochibit.createharmonics.foundation.registry.CommonRegistry
import me.mochibit.createharmonics.foundation.registry.autoRegister
import me.mochibit.createharmonics.gui.CommonGuiEventHandler
import net.createmod.catnip.lang.FontHelper
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab

object CreateHarmonicsMod {
    const val MOD_ID = "createharmonics"
    private var initialized = false

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val _registrate: CreateRegistrate =
        CreateRegistrate
            .create(MOD_ID)
            .defaultCreativeTab(null as ResourceKey<CreativeModeTab>?)
            .setTooltipModifierFactory { item ->
                ItemDescription
                    .Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
            }

    val registrate: CreateRegistrate get() {
        if (!initialized) {
            throw IllegalStateException("Create registrate was not initialized!")
        }
        return _registrate
    }

    fun commonSetup(registrateConfiguration: CreateRegistrate.() -> Unit) {
        if (initialized) {
            return "Common was already initialized".err()
        }
        initialized = true
        _registrate.registrateConfiguration()

        autoRegister<CommonRegistry>()
        autoHandler<CommonGuiEventHandler>()

        modLaunch(Dispatchers.IO) {
            BinStatusManager.initialize()
        }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    runBlocking {
                        ProcessLifecycleManager.shutdownAll()
                    }
                    ModCoroutineScope.shutdown()
                } catch (e: Exception) {
                    "Error shutting down processes: ${e.message}".err()
                }
            },
        )
    }
}

val ModRegistrate: CreateRegistrate = CreateHarmonicsMod.registrate

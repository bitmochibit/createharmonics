package me.mochibit.createharmonics

import com.simibubi.create.foundation.data.CreateRegistrate
import me.mochibit.createharmonics.CreateHarmonicsMod.Companion.MOD_ID
import me.mochibit.createharmonics.init.ModInitializer
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

@Mod(MOD_ID)
class CreateHarmonicsMod(context: FMLJavaModLoadingContext) {
    companion object {
        const val MOD_ID: String = "createharmonics"

        @JvmStatic
        lateinit var instance: CreateHarmonicsMod
            private set
    }

    private val registrate: CreateRegistrate = CreateRegistrate.create(MOD_ID)

    init {
        instance = this

        val forgeEventBus = MinecraftForge.EVENT_BUS
        val modEventBus = context.modEventBus

        val initializer = ModInitializer(registrate, forgeEventBus, modEventBus, context)
        initializer.initialize()
    }

    fun getRegistrate(): CreateRegistrate = registrate
}

val CreateHarmonics: CreateHarmonicsMod
    get() = CreateHarmonicsMod.instance

internal fun String.asResource() = ResourceLocation.fromNamespaceAndPath(MOD_ID, this)
internal fun cRegistrate() = CreateHarmonics.getRegistrate()

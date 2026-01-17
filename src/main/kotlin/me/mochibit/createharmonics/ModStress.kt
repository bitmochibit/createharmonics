package me.mochibit.createharmonics

import com.tterrag.registrate.builders.BlockBuilder
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator
import it.unimi.dsi.fastutil.objects.Object2DoubleMap
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import net.createmod.catnip.config.ConfigBase
import net.createmod.catnip.platform.CatnipServices
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.common.Mod
import java.util.function.DoubleSupplier

/**
 * Configuration for stress impacts and capacities of mechanical blocks.
 *
 * This object manages stress-related values for all blocks added by CreateHarmonics,
 * allowing server administrators to fine-tune mechanical contraption behavior.
 */
@Mod.EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
object ModStress : ConfigBase() {
    // Bump this version to reset configured values on major config changes
    private const val VERSION = 1

    // Stress value constraints
    private const val MIN_STRESS_VALUE = 0.0
    private const val MAX_STRESS_VALUE = 1024.0

    // IDs need to be used since configs load before registration
    private val defaultImpacts: Object2DoubleMap<ResourceLocation> = Object2DoubleOpenHashMap()
    private val defaultCapacities: Object2DoubleMap<ResourceLocation> = Object2DoubleOpenHashMap()

    private val capacities = mutableMapOf<ResourceLocation, ForgeConfigSpec.ConfigValue<Double>>()
    private val impacts = mutableMapOf<ResourceLocation, ForgeConfigSpec.ConfigValue<Double>>()

    override fun registerAll(builder: ForgeConfigSpec.Builder) {
        group(1, "stress_values", "Configure stress impacts and capacities for mechanical blocks.")

        // Register stress impacts
        builder
            .comment(
                ".",
                Comments.SU,
                Comments.IMPACT,
                "Valid range: $MIN_STRESS_VALUE - $MAX_STRESS_VALUE",
            ).push("impact")

        defaultImpacts
            .object2DoubleEntrySet()
            .sortedBy { it.key.path }
            .forEach { (id, value) ->
                impacts[id] =
                    builder
                        .comment("Default: $value SU")
                        .defineInRange(id.path, value, MIN_STRESS_VALUE, MAX_STRESS_VALUE)
            }
        builder.pop()

        // Register stress capacities
        builder
            .comment(
                ".",
                Comments.SU,
                Comments.CAPACITY,
                "Valid range: $MIN_STRESS_VALUE - $MAX_STRESS_VALUE",
            ).push("capacity")

        defaultCapacities
            .object2DoubleEntrySet()
            .sortedBy { it.key.path }
            .forEach { (id, value) ->
                capacities[id] =
                    builder
                        .comment("Default: $value SU")
                        .defineInRange(id.path, value, MIN_STRESS_VALUE, MAX_STRESS_VALUE)
            }
        builder.pop()
    }

    override fun getName(): String = "stressValues.v$VERSION"

    /**
     * Gets the configured stress impact for a block.
     * @param block The block to query
     * @return A DoubleSupplier providing the stress impact value, or null if not configured
     */
    fun getImpact(block: Block): DoubleSupplier? {
        val id = CatnipServices.REGISTRIES.getKeyOrThrow(block)
        return impacts[id]?.let { value -> DoubleSupplier { value.get() } }
    }

    /**
     * Gets the configured stress capacity for a block.
     * @param block The block to query
     * @return A DoubleSupplier providing the stress capacity value, or null if not configured
     */
    fun getCapacity(block: Block): DoubleSupplier? {
        val id = CatnipServices.REGISTRIES.getKeyOrThrow(block)
        return capacities[id]?.let { value -> DoubleSupplier { value.get() } }
    }

    /**
     * Sets a block to have no stress impact (0 SU).
     */
    fun <B : Block, P> setNoImpact(): NonNullUnaryOperator<BlockBuilder<B, P>> = setImpact(0.0)

    /**
     * Configures the stress impact for a block during registration.
     * @param value The stress impact in Stress Units (must be between MIN_STRESS_VALUE and MAX_STRESS_VALUE)
     * @throws IllegalArgumentException if the value is out of range
     * @throws IllegalStateException if called on a non-Harmonics block
     */
    fun <B : Block, P> setImpact(value: Double): NonNullUnaryOperator<BlockBuilder<B, P>> {
        require(value in MIN_STRESS_VALUE..MAX_STRESS_VALUE) {
            "Stress impact must be between $MIN_STRESS_VALUE and $MAX_STRESS_VALUE, got $value"
        }
        return NonNullUnaryOperator { builder ->
            assertFromHarmonics(builder)
            val id = builder.name.asResource()
            defaultImpacts.put(id, value)
            builder
        }
    }

    /**
     * Configures the stress capacity for a block during registration.
     * @param value The stress capacity in Stress Units (must be between MIN_STRESS_VALUE and MAX_STRESS_VALUE)
     * @throws IllegalArgumentException if the value is out of range
     * @throws IllegalStateException if called on a non-Harmonics block
     */
    fun <B : Block, P> setCapacity(value: Double): NonNullUnaryOperator<BlockBuilder<B, P>> {
        require(value in MIN_STRESS_VALUE..MAX_STRESS_VALUE) {
            "Stress capacity must be between $MIN_STRESS_VALUE and $MAX_STRESS_VALUE, got $value"
        }
        return NonNullUnaryOperator { builder ->
            assertFromHarmonics(builder)
            val id = builder.name.asResource()
            defaultCapacities.put(id, value)
            builder
        }
    }

    private fun assertFromHarmonics(builder: BlockBuilder<*, *>) {
        if (builder.owner.modid != CreateHarmonicsMod.MOD_ID) {
            throw IllegalStateException("Non-Harmonics blocks cannot be added to Harmonics' config.")
        }
    }

    private object Comments {
        const val SU = "[in Stress Units]"
        const val IMPACT =
            "Configure the individual stress impact of mechanical blocks. Note that this cost is doubled for every speed increase it receives."
        const val CAPACITY = "Configure how much stress a source can accommodate for."
    }
}

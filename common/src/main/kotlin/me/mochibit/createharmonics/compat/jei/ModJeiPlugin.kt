package me.mochibit.createharmonics.compat.jei

import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.compat.jei.category.CreateRecipeCategory
import com.simibubi.create.compat.jei.category.animations.AnimatedDeployer
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams
import com.simibubi.create.content.processing.recipe.ProcessingOutput
import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.utility.CreateLang
import me.mochibit.createharmonics.content.records.RecordRepairRecipe
import me.mochibit.createharmonics.content.records.RepairKind
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.foundation.registry.ModRecipeTypes
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeHolder

private class DynamicRepairRecipe(
    params: ItemApplicationRecipeParams,
    val dynamicOutputs: ((inputDamage: Int, maxDamage: Int) -> List<ProcessingOutput>)? = null,
) : DeployerApplicationRecipe(params) {
    override fun getType() = ModRecipeTypes.RECORD_REPAIR.get()
}

private class DisplayRecipeParams(
    processedItem: Ingredient,
    heldItem: Ingredient,
    displayResults: List<ProcessingOutput>,
    keepHeld: Boolean = false,
    duration: Int = 40,
) : ItemApplicationRecipeParams() {
    init {
        ingredients.add(processedItem)
        ingredients.add(heldItem)
        displayResults.forEach { results.add(it) }
        processingDuration = duration
        keepHeldItem = keepHeld
    }
}

@JeiPlugin
class ModJeiPlugin : IModPlugin {
    private var recordRepairCategory: CreateRecipeCategory<DeployerApplicationRecipe>? = null

    override fun getPluginUid(): ResourceLocation = "jei_plugin".asResource()

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        buildRecordRepairCategory().also {
            recordRepairCategory = it
            registration.addRecipeCategories(it)
        }
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        recordRepairCategory?.registerRecipes(registration)
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        recordRepairCategory?.let { category ->
            registration.addRecipeCatalyst(
                ItemStack(AllBlocks.DEPLOYER.get()),
                category.recipeType,
            )
        }
    }

    private fun buildRecordRepairCategory(): CreateRecipeCategory<DeployerApplicationRecipe> =
        CreateRecipeCategory
            .Builder(DeployerApplicationRecipe::class.java)
            .emptyBackground(177, 70)
            .doubleItemIcon(AllBlocks.DEPLOYER.get(), AllItems.SUPER_GLUE.get())
            .addRecipes { RecordRepairJEIRecipes.buildDisplayHolders() }
            .build("record_repair".asResource()) { info -> RecordRepairCategory(info) }
}

object RecordRepairJEIRecipes {
    fun buildDisplayHolders(): List<RecipeHolder<DeployerApplicationRecipe>> {
        val level = Minecraft.getInstance().level ?: return emptyList()
        val access = level.registryAccess()
        val glueItem = AllItems.SUPER_GLUE.get()

        return buildList {
            level.recipeManager.getAllRecipesFor(ModRecipeTypes.RECORD_REPAIR.get()).forEach { holder ->
                val recipe = holder.value()

                recipe.recordTypes.filter { !it.isUnbreakable }.forEach { type ->
                    val brokenItem = ModItems.getBrokenEtherealRecordItem(type)?.get() ?: return@forEach
                    val normalItem = ModItems.getEtherealRecordItem(type).get()
                    val max = type.uses

                    fun stack(damage: Int = 0) =
                        normalItem.defaultInstance.also {
                            it.damageValue = damage
                        }
                    fun glueStack(damage: Int) =
                        ItemStack(glueItem).also { it.damageValue = RecordRepairRecipe.calculateGlueRepairCost(max, damage) }
                    fun id(suffix: String) = holder.id().withPath("jei/${holder.id().path}/${type.id.path}/$suffix")

                    val broken = Ingredient.of(brokenItem)
                    val damaged = Ingredient.of(normalItem)
                    val repaired = stack()

                    when (recipe.kind) {
                        RepairKind.FULL -> {
                            add(makeHolder(id("broken"), broken, recipe.material, listOf(ProcessingOutput(repaired, 1f))))
                            add(makeHolder(id("damaged"), damaged, recipe.material, listOf(ProcessingOutput(repaired, 1f))))
                        }

                        RepairKind.PARTIAL -> {
                            val staticPartial = stack(((1f - recipe.fraction) * max).toInt())
                            val dynamic: (Int, Int) -> List<ProcessingOutput> = { inputDamage, itemMax ->
                                listOf(ProcessingOutput(stack((inputDamage - recipe.fraction * itemMax).toInt().coerceAtLeast(0)), 1f))
                            }
                            add(makeHolder(id("broken"), broken, recipe.material, listOf(ProcessingOutput(staticPartial, 1f))))
                            add(makeHolder(id("damaged"), damaged, recipe.material,
                                listOf(ProcessingOutput(staticPartial, 1f)), dynamicOutputs = dynamic))
                        }

                        RepairKind.GLUE -> {
                            add(makeHolder(id("broken"), broken, recipe.material,
                                listOf(ProcessingOutput(repaired, 1f), ProcessingOutput(glueStack(max), 1f))))
                            add(makeHolder(id("damaged"), damaged, recipe.material,
                                listOf(ProcessingOutput(repaired, 1f), ProcessingOutput(glueStack(max), 1f)),
                                dynamicOutputs = { inputDamage, _ ->
                                    listOf(ProcessingOutput(repaired.copy(), 1f), ProcessingOutput(glueStack(inputDamage), 1f))
                                }))
                        }
                    }
                }
            }
        }
    }

    private fun makeHolder(
        id: ResourceLocation,
        processedItem: Ingredient,
        heldItem: Ingredient,
        results: List<ProcessingOutput>,
        dynamicOutputs: ((inputDamage: Int, maxDamage: Int) -> List<ProcessingOutput>)? = null,
    ): RecipeHolder<DeployerApplicationRecipe> =
        RecipeHolder(id, DynamicRepairRecipe(DisplayRecipeParams(processedItem, heldItem, results), dynamicOutputs))
}

class RecordRepairCategory(
    info: Info<DeployerApplicationRecipe>,
) : CreateRecipeCategory<DeployerApplicationRecipe>(info) {
    private val deployer = AnimatedDeployer()

    override fun setRecipe(
        builder: IRecipeLayoutBuilder,
        recipe: DeployerApplicationRecipe,
        focuses: IFocusGroup,
    ) {
        val focusedDamagedStack =
            focuses
                .getFocuses(VanillaTypes.ITEM_STACK)
                .filter { it.role == RecipeIngredientRole.INPUT }
                .map { it.typedValue.ingredient }
                .filter { stack -> stack.damageValue > 0 && recipe.processedItem.test(stack) }
                .findFirst()
                .orElse(null)

        val processedSlot =
            builder
                .addSlot(RecipeIngredientRole.INPUT, 27, 51)
                .setBackground(getRenderedSlot(), -1, -1)

        if (focusedDamagedStack != null) {
            processedSlot.addItemStack(focusedDamagedStack)
        } else {
            processedSlot.addIngredients(recipe.processedItem)
        }

        val handSlot =
            builder
                .addSlot(RecipeIngredientRole.INPUT, 51, 5)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.requiredHeldItem)

        val results: List<ProcessingOutput> =
            if (recipe is DynamicRepairRecipe &&
                recipe.dynamicOutputs != null &&
                focusedDamagedStack != null
            ) {
                recipe.dynamicOutputs.invoke(
                    focusedDamagedStack.damageValue,
                    focusedDamagedStack.maxDamage,
                )
            } else {
                recipe.getRollableResults()
            }

        val single = results.size == 1
        results.forEachIndexed { i, output ->
            val xOffset = if (i % 2 == 0) 0 else 19
            val yOffset = (i / 2) * -19
            builder
                .addSlot(RecipeIngredientRole.OUTPUT, if (single) 132 else 132 + xOffset, 51 + yOffset)
                .setBackground(getRenderedSlot(output), -1, -1)
                .addItemStack(output.stack)
                .addRichTooltipCallback(addStochasticTooltip(output))
        }

        if (recipe.shouldKeepHeldItem()) {
            handSlot.addRichTooltipCallback { view, tooltip ->
                tooltip.add(
                    CreateLang.translateDirect("recipe.deploying.not_consumed").withStyle(ChatFormatting.GOLD),
                )
            }
        }
    }

    override fun draw(
        recipe: DeployerApplicationRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double,
    ) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57)
        AllGuiTextures.JEI_DOWN_ARROW.render(
            graphics,
            126,
            29 + if (recipe.getRollableResults().size > 2) -19 else 0,
        )
        deployer.draw(graphics, background.width / 2 - 13, 22)
    }
}

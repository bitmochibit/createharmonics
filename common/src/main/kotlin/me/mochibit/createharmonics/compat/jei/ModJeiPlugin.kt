package me.mochibit.createharmonics.compat.jei

import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.compat.jei.category.CreateRecipeCategory
import com.simibubi.create.compat.jei.category.animations.AnimatedDeployer
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import com.simibubi.create.content.processing.recipe.ProcessingOutput
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder
import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.utility.CreateLang
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.foundation.registry.ModRecipeTypes
import me.mochibit.createharmonics.handler.RecordRepairHandler.calculateGlueRepairCost
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient

private class DynamicRepairRecipe(
    params: ProcessingRecipeBuilder.ProcessingRecipeParams,
    val dynamicOutputs: ((inputDamage: Int, maxDamage: Int) -> List<ProcessingOutput>)? = null,
) : DeployerApplicationRecipe(params) {
    override fun getType() = ModRecipeTypes.RECORD_REPAIR_GENERIC.get()
}

private class DisplayRecipeParams(
    processedItem: Ingredient,
    heldItem: Ingredient,
    displayResults: List<ProcessingOutput>,
    keepHeld: Boolean = false,
) : ProcessingRecipeBuilder.ProcessingRecipeParams("record_repair_recipe_params".asResource()) {
    init {
        ingredients.add(processedItem)
        ingredients.add(heldItem)
        displayResults.forEach { results.add(it) }
        keepHeldItem = keepHeld
    }
}

private class DoubleItemDrawable(
    private val back: ItemStack,
    private val front: ItemStack,
) : IDrawable {
    override fun getWidth() = 16

    override fun getHeight() = 16

    override fun draw(
        graphics: GuiGraphics,
        xOffset: Int,
        yOffset: Int,
    ) {
        val pose = graphics.pose()

        pose.pushPose()
        pose.translate(xOffset.toDouble(), yOffset.toDouble(), 0.0)
        pose.scale(0.65f, 0.65f, 1f)
        graphics.renderItem(back, 0, 0)
        pose.popPose()

        pose.pushPose()
        pose.translate((xOffset + 6).toDouble(), (yOffset + 5).toDouble(), 100.0)
        pose.scale(0.65f, 0.65f, 1f)
        graphics.renderItem(front, 0, 0)
        pose.popPose()
    }
}

@JeiPlugin
class ModJeiPlugin : IModPlugin {
    private var recordRepairCategory: RecordRepairCategory? = null

    override fun getPluginUid(): ResourceLocation = "jei_plugin".asResource()

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        val guiHelper = registration.jeiHelpers.guiHelper

        val info =
            CreateRecipeCategory.Info(
                RECIPE_TYPE,
                Component.translatable("createharmonics.recipe.record_repair"),
                guiHelper.createBlankDrawable(177, 70),
                DoubleItemDrawable(
                    ItemStack(AllBlocks.DEPLOYER.get()),
                    ItemStack(AllItems.SUPER_GLUE.get()),
                ),
                { RecordRepairJEIRecipes.buildRecipes() },
                emptyList(),
            )

        recordRepairCategory = RecordRepairCategory(info)
        registration.addRecipeCategories(recordRepairCategory!!)
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

    companion object {
        val RECIPE_TYPE: RecipeType<DeployerApplicationRecipe> =
            RecipeType.create("createharmonics", "record_repair", DeployerApplicationRecipe::class.java)
    }
}

object RecordRepairJEIRecipes {
    fun buildRecipes(): List<DeployerApplicationRecipe> =
        buildList {
            RecordType.entries.filter { it != RecordType.CREATIVE }.forEach { recordType ->
                val brokenRecordItem = ModItems.getBrokenEtherealRecordItem(recordType)?.get() ?: return@forEach
                val repairedRecordItem = ModItems.getEtherealRecordItem(recordType).get()

                val brokenIngredient = Ingredient.of(brokenRecordItem)
                val damagedIngredient = Ingredient.of(repairedRecordItem)
                val repairedStack = repairedRecordItem.defaultInstance

                val costedGlue =
                    ItemStack(AllItems.SUPER_GLUE.get()).also {
                        it.damageValue = calculateGlueRepairCost(recordType, recordType.uses)
                    }

                add(
                    makeRecipe(
                        processedItem = brokenIngredient,
                        heldItem = Ingredient.of(AllItems.SUPER_GLUE.get()),
                        results =
                            listOf(
                                ProcessingOutput(repairedStack, 1f),
                                ProcessingOutput(costedGlue, 1f),
                            ),
                    ),
                )

                add(
                    makeRecipe(
                        processedItem = damagedIngredient,
                        heldItem = Ingredient.of(AllItems.SUPER_GLUE.get()),
                        results =
                            listOf(
                                ProcessingOutput(repairedStack, 1f),
                                ProcessingOutput(costedGlue, 1f),
                            ),
                        dynamicOutputs = { inputDamage, _ ->
                            val dynamicGlue =
                                ItemStack(AllItems.SUPER_GLUE.get()).also {
                                    it.damageValue = calculateGlueRepairCost(recordType, inputDamage)
                                }
                            listOf(
                                ProcessingOutput(repairedStack.copy(), 1f),
                                ProcessingOutput(dynamicGlue, 1f),
                            )
                        },
                    ),
                )

                recordType.properties.repair?.fullRepairIngredientProvider?.invoke()?.let { ingredient ->
                    add(
                        makeRecipe(
                            processedItem = brokenIngredient,
                            heldItem = ingredient,
                            results = listOf(ProcessingOutput(repairedStack, 1f)),
                        ),
                    )
                    add(
                        makeRecipe(
                            processedItem = damagedIngredient,
                            heldItem = ingredient,
                            results = listOf(ProcessingOutput(repairedStack, 1f)),
                        ),
                    )
                }

                recordType.properties.repair
                    ?.partialRepairIngredientProvider
                    ?.invoke()
                    ?.let { (ingredient, repairFraction) ->
                        val maxDamage = repairedRecordItem.defaultInstance.maxDamage
                        val staticPartialStack =
                            repairedRecordItem.defaultInstance.also {
                                it.damageValue = ((1f - repairFraction) * maxDamage).toInt()
                            }

                        val dynamicOutputs: (Int, Int) -> List<ProcessingOutput> = { inputDamage, itemMaxDamage ->
                            val resultDamage =
                                (inputDamage - repairFraction * itemMaxDamage)
                                    .toInt()
                                    .coerceAtLeast(0)
                            listOf(
                                ProcessingOutput(
                                    repairedRecordItem.defaultInstance.also { it.damageValue = resultDamage },
                                    1f,
                                ),
                            )
                        }

                        add(
                            makeRecipe(
                                processedItem = brokenIngredient,
                                heldItem = ingredient,
                                results = listOf(ProcessingOutput(staticPartialStack, 1f)),
                            ),
                        )
                        add(
                            makeRecipe(
                                processedItem = damagedIngredient,
                                heldItem = ingredient,
                                results = listOf(ProcessingOutput(staticPartialStack, 1f)),
                                dynamicOutputs = dynamicOutputs,
                            ),
                        )
                    }
            }
        }

    private fun makeRecipe(
        processedItem: Ingredient,
        heldItem: Ingredient,
        results: List<ProcessingOutput>,
        dynamicOutputs: ((inputDamage: Int, maxDamage: Int) -> List<ProcessingOutput>)? = null,
        keepHeldItem: Boolean = false,
    ): DynamicRepairRecipe {
        val params = DisplayRecipeParams(processedItem, heldItem, results, keepHeldItem)
        return DynamicRepairRecipe(params, dynamicOutputs)
    }
}

class RecordRepairCategory(
    info: CreateRecipeCategory.Info<DeployerApplicationRecipe>,
) : CreateRecipeCategory<DeployerApplicationRecipe>(info) {
    private val deployer = AnimatedDeployer()

    override fun getRegistryName(recipe: DeployerApplicationRecipe): ResourceLocation? = null

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
            handSlot.addRichTooltipCallback { _, tooltip ->
                tooltip.add(CreateLang.translateDirect("recipe.deploying.not_consumed").withStyle(ChatFormatting.GOLD))
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

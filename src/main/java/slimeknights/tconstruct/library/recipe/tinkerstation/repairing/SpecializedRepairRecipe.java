package slimeknights.tconstruct.library.recipe.tinkerstation.repairing;

import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.materials.definition.LazyMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.recipe.TinkerStationRepairRecipe;

/**
 * Recipe to repair a specialized tool in the tinker station
 */
public class SpecializedRepairRecipe extends TinkerStationRepairRecipe implements ISpecializedRepairRecipe {
  public static final RecordLoadable<SpecializedRepairRecipe> LOADER =RecordLoadable.create(ContextKey.ID.requiredField(), TOOL_FIELD, REPAIR_MATERIAL_FIELD, SpecializedRepairRecipe::new);

  /** Tool that can be repaired with this recipe */
  @Getter
  private final Ingredient tool;
  /** ID of material used in repairing */
  private final LazyMaterial repairMaterial;
  public SpecializedRepairRecipe(ResourceLocation id, Ingredient tool, MaterialId repairMaterialID) {
    super(id);
    this.tool = tool;
    this.repairMaterial = LazyMaterial.of(repairMaterialID);
  }

  @Override
  public MaterialId getRepairMaterial() {
    return repairMaterial.getId();
  }

  @Override
  public boolean matches(ITinkerStationContainer inv, Level world) {
    ItemStack tinkerable = inv.getTinkerableStack();
    if (!tool.test(tinkerable) || repairMaterial.isUnknown()) {
      return false;
    }
    return findMaterialItem(inv, repairMaterial);
  }

  /** Find the repair item in the inventory */
  public static boolean findMaterialItem(ITinkerStationContainer inv, LazyMaterial repairMaterial) {
    // validate that we have at least one material
    boolean found = false;
    for (int i = 0; i < inv.getInputCount(); i++) {
      // skip empty slots
      ItemStack stack = inv.getInput(i);
      if (stack.isEmpty()) {
        continue;
      }

      // ensure we have a material
      if (!repairMaterial.matches(TinkerStationRepairRecipe.getMaterialFrom(inv, i))) {
        return false;
      }
      found = true;
    }
    return found;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerTables.specializedRepairSerializer.get();
  }
}

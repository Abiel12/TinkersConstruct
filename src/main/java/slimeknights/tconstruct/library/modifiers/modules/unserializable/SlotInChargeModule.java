package slimeknights.tconstruct.library.modifiers.modules.unserializable;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraftforge.common.util.LazyOptional;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.armor.EquipmentChangeModifierHook;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.context.EquipmentChangeContext;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.tools.logic.InteractionHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/** Module for keeping track of a single slot to run all logic for the modifier */
public record SlotInChargeModule(TinkerDataKey<SlotInCharge> key) implements HookProvider, EquipmentChangeModifierHook {
  private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.<SlotInChargeModule>defaultHooks(ModifierHooks.EQUIPMENT_CHANGE);
  private static final Function<TinkerDataKey<?>,SlotInCharge> CONSTRUCTOR = key -> new SlotInCharge();

  /** Checks if the given tool cares about this modifier */
  private static boolean toolValid(IToolStackView tool, EquipmentSlot slot, EquipmentChangeContext context) {
    if (!tool.isBroken() && !context.getEntity().level.isClientSide) {
      return ModifierUtil.validArmorSlot(tool, slot);
    }
    return false;
  }

  @Override
  public void onUnequip(IToolStackView tool, ModifierEntry modifier, EquipmentChangeContext context) {
    // remove slot in charge if that is us
    EquipmentSlot slot = context.getChangedSlot();
    if (toolValid(tool, slot, context)) {
      context.getTinkerData().ifPresent(data -> {
        SlotInCharge slotInCharge = data.get(key);
        if (slotInCharge != null) {
          slotInCharge.removeSlot(slot);
        }
      });
    }
  }

  @Override
  public void onEquip(IToolStackView tool, ModifierEntry modifier, EquipmentChangeContext context) {
    EquipmentSlot slot = context.getChangedSlot();
    if (toolValid(tool, slot, context)) {
      context.getTinkerData().ifPresent(data -> data.computeIfAbsent(key, CONSTRUCTOR).addSlot(slot));
    }
  }

  @Override
  public List<ModuleHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  /** Checks if the given slot is in charge */
  public static boolean isInCharge(LazyOptional<TinkerDataCapability.Holder> data, TinkerDataKey<SlotInCharge> key, EquipmentSlot slot) {
    return data.filter(d -> {
      SlotInCharge inCharge = d.get(key);
      return inCharge != null && inCharge.inCharge == slot;
    }).isPresent();
  }

  /** Tracker to determine which slot should be in charge */
  public static class SlotInCharge {
    private final boolean[] active = new boolean[6];
    @Nullable
    private EquipmentSlot inCharge = null;

    private SlotInCharge() {}

    /** Adds the given slot to the tracker */
    private void addSlot(EquipmentSlot slotType) {
      active[slotType.getFilterFlag()] = true;
      // prefer armor in charge as hand only runs when blocking, prefer mainhand over offhand
      if (inCharge == null || (inCharge.getType() == Type.HAND && slotType != EquipmentSlot.OFFHAND)) {
        inCharge = slotType;
      }
    }

    /** Removes the given slot from the tracker */
    private void removeSlot(EquipmentSlot slotType) {
      active[slotType.getFilterFlag()] = false;
      // prioritize armor slots
      for (EquipmentSlot armorSlot : ModifiableArmorMaterial.ARMOR_SLOTS) {
        if (active[slotType.getFilterFlag()]) {
          inCharge = armorSlot;
          return;
        }
      }
      // if none, find a hand slot
      for (EquipmentSlot hand : InteractionHandler.HAND_SLOTS) {
        if (active[slotType.getFilterFlag()]) {
          inCharge = hand;
          return;
        }
      }
      inCharge = null;
    }
  }
}

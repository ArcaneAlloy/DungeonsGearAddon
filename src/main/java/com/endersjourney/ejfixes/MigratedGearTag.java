package com.endersjourney.ejfixes;

import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantments;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantmentsHelper;
import com.infamous.dungeons_libraries.items.gearconfig.ArmorGear;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

import static com.endersjourney.ejfixes.DungeonsGearGearTypes.isDungeonsGearGear;

/**
 * A {@code BlacksmithUpgradeRecipe} result that isn't one of Dungeons
 * Gear's own gear classes (e.g. a migrated {@code netherite_helmet} or
 * {@code netherite_sword}) can't carry the built-in enchantments
 * capability, so tracking has to live in the migrated item's own NBT
 * instead. This records which enchantments were carried over as innate —
 * for any gear type — and, additionally for armor specifically, which
 * armor set it came from (needed only for the full-set bonus, which is an
 * armor-only concept: a single weapon has nothing to "complete a set"
 * with).
 * <p>
 * {@code FullSetBonusMixin} and {@code BuiltInEnchantmentTooltipFix} use
 * this so a migrated piece — armor or weapon — is still recognized as
 * carrying an inherited enchantment, not one a player manually applied via
 * a table/anvil.
 */
public final class MigratedGearTag {

    private static final String TAG_KEY = "EjFixesMigratedFrom";
    private static final String ARMOR_SET_KEY = "ArmorSet";
    private static final String INNATE_ENCHANTMENTS_KEY = "InnateEnchantments";

    private MigratedGearTag() {
    }

    /**
     * Called by {@code BlacksmithUpgradeEnchantMigrationMixin} right after
     * it writes the migrated enchantments onto the result's real NBT.
     * Records which enchantment ids were just carried over as innate, and —
     * only if the base was armor — the origin armor set (propagating it
     * through a chain of migrations if the base was itself already a
     * migrated item).
     */
    public static void writeFrom(ItemStack base, ItemStack result) {
        List<EnchantmentInstance> innateInstances = getInnateEnchantmentsOf(base);
        if (innateInstances.isEmpty()) {
            return;
        }

        ListTag innateList = new ListTag();
        for (EnchantmentInstance instance : innateInstances) {
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(instance.enchantment);
            if (id != null) {
                innateList.add(StringTag.valueOf(id.toString()));
            }
        }

        CompoundTag marker = new CompoundTag();
        ResourceLocation armorSet = getEffectiveArmorSet(base);
        if (armorSet != null) {
            marker.putString(ARMOR_SET_KEY, armorSet.toString());
        }
        marker.put(INNATE_ENCHANTMENTS_KEY, innateList);
        result.getOrCreateTag().put(TAG_KEY, marker);
    }

    /**
     * The armor set this piece is considered part of for full-set-bonus
     * purposes: its own {@code ArmorGear#getArmorSet()} if it's still
     * Dungeons Gear armor, or the origin set recorded by {@link #writeFrom}
     * if it was migrated. Null for weapons (no such concept) and for
     * anything else that doesn't resolve to one.
     */
    public static ResourceLocation getEffectiveArmorSet(ItemStack stack) {
        if (stack.getItem() instanceof ArmorGear armorGear) {
            return armorGear.getArmorSet();
        }
        CompoundTag marker = getMarker(stack);
        if (marker == null || !marker.contains(ARMOR_SET_KEY)) {
            return null;
        }
        return ResourceLocation.tryParse(marker.getString(ARMOR_SET_KEY));
    }

    /**
     * Whether the given enchantment is an inherited/innate part of this
     * piece — armor or weapon. Checks its own built-in-enchantments
     * capability first (if it's still one of Dungeons Gear's own gear
     * classes), then the recorded migration/inheritance marker — a piece
     * can have both at once now (e.g. Bow of Lost Souls' own Multishot
     * plus an inherited Bonus Shot from a Twin Bow upgrade), so this
     * checks both rather than picking one based on item type alone. False
     * for an enchantment that's only present because it was manually
     * applied via a table/anvil.
     */
    public static boolean isInnate(ItemStack stack, Enchantment enchantment) {
        if (isDungeonsGearGear(stack.getItem())) {
            BuiltInEnchantments capability = BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(stack);
            if (capability.getBuiltInItemEnchantmentLevel(enchantment) > 0) {
                return true;
            }
        }
        CompoundTag marker = getMarker(stack);
        if (marker == null) {
            return false;
        }
        ResourceLocation enchantId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (enchantId == null) {
            return false;
        }
        ListTag innateList = marker.getList(INNATE_ENCHANTMENTS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < innateList.size(); i++) {
            if (enchantId.toString().equals(innateList.getString(i))) {
                return true;
            }
        }
        return false;
    }

    private static CompoundTag getMarker(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        return tag.getCompound(TAG_KEY);
    }

    /** The piece's own innate enchantments — armor or weapon — from the
     * capability if it's still one of Dungeons Gear's own gear classes, or
     * re-derived from its own recorded marker if it was itself already a
     * migrated piece (a chain of upgrades). */
    private static List<EnchantmentInstance> getInnateEnchantmentsOf(ItemStack base) {
        if (isDungeonsGearGear(base.getItem())) {
            return BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(base).getAllBuiltInEnchantmentInstances();
        }
        CompoundTag marker = getMarker(base);
        List<EnchantmentInstance> result = new ArrayList<>();
        if (marker == null) {
            return result;
        }
        ListTag innateList = marker.getList(INNATE_ENCHANTMENTS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < innateList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(innateList.getString(i));
            if (id == null) {
                continue;
            }
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (enchantment == null) {
                continue;
            }
            int level = EnchantmentHelper.getEnchantments(base).getOrDefault(enchantment, 0);
            if (level > 0) {
                result.add(new EnchantmentInstance(enchantment, level));
            }
        }
        return result;
    }
}

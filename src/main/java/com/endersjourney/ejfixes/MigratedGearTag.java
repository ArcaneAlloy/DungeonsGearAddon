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

/**
 * A {@code BlacksmithUpgradeRecipe} result that isn't {@link ArmorGear}
 * (e.g. a migrated {@code netherite_helmet}) can't carry the built-in
 * enchantments capability, so full-set tracking can't rely on
 * {@code instanceof ArmorGear} + {@code getArmorSet()} alone once a set has
 * a migrated piece in it. This records, in the migrated item's own NBT,
 * which armor set it came from and which enchantments were carried over as
 * innate — so {@code FullSetBonusMixin} and {@code BuiltInEnchantmentTooltipFix}
 * can still recognize "these 4 pieces share the same origin set" and "this
 * enchantment on this piece is inherited, not manually enchanted", even
 * after migration.
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
     * Records the base piece's origin armor set (propagating it through a
     * chain of migrations if the base was itself already a migrated item)
     * and the set of enchantment ids that were just carried over.
     */
    public static void writeFrom(ItemStack base, ItemStack result) {
        ResourceLocation armorSet = getEffectiveArmorSet(base);
        if (armorSet == null) {
            return;
        }

        ListTag innateList = new ListTag();
        for (EnchantmentInstance instance : getInnateEnchantments(base)) {
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(instance.enchantment);
            if (id != null) {
                innateList.add(StringTag.valueOf(id.toString()));
            }
        }

        CompoundTag marker = new CompoundTag();
        marker.putString(ARMOR_SET_KEY, armorSet.toString());
        marker.put(INNATE_ENCHANTMENTS_KEY, innateList);
        result.getOrCreateTag().put(TAG_KEY, marker);
    }

    /**
     * The armor set this piece is considered part of for full-set-bonus
     * purposes: its own {@code ArmorGear#getArmorSet()} if it's still
     * Dungeons Gear armor, or the origin set recorded by {@link #writeFrom}
     * if it was migrated. Null if neither applies.
     */
    public static ResourceLocation getEffectiveArmorSet(ItemStack stack) {
        if (stack.getItem() instanceof ArmorGear armorGear) {
            return armorGear.getArmorSet();
        }
        CompoundTag marker = getMarker(stack);
        if (marker == null) {
            return null;
        }
        return ResourceLocation.tryParse(marker.getString(ARMOR_SET_KEY));
    }

    /**
     * Whether the given enchantment is an inherited/innate part of this
     * piece — from its own built-in-enchantments capability if it's still
     * ArmorGear, or from the recorded migration marker otherwise. False for
     * an enchantment that's only present because it was manually applied
     * via a table/anvil.
     */
    public static boolean isInnate(ItemStack stack, Enchantment enchantment) {
        if (stack.getItem() instanceof ArmorGear) {
            BuiltInEnchantments capability = BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(stack);
            return capability.getBuiltInItemEnchantmentLevel(enchantment) > 0;
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

    private static List<EnchantmentInstance> getInnateEnchantments(ItemStack base) {
        if (base.getItem() instanceof ArmorGear) {
            return BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(base).getAllBuiltInEnchantmentInstances();
        }
        // Base was itself already a migrated (non-ArmorGear) piece: there's
        // no capability to read, so re-derive instances from its own
        // recorded marker instead.
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

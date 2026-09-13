package com.endersjourney.ejfixes.mixin;

import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantments;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantmentsHelper;
import com.infamous.dungeons_libraries.items.gearconfig.ArmorGear;
import fr.shoqapik.brokenitems.BrokenItemsEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every Dungeons Gear "aura" enchantment (Melee Aura, Life Steal Aura,
 * Potion Aura, ...) reads its own level each tick via the vanilla
 * {@code EnchantmentHelper.getEnchantmentLevel(Enchantment, LivingEntity)}.
 * Hooking that single method — instead of each enchantment class — means
 * both fixes below apply to every current and future built-in enchantment
 * automatically.
 * <p>
 * Rather than nudge vanilla's own computed result, this recomputes the
 * level from scratch for {@code dungeons_gear} enchantments, using only
 * public/stable APIs:
 * <ul>
 *     <li>{@link BuiltInEnchantmentsHelper} / {@link BuiltInEnchantments} —
 *     the capability holding each piece's own built-in enchantment level
 *     (this is what Dungeons Libraries' own Mixin adds into vanilla's
 *     result unconditionally, which is what causes both bugs below).</li>
 *     <li>{@link BrokenItemsEvents#isItemBroken(ItemStack)} — Don't Break
 *     My Items' own public check for "this item is at 0 durability and
 *     effectively disabled".</li>
 * </ul>
 * <p>
 * Fix 1 — a broken piece's own built-in enchantment no longer counts: Don't
 * Break My Items zeroes a broken item's real (NBT) vanilla enchantments,
 * but Dungeons Gear's built-in enchantments live in a separate capability
 * that bypasses that check entirely, so a broken piece kept contributing
 * its enchantment as if intact. Here, a broken piece's own level is treated
 * as 0.
 * <p>
 * Fix 2 — a broken piece no longer counts toward the full-set bonus (see
 * the completeMatchingSet/anyBroken tracking below): if any of the 4 pieces
 * of an otherwise-complete matching set is broken, the set is no longer
 * "complete" for bonus purposes, even though the remaining intact pieces
 * still correctly provide their own base-level aura.
 */
@Mixin(EnchantmentHelper.class)
public abstract class FullSetBonusMixin {

    @Inject(
            method = "getEnchantmentLevel(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void ejFixes$recomputeBuiltInLevel(Enchantment enchantment, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        ResourceLocation enchantId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (enchantId == null || !enchantId.getNamespace().equals("dungeons_gear")) {
            return;
        }

        int baseLevel = 0;
        ResourceLocation armorSet = null;
        boolean completeMatchingSet = true;
        boolean anyBroken = false;

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);

            if (!(stack.getItem() instanceof ArmorGear armorGear)) {
                completeMatchingSet = false;
                continue;
            }

            ResourceLocation pieceSet = armorGear.getArmorSet();
            if (pieceSet == null) {
                completeMatchingSet = false;
            } else if (armorSet == null) {
                armorSet = pieceSet;
            } else if (!armorSet.equals(pieceSet)) {
                completeMatchingSet = false;
            }

            boolean broken = BrokenItemsEvents.isItemBroken(stack);
            if (broken) {
                anyBroken = true;
                continue; // a broken piece's own built-in enchantment doesn't count (Fix 1)
            }

            BuiltInEnchantments capability = BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(stack);
            baseLevel = Math.max(baseLevel, capability.getBuiltInItemEnchantmentLevel(enchantment));
        }

        if (baseLevel <= 0) {
            cir.setReturnValue(0);
            return;
        }

        int result = baseLevel;
        if (completeMatchingSet && !anyBroken) { // Fix 2
            result = baseLevel + 1;
        }

        cir.setReturnValue(Math.min(result, enchantment.getMaxLevel()));
    }
}

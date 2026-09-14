package com.endersjourney.ejfixes.mixin;

import com.endersjourney.ejfixes.MigratedGearTag;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantments;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantmentsHelper;
import com.infamous.dungeons_libraries.items.gearconfig.ArmorGear;
import com.infamous.dungeons_libraries.items.gearconfig.BowGear;
import com.infamous.dungeons_libraries.items.gearconfig.CrossbowGear;
import com.infamous.dungeons_libraries.items.gearconfig.MeleeGear;
import fr.shoqapik.brokenitems.BrokenItemsEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
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
 * every fix below applies to every current and future built-in enchantment
 * automatically.
 * <p>
 * For each of the 4 armor slots, a piece's own contribution to the level is
 * the sum of two independent sources:
 * <ul>
 *     <li><b>Real vanilla enchantment NBT</b> — read via
 *     {@code EnchantmentHelper.getEnchantments(stack)}, exactly like any
 *     ordinary enchantment. This is what {@code BlacksmithUpgradeEnchantMigrationMixin}
 *     writes onto non-Dungeons-Gear upgrade results (a migrated Reckless on
 *     a plain {@code netherite_helmet}, say), and it's also what Don't
 *     Break My Items already zeroes out on its own for a broken item — so
 *     broken handling for this source needs no extra code here.</li>
 *     <li><b>The built-in-enchantments capability</b> — only present on
 *     {@link ArmorGear}/{@link MeleeGear}/{@link BowGear}/{@link CrossbowGear}
 *     items, via {@link BuiltInEnchantmentsHelper}. Don't Break My Items
 *     does NOT know about this capability, so a broken piece's own
 *     contribution from it is explicitly zeroed below (Fix 1).</li>
 * </ul>
 * The two sources are summed per piece, then the highest value across the 4
 * slots becomes the base level — mirroring vanilla's own per-slot MAX
 * aggregation.
 * <p>
 * Fix 2 — full-set bonus: set membership is tracked via
 * {@link MigratedGearTag#getEffectiveArmorSet(ItemStack)} rather than a
 * plain {@code instanceof ArmorGear} check, so a migrated piece (e.g. that
 * {@code netherite_helmet}) is still recognized as belonging to its origin
 * set. If all 4 equipped pieces resolve to the same armor set, none of them
 * is broken, AND at least one of them carries this specific enchantment as
 * innate (via {@link MigratedGearTag#isInnate}, which likewise checks the
 * capability for real Dungeons Gear armor or the migration marker for a
 * migrated piece), the base level gets +1, capped at the enchantment's own
 * max level.
 * <p>
 * That last condition matters: wearing 4 matching pieces isn't by itself
 * enough — if the enchantment on them only got there via manual table/anvil
 * enchanting rather than being genuinely innate/inherited, no bonus is
 * granted for it. A piece with no resolvable set (a plain, never-migrated
 * item) or a broken piece correctly breaks set completeness.
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
        boolean innateOnAnyPiece = false;

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            boolean broken = BrokenItemsEvents.isItemBroken(stack);

            // Real vanilla NBT enchantment: a migrated built-in enchant on a
            // non-Dungeons-Gear item, or a manually applied one. Already
            // correctly zeroed by Don't Break My Items when broken.
            int ownLevel = EnchantmentHelper.getEnchantments(stack).getOrDefault(enchantment, 0);

            // The built-in-enchantments capability, only meaningful on
            // Dungeons Gear's own gear items.
            if (isDungeonsGearGear(stack.getItem()) && !broken) {
                BuiltInEnchantments capability = BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(stack);
                ownLevel += capability.getBuiltInItemEnchantmentLevel(enchantment); // Fix 1
            }

            baseLevel = Math.max(baseLevel, ownLevel);

            if (!broken && MigratedGearTag.isInnate(stack, enchantment)) {
                innateOnAnyPiece = true;
            }

            ResourceLocation pieceSet = broken ? null : MigratedGearTag.getEffectiveArmorSet(stack);
            if (pieceSet == null) {
                completeMatchingSet = false;
            } else if (armorSet == null) {
                armorSet = pieceSet;
            } else if (!armorSet.equals(pieceSet)) {
                completeMatchingSet = false;
            }
        }

        if (baseLevel <= 0) {
            cir.setReturnValue(0);
            return;
        }

        int result = (completeMatchingSet && innateOnAnyPiece) ? baseLevel + 1 : baseLevel; // Fix 2
        cir.setReturnValue(Math.min(result, enchantment.getMaxLevel()));
    }

    private static boolean isDungeonsGearGear(Item item) {
        return item instanceof ArmorGear
                || item instanceof MeleeGear
                || item instanceof BowGear
                || item instanceof CrossbowGear;
    }
}

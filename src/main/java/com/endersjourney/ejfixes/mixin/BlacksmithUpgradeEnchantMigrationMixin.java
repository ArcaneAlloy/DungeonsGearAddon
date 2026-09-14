package com.endersjourney.ejfixes.mixin;

import com.endersjourney.ejfixes.MigratedGearTag;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantments;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantmentsHelper;
import com.infamous.dungeons_libraries.items.gearconfig.ArmorGear;
import com.infamous.dungeons_libraries.items.gearconfig.BowGear;
import com.infamous.dungeons_libraries.items.gearconfig.CrossbowGear;
import com.infamous.dungeons_libraries.items.gearconfig.MeleeGear;
import fr.shoqapik.btemobs.menu.container.BteAbstractCraftContainer;
import fr.shoqapik.btemobs.recipe.BlacksmithUpgradeRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

import static com.endersjourney.ejfixes.DungeonsGearGearTypes.isDungeonsGearGear;

/**
 * Dungeons Gear's built-in enchantments (Reckless, Melee Aura, Bonus Shot,
 * etc.) live in a capability, not in the item's real vanilla enchantment
 * NBT — so they only work because Dungeons Libraries' own Mixins add that
 * capability's level on top wherever vanilla checks for an enchantment.
 * That mechanism is tied to the {@link ArmorGear}/{@link MeleeGear}/
 * {@link BowGear}/{@link CrossbowGear} item classes specifically, and each
 * of those items' innate enchantment(s) come from ITS OWN gearconfig only —
 * there's no inheritance across an upgrade.
 * <p>
 * BeyondTheEndMobs' blacksmith upgrade recipes ({@link BlacksmithUpgradeRecipe})
 * already copy the base item's entire NBT tag onto the result
 * ({@code assemble()} does this itself, to preserve durability/repairs/etc.),
 * but that copied capability data has nothing to attach to unless the
 * result is also one of those four classes — and even then, a fresh
 * capability for the result is derived purely from the result's OWN
 * gearconfig, ignoring whatever was just copied. Either way, the base's
 * innate enchantment(s) would otherwise be silently lost on upgrade.
 * <p>
 * This Mixin runs after {@code assemble()} builds the result and computes,
 * for each innate enchantment the base has:
 * <ul>
 *     <li>if the result doesn't provide it natively at all — inherit it
 *     as-is (e.g. Bow of Lost Souls gaining Twin Bow's Bonus Shot I
 *     alongside its own native Multishot I);</li>
 *     <li>if the result DOES already provide the very same enchantment
 *     natively — boost it by 1 over the base's level, capped at the
 *     enchantment's own max level (e.g. Haunted Bow's own Bonus Shot I
 *     becoming II when upgraded from Twin Bow's Bonus Shot I) — the same
 *     "upgrading should level up a shared trait" pattern used for the
 *     armor upgrade chains, just computed generically here instead of
 *     hand-edited per gearconfig.</li>
 * </ul>
 * Either way the result is written into the result's real vanilla
 * enchantment NBT — which every enchantment's effect (Dungeons Gear's own
 * included, since they just call the generic
 * {@code EnchantmentHelper.getEnchantmentLevel}) already reads regardless
 * of item type. Nothing is written if the result already natively matches
 * or exceeds what this computes.
 * <p>
 * It also records which enchantments were inherited (see
 * {@link MigratedGearTag}) on the result, so {@code FullSetBonusMixin} and
 * {@code BuiltInEnchantmentTooltipFix} recognize them as genuinely
 * inherited — not manually enchanted — even on a result that's still
 * Dungeons Gear's own gear.
 */
@Mixin(BlacksmithUpgradeRecipe.class)
public abstract class BlacksmithUpgradeEnchantMigrationMixin {

    @Inject(
            method = "assemble(Lfr/shoqapik/btemobs/menu/container/BteAbstractCraftContainer;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"),
            remap = false
    )
    private void ejFixes$migrateBuiltInEnchantments(BteAbstractCraftContainer container, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();

        BlacksmithUpgradeRecipe self = (BlacksmithUpgradeRecipe) (Object) this;

        ItemStack base = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (self.base.test(stack)) {
                base = stack;
            }
        }
        if (base.isEmpty() || !isDungeonsGearGear(base.getItem())) {
            return;
        }

        BuiltInEnchantments baseCapability = BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(base);
        if (!baseCapability.hasBuiltInEnchantment()) {
            return;
        }

        // What the result already provides on its own — don't duplicate or
        // override any of these.
        Map<Enchantment, Integer> resultOwnLevels = new HashMap<>();
        if (isDungeonsGearGear(result.getItem())) {
            BuiltInEnchantments resultCapability = BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(result);
            for (EnchantmentInstance instance : resultCapability.getAllBuiltInEnchantmentInstances()) {
                resultOwnLevels.put(instance.enchantment, instance.level);
            }
        }

        Map<Enchantment, Integer> toWrite = new HashMap<>();
        for (EnchantmentInstance instance : baseCapability.getAllBuiltInEnchantmentInstances()) {
            Integer resultLevel = resultOwnLevels.get(instance.enchantment);
            if (resultLevel == null) {
                // Not provided by the result at all: inherit as-is.
                toWrite.put(instance.enchantment, instance.level);
            } else {
                // Shared with the result's own native enchantment: boost
                // it by 1 over the base's level, never below what the
                // result already natively provides.
                int boosted = Math.min(instance.level + 1, instance.enchantment.getMaxLevel());
                if (boosted > resultLevel) {
                    toWrite.put(instance.enchantment, boosted);
                }
            }
        }
        if (toWrite.isEmpty()) {
            return; // result already covers everything the base had, at an equal or higher level
        }

        Map<Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(result));
        for (Map.Entry<Enchantment, Integer> entry : toWrite.entrySet()) {
            enchantments.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        EnchantmentHelper.setEnchantments(enchantments, result);

        MigratedGearTag.writeFrom(base, result);
    }
}

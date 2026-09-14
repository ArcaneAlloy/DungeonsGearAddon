package com.endersjourney.ejfixes.mixin;

import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantments;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantmentsHelper;
import com.infamous.dungeons_libraries.items.gearconfig.ArmorGear;
import com.infamous.dungeons_libraries.items.gearconfig.BowGear;
import com.infamous.dungeons_libraries.items.gearconfig.CrossbowGear;
import com.infamous.dungeons_libraries.items.gearconfig.MeleeGear;
import fr.shoqapik.btemobs.menu.container.BteAbstractCraftContainer;
import fr.shoqapik.btemobs.recipe.BlacksmithUpgradeRecipe;
import net.minecraft.world.item.Item;
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

/**
 * Dungeons Gear's built-in enchantments (Reckless, Melee Aura, etc.) live in
 * a capability, not in the item's real vanilla enchantment NBT — so they
 * only work because Dungeons Libraries' own Mixins add that capability's
 * level on top wherever vanilla checks for an enchantment. That mechanism
 * is tied to the {@link ArmorGear}/{@link MeleeGear}/{@link BowGear}/
 * {@link CrossbowGear} item classes specifically.
 * <p>
 * BeyondTheEndMobs' blacksmith upgrade recipes ({@link BlacksmithUpgradeRecipe})
 * already copy the base item's entire NBT tag onto the result
 * ({@code assemble()} does this itself, to preserve durability/repairs/etc.),
 * but when the result is a plain item that was never one of those four
 * classes (a vanilla {@code iron_helmet}, a Twilight Forest
 * {@code ironwood_helmet}, ...) that copied capability data has nothing to
 * attach to on the new item, so the enchantment is silently lost.
 * <p>
 * This Mixin runs after {@code assemble()} builds the result and, only when
 * the result is NOT itself one of those four Dungeons Gear gear classes,
 * writes the base piece's built-in enchantments into the result's real
 * vanilla enchantment NBT instead — which every enchantment's effect
 * (Dungeons Gear's own included, since they just call the generic
 * {@code EnchantmentHelper.getEnchantmentLevel}) already reads regardless
 * of item type. Recipes where the result IS still one of those four classes
 * (e.g. Spelunker -> Cave Crawler) are left untouched: those already get
 * their own correct built-in enchantments from their own gearconfig, and
 * adding a second, real-NBT copy on top would just double up the tooltip.
 * <p>
 * It also records the base's origin armor set (see {@link com.endersjourney.ejfixes.MigratedGearTag})
 * on the result, so a migrated piece can still be recognized as part of a
 * complete matching set by {@code FullSetBonusMixin} and
 * {@code BuiltInEnchantmentTooltipFix} even though it's no longer
 * {@link ArmorGear}.
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
        if (isDungeonsGearGear(result.getItem())) {
            return;
        }

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

        BuiltInEnchantments capability = BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(base);
        if (!capability.hasBuiltInEnchantment()) {
            return;
        }

        Map<Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(result));
        for (EnchantmentInstance instance : capability.getAllBuiltInEnchantmentInstances()) {
            enchantments.merge(instance.enchantment, instance.level, Math::max);
        }
        EnchantmentHelper.setEnchantments(enchantments, result);

        com.endersjourney.ejfixes.MigratedGearTag.writeFrom(base, result);
    }

    private static boolean isDungeonsGearGear(Item item) {
        return item instanceof ArmorGear
                || item instanceof MeleeGear
                || item instanceof BowGear
                || item instanceof CrossbowGear;
    }
}

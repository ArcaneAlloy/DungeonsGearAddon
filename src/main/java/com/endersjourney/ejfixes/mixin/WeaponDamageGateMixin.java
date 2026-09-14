package com.endersjourney.ejfixes.mixin;

import com.endersjourney.ejfixes.EyeGateContext;
import com.endersjourney.ejfixes.EyeGateResolver;
import mc.duzo.ender_journey.capabilities.PortalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Fix 8 — Eye Gate for weapon damage.
 * <p>
 * {@code EnchantmentHelper.getDamageBonus(ItemStack, MobType)} is the
 * vanilla method that actually adds Sharpness/Smite/Bane of Arthropods
 * damage in combat. It never goes through
 * {@code EnchantmentHelper.getEnchantmentLevel(Enchantment, LivingEntity)}
 * — that's what {@link FullSetBonusMixin}'s own Eye Gate step hooks — so
 * it needs its own gate here.
 * <p>
 * Rather than guessing at {@code getDamageBonus}'s internal implementation,
 * this independently recomputes the same total using the item's real
 * enchantment map and the stable public {@code Enchantment#getDamageBonus}
 * per-enchantment method, substituting each enchantment's eye-gated level
 * in place of its real one. Only overrides vanilla's result if that
 * substitution actually changes the total.
 */
@Mixin(EnchantmentHelper.class)
public abstract class WeaponDamageGateMixin {

    @Inject(
            method = "getDamageBonus(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/MobType;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void ejFixes$gateWeaponDamage(ItemStack stack, MobType creatureType, CallbackInfoReturnable<Float> cir) {
        LivingEntity attacker = EyeGateContext.getAttacker();
        if (!(attacker instanceof Player player) || player.getAbilities().instabuild) {
            return;
        }

        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        if (enchantments.isEmpty()) {
            return;
        }

        int eyesEarned = PortalPlayer.get(player).map(PortalPlayer::getEyesEarn).orElse(24);

        float gatedTotal = 0.0F;
        boolean anyGated = false;

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            int gatedLevel = EyeGateResolver.isExempt(enchantment)
                    ? level
                    : EyeGateResolver.effectiveLevel(enchantment, level, eyesEarned);
            if (gatedLevel != level) {
                anyGated = true;
            }
            if (gatedLevel > 0) {
                gatedTotal += enchantment.getDamageBonus(gatedLevel, creatureType);
            }
        }

        if (anyGated) {
            cir.setReturnValue(gatedTotal);
        }
    }
}

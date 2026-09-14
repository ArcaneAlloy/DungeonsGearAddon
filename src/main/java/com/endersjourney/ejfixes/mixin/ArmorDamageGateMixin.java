package com.endersjourney.ejfixes.mixin;

import com.endersjourney.ejfixes.EyeGateContext;
import com.endersjourney.ejfixes.EyeGateResolver;
import mc.duzo.ender_journey.capabilities.PortalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
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
 * Fix 9 — Eye Gate for armor damage reduction. Mirrors
 * {@link WeaponDamageGateMixin}'s approach for
 * {@code EnchantmentHelper.getDamageProtection(Iterable<ItemStack>, DamageSource)}
 * (Protection and its variants) instead of {@code getDamageBonus}.
 */
@Mixin(EnchantmentHelper.class)
public abstract class ArmorDamageGateMixin {

    @Inject(
            method = "getDamageProtection(Ljava/lang/Iterable;Lnet/minecraft/world/damagesource/DamageSource;)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void ejFixes$gateArmorProtection(Iterable<ItemStack> armorList, DamageSource source, CallbackInfoReturnable<Integer> cir) {
        LivingEntity defender = EyeGateContext.getDefender();
        if (!(defender instanceof Player player) || player.getAbilities().instabuild) {
            return;
        }

        int eyesEarned = PortalPlayer.get(player).map(PortalPlayer::getEyesEarn).orElse(24);

        int gatedTotal = 0;
        boolean anyGated = false;

        for (ItemStack stack : armorList) {
            if (stack.isEmpty()) {
                continue;
            }
            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                Enchantment enchantment = entry.getKey();
                int level = entry.getValue();
                int gatedLevel = EyeGateResolver.isExempt(enchantment)
                        ? level
                        : EyeGateResolver.effectiveLevel(enchantment, level, eyesEarned);
                if (gatedLevel != level) {
                    anyGated = true;
                }
                if (gatedLevel > 0) {
                    gatedTotal += enchantment.getDamageProtection(gatedLevel, source);
                }
            }
        }

        if (anyGated) {
            cir.setReturnValue(gatedTotal);
        }
    }
}

package com.endersjourney.ejfixes.mixin;

import com.endersjourney.ejfixes.EyeGateContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code EnchantmentHelper.getDamageProtection(Iterable<ItemStack>, DamageSource)}
 * — the vanilla method that actually computes Protection-family damage
 * reduction — takes the armor ItemStacks but no wearer entity.
 * {@link ArmorDamageGateMixin} needs to know who is wearing the armor to
 * look up their Ender Eyes, so this Mixin stashes the defender in
 * {@link EyeGateContext} for the exact duration of
 * {@code LivingEntity#getDamageAfterMagicAbsorb(DamageSource, float)}, the
 * vanilla method that calls {@code getDamageProtection}.
 * <p>
 * Injected at every {@code RETURN} (there's an early-return branch for
 * damage that bypasses magic), so the context always gets cleared
 * regardless of which branch the original method takes.
 */
@Mixin(LivingEntity.class)
public abstract class DefenseContextMixin {

    @Inject(
            method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD")
    )
    private void ejFixes$openDefenseContext(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        EyeGateContext.setDefender((LivingEntity) (Object) this);
    }

    @Inject(
            method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("RETURN")
    )
    private void ejFixes$closeDefenseContext(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        EyeGateContext.clearDefender();
    }
}

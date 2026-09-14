package com.endersjourney.ejfixes.mixin;

import com.endersjourney.ejfixes.EyeGateContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code EnchantmentHelper.getDamageBonus(ItemStack, MobType)} — the
 * vanilla method that actually computes Sharpness/Smite/Bane of
 * Arthropods' bonus melee damage — takes no entity, only the weapon
 * ItemStack. {@link WeaponDamageGateMixin} needs to know who is swinging
 * to look up their Ender Eyes, so this Mixin stashes the attacker in
 * {@link EyeGateContext} for the exact duration of
 * {@code Player#attack(Entity)}, the vanilla method that calls
 * {@code getDamageBonus}.
 */
@Mixin(Player.class)
public abstract class AttackContextMixin {

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void ejFixes$openAttackContext(Entity target, CallbackInfo ci) {
        EyeGateContext.setAttacker((Player) (Object) this);
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void ejFixes$closeAttackContext(Entity target, CallbackInfo ci) {
        EyeGateContext.clearAttacker();
    }
}

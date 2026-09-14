package com.endersjourney.ejfixes;

import net.minecraft.world.entity.LivingEntity;

/**
 * {@code EnchantmentHelper.getDamageBonus(ItemStack, MobType)} and
 * {@code EnchantmentHelper.getDamageProtection(Iterable<ItemStack>, DamageSource)}
 * — the vanilla methods that actually compute Sharpness-family melee bonus
 * damage and Protection-family damage reduction — take only item(s), never
 * the entity wielding/wearing them. {@link com.endersjourney.ejfixes.mixin.WeaponDamageGateMixin}
 * and {@link com.endersjourney.ejfixes.mixin.ArmorDamageGateMixin} need to
 * know WHICH player to check Ender Eyes for, so
 * {@link com.endersjourney.ejfixes.mixin.AttackContextMixin} and
 * {@link com.endersjourney.ejfixes.mixin.DefenseContextMixin} stash the
 * relevant entity here for the exact duration of the vanilla call that
 * leads to each one.
 * <p>
 * Two separate fields (not one): an attack combo can nest a defense call
 * inside it (dealing damage triggers the target's own hurt handling before
 * the outer attack call returns), so attacker and defender context must
 * not be able to clobber each other.
 */
public final class EyeGateContext {

    private EyeGateContext() {}

    private static final ThreadLocal<LivingEntity> ATTACKER = new ThreadLocal<>();
    private static final ThreadLocal<LivingEntity> DEFENDER = new ThreadLocal<>();

    public static void setAttacker(LivingEntity entity) {
        ATTACKER.set(entity);
    }

    public static void clearAttacker() {
        ATTACKER.remove();
    }

    public static LivingEntity getAttacker() {
        return ATTACKER.get();
    }

    public static void setDefender(LivingEntity entity) {
        DEFENDER.set(entity);
    }

    public static void clearDefender() {
        DEFENDER.remove();
    }

    public static LivingEntity getDefender() {
        return DEFENDER.get();
    }
}

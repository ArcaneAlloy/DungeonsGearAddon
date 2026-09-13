package com.endersjourney.ejfixes.mixin;

import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Dungeons Libraries' {@code BuiltInEnchantments(ItemStack)} constructor
 * filters a Dungeons Gear armor piece's {@code built_in_enchantments} list
 * with {@code enchantmentInstance.enchantment.canEnchant(itemStack)}, which
 * checks the enchantment's {@code EnchantmentCategory} (e.g. {@code ARMOR_LEGS}
 * for enchantments like Melee Aura or Life Steal Aura). Since every
 * armor-set config in Dungeons Gear lists its built-in enchantments once for
 * the whole set, this filter means only the one piece whose type matches
 * that category ever keeps the enchantment — every other piece in the set
 * silently drops it.
 * <p>
 * These "aura" enchantments are registered with
 * {@code ModEnchantmentTypes.ARMOR_SLOT} ({@code HEAD, CHEST, LEGS, FEET}),
 * meaning they're designed to trigger from any equipped armor slot — the
 * narrow {@code EnchantmentCategory} only exists to restrict where a player
 * could apply them via a vanilla enchanting table, which is irrelevant here
 * since built-in enchantments bypass the enchanting table entirely.
 * <p>
 * This redirect makes that {@code .filter(...)} call a no-op, so every
 * piece of an armor set carries (and displays) every enchantment listed in
 * its {@code built_in_enchantments}, instead of only the slot-matching
 * piece.
 * <p>
 * This is purely a display/consistency fix: {@code EnchantmentHelper
 * .getEnchantmentLevel} already takes the MAX level found across all worn
 * pieces rather than summing them, so duplicating the same level across
 * multiple pieces does not change the enchantment's effective strength or
 * let it stack.
 */
@Mixin(targets = "com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantments")
public class BuiltInEnchantmentsMixin {

    @Redirect(
            method = "<init>(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"
            ),
            remap = false
    )
    private Stream<EnchantmentInstance> ejFixes$showBuiltInEnchantsOnAllArmorPieces(
            Stream<EnchantmentInstance> stream, Predicate<EnchantmentInstance> unusedCanEnchantFilter) {
        return stream;
    }
}

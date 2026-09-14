package com.endersjourney.ejfixes;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * TEMPORARY — verification helper for the Eye Gate feature (Fixes 7-9).
 * Delete this whole class, its call sites in FullSetBonusMixin,
 * WeaponDamageGateMixin and ArmorDamageGateMixin, once the feature is
 * confirmed working in survival.
 */
public final class EyeGateDebug {

    private EyeGateDebug() {}

    private static long lastLog = 0L;

    public static void announce(Player player, String message) {
        long now = System.currentTimeMillis();
        if (now - lastLog > 2000) {
            lastLog = now;
            player.displayClientMessage(Component.literal("[EyeGate-DEBUG] " + message), false);
        }
    }
}

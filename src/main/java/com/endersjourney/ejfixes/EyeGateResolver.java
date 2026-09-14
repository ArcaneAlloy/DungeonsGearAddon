package com.endersjourney.ejfixes;

import fr.shoqapik.btemobs.BteMobsMod;
import fr.shoqapik.btemobs.recipe.WarlockRecipe;
import fr.shoqapik.btemobs.registry.BteMobsRecipeTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

/**
 * Resolves how many Ender Eyes a player needs before a given enchantment
 * level is allowed to take effect while worn/held (Fix 7 — Eye Gate, see
 * {@link com.endersjourney.ejfixes.mixin.FullSetBonusMixin}).
 * <p>
 * Two data sources, checked in priority order:
 * <ol>
 *     <li><b>Explicit</b> — a matching BeyondTheEndMobs {@link WarlockRecipe}
 *     for the exact (enchantment, level) pair. Authoritative when present,
 *     since it's the threshold the team actually tuned for the Warlock
 *     unlock system.</li>
 *     <li><b>Fallback formula</b> — for every enchantment/level with no such
 *     recipe (vanilla enchanting table, other mods' loot, dungeon chests,
 *     etc.): the level is spread proportionally across the full 1-24 eye
 *     range based on the enchantment's own max level, so nothing is left
 *     uncapped just because nobody authored a recipe for it.</li>
 * </ol>
 * Nothing here mutates an item's actual enchantment NBT — this only answers
 * "how many eyes for this level", the capping itself happens where the
 * result is applied.
 */
public final class EyeGateResolver {

    private EyeGateResolver() {}

    /** Total Ender Eyes in the progression; the fallback formula spreads levels across this range. */
    private static final int MAX_EYES = 24;

    /** Curses are always exempt — capping a penalty makes no sense. */
    public static boolean isExempt(Enchantment enchantment) {
        return enchantment.isCurse();
    }

    /** How many Ender Eyes are needed before {@code level} of {@code enchantment} is allowed to take effect. */
    public static int needEyesFor(Enchantment enchantment, int level) {
        if (level <= 0) {
            return 0;
        }

        Integer explicit = findExplicitNeedEyes(enchantment, level);
        if (explicit != null) {
            return explicit;
        }

        int maxLevel = Math.max(enchantment.getMaxLevel(), level);
        return Math.max(1, (int) Math.ceil((level * (double) MAX_EYES) / maxLevel));
    }

    private static Integer findExplicitNeedEyes(Enchantment enchantment, int level) {
        MinecraftServer server = BteMobsMod.getServer();
        if (server == null) {
            return null; // e.g. called client-side before a world/server is up — fall back to the formula
        }
        List<WarlockRecipe> recipes = server.getRecipeManager().getAllRecipesFor(BteMobsRecipeTypes.WARLOCK_RECIPE.get());
        for (WarlockRecipe recipe : recipes) {
            if (recipe.getEnchantment() == enchantment && recipe.getLevel() == level) {
                return recipe.getNeedEyes();
            }
        }
        return null;
    }

    /**
     * Highest level of {@code enchantment}, at most {@code actualLevel}, that
     * {@code eyesEarned} eyes are enough to unlock. Never returns a value
     * above {@code actualLevel}; returns 0 if even level 1 isn't reachable
     * yet. Checked by walking down from {@code actualLevel} rather than
     * assuming the eyes-per-level thresholds are strictly monotonic.
     */
    public static int effectiveLevel(Enchantment enchantment, int actualLevel, int eyesEarned) {
        if (isExempt(enchantment) || actualLevel <= 0) {
            return actualLevel;
        }
        for (int level = actualLevel; level >= 1; level--) {
            if (eyesEarned >= needEyesFor(enchantment, level)) {
                return level;
            }
        }
        return 0;
    }
}

package com.endersjourney.ejfixes;

import com.infamous.dungeons_libraries.items.gearconfig.ArmorGear;
import com.infamous.dungeons_libraries.items.gearconfig.BowGear;
import com.infamous.dungeons_libraries.items.gearconfig.CrossbowGear;
import com.infamous.dungeons_libraries.items.gearconfig.MeleeGear;
import net.minecraft.world.item.Item;

/**
 * Whether an item is one of Dungeons Gear's own gear classes — the ones
 * whose built-in enchantments live in the {@code BuiltInEnchantments}
 * capability rather than real NBT. Shared by
 * {@code BlacksmithUpgradeEnchantMigrationMixin}, {@code FullSetBonusMixin},
 * and {@code BuiltInEnchantmentTooltipFix} so all three treat "is this
 * Dungeons Gear's own gear" the same way, for armor and weapons alike.
 */
public final class DungeonsGearGearTypes {

    private DungeonsGearGearTypes() {
    }

    public static boolean isDungeonsGearGear(Item item) {
        return item instanceof ArmorGear
                || item instanceof MeleeGear
                || item instanceof BowGear
                || item instanceof CrossbowGear;
    }
}

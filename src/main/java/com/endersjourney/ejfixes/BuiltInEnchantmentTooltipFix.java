package com.endersjourney.ejfixes;

import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantments;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantmentsHelper;
import com.infamous.dungeons_libraries.items.gearconfig.ArmorGearConfigRegistry;
import fr.shoqapik.brokenitems.BrokenItemsEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.endersjourney.ejfixes.DungeonsGearGearTypes.isDungeonsGearGear;

/**
 * Three touch-ups for {@code dungeons_gear} innate enchantment tooltip
 * lines — armor or weapon alike — all driven by {@link MigratedGearTag}.
 * For each relevant enchantment on the hovered item, this compares two
 * numbers:
 * <ul>
 *     <li>{@code nativeLevel} — the level from the item's own built-in
 *     capability (if it's still one of Dungeons Gear's own gear classes),
 *     i.e. exactly what Dungeons Libraries' own tooltip handler
 *     ({@code DescriptionHelper.onItemTooltip}) already rendered, in
 *     orange, with no description.</li>
 *     <li>{@code displayLevel} — the level that should actually be shown:
 *     the higher of {@code nativeLevel} and the real vanilla enchantment
 *     NBT level (which {@code BlacksmithUpgradeEnchantMigrationMixin}
 *     writes for an inherited enchantment, possibly boosted above what the
 *     item natively provides — see its own javadoc), further boosted by 1
 *     if {@code FullSetBonusMixin}'s full-set bonus currently applies
 *     (armor only, and only while actually equipped as part of a complete,
 *     unbroken, matching set).</li>
 * </ul>
 * When the two differ, or when there was no native line to begin with
 * (a migrated piece, or an inherited enchantment the item doesn't provide
 * natively), this rewrites the tooltip line — orange, with the enchantment
 * pack's own description line added under it, matching Dungeons Libraries'
 * only-the-name style otherwise. Runs at LOW priority so it applies after
 * both Dungeons Libraries' handler and vanilla's own tooltip line have
 * already run.
 */
@Mod.EventBusSubscriber(modid = EJFixes.MODID, value = Dist.CLIENT)
public class BuiltInEnchantmentTooltipFix {

    private static final TextColor INNATE_COLOR = TextColor.parseColor("#FF8100");

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack hovered = event.getItemStack();
        boolean isOriginalGear = isDungeonsGearGear(hovered.getItem());

        BuiltInEnchantments capability = isOriginalGear
                ? BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(hovered)
                : null;
        Map<Enchantment, Integer> realNbt = EnchantmentHelper.getEnchantments(hovered);

        Set<Enchantment> relevant = new LinkedHashSet<>();
        if (capability != null) {
            for (EnchantmentInstance instance : capability.getBuiltInEnchantments(ArmorGearConfigRegistry.GEAR_CONFIG_BUILTIN_RESOURCELOCATION)) {
                relevant.add(instance.enchantment);
            }
        }
        for (Enchantment enchantment : realNbt.keySet()) {
            if (MigratedGearTag.isInnate(hovered, enchantment)) {
                relevant.add(enchantment);
            }
        }
        if (relevant.isEmpty()) {
            return;
        }

        ResourceLocation armorSet = MigratedGearTag.getEffectiveArmorSet(hovered);
        boolean fullSetBonus = armorSet != null
                && isWearingAsPartOfCompleteUnbrokenMatchingSet(event.getEntity(), hovered, armorSet);

        List<Component> tooltip = event.getToolTip();

        for (Enchantment enchantment : relevant) {
            int nativeLevel = capability != null ? capability.getBuiltInItemEnchantmentLevel(enchantment) : 0;
            int realLevel = realNbt.getOrDefault(enchantment, 0);
            int bakedLevel = Math.max(nativeLevel, realLevel);
            boolean hasNativeLine = nativeLevel > 0;
            int displayLevel = fullSetBonus ? Math.min(bakedLevel + 1, enchantment.getMaxLevel()) : bakedLevel;

            int nameLineIndex = -1;
            if (!hasNativeLine || displayLevel != nativeLevel) {
                String originalText = enchantment.getFullname(hasNativeLine ? nativeLevel : realLevel).getString();
                Component replacement = enchantment.getFullname(displayLevel).copy()
                        .withStyle(Style.EMPTY.withColor(INNATE_COLOR));
                for (int i = 0; i < tooltip.size(); i++) {
                    if (tooltip.get(i).getString().equals(originalText)) {
                        tooltip.set(i, replacement);
                        nameLineIndex = i;
                        break;
                    }
                }
            } else {
                String currentText = enchantment.getFullname(nativeLevel).getString();
                for (int i = 0; i < tooltip.size(); i++) {
                    if (tooltip.get(i).getString().equals(currentText)) {
                        nameLineIndex = i;
                        break;
                    }
                }
            }

            // Description: add it right under the name line if this
            // enchantment has one and it isn't already present.
            ResourceLocation enchantId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
            if (enchantId != null && nameLineIndex >= 0) {
                String descKey = "enchantment." + enchantId.getNamespace() + "." + enchantId.getPath() + ".desc";
                String descText = Component.translatable(descKey).getString();
                boolean hasDescKey = !descText.equals(descKey);
                boolean alreadyPresent = tooltip.stream().anyMatch(line -> line.getString().equals(descText));
                if (hasDescKey && !alreadyPresent) {
                    tooltip.add(nameLineIndex + 1, Component.literal(descText).withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    private static boolean isWearingAsPartOfCompleteUnbrokenMatchingSet(Player player, ItemStack hovered, ResourceLocation armorSet) {
        if (player == null) {
            return false;
        }
        boolean equipped = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (player.getItemBySlot(slot) == hovered) {
                equipped = true;
                break;
            }
        }
        if (!equipped) {
            return false;
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = player.getItemBySlot(slot);
            if (BrokenItemsEvents.isItemBroken(piece)) {
                return false;
            }
            if (!armorSet.equals(MigratedGearTag.getEffectiveArmorSet(piece))) {
                return false;
            }
        }
        return true;
    }
}

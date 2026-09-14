package com.endersjourney.ejfixes;

import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantments;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantmentsHelper;
import com.infamous.dungeons_libraries.items.gearconfig.ArmorGear;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Three touch-ups for {@code dungeons_gear} innate enchantment tooltip
 * lines, all driven by {@link MigratedGearTag}:
 * <ul>
 *     <li><b>Color</b> — Dungeons Libraries paints an ArmorGear piece's own
 *     innate enchantment lines in orange itself
 *     ({@code DescriptionHelper.onItemTooltip}). A migrated piece (e.g. a
 *     {@code netherite_helmet}) has no such line — its innate enchantment
 *     is just a normal, plainly-colored vanilla enchantment line, with
 *     nothing marking it as inherited. This always recolors it to match,
 *     regardless of whether the full set is currently worn.</li>
 *     <li><b>Description</b> — a migrated piece's enchantment is real
 *     vanilla NBT, so some other mod in the pack that reads
 *     {@code enchantment.<namespace>.<path>.desc} lang entries adds a
 *     description line under it. An ArmorGear piece's own innate line
 *     never gets this, since Dungeons Libraries' handler only adds the
 *     name. This adds the same description line to ArmorGear pieces too,
 *     whenever one isn't already present, so both styles show it
 *     consistently.</li>
 *     <li><b>Level</b> — neither Dungeons Libraries' own tooltip nor
 *     vanilla's generic enchantment line knows about
 *     {@code FullSetBonusMixin}'s +1 set bonus, since that's computed
 *     per-entity at effect-trigger time, not per-item at tooltip time. So
 *     both read e.g. "Life Steal Aura I" even while the full set is
 *     equipped and the real effective level is II. This shows the boosted
 *     level instead, but only while the viewing player actually has this
 *     exact stack equipped as part of a complete, unbroken, matching set —
 *     mirroring exactly the condition
 *     {@link com.endersjourney.ejfixes.mixin.FullSetBonusMixin} uses to
 *     grant the real bonus, so the tooltip and the actual effect never
 *     disagree.</li>
 * </ul>
 * Runs at LOW priority so it applies after both Dungeons Libraries' handler
 * and vanilla's own tooltip line have already run.
 */
@Mod.EventBusSubscriber(modid = EJFixes.MODID, value = Dist.CLIENT)
public class BuiltInEnchantmentTooltipFix {

    private static final TextColor INNATE_COLOR = TextColor.parseColor("#FF8100");

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack hovered = event.getItemStack();
        boolean isArmorGear = hovered.getItem() instanceof ArmorGear;
        ResourceLocation armorSet = MigratedGearTag.getEffectiveArmorSet(hovered);
        if (armorSet == null) {
            return;
        }

        List<EnchantmentInstance> innate = getInnateEnchantments(hovered);
        if (innate.isEmpty()) {
            return;
        }

        boolean fullSetBonus = isWearingAsPartOfCompleteUnbrokenMatchingSet(event.getEntity(), hovered, armorSet);
        List<Component> tooltip = event.getToolTip();

        for (EnchantmentInstance instance : innate) {
            int displayLevel = fullSetBonus
                    ? Math.min(instance.level + 1, instance.enchantment.getMaxLevel())
                    : instance.level;

            // Color + level: only needs rewriting for a migrated piece
            // (always, to recolor) or when the level actually changed.
            int nameLineIndex = -1;
            if (!isArmorGear || displayLevel != instance.level) {
                String originalText = instance.enchantment.getFullname(instance.level).getString();
                Component replacement = instance.enchantment.getFullname(displayLevel).copy()
                        .withStyle(Style.EMPTY.withColor(INNATE_COLOR));
                for (int i = 0; i < tooltip.size(); i++) {
                    if (tooltip.get(i).getString().equals(originalText)) {
                        tooltip.set(i, replacement);
                        nameLineIndex = i;
                        break;
                    }
                }
            } else {
                String currentText = instance.enchantment.getFullname(instance.level).getString();
                for (int i = 0; i < tooltip.size(); i++) {
                    if (tooltip.get(i).getString().equals(currentText)) {
                        nameLineIndex = i;
                        break;
                    }
                }
            }

            // Description: add it right under the name line if this
            // enchantment has one and it isn't already present.
            ResourceLocation enchantId = ForgeRegistries.ENCHANTMENTS.getKey(instance.enchantment);
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

    /** The piece's own innate enchantments — from the capability if it's
     * still Dungeons Gear armor, or from its real NBT filtered down to the
     * ones {@link MigratedGearTag} recorded as inherited, if it was
     * migrated. */
    private static List<EnchantmentInstance> getInnateEnchantments(ItemStack stack) {
        if (stack.getItem() instanceof ArmorGear) {
            BuiltInEnchantments capability = BuiltInEnchantmentsHelper.getBuiltInEnchantmentsCapability(stack);
            return capability.getBuiltInEnchantments(ArmorGearConfigRegistry.GEAR_CONFIG_BUILTIN_RESOURCELOCATION);
        }
        List<EnchantmentInstance> result = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            if (MigratedGearTag.isInnate(stack, entry.getKey())) {
                result.add(new EnchantmentInstance(entry.getKey(), entry.getValue()));
            }
        }
        return result;
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

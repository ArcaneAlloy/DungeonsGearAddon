package com.endersjourney.ejfixes;

import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantments;
import com.infamous.dungeons_libraries.capabilities.builtinenchants.BuiltInEnchantmentsHelper;
import com.infamous.dungeons_libraries.items.gearconfig.ArmorGear;
import com.infamous.dungeons_libraries.items.gearconfig.ArmorGearConfigRegistry;
import fr.shoqapik.brokenitems.BrokenItemsEvents;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dungeons Libraries paints each built-in ("innate") enchantment's tooltip
 * line itself, via a plain {@code ItemTooltipEvent} listener
 * ({@code DescriptionHelper.onItemTooltip}) that always uses the level
 * statically configured in the piece's gearconfig — it has no idea about
 * {@code FullSetBonusMixin}'s +1 set bonus, since that's computed
 * per-entity at effect-trigger time, not per-item at tooltip time. A
 * migrated piece's enchantment line is instead painted by vanilla's own
 * generic enchantment tooltip rendering, which is equally unaware. Either
 * way the tooltip reads e.g. "Life Steal Aura I" even while the full set is
 * equipped and the real effective level is II.
 * <p>
 * This listens to the same event at LOW priority (both Dungeons Libraries'
 * handler and vanilla's own line run at/before the default NORMAL priority,
 * so this runs after them, once the "I" line already exists) and, only
 * while the tooltip is being shown for a piece the viewing player actually
 * has equipped as part of a complete, unbroken, matching set — tracked via
 * {@link MigratedGearTag} so a migrated piece still counts — replaces each
 * innate enchantment's line with the boosted level. This mirrors exactly
 * the same condition {@link com.endersjourney.ejfixes.mixin.FullSetBonusMixin}
 * uses to grant the real bonus, so the tooltip and the actual effect never
 * disagree.
 */
@Mod.EventBusSubscriber(modid = EJFixes.MODID, value = Dist.CLIENT)
public class BuiltInEnchantmentTooltipFix {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack hovered = event.getItemStack();
        ResourceLocation armorSet = MigratedGearTag.getEffectiveArmorSet(hovered);
        if (armorSet == null) {
            return;
        }

        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        // Only reflect the bonus while this exact stack is the one equipped
        // by the player viewing the tooltip — not just any copy sitting in
        // an inventory somewhere.
        boolean equipped = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (player.getItemBySlot(slot) == hovered) {
                equipped = true;
                break;
            }
        }
        if (!equipped || !isWearingCompleteUnbrokenMatchingSet(player, armorSet)) {
            return;
        }

        List<EnchantmentInstance> innate = getInnateEnchantments(hovered);
        if (innate.isEmpty()) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        for (EnchantmentInstance instance : innate) {
            int boostedLevel = Math.min(instance.level + 1, instance.enchantment.getMaxLevel());
            if (boostedLevel <= instance.level) {
                continue;
            }
            String originalText = instance.enchantment.getFullname(instance.level).getString();
            Component boosted = instance.enchantment.getFullname(boostedLevel).copy()
                    .withStyle(Style.EMPTY.withColor(TextColor.parseColor("#FF8100")));
            for (int i = 0; i < tooltip.size(); i++) {
                if (tooltip.get(i).getString().equals(originalText)) {
                    tooltip.set(i, boosted);
                    break;
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

    private static boolean isWearingCompleteUnbrokenMatchingSet(Player player, ResourceLocation armorSet) {
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

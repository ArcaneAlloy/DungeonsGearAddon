package com.endersjourney.ejfixes;

import mc.duzo.ender_journey.capabilities.PortalPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;

/**
 * Fix 10 — Eye Gate tooltip. For each real enchantment on the hovered item
 * that Fixes 7-9's eye gate would currently restrict for the viewing
 * player, adds a line right under its name showing the level actually in
 * effect (or that it's fully locked) and how many more Ender Eyes are
 * needed for the printed level. The real level's own line is never
 * touched — see {@link EyeGateResolver} and
 * {@link com.endersjourney.ejfixes.mixin.FullSetBonusMixin}'s class
 * javadoc for why.
 */
@Mod.EventBusSubscriber(modid = EJFixes.MODID, value = Dist.CLIENT)
public class EyeGateTooltipFix {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null || player.getAbilities().instabuild) {
            return; // creative/spectator: the gate never applies, nothing to show
        }

        ItemStack hovered = event.getItemStack();
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(hovered);
        if (enchantments.isEmpty()) {
            return;
        }

        int eyesEarned = PortalPlayer.get(player).map(PortalPlayer::getEyesEarn).orElse(24);
        List<Component> tooltip = event.getToolTip();

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int realLevel = entry.getValue();
            if (EyeGateResolver.isExempt(enchantment)) {
                continue;
            }

            int gatedLevel = EyeGateResolver.effectiveLevel(enchantment, realLevel, eyesEarned);
            if (gatedLevel == realLevel) {
                continue; // fully unlocked already, nothing to add
            }

            String nameLine = enchantment.getFullname(realLevel).getString();
            int nameLineIndex = -1;
            for (int i = 0; i < tooltip.size(); i++) {
                if (tooltip.get(i).getString().equals(nameLine)) {
                    nameLineIndex = i;
                    break;
                }
            }
            if (nameLineIndex < 0) {
                continue; // vanilla didn't render a line for this one, don't guess where to insert
            }

            int neededForReal = EyeGateResolver.needEyesFor(enchantment, realLevel);
            Component status = gatedLevel > 0
                    ? Component.translatable("ejfixes.eyegate.tooltip.in_use",
                            enchantment.getFullname(gatedLevel), neededForReal, eyesEarned)
                            .withStyle(ChatFormatting.RED)
                    : Component.translatable("ejfixes.eyegate.tooltip.locked", neededForReal, eyesEarned)
                            .withStyle(ChatFormatting.RED);

            tooltip.add(nameLineIndex + 1, status);
        }
    }
}

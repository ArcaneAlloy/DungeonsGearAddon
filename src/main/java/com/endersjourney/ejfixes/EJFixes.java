package com.endersjourney.ejfixes;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ender's Journey Fixes.
 * <p>
 * A small addon mod that patches compatibility/design issues in other mods
 * used by the Ender's Journey — Beyond The End modpack.
 * <p>
 * Fixes are implemented as Mixins under {@code com.endersjourney.ejfixes.mixin},
 * registered in {@code ej_fixes.mixins.json}. This class currently has no
 * runtime setup of its own — add event bus registration here if a future
 * fix needs one (e.g. a config, a client-only feature, a datapack reload
 * listener).
 */
@Mod(EJFixes.MODID)
public class EJFixes {

    public static final String MODID = "ej_fixes";
    public static final Logger LOGGER = LoggerFactory.getLogger(EJFixes.class);

    public EJFixes() {
        LOGGER.info("Ender's Journey Fixes loaded.");
    }
}

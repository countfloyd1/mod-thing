package com.playerwatch;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerWatchMod implements ModInitializer {
    public static final String MOD_ID = "playerwatch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("PlayerWatch server side loaded (client-only mod, no server logic needed)");
    }
}

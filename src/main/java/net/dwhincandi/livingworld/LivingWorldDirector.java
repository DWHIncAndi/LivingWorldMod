package net.dwhincandi.livingworld;

import net.dwhincandi.livingworld.command.DirectorCommands;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LivingWorldDirector implements ModInitializer {
    public static final String MOD_ID = "living-world";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        DirectorCommands.register();
        LOGGER.info("Living World Director 0.1 initialized.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

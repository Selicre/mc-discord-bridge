package selic.re.discordbridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Path;

public class DiscordBridgeMod implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path configFile = FabricLoader.getInstance().getConfigDir().resolve("discord-bridge.json");
            Path lookupFile = FabricLoader.getInstance().getConfigDir().resolve("discord-users.json");
            try {
                @Nullable DiscordBotConfig config = DiscordBotConfig.fromFile(configFile);
                DiscordPlayerLookup lookup = DiscordPlayerLookup.fromFile(lookupFile);
                if (config != null) {
                    DiscordBotImpl.init(config, server, lookup);
                } else {
                    LOGGER.error("A valid token is required in {}", configFile);
                }
            } catch (LoginException e) {
                LOGGER.error("A valid token is required in {}", configFile, e);
            } catch (IOException e) {
                LOGGER.error("Failed to read config {}", configFile, e);
            }
        });
    }
}

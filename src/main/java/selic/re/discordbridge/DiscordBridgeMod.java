package selic.re.discordbridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.security.auth.login.LoginException;
import java.io.IOException;

public final class DiscordBridgeMod implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            final var configFile = FabricLoader.getInstance().getConfigDir().resolve("discord-bridge.json");
            try {
                final var config = DiscordBotConfig.fromFile(configFile);
                if (config != null) {
                    DiscordBot.init(config, server);
                } else {
                    LOGGER.error("A valid token is required in {}", configFile);
                }
            } catch (final LoginException e) {
                LOGGER.error("A valid token is required in {}", configFile, e);
            } catch (final IOException e) {
                LOGGER.error("Failed to read config {}", configFile, e);
            }
        });
    }
}

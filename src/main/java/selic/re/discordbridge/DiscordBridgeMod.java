package selic.re.discordbridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

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

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            DiscordBot.instance().shutdown();
        });

        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.STYLING_PHASE, (sender, message) -> {
            if (sender != null) {
                Text messageText = DiscordBot.instance().formatAndSendMessage(sender.getGameProfile(), message);
                return CompletableFuture.completedFuture(messageText);
            }

            return CompletableFuture.completedFuture(message);
        });
    }
}

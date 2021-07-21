package selic.re.discordbridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;

import javax.security.auth.login.LoginException;
import java.io.IOException;

public class DiscordBridgeMod implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			var configFile = FabricLoader.getInstance().getConfigDir().resolve("discord-bridge.json");
			try {
				DiscordBot.init(Config.reloadFromFile(configFile), server);
			} catch (LoginException e) {
				LogManager.getLogger().error("Can't initialize discord bridge: could not login: " + e.getMessage());
			} catch (IOException e) {
				LogManager.getLogger().error("Can't initialize discord bridge: can't read config at " + configFile + ": " + e.getMessage());
			}
		});
	}
}

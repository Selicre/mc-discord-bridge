package selic.re.discordbridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;

import javax.security.auth.login.LoginException;
import java.io.FileNotFoundException;

public class DiscordBridgeMod implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			var filename = "config/discord-bridge.json";
			try {
				DiscordBot.init(Config.reloadFromFile(filename), server);
			} catch (LoginException e) {
				LogManager.getLogger().error("Can't initialize discord bridge: could not login: " + e.getMessage());
			} catch (FileNotFoundException e) {
				LogManager.getLogger().error("Can't initialize discord bridge: can't read config at " + filename + ": " + e.getMessage());
			}
		});
	}
}

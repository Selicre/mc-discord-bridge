package selic.re.discordbridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import javax.security.auth.login.LoginException;
import java.io.FileNotFoundException;

public class DiscordBridgeMod implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			try {
				DiscordBot.init(Config.reloadFromFile("config/discord-bridge.json"), server);
			} catch (LoginException | FileNotFoundException e) {
				e.printStackTrace();
			}
		});
	}
}

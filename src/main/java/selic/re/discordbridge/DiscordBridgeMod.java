package selic.re.discordbridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class DiscordBridgeMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(DiscordBotConfig::performInit);
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            new DiscordCommand().register(dispatcher);
        });
    }

}

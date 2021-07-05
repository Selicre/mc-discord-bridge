package selic.re.discordbridge.mixin;

import net.minecraft.server.filter.TextStream;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import selic.re.discordbridge.DiscordBot;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class NetworkHandlerMixin {
	@Shadow public abstract ServerPlayerEntity getPlayer();

	@Inject(at = @At("HEAD"), method = "handleMessage")
	public void preMessage(TextStream.Message message, CallbackInfo info) {
		String msg = message.getRaw();
		if (!msg.startsWith("/")) {
			String player = this.getPlayer().getDisplayName().asString();
			DiscordBot.getInstance().sendChatMessage(player, msg);
		}
	}
}

package selic.re.discordbridge.mixin;

import com.mojang.authlib.GameProfile;
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
			GameProfile player = this.getPlayer().getGameProfile();
			/*var db = DiscordBot.getInstance();
			if (db != null) {
				db.sendChatMessage(player, msg);
			}*/
			DiscordBot.getInstance().ifPresent(db -> db.sendChatMessage(player, msg));
		} else if (msg.startsWith("/me ")) {
			// The reason why this has to be handled individually is because in this particular instance,
			// the broadcast() method is used; this relies on internal behaviour and should probably
			// be changed to match the translatable string rather than hijacking this particular class.
			// That said, it works. For now.
			GameProfile player = this.getPlayer().getGameProfile();
			DiscordBot.getInstance().ifPresent(db -> db.sendEmoteMessage(player, msg.substring(4)));
		}
	}
}

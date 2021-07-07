package selic.re.discordbridge.mixin;

import net.minecraft.network.MessageType;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import selic.re.discordbridge.DiscordBot;

import java.util.UUID;

@Mixin(PlayerManager.class)
public class BroadcastHandlerMixin {
    @Inject(at = @At("HEAD"), method = "broadcastChatMessage")
    public void preBroadcastChatMessage(Text message, MessageType type, UUID sender, CallbackInfo info) {
        var db = DiscordBot.getInstance();
        if (db != null) {
            db.sendSystemMessage(message);
        }
    }
}

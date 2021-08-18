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
abstract class BroadcastHandlerMixin {
    @Inject(
        method = "broadcastChatMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V",
        at = @At("HEAD"), require = 1)
    private void preBroadcastChatMessage(Text message, MessageType type, UUID sender, CallbackInfo info) {
        DiscordBot.instance().sendMessage(message);
    }
}

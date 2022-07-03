package selic.re.discordbridge.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import selic.re.discordbridge.DiscordBot;

import java.util.function.Function;

@Mixin(PlayerManager.class)
abstract class BroadcastHandlerMixin {
    @Inject(
        method = "broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Z)V",
        at = @At("HEAD"), require = 1)
    private void preBroadcast(Text message, Function<ServerPlayerEntity, Text> playerMessageFactory, boolean actionBar, CallbackInfo ci) {
        if (!actionBar) DiscordBot.instance().sendMessage(message);
    }
}

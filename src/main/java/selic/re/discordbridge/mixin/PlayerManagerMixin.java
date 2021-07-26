package selic.re.discordbridge.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import selic.re.discordbridge.DiscordBot;

@Mixin(PlayerManager.class)
abstract class PlayerManagerMixin {
    @Inject(
        method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
        at = @At("RETURN"), require = 1)
    private void remove(final CallbackInfo ci) {
        DiscordBot.getInstance().ifPresent(DiscordBot::onPlayersChanged);
    }

    @Inject(
        method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V",
        at = @At("RETURN"), require = 1, allow = 1)
    private void remove(final ClientConnection connection, final ServerPlayerEntity player, final CallbackInfo ci) {
        DiscordBot.getInstance().ifPresent(DiscordBot::onPlayersChanged);
    }
}

package selic.re.discordbridge.mixin;

import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import selic.re.discordbridge.DiscordBot;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Mixin(PlayerManager.class)
public class BroadcastHandlerMixin {
    @Shadow @Final private List<ServerPlayerEntity> players;
    @Shadow @Final private MinecraftServer server;

    @Inject(at = @At("HEAD"), method = "broadcastChatMessage")
    public void preBroadcastChatMessage(Text message, MessageType type, UUID sender, CallbackInfo info) {
        DiscordBot.getInstance().sendSystemMessage(message.getString());
    }
    @Inject(at = @At("HEAD"), method = "broadcast")
    public void preBroadcast(Text serverMessage, Function<ServerPlayerEntity, Text> playerMessageFactory, MessageType playerMessageType, UUID sender, CallbackInfo ci) {
        DiscordBot.getInstance().sendSystemMessage(serverMessage.getString());
    }
}

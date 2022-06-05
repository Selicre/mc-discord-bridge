package selic.re.discordbridge.mixin;

import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.filter.FilteredMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.EntityTrackingListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import selic.re.discordbridge.DiscordBot;

@Mixin(ServerPlayNetworkHandler.class)
abstract class NetworkHandlerMixin implements EntityTrackingListener, ServerPlayPacketListener {
    @Inject(
        method = "handleMessage",
        at = @At("HEAD"), require = 1)
    private void preMessage(ChatMessageC2SPacket packet, FilteredMessage<String> message, CallbackInfo ci) {
        String str = message.raw();

        if (str.startsWith("/me ")) {
            /*
             The reason why this has to be handled individually is because in this particular instance,
             the broadcast() method is used; this relies on internal behaviour and should probably
             be changed to match the translatable string rather than hijacking this particular class.
             That said, it works. For now.
            */
            DiscordBot.instance().sendMessage(getPlayer().getGameProfile(), "*" + str.substring("/me ".length()) + "*");
        }
    }
}

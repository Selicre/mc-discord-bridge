package selic.re.discordbridge.mixin;

import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.EntityTrackingListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import selic.re.discordbridge.DiscordBot;

@Mixin(ServerPlayNetworkHandler.class)
abstract class NetworkHandlerMixin implements EntityTrackingListener, ServerPlayPacketListener {
    @Inject(
        method = "handleDecoratedMessage",
        at = @At("HEAD"), require = 1)
    private void preMessage(SignedMessage signedMessage, CallbackInfo ci) {
        String str = signedMessage.getContent().getString();
        DiscordBot.instance().formatAndSendMessage(signedMessage.getContent());

        if (str.startsWith("/me ")) {
            /*
             The reason why this has to be handled individually is because in this particular instance,
             the broadcast() method is used; this relies on internal behaviour and should probably
             be changed to match the translatable string rather than hijacking this particular class.
             That said, it works. For now.
            */
            //DiscordBot.instance().sendMessage(getPlayer().getGameProfile(), "*" + str.substring("/me ".length()) + "*");
        }
    }
}

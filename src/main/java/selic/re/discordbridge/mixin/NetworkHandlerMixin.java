package selic.re.discordbridge.mixin;

import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.server.filter.TextStream;
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
        method = "handleMessage(Lnet/minecraft/server/filter/TextStream$Message;)V",
        at = @At("HEAD"), require = 1)
    private void preMessage(TextStream.Message message, CallbackInfo info) {
        String str = message.getRaw();

        if (!str.startsWith("/")) {
            DiscordBot.getInstance().ifPresent(bot -> {
                bot.sendChatMessage(getPlayer().getGameProfile(), str);
            });
        } else if (str.startsWith("/me ")) {
            /*
             The reason why this has to be handled individually is because in this particular instance,
             the broadcast() method is used; this relies on internal behaviour and should probably
             be changed to match the translatable string rather than hijacking this particular class.
             That said, it works. For now.
            */
            DiscordBot.getInstance().ifPresent(bot -> {
                bot.sendEmoteMessage(getPlayer().getGameProfile(), str.substring("/me ".length()));
            });
        }
    }
}

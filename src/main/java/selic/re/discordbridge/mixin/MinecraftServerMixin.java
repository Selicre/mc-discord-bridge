package selic.re.discordbridge.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import selic.re.discordbridge.DiscordBot;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Inject(
        method = "shutdown()V",
        at = @At("RETURN"), require = 1)
    public void shutdown(CallbackInfo ci)
    {
        DiscordBot.instance().shutdown();
    }
}

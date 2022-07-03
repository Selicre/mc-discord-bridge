package selic.re.discordbridge.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import selic.re.discordbridge.DiscordBot;

@Mixin(ServerPlayerEntity.class)
abstract class ServerPlayerEntityMixin extends PlayerEntity {

    public ServerPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile gameProfile, @Nullable PlayerPublicKey publicKey) {
        super(world, pos, yaw, gameProfile, publicKey);
    }

    @Inject(
        method = "getPlayerListName()Lnet/minecraft/text/Text;",
        at = @At("HEAD"), require = 1, cancellable = true)
    private void getPlayerListName(CallbackInfoReturnable<Text> ci) {
        Text name = DiscordBot.instance().getDiscordName(this);
        if (name != null) {
            ci.setReturnValue(name);
        }
    }

    @Inject(
        method = "method_44706",
        at = @At("HEAD"), require = 1, cancellable = true)
    private void acceptsMessage(CallbackInfoReturnable<Boolean> ci) {
        if (DiscordBot.instance().isChatHidden(this)) {
            ci.setReturnValue(false);
        }
    }
    @Inject(
        method = "method_44707",
        at = @At("HEAD"), require = 1, cancellable = true)
    private void acceptsMessage(boolean actionBar, CallbackInfoReturnable<Boolean> ci) {
        if (!actionBar && DiscordBot.instance().isChatHidden(this)) {
            ci.setReturnValue(false);
        }
    }

}

package selic.re.discordbridge.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.MessageType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import selic.re.discordbridge.DiscordBot;

@Mixin(ServerPlayerEntity.class)
abstract class ServerPlayerEntityMixin extends PlayerEntity {
    ServerPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Inject(
        method = "getPlayerListName()Lnet/minecraft/text/Text;",
        at = @At("HEAD"), require = 1, cancellable = true)
    private void getPlayerListName(CallbackInfoReturnable<Text> ci) {
        if (DiscordBot.getInstance().isEmpty()) {
            return;
        }
        Text name = DiscordBot.getInstance().get().getDiscordName(this);
        if (name != null) {
            ci.setReturnValue(name);
        }
    }

    @Inject(
        method = "acceptsMessage(Lnet/minecraft/network/MessageType;)Z",
        at = @At("HEAD"), require = 1, cancellable = true)
    private void acceptsMessage(MessageType type, CallbackInfoReturnable<Boolean> ci) {
        if (DiscordBot.getInstance().isEmpty()) {
            return;
        }
        DiscordBot discordBot = DiscordBot.getInstance().get();
        if (type == MessageType.CHAT && discordBot.isChatHidden(getUuid())) {
            ci.setReturnValue(false);
        }
    }
}

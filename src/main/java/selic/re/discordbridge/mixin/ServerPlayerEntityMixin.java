package selic.re.discordbridge.mixin;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.entities.Member;
import net.minecraft.network.MessageType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.entity.EntityLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import selic.re.discordbridge.DiscordBot;
import selic.re.discordbridge.DiscordFormattingConverter;

@Mixin(ServerPlayerEntity.class)
abstract class ServerPlayerEntityMixin implements EntityLike {
    @Inject(
        method = "getPlayerListName()Lnet/minecraft/text/Text;",
        at = @At("HEAD"), require = 1, cancellable = true)
    private void getPlayerListName(CallbackInfoReturnable<Text> ci) {
        if (DiscordBot.getInstance().isEmpty()) {
            return;
        }
        DiscordBot discordBot = DiscordBot.getInstance().get();
        Member member = discordBot.getLookup().getDiscordMember(discordBot.getGuild(), new GameProfile(getUuid(), null));
        if (member != null) {
            ci.setReturnValue(DiscordFormattingConverter.discordUserToMinecraft(member.getUser(), discordBot.getGuild(), false));
            ci.cancel();
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
        if (type == MessageType.CHAT && discordBot.isStreaming(getUuid())) {
            ci.setReturnValue(false);
            ci.cancel();
        }
    }
}

package selic.re.discordbridge.mixin;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Member;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import selic.re.discordbridge.DiscordBot;
import selic.re.discordbridge.DiscordFormattingConverter;

@Mixin(PlayerManager.class)
abstract class PlayerManagerMixin {
    @Inject(
        method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
        at = @At("RETURN"), require = 1)
    private void remove(CallbackInfo ci) {
        DiscordBot.getInstance().ifPresent(DiscordBot::onPlayersChanged);
    }

    @Inject(
        method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V",
        at = @At("RETURN"), require = 1, allow = 1)
    private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        if (DiscordBot.getInstance().isEmpty()) {
            return;
        }

        DiscordBot instance = DiscordBot.getInstance().get();
        instance.onPlayersChanged();
        instance.onPlayerConnected(player.getGameProfile());
        if (instance.getConfig().listDiscordUsers) {
            for (Member member : instance.getChannelMembers()) {
                String name = "@" + member.getUser().getName();
                if (name.length() > 16 || member.getUser().isBot()) {
                    continue;
                }
                PlayerListS2CPacket packet = new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER);
                GameProfile profile = new GameProfile(PlayerEntity.getOfflinePlayerUuid(member.getId()), name);
                int latency = member.getOnlineStatus() == OnlineStatus.OFFLINE ? -1 : 10000;
                Text displayName = DiscordFormattingConverter.discordUserToMinecraft(member.getUser(), member.getGuild(), false);
                packet.getEntries().add(new PlayerListS2CPacket.Entry(profile, latency, GameMode.SPECTATOR, displayName));
                connection.send(packet);
            }
        }
    }
}

package selic.re.discordbridge;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import javax.annotation.Nullable;
import java.util.List;

final class UninitializedDiscordBot implements DiscordBot {
    static final DiscordBot INSTANCE = new UninitializedDiscordBot();

    private final DiscordBotConfig defaultConfig = new DiscordBotConfig();

    private UninitializedDiscordBot() {
    }

    @Override
    public DiscordBotConfig getConfig() {
        return defaultConfig;
    }

    @Override
    public List<Member> getChannelMembers() {
        return List.of();
    }

    @Override
    @Nullable
    public ServerPlayerEntity getPlayer(User user) {
        return null;
    }

    @Override
    @Nullable
    public Text getDiscordName(PlayerEntity player) {
        return null;
    }

    @Override
    public void sendMessage(Text message) {
    }

    @Override
    public void sendMessage(GameProfile sender, String message) {
    }

    @Override
    @Nullable
    public Text formatAndSendMessage(GameProfile sender, String message) {
        return null;
    }

    @Override
    public void onPlayersChanged() {
    }

    @Override
    public void onPlayerConnected(GameProfile profile) {
    }

    @Override
    public boolean isChatHidden(PlayerEntity player) {
        return false;
    }
}

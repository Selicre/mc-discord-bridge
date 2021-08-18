package selic.re.discordbridge;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import javax.annotation.Nullable;
import java.util.List;

public interface DiscordBot {
    static DiscordBot instance() {
        DiscordBot bot = DiscordBotImpl.getCurrentInstance();
        if (bot == null) {
            return UninitializedDiscordBot.INSTANCE;
        }
        return bot;
    }

    DiscordBotConfig getConfig();

    List<Member> getChannelMembers();

    @Nullable
    ServerPlayerEntity getPlayer(User user);

    @Nullable
    Text getDiscordName(PlayerEntity player);

    void sendMessage(Text message);

    void sendMessage(GameProfile sender, String message);

    @Nullable
    Text formatAndSendMessage(GameProfile sender, String message);


    void onPlayersChanged();

    void onPlayerConnected(GameProfile profile);

    boolean isChatHidden(PlayerEntity player);
}

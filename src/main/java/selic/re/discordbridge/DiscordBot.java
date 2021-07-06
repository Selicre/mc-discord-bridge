package selic.re.discordbridge;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.util.UUID;

public class DiscordBot extends ListenerAdapter {
    static DiscordBot INSTANCE;

    private final WebhookClient webhook;
    MinecraftServer server;
    Config config;

    public static void init(Config config, MinecraftServer server) throws LoginException {
        INSTANCE = new DiscordBot(config, server);
    }

    @Nullable
    public static DiscordBot getInstance() {
        return INSTANCE;
    }
    DiscordBot(Config config, MinecraftServer server) throws LoginException {
        this.server = server;
        this.config = config;
        JDABuilder.createLight(config.token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
            .addEventListeners(this)
            .build();
        this.webhook = new WebhookClientBuilder(config.webhook_url).build();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.getChannel().getIdLong() == config.channel_id && !event.getAuthor().isBot()) {
            Message msg = event.getMessage();
            StringBuilder sb = new StringBuilder();
            sb.append("<@");
            sb.append(msg.getAuthor().getName());
            if (msg.getReferencedMessage() != null) {
                sb.append(" ⏶");
            }
            sb.append("> ");
            sb.append(msg.getContentStripped());

            if (!msg.getAttachments().isEmpty()) {
                if (msg.getContentStripped().length() != 0) {
                    sb.append(" ");
                }
                sb.append("[attachment]");
            }
            Text text = new LiteralText(sb.toString());
            this.broadcastNoMirror(server.getPlayerManager(), text);
        }
    }
    // This method is a reimplementation of broadcastChatMessage that will not mirror to discord.
    private void broadcastNoMirror(PlayerManager pm, Text message) {
        MessageType type = MessageType.CHAT;
        UUID sender = new UUID(0, 0);

        pm.getServer().sendSystemMessage(message, sender);

        for (ServerPlayerEntity serverPlayerEntity : pm.getPlayerList()) {
            serverPlayerEntity.sendMessage(message, type, sender);
        }
    }

    public void sendChatMessage(GameProfile player, String msg) {
        webhook.send(startWebhook(player).setContent(msg).build());
    }

    public void sendSystemMessage(String string) {
        webhook.send(new WebhookMessageBuilder().setContent(string).build());
    }

    protected WebhookMessageBuilder startWebhook(GameProfile player) {
        String avatar = "https://crafatar.com/renders/head/" + player.getId().toString() + "?overlay";

        return new WebhookMessageBuilder().setUsername(player.getName()).setAvatarUrl(avatar);
    }
}

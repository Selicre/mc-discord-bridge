package selic.re.discordbridge;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.util.UUID;

import static selic.re.discordbridge.DiscordFormattingConverter.discordUserToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.discordMessageToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.minecraftToDiscord;

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
        JDABuilder.create(config.token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
            .addEventListeners(this)
            .build();
        this.webhook = new WebhookClientBuilder(config.webhook_url).build();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.getChannel().getIdLong() == config.channel_id && !event.getAuthor().isBot()) {
            Message msg = event.getMessage();
            LiteralText root = new LiteralText("");

            root.append("<");
            root.append(discordUserToMinecraft(msg.getAuthor(), msg.getGuild()));

            if (msg.getReferencedMessage() != null) {
                root.append(" -> ");
                root.append(discordUserToMinecraft(msg.getReferencedMessage().getAuthor(), msg.getGuild()));
            }

            root.append(">");

            if (!msg.getContentRaw().isEmpty()) {
                root.append(" ");
                root.append(discordMessageToMinecraft(msg));
            }

            if (!msg.getAttachments().isEmpty()) {
                for (Message.Attachment attachment : msg.getAttachments()) {
                    root.append(" [");
                    LiteralText text;
                    if (attachment.isImage()) {
                        text = new LiteralText("image");
                    } else if (attachment.isVideo()) {
                        text = new LiteralText("video");
                    } else {
                        text = new LiteralText("attachment");
                    }
                    ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, attachment.getUrl());
                    HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(attachment.getFileName() + "\n" + (attachment.getSize() / 1000) + " kb"));
                    text.setStyle(Style.EMPTY.withClickEvent(click).withHoverEvent(hover));
                    root.append(text);
                    root.append("]");
                }
            }
            this.broadcastNoMirror(server.getPlayerManager(), root);
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

    public void sendEmoteMessage(GameProfile player, String msg) {
        webhook.send(startWebhook(player).setContent("*" + msg + "*").build());
    }

    public void sendSystemMessage(Text text) {
        webhook.send(new WebhookMessageBuilder().setContent(minecraftToDiscord(text)).build());
    }

    protected WebhookMessageBuilder startWebhook(GameProfile player) {
        String avatar = "https://crafatar.com/renders/head/" + player.getId().toString() + "?overlay";

        return new WebhookMessageBuilder().setUsername(player.getName()).setAvatarUrl(avatar);
    }
}

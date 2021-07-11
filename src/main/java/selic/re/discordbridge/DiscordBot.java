package selic.re.discordbridge;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static selic.re.discordbridge.DiscordFormattingConverter.discordChannelToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.discordMessageToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.discordUserToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.minecraftToDiscord;

public class DiscordBot extends ListenerAdapter {
    // Discord... please :(
    private static final TemporalAmount TIME_BETWEEN_TOPIC_UPDATES = Duration.ofMinutes(10);

    static DiscordBot INSTANCE;

    private final WebhookClient webhook;
    private final JDA discord;
    private final Timer updateTimer = new Timer();
    private Instant nextTopicUpdateTime = Instant.now();
    private boolean topicNeedsUpdating = true;
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
        this.discord = JDABuilder.create(config.token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_VOICE_STATES)
            .addEventListeners(this)
            .build();
        this.webhook = new WebhookClientBuilder(config.webhook_url).build();

        this.updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                server.execute(() -> {
                    if (topicNeedsUpdating && Instant.now().isAfter(nextTopicUpdateTime)) {
                        DiscordBot.this.updateChannels();
                    }
                });
            }
        }, 30000, 30000);
    }

    public void onGuildVoiceUpdate(@Nonnull GuildVoiceUpdateEvent event) {
        VoiceChannel joined = event.getChannelJoined();
        VoiceChannel left = event.getChannelLeft();

        if (joined != null && joined.getIdLong() == config.voice_channel_id) {
            LiteralText root = new LiteralText("");
            root.append(discordUserToMinecraft(event.getMember().getUser(), event.getGuild()));
            root.append(" has joined ");
            root.append(discordChannelToMinecraft(joined));
            root.append(" (" + joined.getMembers().size() + " users now in VC)");
            broadcastNoMirror(root);
        }

        if (left != null && left.getIdLong() == config.voice_channel_id) {
            LiteralText root = new LiteralText("");
            root.append(discordUserToMinecraft(event.getMember().getUser(), event.getGuild()));
            root.append(" has left ");
            root.append(discordChannelToMinecraft(left));
            root.append(" (" + left.getMembers().size() + " users now in VC)");
            broadcastNoMirror(root);
        }
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
            this.broadcastNoMirror(root);
        }
    }

    // This method is a reimplementation of broadcastChatMessage that will not mirror to discord.
    private void broadcastNoMirror(Text message) {
        if (!server.isOnThread()) {
            server.execute(() -> this.broadcastNoMirror(message));
            return;
        }
        MessageType type = MessageType.CHAT;
        UUID sender = new UUID(0, 0);

        server.sendSystemMessage(message, sender);

        for (ServerPlayerEntity serverPlayerEntity : server.getPlayerManager().getPlayerList()) {
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

    private void updateChannels() {
        nextTopicUpdateTime = Instant.now().plus(TIME_BETWEEN_TOPIC_UPDATES);
        topicNeedsUpdating = false;

        TextChannel chatChannel = discord.getTextChannelById(config.channel_id);
        if (chatChannel != null) {
            if (server.getPlayerNames().length == 0) {
                chatChannel.getManager().setTopic("Online!").queue();
            } else {
                StringBuilder topic = new StringBuilder();
                for (String name : server.getPlayerNames()) {
                    if (!topic.isEmpty()) {
                        topic.append(", ");
                    }
                    topic.append(name);
                }
                chatChannel.getManager().setTopic("Online: " + topic).queue();
            }
        }

        GuildChannel renameChannel = discord.getGuildChannelById(config.rename_channel_id);
        if (renameChannel != null) {
            renameChannel.getManager().setName(String.format(config.rename_channel_format, server.getCurrentPlayerCount())).queue();
        }
    }

    public void onPlayersChanged() {
        if (Instant.now().isBefore(nextTopicUpdateTime)) {
            // We're going to be rate limited, so let the timer adjust it when we're free.
            topicNeedsUpdating = true;
            return;
        }

        // We haven't updated the topic in a while, so let's do it immediately.
        updateChannels();
    }
}

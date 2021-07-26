package selic.re.discordbridge;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
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
import net.minecraft.text.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static selic.re.discordbridge.DiscordFormattingConverter.*;

public class DiscordBot extends ListenerAdapter {
    // Discord... please :(
    private static final TemporalAmount TIME_BETWEEN_TOPIC_UPDATES = Duration.ofMinutes(10);

    // Unlike %.2f, a decimal format will omit redundant trailing zeros, e.g 10.00 MB -> 10 MB
    private static final DecimalFormat FILE_SIZE_FORMAT = new DecimalFormat("#.##");

    // Decimal conversions rather than binary
    private static final int KB = 1000;
    private static final int MB = KB * KB;

    static DiscordBot INSTANCE;

    private final WebhookClient webhook;
    private final JDA discord;
    private final Timer updateTimer = new Timer();
    private Instant nextTopicUpdateTime = Instant.now();
    private boolean topicNeedsUpdating = true;
    MinecraftServer server;
    DiscordBotConfig config;

    public static void init(DiscordBotConfig config, MinecraftServer server) throws LoginException {
        INSTANCE = new DiscordBot(config, server);
    }

    @Nonnull
    public static Optional<DiscordBot> getInstance() {
        return Optional.of(INSTANCE);
    }

    DiscordBot(DiscordBotConfig config, MinecraftServer server) throws LoginException {
        this.server = server;
        this.config = config;
        this.discord = JDABuilder.create(config.token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_VOICE_STATES)
            .addEventListeners(this)
            .build();
        this.webhook = new WebhookClientBuilder(config.webhookUrl).build();

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
        final var joined = event.getChannelJoined();

        if (joined != null && joined.getIdLong() == config.voiceChannelId) {
            broadcastUpdate(joined, event.getMember(), "joined");
        }

        final var left = event.getChannelLeft();

        if (left != null && left.getIdLong() == config.voiceChannelId) {
            broadcastUpdate(left, event.getMember(), "left");
        }
    }

    private void broadcastUpdate(final VoiceChannel channel, final Member member, final String action) {
        broadcastNoMirror(new LiteralText("")
            .append(discordUserToMinecraft(member.getUser(), member.getGuild()))
            .append(" " + action + " ")
            .append(discordChannelToMinecraft(channel))
            .append(" (" + channel.getMembers().size() + " users connected)"));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.getChannel().getIdLong() == config.channelId && !event.getAuthor().isBot()) {
            Message msg = event.getMessage();
            LiteralText root = new LiteralText("");

            root.append("<");
            root.append(discordUserToMinecraft(msg.getAuthor(), msg.getGuild()));

            if (msg.getReferencedMessage() != null) {
                MutableText arrow = new LiteralText(" -> ");
                if (!msg.getReferencedMessage().getContentRaw().isBlank()) {
                    arrow.setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, discordMessageToMinecraft(msg))));
                }
                root.append(arrow).append(discordUserToMinecraft(msg.getReferencedMessage().getAuthor(), msg.getGuild()));
            }

            root.append(">");

            if (!msg.getContentRaw().isEmpty()) {
                root.append(" ");
                root.append(discordMessageToMinecraft(msg));
            }

            if (!msg.getAttachments().isEmpty()) {
                for (Message.Attachment attachment : msg.getAttachments()) {
                    root.append(" [");
                    LiteralText fileType = null;
                    if (attachment.isImage()) {
                        fileType = new LiteralText("image");
                    } else if (attachment.isVideo()) {
                        fileType = new LiteralText("video");
                    } else {
                        @Nullable String mediaType = attachment.getContentType();
                        if (mediaType != null) {
                            if (mediaType.startsWith("text")) {
                                fileType = new LiteralText("text");
                            } else if (mediaType.startsWith("audio")) {
                                fileType = new LiteralText("audio");
                            }
                        }
                        if (fileType == null)  {
                            fileType = new LiteralText("attachment");
                        }
                    }
                    ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, attachment.getUrl());
                    HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText("")
                        .append(attachment.getFileName())
                        .append("\n")
                        .append(new LiteralText(readableFileSize(attachment.getSize()))));
                    fileType.setStyle(Style.EMPTY.withClickEvent(click).withHoverEvent(hover));
                    root.append(fileType);
                    root.append("]");
                }
            }
            this.broadcastNoMirror(root);
        }
    }

    private static String readableFileSize(final int sizeBytes) {
        if ((sizeBytes / MB) > 0) { // Size is more than or equal to 1 MB
            return "%s MB".formatted(FILE_SIZE_FORMAT.format(sizeBytes / (double) MB));
        }

        if ((sizeBytes / KB) > 0) { // Size is more than or equal to 1 KB
            return "%s KB".formatted(FILE_SIZE_FORMAT.format(sizeBytes / (double) KB));
        }

        // Size is less than 1000 bytes
        return "%d bytes".formatted(sizeBytes);
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

        TextChannel chatChannel = discord.getTextChannelById(config.channelId);
        if (chatChannel != null) {
            String[] playerNames = server.getPlayerNames();
            if (playerNames.length == 0) {
                chatChannel.getManager().setTopic("Online!").queue();
            } else {
                Arrays.sort(playerNames, String.CASE_INSENSITIVE_ORDER);
                chatChannel.getManager().setTopic("Online: " + String.join(", ", playerNames)).queue();
            }
        }

        GuildChannel renameChannel = discord.getGuildChannelById(config.renameChannelId);
        if (renameChannel != null) {
            renameChannel.getManager().setName(String.format(config.renameChannelFormat, server.getCurrentPlayerCount())).queue();
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

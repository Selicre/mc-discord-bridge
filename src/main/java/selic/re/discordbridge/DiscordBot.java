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
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static selic.re.discordbridge.DiscordFormattingConverter.discordChannelToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.discordMessageToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.discordUserToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.minecraftToDiscord;
import static selic.re.discordbridge.GameMessageConverter.convertGameMessage;

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
        this.discord = JDABuilder.create(config.token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_EMOJIS)
            .addEventListeners(this)
            .build();
        if (config.webhookUrl != null) {
            this.webhook = new WebhookClientBuilder(config.webhookUrl).setWait(false).build();
        } else {
            this.webhook = null;
        }

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

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        VoiceChannel joined = event.getChannelJoined();
        VoiceChannel left = event.getChannelLeft();
        boolean broadcastJoined = config.hasVoiceChannel(joined);
        boolean broadcastLeft = config.hasVoiceChannel(left);

        if (broadcastLeft && broadcastJoined) {
            broadcastUpdate(joined, event.getMember(), "moved to");
        } else if (broadcastJoined) {
            broadcastUpdate(joined, event.getMember(), "joined");
        } else if (broadcastLeft) {
            broadcastUpdate(left, event.getMember(), "left");
        }
    }

    private void broadcastUpdate(VoiceChannel channel, Member member, String action) {
        broadcastNoMirror(new LiteralText("")
            .append(discordUserToMinecraft(member.getUser(), member.getGuild(), false))
            .append(" " + action + " ")
            .append(discordChannelToMinecraft(channel))
            .append(" (" + channel.getMembers().size() + " users connected)"));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (config.allowsMessagesFrom(event.getChannel(), event.getAuthor())) {
            Message msg = event.getMessage();
            LiteralText root = new LiteralText("");

            root.append("<");
            root.append(discordUserToMinecraft(msg.getAuthor(), msg.getGuild(), false));

            Message refMsg = msg.getReferencedMessage();
            if (refMsg != null) {
                MutableText arrow = new LiteralText(" -> ");
                if (!refMsg.getContentRaw().isBlank()) {
                    arrow.setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, discordMessageToMinecraft(refMsg))));
                }
                root.append(arrow).append(discordUserToMinecraft(refMsg.getAuthor(), msg.getGuild(), false));
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

    private static String readableFileSize(int sizeBytes) {
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

    public Text broadcastAndReplaceChatMessage(GameProfile author, String message) {
        TextChannel chatChannel = discord.getTextChannelById(config.channelId);
        if (chatChannel == null) {
            return new LiteralText(message);
        }

        GameMessageConverter.Results results = convertGameMessage(message, chatChannel, discord);
        sendChatMessage(author, results.discordOutput);
        return results.gameOutput;
    }

    public void sendChatMessage(GameProfile player, String msg) {
        if (webhook != null) {
            webhook.send(startWebhook(player).setContent(msg).build());
        } else {
            TextChannel chatChannel = discord.getTextChannelById(config.channelId);
            if (chatChannel != null) {
                chatChannel.sendMessage("<" + player.getName() + "> " + msg).queue();
            }
        }
    }

    public void sendSystemMessage(Text text) {
        if (webhook != null) {
            webhook.send(new WebhookMessageBuilder().setContent(minecraftToDiscord(text)).build());
        } else {
            TextChannel chatChannel = discord.getTextChannelById(config.channelId);
            if (chatChannel != null) {
                chatChannel.sendMessage(minecraftToDiscord(text)).queue();
            }
        }
    }

    protected WebhookMessageBuilder startWebhook(GameProfile player) {
        String avatar = config.getAvatarUrl(player.getId());

        return new WebhookMessageBuilder().setUsername(player.getName()).setAvatarUrl(avatar);
    }

    private void updateChannels() {
        nextTopicUpdateTime = Instant.now().plus(TIME_BETWEEN_TOPIC_UPDATES);
        topicNeedsUpdating = false;

        TextChannel chatChannel = discord.getTextChannelById(config.channelId);

        if (chatChannel != null && config.updateTopic) {
            String topic = config.getTopicName(server.getPlayerNames());
            chatChannel.getManager().setTopic(topic).queue(
                null,
                new ErrorHandler().handle(ErrorResponse.MISSING_PERMISSIONS, c -> {
                    LogManager.getLogger().warn("Missing permissions to change channel info!");
                    // Don't try again
                    config.updateTopic = false;
                })
            );
        }

        GuildChannel renameChannel = discord.getGuildChannelById(config.renameChannelId);
        int playerCount = server.getCurrentPlayerCount();

        if (renameChannel != null) {
            renameChannel.getManager().setName(config.getRenameChannelName(playerCount)).queue();
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

    public List<Member> getChannelMembers() {
        TextChannel chatChannel = discord.getTextChannelById(config.channelId);
        if (chatChannel != null) {
            return chatChannel.getMembers();
        }

        return new ArrayList<>();
    }
}

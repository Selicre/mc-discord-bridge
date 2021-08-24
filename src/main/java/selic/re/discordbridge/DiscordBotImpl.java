package selic.re.discordbridge;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceStreamEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateActivitiesEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static selic.re.discordbridge.DiscordFormattingConverter.discordChannelToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.discordMessageToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.discordUserToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.minecraftToDiscord;
import static selic.re.discordbridge.GameMessageConverter.convertGameMessage;

class DiscordBotImpl extends ListenerAdapter implements DiscordBot {
    private static final Logger LOGGER = LogManager.getLogger();

    // Discord... please :(
    private static final TemporalAmount TIME_BETWEEN_TOPIC_UPDATES = Duration.ofMinutes(10);

    // Unlike %.2f, a decimal format will omit redundant trailing zeros, e.g 10.00 MB -> 10 MB
    private static final DecimalFormat FILE_SIZE_FORMAT = new DecimalFormat("#.##");


    // Decimal conversions rather than binary
    private static final int KB = 1000;
    private static final int MB = KB * KB;

    static DiscordBotImpl INSTANCE;

    private final WebhookClient webhook;
    private final JDA discord;
    private final DiscordPlayerLookup playerLookup;
    private final Timer updateTimer = new Timer();
    private final Set<UUID> livePlayers = new HashSet<>();
    private Instant nextTopicUpdateTime = Instant.now();
    private boolean topicNeedsUpdating = true;
    private Guild guild;
    MinecraftServer server;
    DiscordBotConfig config;

    static void init(DiscordBotConfig config, MinecraftServer server, DiscordPlayerLookup playerLookup) throws LoginException {
        INSTANCE = new DiscordBotImpl(config, server, playerLookup);
    }

    @Nullable
    static DiscordBot getCurrentInstance() {
        return INSTANCE;
    }

    DiscordBotImpl(DiscordBotConfig config, MinecraftServer server, DiscordPlayerLookup playerLookup) throws LoginException {
        this.server = server;
        this.config = config;
        this.discord = JDABuilder.create(config.token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_EMOJIS, GatewayIntent.GUILD_PRESENCES)
            .addEventListeners(this)
            .build();
        this.playerLookup = playerLookup;
        if (!config.webhookUrl.isEmpty()) {
            this.webhook = new WebhookClientBuilder(config.webhookUrl).setWait(false).build();
        } else {
            this.webhook = null;
        }

        this.updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                server.execute(() -> {
                    if (topicNeedsUpdating && Instant.now().isAfter(nextTopicUpdateTime)) {
                        DiscordBotImpl.this.updateChannels();
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
            broadcastVoiceUpdate(joined, event.getMember(), "moved to");
        } else if (broadcastJoined) {
            broadcastVoiceUpdate(joined, event.getMember(), "joined");
        } else if (broadcastLeft) {
            broadcastVoiceUpdate(left, event.getMember(), "left");
        }
    }

    @Override
    public void onUserUpdateActivities(@Nonnull UserUpdateActivitiesEvent event) {
        String streamLink = null;

        for (Activity activity : event.getMember().getActivities()) {
            if (activity.getType() == Activity.ActivityType.STREAMING && Activity.isValidStreamingUrl(activity.getUrl())) {
                streamLink = activity.getUrl();
                break;
            }
        }

        UUID uuid = playerLookup.getPlayerProfileId(event.getUser());
        if (uuid != null) {
            boolean wasLive = livePlayers.contains(uuid);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (streamLink != null && !wasLive) {
                livePlayers.add(uuid);

                if (player != null) {
                    MutableText message = new LiteralText("")
                        .append(discordUserToMinecraft(event.getUser(), event.getGuild(), false))
                        .append(" is now streaming to ")
                        .append(new LiteralText(streamLink).setStyle(Style.EMPTY.withUnderline(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, streamLink))));
                    if (config.hideChatFromStreamers) {
                        message.append(" - Chat has been disabled.");
                    }
                    broadcastNoMirror(message);
                }
            } else if (streamLink == null && wasLive) {
                livePlayers.remove(uuid);

                if (player != null) {
                    broadcastNoMirror(new LiteralText("")
                        .append(discordUserToMinecraft(event.getUser(), event.getGuild(), false))
                        .append(" is no longer streaming. Chat has been reenabled."));
                }
            }
        }
    }

    @Override
    public void onGuildVoiceStream(@NotNull GuildVoiceStreamEvent event) {
        announcePossibleStream(event.getVoiceState());
    }

    private void announcePossibleStream(@Nullable GuildVoiceState voice) {
        if (voice != null && voice.getChannel() != null && voice.isStream() && isOnline(voice.getMember().getUser())) {
            MutableText text = new LiteralText("")
                .append(discordUserToMinecraft(voice.getMember().getUser(), voice.getGuild(), false))
                .append(" is now streaming to ")
                .append(discordChannelToMinecraft(voice.getChannel()));
            broadcastNoMirror(text);
        }
    }

    private void broadcastVoiceUpdate(VoiceChannel channel, Member member, String action) {
        broadcastNoMirror(new LiteralText("")
            .append(discordUserToMinecraft(member.getUser(), member.getGuild(), false))
            .append(" " + action + " ")
            .append(discordChannelToMinecraft(channel))
            .append(" (" + channel.getMembers().size() + " users connected)"));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (config.allowsMessagesFrom(event.getChannel(), event.getAuthor())) {
            if (event.getMessage().getContentRaw().startsWith("!")) {
                handleDiscordCommand(event.getMessage());
                return;
            }
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

    private void handleDiscordCommand(Message message) {
        if (message.getMember() != null && message.getContentRaw().startsWith("!mcname")) {
            String[] args = message.getContentRaw().split(" ");
            if (args.length == 2) {
                Optional<GameProfile> lookupResult = server.getUserCache().findByName(args[1]);
                if (lookupResult.isEmpty()) {
                    message.reply("I couldn't find a Minecraft user called `" + args[1] + "` - are you sure that it's correct?").queue();
                } else {
                    GameProfile profile = lookupResult.get();
                    UUID old = playerLookup.updatePlayerProfile(message.getMember(), profile);
                    server.getPlayerManager().getWhitelist().add(new WhitelistEntry(profile));

                    if (old == null) {
                        message.reply("Welcome, " + profile.getName() + "! You're all set up and have been added to the whitelist.").queue();
                    } else if (old.equals(profile.getId())) {
                        message.reply("I already know who you are <3").queue();
                    } else {
                        message.reply("I will remember that your new player profile is " + profile.getName() + ", but your old account has been forgotten.").queue();
                        server.getPlayerManager().getWhitelist().remove(new GameProfile(old, null));
                    }

                    try {
                        server.getPlayerManager().getWhitelist().save();
                    } catch (IOException ex) {
                        LOGGER.warn("Couldn't save whitelist", ex);
                    }
                }
            } else {
                message.reply("Usage: !mcname MyMinecraftUsername").queue();
            }
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

    @Override
    public Text formatAndSendMessage(GameProfile author, String message) {
        TextChannel chatChannel = discord.getTextChannelById(config.channelId);
        if (chatChannel == null) {
            return new LiteralText(message);
        }

        GameMessageConverter.Results results = convertGameMessage(message, chatChannel, discord);
        sendMessage(author, results.discordOutput);
        return results.gameOutput;
    }

    @Override
    public void sendMessage(GameProfile player, String msg) {
        Member member = playerLookup.getDiscordMember(getGuild(), player);
        String name = member != null ? member.getEffectiveName() : player.getName();

        if (webhook != null) {
            webhook.send(startWebhook(player.getId(), name).setContent(msg).build());
        } else {
            TextChannel chatChannel = discord.getTextChannelById(config.channelId);
            if (chatChannel != null) {
                chatChannel.sendMessage("<" + name + "> " + msg).queue();
            }
        }
    }

    @Override
    public void sendMessage(Text text) {
        if (webhook != null) {
            webhook.send(new WebhookMessageBuilder().setContent(minecraftToDiscord(text)).build());
        } else {
            TextChannel chatChannel = discord.getTextChannelById(config.channelId);
            if (chatChannel != null) {
                chatChannel.sendMessage(minecraftToDiscord(text)).queue();
            }
        }
    }

    @Override
    public Text getDiscordName(PlayerEntity player) {
        Member member = playerLookup.getDiscordMember(getGuild(), new GameProfile(player.getUuid(), null));
        if (member != null) {
            return DiscordFormattingConverter.discordUserToMinecraft(member.getUser(), getGuild(), false);
        }
        return null;
    }

    protected WebhookMessageBuilder startWebhook(UUID id, String name) {
        String avatar = config.getAvatarUrl(id);

        return new WebhookMessageBuilder().setUsername(name).setAvatarUrl(avatar);
    }

    private void updateChannels() {
        nextTopicUpdateTime = Instant.now().plus(TIME_BETWEEN_TOPIC_UPDATES);
        topicNeedsUpdating = false;

        TextChannel chatChannel = discord.getTextChannelById(config.channelId);

        if (chatChannel != null && config.updateTopic) {
            String topic = config.getTopicName(getPlayerNames());
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

    private String[] getPlayerNames() {
        List<String> result = new ArrayList<>();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Member member = playerLookup.getDiscordMember(getGuild(), player.getGameProfile());
            if (member != null) {
                result.add(member.getEffectiveName());
            } else {
                result.add(player.getGameProfile().getName());
            }
        }

        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result.toArray(new String[0]);
    }

    @Override
    public void onPlayersChanged() {
        if (Instant.now().isBefore(nextTopicUpdateTime)) {
            // We're going to be rate limited, so let the timer adjust it when we're free.
            topicNeedsUpdating = true;
            return;
        }

        // We haven't updated the topic in a while, so let's do it immediately.
        updateChannels();
    }

    @Override
    public List<Member> getChannelMembers() {
        TextChannel chatChannel = discord.getTextChannelById(config.channelId);
        if (chatChannel != null) {
            return chatChannel.getMembers();
        }

        return new ArrayList<>();
    }

    @Override
    public DiscordBotConfig getConfig() {
        return this.config;
    }

    public boolean isOnline(User discordUser) {
        return getPlayer(discordUser) != null;
    }

    @Override
    @Nullable
    public ServerPlayerEntity getPlayer(User discordUser) {
        UUID id = playerLookup.getPlayerProfileId(discordUser);
        if (id == null) {
            return null;
        }
        return server.getPlayerManager().getPlayer(id);
    }

    @Override
    public void onPlayerConnected(GameProfile profile) {
        Guild guild = getGuild();
        if (guild != null) {
            Member member = playerLookup.getDiscordMember(guild, profile);
            if (member != null) {
                announcePossibleStream(member.getVoiceState());

                if (config.hideChatFromStreamers && livePlayers.contains(profile.getId())) {
                    broadcastNoMirror(new LiteralText("")
                        .append(discordUserToMinecraft(member.getUser(), guild, false))
                        .append(" is streaming. Chat has been disabled."));
                }
            }
        }
    }

    public DiscordPlayerLookup getLookup() {
        return playerLookup;
    }

    public Guild getGuild() {
        if (guild == null) {
            TextChannel chatChannel = discord.getTextChannelById(config.channelId);
            if (chatChannel != null) {
                this.guild = chatChannel.getGuild();
            }
        }
        return guild;
    }

    @Override
    public boolean isChatHidden(PlayerEntity player) {
        return config.hideChatFromStreamers && livePlayers.contains(player.getUuid());
    }
}

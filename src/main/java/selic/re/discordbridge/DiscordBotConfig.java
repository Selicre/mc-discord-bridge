package selic.re.discordbridge;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

public class DiscordBotConfig {
    private static final Gson GSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setLenient()
        .setPrettyPrinting()
        .create();

    public String token = "";
    public String webhookUrl = null;
    public long channelId = 0;
    public long renameChannelId = 0;
    @JsonAdapter(LongSetDeserializer.class)
    public LongSet voiceChannels = LongSets.EMPTY_SET;
    public boolean updateTopic = false;
    public boolean listDiscordUsers = false;
    @JsonAdapter(LongSetDeserializer.class)
    public LongSet botWhitelist = LongSets.EMPTY_SET;
    public String renameChannelFormat = "%d player(s) online";
    public String noPlayersTopicFormat = "Online!";
    public String withPlayersTopicFormat = "Online: %s";
    public String avatarUrl = "https://crafatar.com/renders/head/%s?overlay";
    public boolean hideChatFromStreamers = true;

    public DiscordBotConfig() {}

    @Nullable
    public static DiscordBotConfig fromFile(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, DiscordBotConfig.class);
        } catch (NoSuchFileException e) {
            // Generate a config stub to provide all keys to the end user
            DiscordBotConfig config = new DiscordBotConfig();
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                GSON.toJson(config, writer);
            }
            return null;
        }
    }

    public String getRenameChannelName(int playerCount) {
        return String.format(Locale.ROOT, renameChannelFormat, playerCount);
    }

    public String getTopicName(String[] players) {
        String topic = noPlayersTopicFormat;
        if (players.length > 0) {
            topic = String.format(Locale.ROOT, withPlayersTopicFormat, String.join(", ", players));
        }
        return topic;
    }

    public String getAvatarUrl(UUID uuid) {
        return String.format(Locale.ROOT, avatarUrl, uuid.toString());
    }

    public boolean hasVoiceChannel(@Nullable VoiceChannel channel) {
        return channel != null && voiceChannels.contains(channel.getIdLong());
    }

    public boolean allowsMessagesFrom(MessageChannel channel, User user) {
        return channelId == channel.getIdLong() && (!user.isBot() || botWhitelist.contains(user.getIdLong()));
    }

    private static final class LongSetDeserializer implements JsonDeserializer<LongSet> {
        @Override
        public LongSet deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) { // Backwards compatibility
                return LongSets.singleton(context.<Long>deserialize(json, long.class));
            }
            return new LongOpenHashSet(context.<long[]>deserialize(json, long[].class));
        }
    }
}

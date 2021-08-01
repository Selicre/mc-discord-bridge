package selic.re.discordbridge;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public class DiscordBotConfig {
    private static final Gson GSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setLenient()
        .setPrettyPrinting()
        .create();
    private static final Logger LOGGER = LogManager.getLogger();

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

    public static int performInit(MinecraftServer server) {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("discord-bridge.json");
        try {
            @Nullable DiscordBotConfig config = fromFile(configFile);
            if (config != null) {
                DiscordBot.init(config, server);
                return 1;
            } else {
                LOGGER.error("A valid token is required in {}", configFile);
                return 0;
            }
        } catch (LoginException e) {
            LOGGER.error("A valid token is required in {}", configFile, e);
            return 1;
        } catch (IOException e) {
            LOGGER.error("Failed to read config {}", configFile, e);
            return 1;
        }
    }

    public String getRenameChannelName(int playerCount) {
        return String.format(Locale.ROOT, renameChannelFormat, playerCount);
    }

    public String getTopicName(String[] players) {
        String topic = noPlayersTopicFormat;
        if (players.length > 0) {
            Arrays.sort(players, String.CASE_INSENSITIVE_ORDER);
            topic = String.format(Locale.ROOT, withPlayersTopicFormat, String.join(",", players));
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

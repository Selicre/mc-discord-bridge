package selic.re.discordbridge;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nullable;
import java.io.*;
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
    public long voiceChannelId = 0;
    public boolean updateTopic = false;
    public ArrayList<Long> botWhitelist = new ArrayList<>();
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
}

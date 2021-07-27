package selic.re.discordbridge;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;

@SuppressWarnings("ClassCanBeRecord") // Gson
public class DiscordBotConfig {
    private static final Gson GSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setLenient()
        .create();

    public final String token;
    public final long channelId;
    public final long renameChannelId;
    public final String renameChannelFormat;
    public final String webhookUrl;
    public final long voiceChannelId;

    public DiscordBotConfig(
        String token,
        long channelId,
        long renameChannelId,
        String renameChannelFormat,
        String webhookUrl,
        long voiceChannelId
    ) {
        this.token = token;
        this.channelId = channelId;
        this.renameChannelId = renameChannelId;
        this.renameChannelFormat = renameChannelFormat;
        this.webhookUrl = webhookUrl;
        this.voiceChannelId = voiceChannelId;
    }

    @Nullable
    public static DiscordBotConfig fromFile(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, DiscordBotConfig.class);
        } catch (NoSuchFileException e) {
            // Generate a config stub to provide all keys to the end user
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                GSON.toJson(new DiscordBotConfig("", 0L, 0L, "%d player(s) online", "", 0L), writer);
            }
            return null;
        }
    }

    public String getRenameChannelName(int playerCount) {
        return String.format(Locale.ROOT, renameChannelFormat, playerCount);
    }
}

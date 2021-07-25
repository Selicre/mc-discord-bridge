package selic.re.discordbridge;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@SuppressWarnings("ClassCanBeRecord") // Gson
public final class DiscordBotConfig {
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
        final String token,
        final long channelId,
        final long renameChannelId,
        final String renameChannelFormat,
        final String webhookUrl,
        final long voiceChannelId
    ) {
        this.token = token;
        this.channelId = channelId;
        this.renameChannelId = renameChannelId;
        this.renameChannelFormat = renameChannelFormat;
        this.webhookUrl = webhookUrl;
        this.voiceChannelId = voiceChannelId;
    }

    @Nullable
    public static DiscordBotConfig fromFile(final Path file) throws IOException {
        try (final var reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, DiscordBotConfig.class);
        } catch (final NoSuchFileException e) {
            // Generate a config stub to provide all keys to the end user
            try (final var writer = Files.newBufferedWriter(file)) {
                GSON.toJson(new DiscordBotConfig("", 0L, 0L, "%d player(s) online", "", 0L), writer);
            }
            return null;
        }
    }
}

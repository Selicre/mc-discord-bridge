package selic.re.discordbridge;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    String token;
    long channel_id;
    long rename_channel_id;
    String rename_channel_format;
    String webhook_url;
    long voice_channel_id;

    public static Config reloadFromFile(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
          return new Gson().fromJson(reader, Config.class);
        }
    }
}

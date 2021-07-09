package selic.re.discordbridge;

import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.FileReader;

public class Config {
    String token;
    long channel_id;
    long rename_channel_id;
    String rename_channel_format;
    String webhook_url;

    public static Config reloadFromFile(String filename) throws FileNotFoundException {
        FileReader fr = new FileReader(filename);
        return new Gson().fromJson(fr, Config.class);
    }
}

package selic.re.discordbridge;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class DiscordPlayerLookup {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final TypeToken<Long2ObjectMap<PlayerInfo>> LOOKUP_TYPE_TOKEN = new TypeToken<>() {};
    private static final Gson GSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(LOOKUP_TYPE_TOKEN.getType(), new Long2ObjectMapSerializer())
        .setLenient()
        .setPrettyPrinting()
        .create();

    private final Path path;
    private final Long2ObjectMap<PlayerInfo> playersByDiscordId;
    private final Object2LongMap<UUID> discordsByPlayerId = new Object2LongOpenHashMap<>();

    protected DiscordPlayerLookup(Path path) {
        this(path, new Long2ObjectOpenHashMap<>());
    }

    protected DiscordPlayerLookup(Path path, Long2ObjectMap<PlayerInfo> playersByDiscordId) {
        this.path = path;
        this.playersByDiscordId = playersByDiscordId;

        recalculateCache();
    }

    private void recalculateCache() {
        discordsByPlayerId.clear();
        for (Long2ObjectMap.Entry<PlayerInfo> entry : playersByDiscordId.long2ObjectEntrySet()) {
            discordsByPlayerId.put(entry.getValue().profileId, entry.getLongKey());
        }
    }

    public static DiscordPlayerLookup fromFile(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return new DiscordPlayerLookup(path, GSON.fromJson(reader, LOOKUP_TYPE_TOKEN.getType()));
        } catch (NoSuchFileException e) {
            return new DiscordPlayerLookup(path);
        }
    }

    public Member getDiscordMember(Guild guild, GameProfile profile) {
        if (!discordsByPlayerId.containsKey(profile.getId())) {
            return null;
        }
        long id = discordsByPlayerId.getLong(profile.getId());
        return guild.getMemberById(id);
    }

    public UUID updatePlayerProfile(Member member, GameProfile profile) {
        UUID old = null;

        if (playersByDiscordId.containsKey(member.getIdLong())) {
            PlayerInfo playerInfo = playersByDiscordId.get(member.getIdLong());
            old = playerInfo.profileId;
            playerInfo.profileId = profile.getId();
        } else {
            PlayerInfo value = new PlayerInfo();
            value.profileId = profile.getId();
            playersByDiscordId.put(member.getIdLong(), value);
        }
        recalculateCache();
        save();

        return old;
    }

    private void save() {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            GSON.toJson(playersByDiscordId, writer);
            LOGGER.info("Updated discord<->player lookup map");
        } catch (IOException e) {
            LOGGER.error("Couldn't save discord<->player lookup map!", e);
        }
    }

    public UUID getPlayerProfileId(User user) {
        PlayerInfo playerInfo = playersByDiscordId.get(user.getIdLong());
        if (playerInfo == null) {
            return null;
        }
        return playerInfo.profileId;
    }

    public static class PlayerInfo {
        public UUID profileId;
        // public final String twitch;
    }

    private static final class Long2ObjectMapSerializer implements JsonDeserializer<Long2ObjectMap<PlayerInfo>>, JsonSerializer<Long2ObjectMap<PlayerInfo>> {
        @Override
        public Long2ObjectMap<PlayerInfo> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject object = json.getAsJsonObject();
            Long2ObjectMap<PlayerInfo> result = new Long2ObjectOpenHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                try {
                    long key = Long.parseLong(entry.getKey());
                    result.put(key, context.deserialize(entry.getValue(), PlayerInfo.class));
                } catch (NumberFormatException ex) {
                    LOGGER.warn(ex);
                }
            }
            return result;
        }

        @Override
        public JsonElement serialize(Long2ObjectMap<PlayerInfo> src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject result = new JsonObject();

            for (Long2ObjectMap.Entry<PlayerInfo> entry : src.long2ObjectEntrySet()) {
                result.add(String.valueOf(entry.getLongKey()), context.serialize(entry.getValue()));
            }

            return result;
        }
    }
}

package selic.re.discordbridge;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static selic.re.discordbridge.DiscordFormattingConverter.discordEmoteToMinecraft;
import static selic.re.discordbridge.DiscordFormattingConverter.discordUserToMinecraft;

public class GameMessageConverter {
    private static final Pattern DISCORD_MENTION_PATTERN = Pattern.compile("^.{2,32}?#\\d{4}");
    private static final Pattern DISCORD_EMOJI_PATTERN = Pattern.compile("^:([\\w_]{2,}):");
    private static final int DISCORD_USERNAME_MAX_LENGTH = 32;
    private static final int DISCORD_USERNAME_MIN_LENGTH = 2;


    private final String input;
    private final MutableText gameOutput;
    private boolean gameOutputDecorated = false;
    private final StringBuilder discordOutput;
    private final TextChannel channel;
    private final JDA discord;
    private int cursor;
    private final StringBuilder textBuffer = new StringBuilder();

    @Nullable
    private List<Member> mentionableMembers = null;

    protected GameMessageConverter(String input, TextChannel channel, JDA discord) {
        this.input = input;
        this.channel = channel;
        this.discord = discord;
        this.gameOutput = Text.empty();
        this.discordOutput = new StringBuilder();
    }

    public static Results convertGameMessage(Text message, TextChannel channel, JDA discord) {
        GameMessageConverter converter = new GameMessageConverter(message.getString(), channel, discord);
        converter.readToEnd();
        Text gameMessage = (converter.gameOutputDecorated ? converter.gameOutput : message);
        return new Results(gameMessage, converter.discordOutput.toString());
    }

    protected char read() {
        return input.charAt(cursor++);
    }

    protected boolean isEOF() {
        return cursor >= input.length();
    }

    protected boolean consume(String prefix) {
        if (cursor + prefix.length() <= input.length() && input.startsWith(prefix, cursor)) {
            cursor += prefix.length();
            return true;
        } else {
            return false;
        }
    }

    private void readToEnd() {
        while (!isEOF()) {
            if (tryUserMention()) continue;
            if (tryEmoteMention()) continue;
            textBuffer.append(read());
        }
        flushTextBuffer();
    }

    @Nullable
    private Member findMemberByName(String name) {
        if (mentionableMembers == null) {
            mentionableMembers = channel.getMembers().stream().filter(m -> !m.getUser().isBot()).collect(Collectors.toList());
        }
        for (Member member : mentionableMembers) {
            if (member.getUser().getName().equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    private boolean tryUserMention() {
        int reset = cursor;
        if (consume("@") && input.length() - cursor >= DISCORD_USERNAME_MIN_LENGTH) {
            Matcher matcher = DISCORD_MENTION_PATTERN.matcher(input.substring(cursor));
            if (matcher.find()) {
                Member member = channel.getGuild().getMemberByTag(matcher.group());
                if (member != null) {
                    cursor += matcher.end();
                    insertMention(member);
                    return true;
                }
            } else {
                for (int length = Math.min(DISCORD_USERNAME_MAX_LENGTH, input.length() - cursor); length >= DISCORD_USERNAME_MIN_LENGTH; length--) {
                    Member member = findMemberByName(input.substring(cursor, cursor + length));
                    if (member != null) {
                        cursor += length;
                        insertMention(member);
                        return true;
                    }
                }
            }
        }

        cursor = reset;
        return false;
    }

    private boolean tryEmoteMention() {
        Matcher matcher = DISCORD_EMOJI_PATTERN.matcher(input.substring(cursor));
        if (matcher.find()) {
            // Try to find something case sensitive before checking insensitive.
            List<Emote> emotes = discord.getEmotesByName(matcher.group(1), false);
            if (emotes.isEmpty()) {
                emotes = discord.getEmotesByName(matcher.group(1), true);
            }
            if (!emotes.isEmpty()) {
                cursor += matcher.end();
                insertEmote(emotes.get(0));
                return true;
            }
        }
        return false;
    }

    private void insertMention(Member member) {
        flushTextBuffer();
        discordOutput.append(member.getAsMention());
        gameOutput.append(discordUserToMinecraft(member.getUser(), member.getGuild(), true));
        gameOutputDecorated = true;
    }

    private void insertEmote(Emote emote) {
        flushTextBuffer();
        discordOutput.append(emote.getAsMention());
        gameOutput.append(discordEmoteToMinecraft(emote));
        gameOutputDecorated = true;
    }

    private void flushTextBuffer() {
        discordOutput.append(textBuffer);
        gameOutput.append(textBuffer.toString());
        textBuffer.setLength(0);
    }

    public static class Results {
        public final Text gameOutput;
        public final String discordOutput;

        protected Results(Text gameOutput, String discordOutput) {
            this.gameOutput = gameOutput;
            this.discordOutput = discordOutput;
        }
    }
}

package selic.re.discordbridge;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.minecraft.text.LiteralText;

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
    private final LiteralText gameOutput;
    private final StringBuilder discordOutput;
    private final TextChannel channel;
    private int cursor;
    private final StringBuilder textBuffer = new StringBuilder();

    @Nullable
    private List<Member> mentionableMembers = null;

    protected GameMessageConverter(String input, TextChannel channel) {
        this.input = input;
        this.channel = channel;
        this.gameOutput = new LiteralText("");
        this.discordOutput = new StringBuilder();
    }

    public static Results convertGameMessage(String input, TextChannel channel) {
        GameMessageConverter converter = new GameMessageConverter(input, channel);
        converter.readToEnd();
        return new Results(converter.gameOutput, converter.discordOutput.toString());
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
            List<Emote> emotes = channel.getGuild().getEmotesByName(matcher.group(1), false);
            if (emotes.isEmpty()) {
                emotes = channel.getGuild().getEmotesByName(matcher.group(1), true);
            }
            if (!emotes.isEmpty()) {
                insertEmote(emotes.get(0));
            }
        }
        return false;
    }

    private void insertMention(Member member) {
        flushTextBuffer();
        discordOutput.append(member.getAsMention());
        gameOutput.append(discordUserToMinecraft(member.getUser(), member.getGuild(), true));
    }

    private void insertEmote(Emote emote) {
        flushTextBuffer();
        discordOutput.append(emote.getAsMention());
        gameOutput.append(discordEmoteToMinecraft(emote));
    }

    private void flushTextBuffer() {
        discordOutput.append(textBuffer);
        gameOutput.append(new LiteralText(textBuffer.toString()));
        textBuffer.setLength(0);
    }

    public static class Results {
        public final LiteralText gameOutput;
        public final String discordOutput;

        protected Results(LiteralText gameOutput, String discordOutput) {
            this.gameOutput = gameOutput;
            this.discordOutput = discordOutput;
        }
    }
}

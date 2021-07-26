package selic.re.discordbridge;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.Timestamp;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class DiscordFormattingConverter {
    // Order matters here, we don't want _ to trigger and stop __ from being detected
    private static final Formatting[] FORMATTING_PRIORITIES = {
        // Three character triggers
//        Formatting.BlockQuotes,
//        Formatting.CodeBlock,

        // Two character triggers
        Formatting.Bold,
        Formatting.Underline,
        Formatting.Strikethrough,
        Formatting.Spoilers,

        // One character triggers
        Formatting.Italic,
        Formatting.InlineCode,
//        Formatting.Quote,
    };

    /**
     * A single-group implementation of {@link TimeFormat#MARKDOWN} that also accounts for trailing characters
     */
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^(<t:-?\\d{1,17}(?::[tTdDfFR])?>).*?");

    /**
     * String formatter templates for each timestamp formatting style
     *
     * Relative timestamps aren't effective for in-game messages due to the static nature
     * of the chat history, so we use a short date-times format (the default style).
     */
    private static final Map<TimeFormat, String> TIMESTAMP_FORMATS =
        Maps.immutableEnumMap(ImmutableMap.<TimeFormat, String>builder()
            .put(TimeFormat.TIME_SHORT, "%1$tH:%1$tM UTC")
            .put(TimeFormat.TIME_LONG, "%1$tH:%1$tM:%1$tS UTC")
            .put(TimeFormat.DATE_SHORT, "%1$te/%1$tm/%1$tY")
            .put(TimeFormat.DATE_LONG, "%1$te %1$tB %1$tY")
            .put(TimeFormat.RELATIVE, "%1$te %1$tB %1$tY %1$tH:%1$tM UTC")
            .put(TimeFormat.DATE_TIME_SHORT, "%1$te %1$tB %1$tY %1$tH:%1$tM UTC")
            .put(TimeFormat.DATE_TIME_LONG, "%1$tA, %1$te %1$tB %1$tY %1$tH:%1$tM UTC")
            .build());

    private final Message message;
    private final String markdown;
    private int cursor;
    private final LiteralText root = new LiteralText("");
    private List<ActiveFormatting> formattingStack = new ArrayList<>();
    private Set<Formatting> activeFormatting = EnumSet.noneOf(Formatting.class);
    final StringBuilder textBuffer = new StringBuilder();

    protected DiscordFormattingConverter(Message message) {
        this.message = message;
        this.markdown = message.getContentRaw();
        this.cursor = 0;
        this.formattingStack.add(new ActiveFormatting("", Formatting.Root));
    }

    protected char read() {
        return markdown.charAt(cursor++);
    }

    protected boolean isEOF() {
        return cursor >= markdown.length();
    }

    protected boolean consume(String input) {
        if (cursor + input.length() <= markdown.length() && markdown.startsWith(input, cursor)) {
            cursor += input.length();
            return true;
        } else {
            return false;
        }
    }

    /**
     * An implementation of {@link #consume(String)} that matches a given regex pattern
     * against the start of the remaining markdown, returning the first captured group
     * and offsetting the cursor by its total length
     *
     * @param pattern The pattern to match against
     * @return The first captured group or null if absent
     */
    @Nullable
    protected String consumeMatching(final Pattern pattern) {
        final var input = this.markdown.substring(this.cursor);
        if (input.isEmpty()) { // Nothing to match
            return null;
        }
        final var matcher = pattern.matcher(input);
        if (matcher.find()) {
            final var first = matcher.group(1);
            this.cursor += first.length();
            return first;
        }
        return null;
    }

    protected boolean tryMarkdown() {
        for (Formatting formattingPriority : FORMATTING_PRIORITIES) {
            if (tryFormatting(formattingPriority)) {
                return true;
            }
        }
        return false;
    }

    protected boolean tryFormatting(Formatting formatting) {
        if (activeFormatting.contains(formatting)) {
            ActiveFormatting last = formattingStack.get(formattingStack.size() - 1);
            if (last.formatting == formatting) {
                if (this.consume(last.trigger)) {
                    popFormatting(last);

                    return true;
                }
            }

            // We already have this formatting but the user didn't intend to pop it - so ignore it.
            return false;
        }

        for (int i = 0; i < formatting.triggers.length; i++) {
            String trigger = formatting.triggers[i];
            if (this.consume(trigger)) {
                if (!textBuffer.isEmpty()) {
                    popSimpleText();
                }
                this.activeFormatting.add(formatting);
                this.formattingStack.add(new ActiveFormatting(trigger, formatting));
                return true;
            }
        }
        return false;
    }

    private void popSimpleText() {
        LiteralText text = new LiteralText(textBuffer.toString());
        textBuffer.setLength(0);
        addText(text);
    }

    private void popFormatting(ActiveFormatting entry) {
        formattingStack.remove(formattingStack.size() - 1);
        activeFormatting.remove(entry.formatting);

        LiteralText text = new LiteralText(textBuffer.toString());
        textBuffer.setLength(0);
        text.setStyle(entry.formatting.getStyle(text));
        for (Text child : entry.children) {
            text.append(child);
        }
        addText(text);
    }

    protected void readToEnd() {
        while (!isEOF()) {
            if (tryMarkdown()) continue;
            if (tryMentions()) continue;
            textBuffer.append(read());
        }
        popSimpleText();

        while (!formattingStack.isEmpty()) {
            ActiveFormatting last = formattingStack.get(formattingStack.size() - 1);
            popFormatting(last);
        }
    }

    private boolean tryMentions() {
        for (User user : message.getMentionedUsers()) {
            if (consume("<@!" + user.getId() + '>') || consume("<@" + user.getId() + '>')) {
                addUserMention(user);
                return true;
            }
        }
        for (Emote emote : message.getEmotes()) {
            if (consume(emote.getAsMention())) {
                addEmoteMention(emote);
                return true;
            }
        }
        for (TextChannel channel : message.getMentionedChannels()) {
            if (consume(channel.getAsMention())) {
                addChannelMention(channel);
                return true;
            }
        }
        for (Role role : message.getMentionedRoles()) {
            if (consume(role.getAsMention())) {
                addRoleMention(role);
                return true;
            }
        }
        String result = consumeMatching(TIMESTAMP_PATTERN);
        if (result != null) {
            addTimestamp(TimeFormat.parse(result));
            return true;
        }
        return false;
    }

    private void addRoleMention(Role role) {
        LiteralText text = new LiteralText("@" + role.getName());
        text.setStyle(Style.EMPTY.withColor(role.getColorRaw()).withInsertion(role.getAsMention()));
        popSimpleText();
        addText(text);
    }

    private void addChannelMention(TextChannel channel) {
        popSimpleText();
        addText(discordChannelToMinecraft(channel));
    }

    private void addEmoteMention(Emote emote) {
        LiteralText text = new LiteralText(":" + emote.getName() + ":");
        ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, emote.getImageUrl());
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(emote.getGuild() == null ? "Emote" : "Emote from " + emote.getGuild().getName()));
        text.setStyle(Style.EMPTY.withClickEvent(click).withHoverEvent(hover).withInsertion(emote.getAsMention()));
        popSimpleText();
        addText(text);
    }

    private void addUserMention(User user) {
        popSimpleText();
        Text userText = discordUserToMinecraft(user, message.getGuild());
        addText(new LiteralText("@").setStyle(userText.getStyle()).append(userText));
    }

    private void addTimestamp(final Timestamp timestamp) {
        popSimpleText();
        addText(discordTimestampToMinecraft(timestamp));
    }

    private void addText(Text text) {
        if (formattingStack.size() > 0) {
            formattingStack.get(formattingStack.size() - 1).children.add(text);
        } else {
            root.append(text);
        }
    }

    public static Text discordChannelToMinecraft(GuildChannel channel) {
        String name;
        String type;
        if (channel.getType().isAudio()) {
            type = "Voice channel";
            name = channel.getName();
        } else if (channel.getType().isMessage()) {
            type = "Text channel";
            name = "#" + channel.getName();
        } else {
            type = "Channel";
            name = channel.getName();
        }
        LiteralText text = new LiteralText(name);
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(type));
        text.setStyle(Style.EMPTY.withInsertion(channel.getAsMention()).withHoverEvent(hover));
        return text;
    }

    public static Text discordMessageToMinecraft(Message message) {
        DiscordFormattingConverter converter = new DiscordFormattingConverter(message);
        converter.readToEnd();
        return converter.root;
    }

    public static LiteralText discordUserToMinecraft(User user, Guild guild) {
        Member member = guild.getMember(user);
        LiteralText author = new LiteralText(member == null ? user.getName() : member.getEffectiveName());
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(user.getAsTag()));
        Style style = Style.EMPTY;
        if (member != null) {
            style = style.withColor(member.getColorRaw());
        }
        author.setStyle(style.withHoverEvent(hover).withInsertion(user.getAsMention()));
        return author;
    }

    /**
     * Parses the given timestamp markdown into a chat component, with a tooltip
     * containing the full UTC date and time regardless of the timestamp style,
     * and an insertion for the original markdown string.
     *
     * @param timestamp The timestamp instance
     * @return A chat component representing the parsed timestamp
     * @see <a href="https://discord.com/developers/docs/reference#message-formatting">
     *     discord.com/developers/docs/reference#message-formatting
     *     </a>
     */
    public static Text discordTimestampToMinecraft(final Timestamp timestamp) {
        // TODO Allow configuration of preferred time zone and hour (24/12) format?
        final var dateTime = LocalDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
        return new LiteralText("[")
            .append(TIMESTAMP_FORMATS.get(timestamp.getFormat()).formatted(dateTime))
            .append("]")
            .setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new LiteralText(TIMESTAMP_FORMATS.get(TimeFormat.DATE_TIME_LONG).formatted(dateTime))
            )).withInsertion(timestamp.toString()));
    }

    public static String minecraftToDiscord(Text root) {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        root.visit(visitor, Style.EMPTY);
        return visitor.finish();
    }

    protected enum Formatting {
        Root((msg, style) -> style),
        Bold((msg, style) -> style.withBold(true), "**"),
        Italic((msg, style) -> style.withItalic(true), "*", "_"),
        Underline((msg, style) -> style.withUnderline(true), "__"),
        Strikethrough((msg, style) -> style.withStrikethrough(true), "~~"),
        InlineCode((msg, style) -> style.withColor(net.minecraft.util.Formatting.GRAY), "`"),
        //CodeBlock(style -> style, "```"),
        //Quote(style -> style, ">"),
        //BlockQuotes(style -> style, ">>>"),
        Spoilers((msg, style) -> style.obfuscated(true).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, msg)), "||"),
        ;

        protected final BiFunction<Text, Style, Style> styler;
        protected final String[] triggers;

        Formatting(BiFunction<Text, Style, Style> styler, String... triggers) {
            this.styler = styler;
            this.triggers = triggers;
        }

        protected Style getStyle(Text message) {
            return styler.apply(message, Style.EMPTY);
        }
    }

    protected static class ActiveFormatting {
        protected final String trigger;
        protected final Formatting formatting;
        protected final List<Text> children = new ArrayList<>();

        public ActiveFormatting(String trigger, Formatting formatting) {
            this.trigger = trigger;
            this.formatting = formatting;
        }
    }
}

package selic.re.discordbridge;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
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
        text.setStyle(entry.formatting.getStyle());
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
        return false;
    }

    private void addRoleMention(Role role) {
        LiteralText text = new LiteralText("@" + role.getName());
        text.setStyle(Style.EMPTY.withColor(role.getColorRaw()).withInsertion(role.getAsMention()));
        popSimpleText();
        addText(text);
    }

    private void addChannelMention(TextChannel channel) {
        LiteralText text = new LiteralText("#" + channel.getName());
        text.setStyle(Style.EMPTY.withInsertion(channel.getAsMention()));
        popSimpleText();
        addText(text);
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
        addText(discordUserToMinecraft(user, message.getGuild()));
    }

    private void addText(Text text) {
        if (formattingStack.size() > 0) {
            formattingStack.get(formattingStack.size() - 1).children.add(text);
        } else {
            root.append(text);
        }
    }

    public static Text discordMessageToMinecraft(Message message) {
        DiscordFormattingConverter converter = new DiscordFormattingConverter(message);
        converter.readToEnd();
        return converter.root;
    }

    public static LiteralText discordUserToMinecraft(User user, Guild guild) {
        Member member = guild.getMember(user);
        System.out.println(member);
        LiteralText author = new LiteralText(member == null ? user.getName() : member.getEffectiveName());
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(user.getAsTag()));
        Style style = Style.EMPTY;
        if (member != null) {
            style = style.withColor(member.getColorRaw());
        }
        author.setStyle(style.withHoverEvent(hover).withInsertion(user.getAsMention()));
        return author;
    }

    public static String minecraftToDiscord(Text root) {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        root.visit(visitor, Style.EMPTY);
        return visitor.get();
    }

    protected enum Formatting {
        Root(style -> style),
        Bold(style -> style.withBold(true), "**"),
        Italic(style -> style.withItalic(true), "*", "_"),
        Underline(style -> style.withUnderline(true), "__"),
        Strikethrough(style -> style.withStrikethrough(true), "~~"),
        InlineCode(style -> style.withColor(net.minecraft.util.Formatting.GRAY), "`"),
        //CodeBlock(style -> style, "```"),
        //Quote(style -> style, ">"),
        //BlockQuotes(style -> style, ">>>"),
        Spoilers(style -> style.obfuscated(true), "||"),
        ;

        protected final Function<Style, Style> styler;
        protected final String[] triggers;

        Formatting(Function<Style, Style> styler, String... triggers) {
            this.styler = styler;
            this.triggers = triggers;
        }

        protected Style getStyle() {
            return styler.apply(Style.EMPTY);
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

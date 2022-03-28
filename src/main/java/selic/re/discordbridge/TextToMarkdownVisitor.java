package selic.re.discordbridge;

import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;

import java.util.Optional;

public class TextToMarkdownVisitor implements StringVisitable.StyledVisitor<Void> {
    private static final String BOLD_TOKEN = "**";
    private static final String ITALIC_TOKEN = "*";
    private static final String UNDERLINED_TOKEN = "__";
    private static final String STRIKETHROUGH_TOKEN = "~~";
    private static final String SPOILER_TOKEN = "||";
    private static final String INTERACTIVE_TOKEN = BOLD_TOKEN;

    private final StringBuilder markdown = new StringBuilder(0);
    private Style lastStyle = Style.EMPTY;

    public String finish() {
        this.popTokens(Style.EMPTY); // Pop dangling tokens

        return this.markdown.toString();
    }

    @Override
    public Optional<Void> accept(Style style, String text) {
        this.pushTokens(style);
        this.popTokens(style);
        this.pushText(text);

        return Optional.empty();
    }

    private void pushTokens(Style style) {
        if (style.isEmpty()) {
            return;
        }

        if (!this.lastStyle.isBold() && style.isBold()) {
            this.markdown.append(BOLD_TOKEN);
        }

        if (!this.lastStyle.isItalic() && style.isItalic()) {
            this.markdown.append(ITALIC_TOKEN);
        }

        if (!this.lastStyle.isUnderlined() && style.isUnderlined()) {
            this.markdown.append(UNDERLINED_TOKEN);
        }

        if (!this.lastStyle.isStrikethrough() && style.isStrikethrough()) {
            this.markdown.append(STRIKETHROUGH_TOKEN);
        }

        if (!this.lastStyle.isObfuscated() && style.isObfuscated()) {
            this.markdown.append(SPOILER_TOKEN);
        }

        if (!this.isInteractive(this.lastStyle) && this.isInteractive(style)) {
            this.markdown.append(INTERACTIVE_TOKEN);
        }
    }

    private void popTokens(Style style) {
        if (this.lastStyle.isEmpty()) {
            this.lastStyle = style;
            return;
        }

        if (this.isInteractive(this.lastStyle) && !this.isInteractive(style)) {
            this.markdown.append(INTERACTIVE_TOKEN);
        }

        if (this.lastStyle.isObfuscated() && !style.isObfuscated()) {
            this.markdown.append(SPOILER_TOKEN);
        }

        if (this.lastStyle.isStrikethrough() && !style.isStrikethrough()) {
            this.markdown.append(STRIKETHROUGH_TOKEN);
        }

        if (this.lastStyle.isUnderlined() && !style.isUnderlined()) {
            this.markdown.append(UNDERLINED_TOKEN);
        }

        if (this.lastStyle.isItalic() && !style.isItalic()) {
            this.markdown.append(ITALIC_TOKEN);
        }

        if (this.lastStyle.isBold() && !style.isBold()) {
            this.markdown.append(BOLD_TOKEN);
        }

        this.lastStyle = style;
    }

    private void pushText(String text) {
        this.markdown.append(text.replaceAll("([*_~|`])+?", "\\\\$1"));
    }

    private boolean isInteractive(Style style) {
        return style.getHoverEvent() != null || style.getClickEvent() != null;
    }
}

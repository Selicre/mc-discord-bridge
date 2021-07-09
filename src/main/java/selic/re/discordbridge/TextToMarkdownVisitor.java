package selic.re.discordbridge;

import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;

import java.util.Optional;

public class TextToMarkdownVisitor implements StringVisitable.StyledVisitor<Void> {
    StringBuilder result = new StringBuilder();
    boolean bold;
    boolean italic;
    boolean underline;
    boolean strikethrough;
    boolean spoiler;

    public String finish() {
        if (spoiler) {
            result.append("||");
            spoiler = false;
        }
        if (strikethrough) {
            result.append("~~");
            strikethrough = false;
        }
        if (underline) {
            result.append("__");
            underline = false;
        }
        if (italic) {
            result.append("*");
            italic = false;
        }
        if (bold) {
            result.append("**");
            bold = false;
        }
        return result.toString();
    }

    @Override
    public Optional<Void> accept(Style style, String text) {
        // Applying new style
        boolean shouldBeBold = style.isBold() || style.getHoverEvent() != null;
        boolean shouldBeItalic = style.isItalic();
        boolean shouldBeUnderlined = style.isUnderlined();
        boolean shouldBeStrikethrough = style.isStrikethrough();
        boolean shouldBeSpoiler = style.isObfuscated();

        if (shouldBeBold && !this.bold) {
            result.append("**");
        }
        if (shouldBeItalic && !this.italic) {
            result.append("*");
        }
        if (shouldBeUnderlined && !underline) {
            result.append("__");
        }
        if (shouldBeStrikethrough && !this.strikethrough) {
            result.append("~~");
        }
        if (shouldBeSpoiler && !spoiler) {
            result.append("||");
        }

        // Removing existing style - REVERSE ORDER
        if (!shouldBeSpoiler && spoiler) {
            result.append("||");
        }
        if (!shouldBeStrikethrough && this.strikethrough) {
            result.append("~~");
        }
        if (!shouldBeUnderlined && underline) {
            result.append("__");
        }
        if (!shouldBeItalic && this.italic) {
            result.append("*");
        }
        if (!shouldBeBold && this.bold) {
            result.append("**");
        }

        result.append(text);
        this.bold = shouldBeBold;
        this.italic = shouldBeItalic;
        this.strikethrough = shouldBeStrikethrough;
        this.underline = shouldBeUnderlined;
        this.spoiler = shouldBeSpoiler;

        return Optional.empty();
    }
}

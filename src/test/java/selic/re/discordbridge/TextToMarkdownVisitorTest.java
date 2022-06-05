package selic.re.discordbridge;

import net.minecraft.text.*;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class TextToMarkdownVisitorTest {
    @Test
    public void boldStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText bold = Text.empty().append("This is bold");

        bold.setStyle(Style.EMPTY.withBold(true));
        bold.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("**This is bold**");
    }

    @Test
    public void italicStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText italic = Text.empty().append("This is italic");

        italic.setStyle(Style.EMPTY.withItalic(true));
        italic.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("*This is italic*");
    }

    @Test
    public void underlineStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText underline = Text.empty().append("This is underline");

        underline.setStyle(Style.EMPTY.withUnderline(true));
        underline.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("__This is underline__");
    }

    @Test
    public void strikethroughStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText strikethrough = Text.empty().append("This is strikethrough");

        strikethrough.setStyle(Style.EMPTY.withStrikethrough(true));
        strikethrough.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("~~This is strikethrough~~");
    }

    @Test
    public void spoilerStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText spoiler = Text.empty().append("This is spoiler");

        spoiler.setStyle(Style.EMPTY.withObfuscated(true));
        spoiler.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("||This is spoiler||");
    }

    @Test
    public void styleWithHoverEventIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText component = Text.empty().append("This has a hover event");
        HoverEvent event = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.empty());

        component.setStyle(Style.EMPTY.withHoverEvent(event));
        component.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("**This has a hover event**");
    }

    @Test
    public void styleWithClickEventIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText component = Text.empty().append("This has a click event");
        ClickEvent event = new ClickEvent(ClickEvent.Action.OPEN_URL, "");

        component.setStyle(Style.EMPTY.withClickEvent(event));
        component.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("**This has a click event**");
    }

    @Test
    public void nestedStylesAreTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText bold = Text.empty().append("This is ").setStyle(Style.EMPTY.withBold(true));
        MutableText italic = Text.empty().append("italic, ").setStyle(Style.EMPTY.withItalic(true));
        MutableText underlined = Text.empty().append("underlined").setStyle(Style.EMPTY.withUnderline(true));

        bold.append(italic.append(underlined)).append(Text.empty().append(", and bold"));

        bold.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("**This is *italic, __underlined__*, and bold**");
    }

    @Test
    public void literalTokensAreEscaped() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText tokenized = Text.empty().append("*These* __are__ ||literal|| `tokens`");

        tokenized.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("\\*These\\* \\_\\_are\\_\\_ \\|\\|literal\\|\\| \\`tokens\\`");
    }
}

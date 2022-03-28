package selic.re.discordbridge;

import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class TextToMarkdownVisitorTest {
    @Test
    public void boldStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText bold = new LiteralText("This is bold");

        bold.setStyle(Style.EMPTY.withBold(true));
        bold.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("**This is bold**");
    }

    @Test
    public void italicStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText italic = new LiteralText("This is italic");

        italic.setStyle(Style.EMPTY.withItalic(true));
        italic.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("*This is italic*");
    }

    @Test
    public void underlineStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText underline = new LiteralText("This is underline");

        underline.setStyle(Style.EMPTY.withUnderline(true));
        underline.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("__This is underline__");
    }

    @Test
    public void strikethroughStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText strikethrough = new LiteralText("This is strikethrough");

        strikethrough.setStyle(Style.EMPTY.withStrikethrough(true));
        strikethrough.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("~~This is strikethrough~~");
    }

    @Test
    public void spoilerStyleIsTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText spoiler = new LiteralText("This is spoiler");

        spoiler.setStyle(Style.EMPTY.withObfuscated(true));
        spoiler.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("||This is spoiler||");
    }

    @Test
    public void nestedStylesAreTokenized() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText bold = new LiteralText("This is ").setStyle(Style.EMPTY.withBold(true));
        MutableText italic = new LiteralText("italic, ").setStyle(Style.EMPTY.withItalic(true));
        MutableText underlined = new LiteralText("underlined").setStyle(Style.EMPTY.withUnderline(true));

        bold.append(italic.append(underlined)).append(new LiteralText(", and bold"));

        bold.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("**This is *italic, __underlined__*, and bold**");
    }

    @Test
    public void literalTokensAreEscaped() {
        TextToMarkdownVisitor visitor = new TextToMarkdownVisitor();
        MutableText tokenized = new LiteralText("*These* __are__ ||literal|| `tokens`");

        tokenized.visit(visitor, Style.EMPTY);

        assertThat(visitor.finish()).isEqualTo("\\*These\\* \\_\\_are\\_\\_ \\|\\|literal\\|\\| \\`tokens\\`");
    }
}

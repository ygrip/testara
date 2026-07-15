package io.github.ygrip.testara.command;

import io.github.ygrip.testara.command.ast.CommandCallNode;
import io.github.ygrip.testara.command.ast.CommandNode;
import io.github.ygrip.testara.command.ast.LiteralNode;
import io.github.ygrip.testara.command.ast.RawLiteralNode;
import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.command.parser.CommandModelConverter;
import io.github.ygrip.testara.command.parser.CommandParseException;
import io.github.ygrip.testara.command.parser.CommandParserOptions;
import io.github.ygrip.testara.command.parser.StreamingCommandParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit-level compatibility tests for {@link StreamingCommandParser} and {@link CommandModelConverter}.
 * These tests verify that the AST parser produces the same logical structure as the legacy parser for
 * the supported syntax subset, including the unbalanced-paren heuristic used by {@code !(…)} arguments.
 */
@Tag("command")
public class CommandAstParserTests {

    private final StreamingCommandParser parser = new StreamingCommandParser(CommandParserOptions.defaults());
    private final CommandModelConverter converter = new CommandModelConverter();

    // -------------------------------------------------------------------------
    // Simple commands
    // -------------------------------------------------------------------------

    @Test
    void simpleCommandNoArgs() throws CommandParseException {
        CommandCallNode node = parser.parse("uuid()");
        assertThat(node.name(), equalTo("uuid"));
        assertThat(node.arguments(), hasSize(0));
    }

    @Test
    void simpleCommandOneArg() throws CommandParseException {
        CommandCallNode node = parser.parse("random(10)");
        assertThat(node.name(), equalTo("random"));
        assertThat(node.arguments(), hasSize(1));
        assertThat(node.arguments().get(0), instanceOf(LiteralNode.class));
        assertThat(node.arguments().get(0).toString(), equalTo("10"));
    }

    @Test
    void simpleCommandMultipleArgs() throws CommandParseException {
        CommandCallNode node = parser.parse("random(10,NUMERIC)");
        assertThat(node.name(), equalTo("random"));
        assertThat(node.arguments(), hasSize(2));
        assertThat(node.arguments().get(0).toString(), equalTo("10"));
        assertThat(node.arguments().get(1).toString(), equalTo("NUMERIC"));
    }

    @Test
    void leadingSpaceInInput() throws CommandParseException {
        CommandCallNode node = parser.parse(" request($['sentence'])");
        assertThat(node.name(), equalTo("request"));
        assertThat(node.arguments(), hasSize(1));
    }

    // -------------------------------------------------------------------------
    // Combine syntax (empty command name)
    // -------------------------------------------------------------------------

    @Test
    void combineSyntax() throws CommandParseException {
        CommandCallNode node = parser.parse("(.,-,5)");
        assertThat(node.name(), equalTo(""));
        assertThat(node.arguments(), hasSize(3));
        assertThat(node.arguments().get(0).toString(), equalTo("."));
        assertThat(node.arguments().get(1).toString(), equalTo("-"));
        assertThat(node.arguments().get(2).toString(), equalTo("5"));
    }

    @Test
    void combineWithNestedCommand() throws CommandParseException {
        CommandCallNode node = parser.parse("(author name is, ,properties(author))");
        assertThat(node.name(), equalTo(""));
        assertThat(node.arguments(), hasSize(3));
        assertThat(node.arguments().get(2), instanceOf(CommandCallNode.class));
        assertThat(((CommandCallNode) node.arguments().get(2)).name(), equalTo("properties"));
    }

    // -------------------------------------------------------------------------
    // Nested commands
    // -------------------------------------------------------------------------

    @Test
    void singleNesting() throws CommandParseException {
        CommandCallNode node = parser.parse("sizeof(request($['dummy']))");
        assertThat(node.name(), equalTo("sizeof"));
        assertThat(node.arguments(), hasSize(1));
        CommandCallNode inner = (CommandCallNode) node.arguments().get(0);
        assertThat(inner.name(), equalTo("request"));
        assertThat(inner.arguments().get(0).toString(), equalTo("$['dummy']"));
    }

    @Test
    void deepNesting() throws CommandParseException {
        CommandCallNode node = parser.parse("jsonpath(split(request($['fileName']),/),[-1])");
        assertThat(node.name(), equalTo("jsonpath"));
        assertThat(node.arguments(), hasSize(2));
        CommandCallNode split = (CommandCallNode) node.arguments().get(0);
        assertThat(split.name(), equalTo("split"));
        assertThat(split.arguments(), hasSize(2));
        CommandCallNode request = (CommandCallNode) split.arguments().get(0);
        assertThat(request.name(), equalTo("request"));
        assertThat(node.arguments().get(1).toString(), equalTo("[-1]"));
    }

    // -------------------------------------------------------------------------
    // Raw literal / ignored command
    // -------------------------------------------------------------------------

    @Test
    void topLevelIgnoredCommand() throws CommandParseException {
        CommandCallNode node = parser.parse("!(.,-,5)");
        assertThat(node.name(), equalTo("!"));
        assertThat(node.arguments(), hasSize(1));
        assertThat(node.arguments().get(0), instanceOf(RawLiteralNode.class));
        assertThat(node.arguments().get(0).toString(), equalTo(".,-,5"));
    }

    @Test
    void rawLiteralEscapeAsArg() throws CommandParseException {
        CommandCallNode node = parser.parse("split(properties(foo),!(,))");
        assertThat(node.name(), equalTo("split"));
        assertThat(node.arguments(), hasSize(2));
        CommandNode second = node.arguments().get(1);
        assertThat(second, instanceOf(CommandCallNode.class));
        CommandCallNode ignored = (CommandCallNode) second;
        assertThat(ignored.name(), equalTo("!"));
        assertThat(ignored.arguments().get(0), instanceOf(RawLiteralNode.class));
        assertThat(ignored.arguments().get(0).toString(), equalTo(","));
    }

    // -------------------------------------------------------------------------
    // JsonPath-like literals treated as opaque arguments
    // -------------------------------------------------------------------------

    @Test
    void jsonPathLiteralRemainsLiteral() throws CommandParseException {
        CommandCallNode node = parser.parse("request($['items'][?(@.index == 2)])");
        assertThat(node.name(), equalTo("request"));
        assertThat(node.arguments(), hasSize(1));
        assertThat(node.arguments().get(0), instanceOf(LiteralNode.class));
        assertThat(node.arguments().get(0).toString(), equalTo("$['items'][?(@.index == 2)]"));
    }

    // -------------------------------------------------------------------------
    // Loop syntax
    // -------------------------------------------------------------------------

    @Test
    void loopWithOccurrenceAndSeparator() throws CommandParseException {
        CommandCallNode node = parser.parse("loop(.,-,5)");
        assertThat(node.name(), equalTo("loop"));
        assertThat(node.arguments(), hasSize(3));
        assertThat(node.arguments().get(0).toString(), equalTo("."));
        assertThat(node.arguments().get(1).toString(), equalTo("-"));
        assertThat(node.arguments().get(2).toString(), equalTo("5"));
    }

    @Test
    void loopWithOccurrence() throws CommandParseException {
        CommandCallNode node = parser.parse("loop(.,5)");
        assertThat(node.name(), equalTo("loop"));
        assertThat(node.arguments(), hasSize(2));
    }

    @Test
    void loopWithoutOccurrence() throws CommandParseException {
        CommandCallNode node = parser.parse("loop(.)");
        assertThat(node.name(), equalTo("loop"));
        assertThat(node.arguments(), hasSize(1));
    }

    // -------------------------------------------------------------------------
    // Quick reject
    // -------------------------------------------------------------------------

    @Test
    void rejectsNonCommandInput() {
        assertThrows(CommandParseException.class, () -> parser.parse("nama saya yunaz("));
    }

    @Test
    void rejectsRegexLike() {
        String regex = "^(general_remainder\\|1st_penalty\\|-)$";
        assertThrows(CommandParseException.class, () -> parser.parse(regex));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(CommandParseException.class, () -> parser.parse(null));
    }

    // -------------------------------------------------------------------------
    // Guard-rail limits
    // -------------------------------------------------------------------------

    @Test
    void rejectsWhenCommandNameExceedsMaxLength() {
        CommandParserOptions opts = new CommandParserOptions(5, 10, 4, ",");
        StreamingCommandParser strictParser = new StreamingCommandParser(opts);
        assertThrows(CommandParseException.class, () -> strictParser.parse("toolongname()"));
    }

    @Test
    void rejectsWhenDepthExceeded() {
        CommandParserOptions opts = new CommandParserOptions(1, 10, 64, ",");
        StreamingCommandParser strictParser = new StreamingCommandParser(opts);
        assertThrows(CommandParseException.class, () -> strictParser.parse("a(b(c()))"));
    }

    @Test
    void rejectsWhenArgCountExceeded() {
        CommandParserOptions opts = new CommandParserOptions(5, 2, 64, ",");
        StreamingCommandParser strictParser = new StreamingCommandParser(opts);
        assertThrows(CommandParseException.class, () -> strictParser.parse("cmd(a,b,c)"));
    }

    // -------------------------------------------------------------------------
    // CommandModelConverter
    // -------------------------------------------------------------------------

    @Test
    void converterProducesCommandModel() throws CommandParseException {
        CommandCallNode node = parser.parse("random(10,NUMERIC)");
        CommandModel model = converter.convert(node, null);
        assertThat(model, notNullValue());
        assertThat(model.getCommand(), equalTo("random"));
        assertThat(model.getParameters(), hasSize(2));
        assertThat(model.getParameters().get(0), equalTo("10"));
        assertThat(model.getParameters().get(1), equalTo("NUMERIC"));
    }

    @Test
    void converterHandlesNestedCommand() throws CommandParseException {
        CommandCallNode node = parser.parse("sizeof(request($['x']))");
        CommandModel model = converter.convert(node, null);
        assertThat(model.getCommand(), equalTo("sizeof"));
        assertThat(model.getParameters().get(0), instanceOf(CommandModel.class));
        CommandModel inner = (CommandModel) model.getParameters().get(0);
        assertThat(inner.getCommand(), equalTo("request"));
    }

    @Test
    void converterHandlesIgnoredCommandArg() throws CommandParseException {
        CommandCallNode node = parser.parse("split(properties(foo),!(,))");
        CommandModel model = converter.convert(node, null);
        assertThat(model.getParameters().get(1), instanceOf(CommandModel.class));
        CommandModel ignored = (CommandModel) model.getParameters().get(1);
        assertThat(ignored.getCommand(), equalTo("!"));
        assertThat(ignored.getParameters(), equalTo(List.of(",")));
    }

    @Test
    void converterHandlesCombineSyntax() throws CommandParseException {
        CommandCallNode node = parser.parse("(.,-,5)");
        CommandModel model = converter.convert(node, null);
        assertThat(model.getCommand(), equalTo(""));
        assertThat(model.getParameters(), hasSize(3));
    }

    // -------------------------------------------------------------------------
    // Unbalanced-paren heuristic — parity with legacy parser
    // -------------------------------------------------------------------------

    /**
     * "(!((.,-,5),yunaz)" has three '(' but only two ')'. The legacy parser handles this via a
     * heuristic: when '!' is encountered during paren-matching, jump to lastIndexOf(')').
     * The AST parser replicates both heuristics (findMatchingClose + reverseMatchingClose),
     * so it produces the same two-argument combine as the legacy parser.
     */
    @Test
    void supportsUnbalancedParenWithIgnoreHeuristic() throws CommandParseException {
        CommandCallNode node = parser.parse("(!((.,-,5),yunaz)");
        assertThat(node.name(), equalTo(""));
        assertThat(node.arguments(), hasSize(2));
        CommandNode first = node.arguments().get(0);
        assertThat(first, instanceOf(CommandCallNode.class));
        CommandCallNode ignored = (CommandCallNode) first;
        assertThat(ignored.name(), equalTo("!"));
        assertThat(ignored.arguments().get(0), instanceOf(RawLiteralNode.class));
        assertThat(ignored.arguments().get(0).toString(), equalTo("(.,-,5"));
        assertThat(node.arguments().get(1).toString(), equalTo("yunaz"));
    }
}

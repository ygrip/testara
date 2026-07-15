package io.github.ygrip.testara.command;

import io.github.ygrip.testara.command.ast.CommandCallNode;
import io.github.ygrip.testara.command.ast.LiteralNode;
import io.github.ygrip.testara.command.ast.TextSlice;
import io.github.ygrip.testara.command.parser.CommandParseException;
import io.github.ygrip.testara.command.parser.CommandParserOptions;
import io.github.ygrip.testara.command.parser.LegacyCommandParser;
import io.github.ygrip.testara.command.parser.StreamingCommandParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Performance-oriented tests for {@link StreamingCommandParser}.
 * These tests verify quick-reject behaviour for large non-command inputs, correctness
 * near guard-rail limits, and that common expressions parse within a generous time budget.
 *
 * <p>Timing assertions use very generous thresholds (seconds not milliseconds) so they
 * remain green on slow CI hardware. They are smoke tests, not micro-benchmarks.
 */
@Tag("command")
public class CommandParserPerformanceTests {

    private static final StreamingCommandParser PARSER =
        new StreamingCommandParser(CommandParserOptions.defaults());
    private static final LegacyCommandParser LEGACY =
        new LegacyCommandParser(",");

    // -------------------------------------------------------------------------
    // Quick reject — large non-command text
    // -------------------------------------------------------------------------

    @Test
    void quicklyRejectsHugeTextWithoutTrailingParen() {
        // 50 000-char payload that does not end with ')'
        String huge = "x".repeat(50_000) + " {}";
        long start = System.nanoTime();
        assertThrows(CommandParseException.class, () -> PARSER.parse(huge));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // strip() on a 50k string + endsWith check should be well under 10 ms
        assertThat("Quick reject must complete in < 10 ms", elapsedMs, lessThan(10L));
    }

    @Test
    void quicklyRejectsLargeJsonLikeLiteral() {
        // 20 000-char JSON fragment — does not end with ')'
        String json = "{\"items\":[" + IntStream.range(0, 1000)
            .mapToObj(i -> "{\"id\":" + i + ",\"name\":\"item" + i + "\"}")
            .collect(Collectors.joining(",")) + "]}";
        assertThat(json.endsWith(")"), equalTo(false));
        assertThrows(CommandParseException.class, () -> PARSER.parse(json));
    }

    // -------------------------------------------------------------------------
    // TextSlice — lazy materialisation
    // -------------------------------------------------------------------------

    @Test
    void textSliceDoesNotCopySourceUntilToString() throws CommandParseException {
        // The argument to a simple command is stored as TextSlice, not a copied String.
        CommandCallNode node = PARSER.parse("substring(automation,4)");
        // The first argument is "automation" — verify it comes back as a TextSlice via LiteralNode
        assertThat(node.arguments().get(0), instanceOf(LiteralNode.class));
        LiteralNode lit = (LiteralNode) node.arguments().get(0);
        assertThat(lit.value(), instanceOf(TextSlice.class));
        // toString() materialises correctly
        assertThat(lit.value().toString(), equalTo("automation"));
    }

    @Test
    void textSliceBoundsAreCorrect() {
        TextSlice slice = new TextSlice("hello world", 6, 11);
        assertThat(slice.length(), equalTo(5));
        assertThat(slice.charAt(0), equalTo('w'));
        assertThat(slice.toString(), equalTo("world"));
        assertThat(slice.subSequence(0, 3).toString(), equalTo("wor"));
    }

    // -------------------------------------------------------------------------
    // Near-limit cases — depth
    // -------------------------------------------------------------------------

    @Test
    void parsesCommandAtMaxDepth() throws CommandParseException {
        // maxDepth=20 allows parseExpression to be called at depth 0..20.
        // buildNested(21, "leaf") = a0(a1(...a20(leaf)...)) — the deepest parseExpression
        // call is at depth 20, which satisfies depth <= maxDepth.
        String nested = buildNested(21, "leaf");
        CommandCallNode node = PARSER.parse(nested);
        assertThat(node.name(), equalTo("a0"));
    }

    @Test
    void rejectsCommandOneOverMaxDepth() {
        // buildNested(22, "leaf") causes parseExpression to be called at depth 21 (> 20).
        String overNested = buildNested(22, "leaf");
        assertThrows(CommandParseException.class, () -> PARSER.parse(overNested));
    }

    // -------------------------------------------------------------------------
    // Near-limit cases — argument count
    // -------------------------------------------------------------------------

    @Test
    void parsesCommandAtMaxArguments() throws CommandParseException {
        // Default maxArguments is 50
        String manyArgs = "cmd(" + IntStream.range(0, 50).mapToObj(i -> "arg" + i)
            .collect(Collectors.joining(",")) + ")";
        CommandCallNode node = PARSER.parse(manyArgs);
        assertThat(node.arguments().size(), equalTo(50));
    }

    @Test
    void rejectsCommandOneOverMaxArguments() {
        // 51 args — one over the default limit of 50
        String tooManyArgs = "cmd(" + IntStream.range(0, 51).mapToObj(i -> "arg" + i)
            .collect(Collectors.joining(",")) + ")";
        assertThrows(CommandParseException.class, () -> PARSER.parse(tooManyArgs));
    }

    // -------------------------------------------------------------------------
    // Throughput smoke test — common small commands
    // -------------------------------------------------------------------------

    @Test
    void parsesCommonCommandsQuickly() throws CommandParseException {
        String[] inputs = {
            "uuid()",
            "random(10,NUMERIC)",
            "substring(automation,4)",
            "properties(some.key)",
            "request($['items'])",
            "sizeof(request($['dummy']))",
            "(.,-,5)",
            "!(comma,escaped)",
        };

        int iterations = 10_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (String input : inputs) {
                try {
                    PARSER.parse(input);
                } catch (CommandParseException ignored) {
                    // "!(comma,escaped)" is not a valid top-level parse, but it exercises the path
                }
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 80 000 parse calls should complete well within 5 seconds on any hardware
        assertThat("80k parse calls must complete in < 5000 ms", elapsedMs, lessThan(5000L));
        assertThat("elapsed must be > 0 to confirm test ran", elapsedMs, greaterThan(0L));
    }

    // -------------------------------------------------------------------------
    // Legacy vs AST throughput comparison
    // -------------------------------------------------------------------------

    /**
     * Runs the same representative command set through both parsers and reports timing.
     * Asserts only that both complete within a generous wall-clock budget; relative performance
     * will vary by JVM warm-up and hardware. Use the printed ratio for manual comparison.
     */
    @Test
    void legacyVsAstThroughputComparison() throws Exception {
        String[] inputs = {
            "uuid()",
            "random(10,NUMERIC)",
            "substring(automation,4)",
            "sizeof(request($['dummy']))",
            "jsonpath(split(request($['fileName']),/),[-1])",
            "oneof(split(properties(internal.game.clients),!(,)))",
            "(.,-,5)",
            "(!((.,-,5),yunaz)",
            "loop(.,-,5)",
        };

        int iterations = 5_000;

        // Warm-up — prevent first-call overhead from skewing results
        for (String input : inputs) {
            try { PARSER.parse(input); } catch (Exception ignored) {}
            try { LEGACY.parse(input, null); } catch (Exception ignored) {}
        }

        long astStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (String input : inputs) {
                try { PARSER.parse(input); } catch (Exception ignored) {}
            }
        }
        long astMs = (System.nanoTime() - astStart) / 1_000_000;

        long legacyStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (String input : inputs) {
                try { LEGACY.parse(input, null); } catch (Exception ignored) {}
            }
        }
        long legacyMs = (System.nanoTime() - legacyStart) / 1_000_000;

        System.out.printf("Parser throughput (%d×%d calls): AST=%dms  Legacy=%dms  ratio=%.2fx%n",
            iterations, inputs.length, astMs, legacyMs,
            legacyMs > 0 ? (double) astMs / legacyMs : Double.NaN);

        // Both must complete well within CI time budget (generous: 5 s each)
        assertThat("AST must complete in < 5000 ms", astMs, lessThan(5000L));
        assertThat("Legacy must complete in < 5000 ms", legacyMs, lessThan(5000L));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a depth-level nesting: {@code a0(a1(a2(...(leaf)...)))}.
     */
    private static String buildNested(int depth, String leaf) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("a").append(i).append("(");
        }
        sb.append(leaf);
        sb.append(")".repeat(depth));
        return sb.toString();
    }
}

package com.fillumina.xjc.primitives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMod;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Driver;
import com.sun.tools.xjc.Options;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Generates a schema with primitive fields through XJC and checks what the
 * plugin produced, with the selectors and without them.
 *
 * <p>
 * Generation runs in this JVM through {@link Driver}, the entry point of the
 * {@code xjc} command line, so the test needs neither a Maven build nor a
 * lifecycle. The expected text is collapsed to single spaces first, which makes
 * the assertions independent of how the generator wraps its lines.
 */
class PrimitiveFixerPluginTest {

    private static final Path SCHEMA = Path.of("src", "test", "resources", "primitives.xsd");

    /**
     * One primitive field of the schema, with the type XJC gives it and the one the
     * plugin gives it instead. The boolean attribute {@code flag} is not here: XJC
     * boxes an optional attribute on its own, so the plugin has nothing to do with
     * it.
     */
    private record Field(String name, String primitive, String boxed) {

        String getter() {
            return (primitive.equals("boolean") ? "is" : "get") + capitalised();
        }

        String setter() {
            return "set" + capitalised();
        }

        private String capitalised() {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }

    private static final List<Field> FIELDS = List.of(
            new Field("count", "int", "Integer"),
            new Field("amount", "long", "Long"),
            new Field("active", "boolean", "Boolean"),
            new Field("ratio", "double", "Double"),
            new Field("tiny", "byte", "Byte"),
            new Field("small", "short", "Short"),
            new Field("fraction", "float", "Float"));

    @TempDir
    Path outputDirectory;

    @Test
    void replacesPrimitivesWithBoxedTypes() throws Exception {
        String generated = generate(true);

        for (Field field : FIELDS) {
            assertBoxed(generated, field);
        }
    }

    @Test
    void leavesPrimitivesAloneWhenTheOptionIsNotGiven() throws Exception {
        String generated = generate(false);

        for (Field field : FIELDS) {
            assertPrimitive(generated, field);
        }
    }

    @Test
    void everyFieldCanBeSelectedOnItsOwn() throws Exception {
        for (Field selected : FIELDS) {
            String generated = generate(true, "-XReplacePrimitives:include=*#" + selected.name());

            assertBoxed(generated, selected);
            for (Field other : FIELDS) {
                if (!other.name().equals(selected.name())) {
                    assertPrimitive(generated, other);
                }
            }
        }
    }

    @Test
    void everyFieldCanBeLeftAloneOnItsOwn() throws Exception {
        for (Field excluded : FIELDS) {
            String generated = generate(true, "-XReplacePrimitives:exclude=*#" + excluded.name());

            assertPrimitive(generated, excluded);
            for (Field other : FIELDS) {
                if (!other.name().equals(excluded.name())) {
                    assertBoxed(generated, other);
                }
            }
        }
    }

    @Test
    void aWholeClassCanBeSelectedByItsNameOrByAGlob() throws Exception {
        for (String selector : List.of("*Primitives", "generated.Primitives", "generated.*",
                "*Primitive?", "generated.Primitive[sz]")) {
            String generated = generate(true, "-XReplacePrimitives:include=" + selector);

            for (Field field : FIELDS) {
                assertBoxed(generated, field);
            }
        }
    }

    @Test
    void aWholeClassCanBeLeftAlone() throws Exception {
        String generated = generate(true, "-XReplacePrimitives:exclude=generated.Primitives");

        for (Field field : FIELDS) {
            assertPrimitive(generated, field);
        }
    }

    @Test
    void severalSelectorsAddUp() throws Exception {
        String generated = generate(true,
                "-XReplacePrimitives:include=*#count",
                "-XReplacePrimitives:include=*#flag",
                "-XReplacePrimitives:include=*#tiny");

        assertBoxed(generated, field("count"));
        assertBoxed(generated, field("tiny"));
        assertPrimitive(generated, field("amount"));
        assertPrimitive(generated, field("small"));
    }

    @Test
    void aCharacterClassSelectsSeveralFields() throws Exception {
        // the fields that start with an a followed by c or m: amount and active, but
        // not count
        String generated = generate(true, "-XReplacePrimitives:include=*#a[cm]*");

        assertBoxed(generated, field("amount"));
        assertBoxed(generated, field("active"));
        assertPrimitive(generated, field("count"));
        assertPrimitive(generated, field("ratio"));
    }

    @Test
    void anExcludeWinsOverAnInclude() throws Exception {
        String generated = generate(true,
                "-XReplacePrimitives:include=*#a[cm]*",
                "-XReplacePrimitives:exclude=*#amount");

        assertBoxed(generated, field("active"));
        assertPrimitive(generated, field("amount"));
        assertPrimitive(generated, field("count"));
    }

    @Test
    void everyFieldCanBeSelectedOrLeftAloneByTheSameRun() throws Exception {
        String generated = generate(true,
                "-XReplacePrimitives:include=*#*",
                "-XReplacePrimitives:exclude=*#a[cm]*");

        assertPrimitive(generated, field("amount"));
        assertPrimitive(generated, field("active"));
        for (Field field : FIELDS) {
            if (!field.name().equals("amount") && !field.name().equals("active")) {
                assertBoxed(generated, field);
            }
        }
    }

    @Test
    void aFieldThatIsAlreadyBoxedIsLeftAloneAndNotReported() throws Exception {
        // XJC boxes the optional attribute on its own, so a selector naming it has
        // nothing to do and
        // is not an error
        Run run = run(true, "-XReplacePrimitives:include=*#flag");

        assertEquals(0, run.exitCode(), run.messages());
        assertTrue(run.source().contains("protected Boolean flag;"), run.source());
        for (Field field : FIELDS) {
            assertPrimitive(run.source(), field);
        }
        assertFalse(run.messages().contains("[ERROR]"), run.messages());
    }

    @Test
    void aSelectorThatNamesNoFieldIsAnError() throws Exception {
        // the run is quiet, and an error is what a quiet run does not silence
        Run run = run(true, "-XReplacePrimitives:include=*#amout");

        assertNotEquals(0, run.exitCode(), run.messages());
        assertTrue(run.messages().contains(
                "[ERROR] include=*#amout matched no class and no field"), run.messages());
    }

    @Test
    void aSelectorThatNamesNoFieldIsAnErrorWhenItExcludes() throws Exception {
        Run run = run(true, "-XReplacePrimitives:exclude=*#amout");

        assertNotEquals(0, run.exitCode(), run.messages());
        assertTrue(run.messages().contains(
                "[ERROR] exclude=*#amout matched no class and no field"), run.messages());
    }

    @Test
    void onlyTheSelectorThatNamedNothingIsAnError() throws Exception {
        Run run = run(true, "-XReplacePrimitives:include=*#count",
                "-XReplacePrimitives:include=*#amout");

        assertNotEquals(0, run.exitCode(), run.messages());
        assertTrue(run.messages().contains("[ERROR] include=*#amout matched no class and no field"),
                run.messages());
        assertFalse(run.messages().contains("[ERROR] include=*#count"), run.messages());
    }

    @Test
    void aSelectorNamingAClassTheOutlineDoesNotHoldIsAnError() throws Exception {
        // ObjectFactory is generated but is not a class of the outline, so a selector
        // naming it
        // selected nothing
        Run run = run(true, "-XReplacePrimitives:include=*ObjectFactory");

        assertNotEquals(0, run.exitCode(), run.messages());
        assertTrue(run.messages().contains(
                "[ERROR] include=*ObjectFactory matched no class and no field"), run.messages());
    }

    @Test
    void aSelectorThatCannotBeReadIsRefused() {
        PrimitiveFixerPlugin plugin = new PrimitiveFixerPlugin();

        for (String broken : List.of("*#item[9-0]", "*#item[0-9", "*#", "#amount", "")) {
            String argument = "-XReplacePrimitives:include=" + broken;
            BadCommandLineException thrown = assertThrows(BadCommandLineException.class,
                    () -> plugin.parseArgument(new Options(), new String[] { argument }, 0));

            assertTrue(thrown.getMessage().contains("include=" + broken), thrown.getMessage());
        }
    }

    @Test
    void theErrorOfASelectorWithoutAClassOrAFieldNamesTheOptionOnce() {
        PrimitiveFixerPlugin plugin = new PrimitiveFixerPlugin();

        for (String option : List.of("include", "exclude")) {
            String argument = "-XReplacePrimitives:" + option + "=#amount";
            BadCommandLineException thrown = assertThrows(BadCommandLineException.class,
                    () -> plugin.parseArgument(new Options(), new String[] { argument }, 0));

            assertEquals("the option " + argument + ": no class name before the #",
                    thrown.getMessage());
        }

        String argument = "-XReplacePrimitives:exclude=com.acme.Invoice#";
        BadCommandLineException thrown = assertThrows(BadCommandLineException.class,
                () -> plugin.parseArgument(new Options(), new String[] { argument }, 0));

        assertEquals("the option " + argument + ": no field name after the #",
                thrown.getMessage());
    }

    @Test
    void aSelectorThatCannotBeReadStopsTheGeneration() throws Exception {
        BadCommandLineException thrown = assertThrows(BadCommandLineException.class,
                () -> run(true, "-XReplacePrimitives:include=*#item[9-0]"));

        assertTrue(thrown.getMessage().contains("item[9-0]"), thrown.getMessage());
    }

    @Test
    void anOptionWithoutAValueIsRefused() {
        PrimitiveFixerPlugin plugin = new PrimitiveFixerPlugin();
        String[] arguments = { "-XReplacePrimitives:include" };

        BadCommandLineException thrown = assertThrows(BadCommandLineException.class,
                () -> plugin.parseArgument(new Options(), arguments, 0));

        assertTrue(thrown.getMessage().contains("needs a value"), thrown.getMessage());
    }

    @Test
    void anUnknownOptionIsRefused() {
        PrimitiveFixerPlugin plugin = new PrimitiveFixerPlugin();
        String[] arguments = { "-XReplacePrimitives:only=*#count" };

        BadCommandLineException thrown = assertThrows(BadCommandLineException.class,
                () -> plugin.parseArgument(new Options(), arguments, 0));

        assertTrue(thrown.getMessage().contains("include or exclude"), thrown.getMessage());
    }

    @Test
    void aSelectorAloneDoesNotSwitchThePluginOn() throws Exception {
        // XJC activates a plugin for the plain option only: given without it, the
        // selector is read
        // and then nothing is boxed, which is a trap worth pinning
        String generated = generate(false, "-XReplacePrimitives:include=*#count");

        for (Field field : FIELDS) {
            assertPrimitive(generated, field);
        }
    }

    @Test
    void thePlainOptionAndTheArgumentsOfOtherPluginsAreReadAsExpected() throws Exception {
        PrimitiveFixerPlugin plugin = new PrimitiveFixerPlugin();

        assertEquals(1, plugin.parseArgument(new Options(),
                new String[] { "-XReplacePrimitives" }, 0));
        assertEquals(1, plugin.parseArgument(new Options(),
                new String[] { "-XReplacePrimitives:include=*#count" }, 0));
        assertEquals(1, plugin.parseArgument(new Options(),
                new String[] { "-XReplacePrimitives:exclude=*#count" }, 0));
        assertEquals(0, plugin.parseArgument(new Options(),
                new String[] { "-XSomeOtherPlugin" }, 0));
        assertEquals(0, plugin.parseArgument(new Options(),
                new String[] { "-XReplacePrimitives2" }, 0));
    }

    @Test
    void theUsageNamesBothOptions() {
        String usage = new PrimitiveFixerPlugin().getUsage();

        assertTrue(usage.contains("-XReplacePrimitives:include=ClassGlob[#fieldGlob]"), usage);
        assertTrue(usage.contains("-XReplacePrimitives:exclude=ClassGlob[#fieldGlob]"), usage);
        assertTrue(usage.contains("-XReplacePrimitives    :"), usage);
    }

    /**
     * A primitive field whose accessor is missing cannot be followed, and the
     * plugin says so rather than leaving a half-boxed property behind. XJC always
     * writes the accessors, so the situation is built here instead of through a
     * schema.
     */
    @Test
    void aPrimitiveFieldWithoutAccessorsIsRefused() throws Exception {
        JCodeModel codeModel = new JCodeModel();
        JDefinedClass bean = codeModel._class("example.Bean");
        JFieldVar field = bean.field(JMod.PROTECTED, codeModel.INT, "count");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new PrimitiveFixerPlugin().getter(bean, field));

        assertTrue(thrown.getMessage().contains("count"), thrown.getMessage());
    }

    /** Asserts that the field and both its accessors carry the boxed type. */
    private static void assertBoxed(String generated, Field field) {
        assertTrue(generated.contains("protected " + field.boxed() + " " + field.name() + ";"),
                field.name() + " is not boxed: " + generated);
        assertTrue(generated.contains("public " + field.boxed() + " " + field.getter() + "()"),
                field.name() + " has no boxed getter: " + generated);
        assertTrue(generated.contains("public void " + field.setter() + "(" + field.boxed()
                + " value)"), field.name() + " has no boxed setter: " + generated);
        assertFalse(generated.contains("protected " + field.primitive() + " " + field.name() + ";"),
                field.name() + " is still primitive: " + generated);
    }

    /**
     * Asserts that the field and both its accessors carry the type XJC generated.
     */
    private static void assertPrimitive(String generated, Field field) {
        assertTrue(generated.contains("protected " + field.primitive() + " " + field.name() + ";"),
                field.name() + " is not primitive: " + generated);
        assertTrue(generated.contains("public " + field.primitive() + " " + field.getter() + "()"),
                field.name() + " has no primitive getter: " + generated);
        assertTrue(generated.contains("public void " + field.setter() + "(" + field.primitive()
                + " value)"), field.name() + " has no primitive setter: " + generated);
        assertFalse(generated.contains("protected " + field.boxed() + " " + field.name() + ";"),
                field.name() + " is boxed: " + generated);
    }

    private static Field field(String name) {
        return FIELDS.stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /** What one run of XJC produced. */
    private record Run(int exitCode, String messages, String source) {
    }

    /**
     * Runs XJC once over the schema.
     *
     * @param replacePrimitives whether the plugin is switched on
     * @param extraArguments further arguments, such as an {@code include} selector
     */
    private Run run(boolean replacePrimitives, String... extraArguments) throws Exception {
        Path schema = SCHEMA.toAbsolutePath();
        assertTrue(Files.exists(schema), "schema not found: " + schema);

        List<String> arguments = new ArrayList<>();
        arguments.add("-quiet");
        arguments.add("-extension");
        arguments.add("-d");
        arguments.add(outputDirectory.toString());
        if (replacePrimitives) {
            arguments.add("-" + PrimitiveFixerPlugin.PLUGIN_NAME);
        }
        arguments.addAll(List.of(extraArguments));
        arguments.add(schema.toString());

        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream stream = new PrintStream(messages, true, StandardCharsets.UTF_8)) {
            exitCode = Driver.run(arguments.toArray(String[]::new), stream, stream);
        }

        return new Run(exitCode, messages.toString(StandardCharsets.UTF_8),
                exitCode == 0 ? Files.readString(generatedSource()).replaceAll("\\s+", " ") : null);
    }

    /**
     * Runs XJC once and returns the generated class with its whitespace collapsed.
     *
     * @param replacePrimitives whether the plugin is switched on
     * @param extraArguments further arguments, such as an {@code include} selector
     */
    private String generate(boolean replacePrimitives, String... extraArguments) throws Exception {
        Run run = run(replacePrimitives, extraArguments);
        assertEquals(0, run.exitCode(), () -> "xjc failed: " + run.messages());
        return run.source();
    }

    /**
     * The one generated class that holds the fields, whatever package XJC has
     * chosen.
     */
    private Path generatedSource() throws Exception {
        try (Stream<Path> files = Files.walk(outputDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("Primitives.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no generated class in "
                            + outputDirectory));
        }
    }
}

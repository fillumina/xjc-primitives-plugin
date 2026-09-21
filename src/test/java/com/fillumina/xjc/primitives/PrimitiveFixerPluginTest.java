package com.fillumina.xjc.primitives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMod;
import com.sun.tools.xjc.Driver;
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
 * Generates a schema with primitive fields through XJC and checks what the plugin produced.
 *
 * <p>Generation runs in this JVM through {@link Driver}, the entry point of the {@code xjc}
 * command line, so the test needs neither a Maven build nor a lifecycle. The expected text is
 * collapsed to single spaces first, which makes the assertions independent of how the generator
 * wraps its lines.
 */
class PrimitiveFixerPluginTest {

    private static final Path SCHEMA = Path.of("src", "test", "resources", "primitives.xsd");

    @TempDir
    Path outputDirectory;

    @Test
    void replacesPrimitivesWithBoxedTypes() throws Exception {
        String generated = generate(true);

        assertTrue(generated.contains("protected Integer count;"), generated);
        assertTrue(generated.contains("protected Long amount;"), generated);
        assertTrue(generated.contains("protected Boolean active;"), generated);
        assertTrue(generated.contains("protected Double ratio;"), generated);
        assertTrue(generated.contains("protected Byte tiny;"), generated);
        assertTrue(generated.contains("protected Short small;"), generated);
        assertTrue(generated.contains("protected Float fraction;"), generated);
        assertTrue(generated.contains("protected Boolean flag;"), generated);

        assertTrue(generated.contains("public Integer getCount()"), generated);
        assertTrue(generated.contains("public void setCount(Integer value)"), generated);
        assertTrue(generated.contains("public Boolean isActive()"), generated);
        assertTrue(generated.contains("public void setActive(Boolean value)"), generated);
        assertTrue(generated.contains("public Byte getTiny()"), generated);
        assertTrue(generated.contains("public Short getSmall()"), generated);
        assertTrue(generated.contains("public void setFraction(Float value)"), generated);

        assertFalse(generated.contains("protected int count;"), generated);
        assertFalse(generated.contains("public int getCount()"), generated);
        assertFalse(generated.contains("public void setCount(int value)"), generated);
    }

    @Test
    void leavesPrimitivesAloneWhenTheOptionIsNotGiven() throws Exception {
        String generated = generate(false);

        assertTrue(generated.contains("protected int count;"), generated);
        assertTrue(generated.contains("public int getCount()"), generated);
        assertTrue(generated.contains("public void setCount(int value)"), generated);
    }

    /**
     * A primitive field whose accessor is missing cannot be followed, and the plugin says so rather
     * than leaving a half-boxed property behind. XJC always writes the accessors, so the situation
     * is built here instead of through a schema.
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

    /**
     * Runs XJC once and returns the generated class with its whitespace collapsed.
     *
     * @param replacePrimitives whether the plugin is switched on
     */
    private String generate(boolean replacePrimitives) throws Exception {
        Path schema = SCHEMA.toAbsolutePath();
        assertTrue(Files.exists(schema), "schema not found: " + schema);

        List<String> arguments = new ArrayList<>();
        arguments.add("-quiet");
        arguments.add("-d");
        arguments.add(outputDirectory.toString());
        if (replacePrimitives) {
            arguments.add("-extension");
            arguments.add("-" + PrimitiveFixerPlugin.PLUGIN_NAME);
        }
        arguments.add(schema.toString());

        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(messages, true, StandardCharsets.UTF_8)) {
            int exitCode = Driver.run(arguments.toArray(String[]::new), stream, stream);
            assertEquals(0, exitCode, () -> "xjc failed: " + messages);
        }

        Path file = generatedSource();
        return Files.readString(file).replaceAll("\\s+", " ");
    }

    /** The one generated class that holds the fields, whatever package XJC has chosen. */
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

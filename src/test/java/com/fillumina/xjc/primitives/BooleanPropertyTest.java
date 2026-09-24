package com.fillumina.xjc.primitives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.tools.xjc.Driver;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAccessType;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the JavaBeans API of actual compiled XJC output, including selective boxing. */
class BooleanPropertyTest {
    @TempDir Path directory;

    @Test
    void compiledPropertiesStayReadableAndWritable() throws Exception {
        for (String mode : List.of("boxed", "plain", "excluded", "selected")) {
            Path sources = Files.createDirectory(directory.resolve(mode));
            Path classes = Files.createDirectory(directory.resolve(mode + "-classes"));
            List<String> args = new java.util.ArrayList<>(List.of("-quiet", "-extension", "-d", sources.toString()));
            if (!mode.equals("plain")) {
                args.add("-XReplacePrimitives");
            }
            if (mode.equals("excluded")) {
                args.add("-XReplacePrimitives:exclude=*#active");
            }
            if (mode.equals("selected")) {
                args.add("-XReplacePrimitives:include=*#active");
            }
            args.add(Path.of("src/test/resources/primitives.xsd").toAbsolutePath().toString());
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            try (PrintStream stream = new PrintStream(errors)) {
                assertEquals(0, Driver.run(args.toArray(String[]::new), stream, stream), errors.toString());
            }
            List<String> javaFiles;
            try (Stream<Path> files = Files.walk(sources)) {
                javaFiles = files.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList();
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            assertNotNull(compiler);
            List<String> compileArgs = new java.util.ArrayList<>(List.of("-d", classes.toString(),
                    "-classpath", System.getProperty("java.class.path")));
            compileArgs.addAll(javaFiles);
            assertEquals(0, compiler.run(null, null, null, compileArgs.toArray(String[]::new)), mode);
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] { classes.toUri().toURL() },
                    getClass().getClassLoader())) {
                Class<?> bean = loader.loadClass("generated.Primitives");
                assertEquals(XmlAccessType.FIELD, bean.getAnnotation(XmlAccessorType.class).value());
                assertTrue(bean.getDeclaredField("flag").isAnnotationPresent(XmlAttribute.class));
                assertEquals(Boolean.class, bean.getDeclaredField("flag").getType());
                for (String name : List.of("count", "amount", "active", "ratio", "tiny", "small", "fraction")) {
                    boolean boxed = mode.equals("boxed") || (mode.equals("selected") && name.equals("active"))
                            || (mode.equals("excluded") && !name.equals("active"));
                    Class<?> primitive = switch (name) {
                        case "count" -> int.class;
                        case "amount" -> long.class;
                        case "active" -> boolean.class;
                        case "ratio" -> double.class;
                        case "tiny" -> byte.class;
                        case "small" -> short.class;
                        case "fraction" -> float.class;
                        default -> throw new AssertionError(name);
                    };
                    Class<?> expected = primitive;
                    if (boxed) {
                        expected = switch (name) {
                            case "count" -> Integer.class;
                            case "amount" -> Long.class;
                            case "active" -> Boolean.class;
                            case "ratio" -> Double.class;
                            case "tiny" -> Byte.class;
                            case "small" -> Short.class;
                            case "fraction" -> Float.class;
                            default -> throw new AssertionError(name);
                        };
                    }
                    PropertyDescriptor descriptor = Arrays.stream(Introspector.getBeanInfo(bean).getPropertyDescriptors())
                            .filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
                    assertNotNull(descriptor.getReadMethod(), mode + ": " + name);
                    assertNotNull(descriptor.getWriteMethod(), mode + ": " + name);
                    assertEquals(expected, descriptor.getPropertyType(), mode + ": " + name);
                    assertEquals(expected, descriptor.getReadMethod().getReturnType(), mode + ": " + name);
                    assertEquals(expected, descriptor.getWriteMethod().getParameterTypes()[0], mode + ": " + name);
                }
                if (mode.equals("boxed") || mode.equals("selected")) {
                    assertEquals(Boolean.class, bean.getMethod("isActive").getReturnType());
                    assertEquals(Boolean.class, bean.getMethod("getActive").getReturnType());
                    Object instance = bean.getConstructor().newInstance();
                    assertEquals(null, bean.getMethod("getActive").invoke(instance));
                    bean.getMethod("setActive", Boolean.class).invoke(instance, Boolean.TRUE);
                    assertEquals(Boolean.TRUE, bean.getMethod("isActive").invoke(instance));
                    assertEquals(Boolean.TRUE, bean.getMethod("getActive").invoke(instance));
                }
            }
        }
    }
}

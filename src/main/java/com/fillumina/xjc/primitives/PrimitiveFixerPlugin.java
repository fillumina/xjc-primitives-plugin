package com.fillumina.xjc.primitives;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Replaces the primitive type of every generated field, and of the field's getter and setter, with
 * the matching boxed class, so that an annotation can be applied to the property: {@code int}
 * becomes {@link Integer}, {@code boolean} becomes {@link Boolean}, and so on.
 *
 * <p>The plugin is switched on with the {@code -XReplacePrimitives} option and is registered in
 * {@code META-INF/services/com.sun.tools.xjc.Plugin}. The {@code include} and {@code exclude}
 * options narrow which fields are boxed, instead of every primitive the plugin finds. An option
 * that cannot be read stops the generation before it starts; a selector that is well formed but
 * names nothing is only reported.
 *
 * @author Vojtech Krasa
 * @author Francesco Illuminati
 */
public class PrimitiveFixerPlugin extends Plugin {

    /** The option that switches this plugin on, without its leading dash. */
    public static final String PLUGIN_NAME = "XReplacePrimitives";

    /** The plugin with the first option of a run, as it is written on the command line. */
    public static final String OPTION_PREFIX = "-" + PLUGIN_NAME + ":";

    /** The selectors of the {@code include} option, as they were given. */
    private final List<String> includes = new ArrayList<>();

    /** The selectors of the {@code exclude} option, as they were given. */
    private final List<String> excludes = new ArrayList<>();

    /**
     * Creates the plugin. XJC instantiates it from the service file, so it takes no arguments.
     */
    public PrimitiveFixerPlugin() {
    }

    private static final Map<String, Class<?>> BOXED_TYPES = Map.of(
            "int", Integer.class,
            "long", Long.class,
            "boolean", Boolean.class,
            "double", Double.class,
            "float", Float.class,
            "byte", Byte.class,
            "short", Short.class);

    @Override
    public String getOptionName() {
        return PLUGIN_NAME;
    }

    /**
     * Reads one option of this plugin. XJC activates a plugin for the argument equal to {@code "-"}
     * plus {@link #getOptionName()} and hands every other argument to every plugin, so an argument
     * this method does not recognize is left to XJC and the other plugins.
     *
     * <p>An option of this plugin that cannot be read is refused with a {@code BadCommandLineException},
     * which stops the generation before anything is written. A selector that is well formed but names
     * nothing is not refused here: it is reported at the end of the run, see {@link #reportUnmatched}.
     */
    @Override
    public int parseArgument(Options opt, String[] args, int index) throws BadCommandLineException {
        final String argument = args[index];
        if (("-" + PLUGIN_NAME).equals(argument)) {
            // the plain option switches the plugin on, which XJC did before calling this
            return 1;
        }
        if (!argument.startsWith(OPTION_PREFIX)) {
            return 0;
        }
        final String rest = argument.substring(OPTION_PREFIX.length());
        final int equals = rest.indexOf('=');
        if (equals == -1) {
            throw new BadCommandLineException("the option " + argument + " needs a value, as in "
                    + OPTION_PREFIX + "include=com.acme.Invoice#amount");
        }
        final String name = rest.substring(0, equals);
        final String value = rest.substring(equals + 1);
        final String error = Selectors.validate(value);
        if (error != null) {
            throw new BadCommandLineException("the option " + argument + ": " + error);
        }
        if ("include".equals(name)) {
            includes.add(value);
        } else if ("exclude".equals(name)) {
            excludes.add(value);
        } else {
            throw new BadCommandLineException(
                    "unknown option " + argument + ", it is include or exclude");
        }
        return 1;
    }

    @Override
    public String getUsage() {
        return "-" + PLUGIN_NAME + "    :   Replaces the primitive types of the generated fields, "
                + "getters and setters with the matching boxed classes. Must be defined before "
                + "other plugins that copy the property types, such as the ones generating "
                + "equals and hashCode.\n"
                + OPTION_PREFIX + "include=ClassGlob[#fieldGlob]    :   Boxes only the fields "
                + "the selector names, instead of every primitive. Repeat the option to add "
                + "selectors.\n"
                + OPTION_PREFIX + "exclude=ClassGlob[#fieldGlob]    :   Leaves the fields the "
                + "selector names as XJC generated them. Repeat the option to add selectors.\n";
    }

    @Override
    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) throws SAXException {
        Selectors selectors = Selectors.of(includes, excludes);
        for (ClassOutline classOutline : outline.getClasses()) {
            JDefinedClass implClass = classOutline.implClass;
            String className = implClass.fullName();
            for (JFieldVar field : implClass.fields().values()) {
                // serialVersionUID has no getter or setter to follow it
                if ("serialVersionUID".equals(field.name())) {
                    continue;
                }
                // asked of every field, so that a selector naming one there is nothing to do with,
                // as an attribute XJC has boxed on its own, is not reported as a typo
                if (!selectors.accepts(className, field.name())) {
                    continue;
                }
                JType type = field.type();
                if (!type.isPrimitive()) {
                    continue;
                }
                Class<?> boxedClass = BOXED_TYPES.get(type.name());
                if (boxedClass == null) {
                    continue;
                }
                JClass boxedType = boxedType(classOutline, boxedClass);
                field.type(boxedType);
                getter(implClass, field).type(boxedType);
                setter(implClass, field).listParams()[0].type(boxedType);
            }
        }
        reportUnmatched(selectors, errorHandler);
        return true;
    }

    /**
     * Reports every selector that named no class and no field, once every class of the schema has
     * been seen and the verdict is sure: a selector that matched nothing is as wrong as one that
     * cannot be read, and the fields it was meant to name keep their primitive type without a word
     * otherwise.
     */
    private void reportUnmatched(Selectors selectors, ErrorHandler errorHandler) throws SAXException {
        for (Selectors.Selector selector : selectors.unmatched()) {
            // a null locator is an option-level mistake, which XJC reports as an unknown location
            errorHandler.error(new SAXParseException(
                    selector + " matched no class and no field", (Locator) null));
        }
    }

    /**
     * The boxed class as a type of the code model that owns the generated class, so that the field
     * and its accessors all refer to the same model.
     */
    private JClass boxedType(ClassOutline classOutline, Class<?> boxedClass) {
        JCodeModel codeModel = classOutline.implClass.owner();
        return codeModel.ref(boxedClass);
    }

    /**
     * Finds the getter of a field: the method that starts with {@code get} or {@code is} and whose
     * body is a plain {@code return field;}.
     */
    JMethod getter(JDefinedClass type, JFieldVar field) {
        String expected = "return " + field.name() + ";";
        for (JMethod method : type.methods()) {
            String name = method.name();
            if (method.type().isPrimitive()
                    && (name.startsWith("get") || name.startsWith("is"))
                    && expected.equals(firstStatement(method).trim())) {
                return method;
            }
        }
        throw new IllegalStateException("no getter found for " + field.name()
                + ", remove the XReplacePrimitives option and report a bug");
    }

    /**
     * Finds the setter of a field: the method that starts with {@code set} and whose body assigns
     * the field from its own parameter.
     */
    JMethod setter(JDefinedClass type, JFieldVar field) {
        String expected = "this." + field.name() + " =";
        for (JMethod method : type.methods()) {
            String name = method.name();
            if (method.type().isPrimitive()
                    && name.startsWith("set")
                    && firstStatement(method).startsWith(expected)) {
                return method;
            }
        }
        throw new IllegalStateException("no setter found for " + field.name()
                + ", remove the XReplacePrimitives option and report a bug");
    }

    /**
     * The source text of the first statement of a method body, or an empty string when the body is
     * empty.
     */
    private String firstStatement(JMethod method) {
        List<?> statements = method.body().getContents();
        if (statements.isEmpty() || !(statements.get(0) instanceof JStatement statement)) {
            return "";
        }
        StringWriter writer = new StringWriter();
        statement.state(new JFormatter(writer));
        return writer.toString();
    }
}

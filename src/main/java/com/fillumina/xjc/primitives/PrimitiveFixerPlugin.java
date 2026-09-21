package com.fillumina.xjc.primitives;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

/**
 * Replaces the primitive type of every generated field, and of the field's getter and setter, with
 * the matching boxed class, so that an annotation can be applied to the property: {@code int}
 * becomes {@link Integer}, {@code boolean} becomes {@link Boolean}, and so on.
 *
 * <p>The plugin is switched on with the {@code -XReplacePrimitives} option and is registered in
 * {@code META-INF/services/com.sun.tools.xjc.Plugin}.
 *
 * @author Vojtech Krasa
 * @author Francesco Illuminati
 */
public class PrimitiveFixerPlugin extends Plugin {

    /** The option that switches this plugin on, without its leading dash. */
    public static final String PLUGIN_NAME = "XReplacePrimitives";

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

    @Override
    public String getUsage() {
        return "-" + PLUGIN_NAME + "    :   Replaces the primitive types of the generated fields, "
                + "getters and setters with the matching boxed classes. Must be defined before "
                + "other plugins that copy the property types, such as the ones generating "
                + "equals and hashCode.\n";
    }

    @Override
    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) throws SAXException {
        for (ClassOutline classOutline : outline.getClasses()) {
            for (JFieldVar field : classOutline.implClass.fields().values()) {
                // serialVersionUID has no getter or setter to follow it
                if ("serialVersionUID".equals(field.name())) {
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
                getter(classOutline, field).type(boxedType);
                setter(classOutline, field).listParams()[0].type(boxedType);
            }
        }
        return true;
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
    private JMethod getter(ClassOutline classOutline, JFieldVar field) {
        String expected = "return " + field.name() + ";";
        for (JMethod method : classOutline.implClass.methods()) {
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
    private JMethod setter(ClassOutline classOutline, JFieldVar field) {
        String expected = "this." + field.name() + " =";
        for (JMethod method : classOutline.implClass.methods()) {
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

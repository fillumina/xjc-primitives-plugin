package com.fillumina.xjc.primitives;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The selectors of the {@code include} and {@code exclude} options. A selector
 * is {@code ClassGlob[#fieldGlob]}:
 *
 * <ul>
 * <li>the class glob is matched against the qualified name of the generated
 * class, and the field glob against the name of the field, both with the
 * {@link Glob} syntax, which is the one of the {@code override} option of the
 * sibling {@code xjc-bean-validation-plugin};</li>
 * <li>a selector without {@code #} covers every field of the class it
 * names;</li>
 * <li>the options are additive: the same option can be given more than once,
 * and every selector it gives is added to the ones already there.</li>
 * </ul>
 *
 * <p>
 * Without any selector every primitive is boxed. With at least one
 * {@code include} only the fields it selects are boxed, and an {@code exclude}
 * takes fields out of that result whatever the includes say.
 *
 * <p>
 * A selector that is not one a glob can read is refused while the options are
 * read, before anything is generated, see {@link #validate(String)}. A selector
 * that is well formed but names no class and no field of this schema is not an
 * error: it is a warning at the end of the run, because the same selectors are
 * often given to several schemas, and the fields it meant to name keep the type
 * XJC generated for them.
 *
 * @author Francesco Illuminati
 */
final class Selectors {

    private static final Selectors ALL = new Selectors(Collections.<Selector>emptyList(),
            Collections.<Selector>emptyList());

    /** The option a selector belongs to. */
    static enum Option {
        include, exclude
    }

    /**
     * @return {@code null} when the selector is well formed, the reason when it is
     * not.
     */
    static String validate(String selector) {
        try {
            Selector.parse(Option.include, selector);
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    static Selectors of(List<String> includes, List<String> excludes) {
        if (includes.isEmpty() && excludes.isEmpty()) {
            return ALL;
        }
        return new Selectors(parse(Option.include, includes), parse(Option.exclude, excludes));
    }

    private static List<Selector> parse(Option option, List<String> selectors) {
        List<Selector> parsed = new ArrayList<>();
        for (String selector : selectors) {
            parsed.add(Selector.parse(option, selector));
        }
        return parsed;
    }

    private final List<Selector> includes;
    private final List<Selector> excludes;

    private Selectors(List<Selector> includes, List<Selector> excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    /**
     * @return whether the field is to be boxed: every primitive when no selector
     * was given, only the ones an include selects otherwise, and none of the ones
     * an exclude selects
     */
    boolean accepts(String className, String fieldName) {
        // both sides are asked, without short-circuiting, so that a selector is
        // reported as
        // unmatched only when it really selected nothing
        boolean included = includes.isEmpty() || matchesAny(includes, className, fieldName);
        boolean excluded = matchesAny(excludes, className, fieldName);
        return included && !excluded;
    }

    private static boolean matchesAny(List<Selector> selectors, String className, String fieldName) {
        boolean matched = false;
        for (Selector selector : selectors) {
            matched |= selector.matches(className, fieldName);
        }
        return matched;
    }

    /**
     * @return the selectors that selected no class and no field of this schema, which
     * are worth a warning: they may be typos, or they may name fields of another
     * schema the same selectors are given to
     */
    List<Selector> unmatched() {
        List<Selector> unmatched = new ArrayList<>();
        for (Selector selector : includes) {
            if (selector.isUnmatched()) {
                unmatched.add(selector);
            }
        }
        for (Selector selector : excludes) {
            if (selector.isUnmatched()) {
                unmatched.add(selector);
            }
        }
        return unmatched;
    }

    static class Selector {

        private final Option option;
        private final String text;
        private final Glob classGlob;
        private final Glob fieldGlob;
        private boolean matched;

        /**
         * @throws IllegalArgumentException when the selector is not one a glob can
         * read.
         */
        static Selector parse(Option option, String text) {
            if (text == null) {
                throw new IllegalArgumentException("no class name");
            }
            final String value = text.trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("no class name");
            }
            String classGlob = value;
            String fieldGlob = null;
            final int hashIdx = value.indexOf('#');
            if (hashIdx != -1) {
                classGlob = value.substring(0, hashIdx).trim();
                fieldGlob = value.substring(hashIdx + 1).trim();
                if (classGlob.isEmpty()) {
                    throw new IllegalArgumentException("no class name before the #");
                }
                if (fieldGlob.isEmpty()) {
                    throw new IllegalArgumentException("no field name after the #");
                }
            }
            return new Selector(option, value, Glob.of(classGlob),
                    fieldGlob == null ? null : Glob.of(fieldGlob));
        }

        private Selector(Option option, String text, Glob classGlob, Glob fieldGlob) {
            this.option = option;
            this.text = text;
            this.classGlob = classGlob;
            this.fieldGlob = fieldGlob;
        }

        /**
         * @return whether the selector names this field, remembering that it named
         * something.
         */
        boolean matches(String className, String fieldName) {
            if (!classGlob.matches(className)) {
                return false;
            }
            if (fieldGlob != null && !fieldGlob.matches(fieldName)) {
                return false;
            }
            matched = true;
            return true;
        }

        /** @return true when the selector named no class and no field. */
        boolean isUnmatched() {
            return !matched;
        }

        /**
         * @return the selector as it was written on the command line, without the
         * leading dash.
         */
        @Override
        public String toString() {
            return option.name() + "=" + text;
        }
    }
}

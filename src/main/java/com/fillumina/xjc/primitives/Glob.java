package com.fillumina.xjc.primitives;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A glob of a generated name, as the {@code include} and {@code exclude}
 * options write it: {@code *} matches any sequence, {@code ?} one character,
 * and {@code [...]} one character of a set, as {@code [abc]}, of a range, as
 * {@code [a-z]}, or of a negated set, as {@code [!abc]} and {@code [^abc]}.
 * Everything else is literal, so that the {@code .} of a qualified name is not
 * the "any character" of a pattern.
 *
 * <p>
 * The whole name is matched: a character class stands for one character, not
 * for the beginning of a name.
 *
 * <p>
 * The syntax is the one of the {@code override} option of the sibling
 * {@code xjc-bean-validation-plugin}. The matcher is copied from it, method by
 * method, so that the two can be read side by side and a change in one of them
 * shows up as a difference rather than as a surprise.
 *
 * @author Francesco Illuminati
 */
final class Glob {

    private final String text;
    private final Pattern pattern;

    /**
     * @return the glob of the given text
     * @throws IllegalArgumentException when the glob is not one a pattern can read,
     *                                  which is reported as an error of the option
     *                                  rather than failing during generation
     */
    static Glob of(String text) {
        return new Glob(text, toPattern(text));
    }

    private Glob(String text, Pattern pattern) {
        this.text = text;
        this.pattern = pattern;
    }

    /** @return whether the name is one the glob matches. */
    boolean matches(String name) {
        return pattern.matcher(name).matches();
    }

    /**
     * @return the glob as a pattern: {@code *} matches any sequence, {@code ?} one
     *         character, and {@code [...]} one character of a set, as
     *         {@code [abc]}, of a range, as {@code [a-z]}, or of a negated set, as
     *         {@code [!abc]} and {@code [^abc]}. Everything else is literal.
     */
    private static Pattern toPattern(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else if (c == '[') {
                i = appendCharacterClass(glob, i, regex);
            } else {
                appendLiteral(c, regex);
            }
        }
        try {
            return Pattern.compile(regex.append('$').toString());
        } catch (PatternSyntaxException ex) {
            // a set a pattern cannot read, as an inverted range: reported as an option
            // error
            throw new IllegalArgumentException("the glob " + glob + " is not a valid pattern", ex);
        }
    }

    /**
     * Copies the character class that starts at the {@code [} through to its
     * {@code ]}, so that the ranges, the negation and the {@code *} and {@code ?} a
     * class holds keep the meaning they have in a pattern.
     *
     * @return the index of the closing {@code ]}
     */
    private static int appendCharacterClass(String glob, int open, StringBuilder regex) {
        final int close = glob.indexOf(']', open + 1);
        if (close < 0) {
            throw new IllegalArgumentException("no ] closing the [ of the glob " + glob);
        }
        final String content = glob.substring(open + 1, close);
        if (content.isEmpty() || content.equals("!") || content.equals("^")) {
            throw new IllegalArgumentException("the [] of the glob " + glob + " holds no character");
        }
        // Ant writes the negation of a class with ! and a pattern with ^: both are
        // accepted here.
        // A backslash stays literal, so that it cannot swallow the closing bracket.
        final String body = content.charAt(0) == '!' ? "^" + content.substring(1) : content;
        regex.append('[').append(body.replace("\\", "\\\\")).append(']');
        return close;
    }

    /**
     * Appends one literal character, escaped unless it is a letter, a digit or
     * {@code _}: the characters of a generated name that a pattern reads as they
     * are written. A {@code .} and a {@code $} of a qualified name are escaped
     * along with everything else.
     */
    private static void appendLiteral(char c, StringBuilder regex) {
        if (!Character.isLetterOrDigit(c) && c != '_') {
            regex.append('\\');
        }
        regex.append(c);
    }

    @Override
    public String toString() {
        return text;
    }

}

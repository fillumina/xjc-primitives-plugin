package com.fillumina.xjc.primitives;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The glob of a generated name: {@code *}, {@code ?} and the character classes.
 * The cases are the ones the sibling {@code xjc-bean-validation-plugin} pins
 * for its {@code override} option, whose matcher this one is copied from.
 */
class GlobTest {

    @Test
    void aStarMatchesAnySequenceIncludingNone() {
        assertTrue(matches("*", ""));
        assertTrue(matches("*", "anything"));
        assertTrue(matches("com.*.Invoice", "com.acme.Invoice"));
        assertTrue(matches("com*Invoice", "com.acme.Invoice"));
        assertTrue(matches("*.Invoice", "com.acme.Invoice"));
        assertFalse(matches("com.*.Invoice", "com.acme.Invoic"));
    }

    @Test
    void aQuestionMarkMatchesExactlyOneCharacter() {
        assertTrue(matches("item?", "item7"));
        assertFalse(matches("item?", "item"));
        assertFalse(matches("item?", "item77"));
    }

    @Test
    void aGlobWithoutAWildcardIsAnExactMatch() {
        assertTrue(matches("Invoice", "Invoice"));
        assertFalse(matches("Invoice", "Invoice1"));
        assertFalse(matches("Invoice", "invoice"));
    }

    @Test
    void aCharacterClassMatchesOneOfItsCharacters() {
        assertTrue(matches("cod[e]", "code"));
        assertFalse(matches("cod[e]", "codf"));
        // the whole name is matched: a class is one character, not the beginning of a
        // name
        assertFalse(matches("cod[e]", "codee"));
    }

    @Test
    void aCharacterClassMatchesARange() {
        assertTrue(matches("item[0-9]", "item7"));
        assertFalse(matches("item[0-9]", "itemx"));
        assertTrue(matches("item[a-z]", "itemx"));
    }

    @Test
    void aCharacterClassIsNegatedWithAnExclamationMarkOrACaret() {
        assertFalse(matches("item[!0-9]", "item7"));
        assertTrue(matches("item[!0-9]", "itemx"));
        assertFalse(matches("item[^0-9]", "item7"));
        assertTrue(matches("item[^0-9]", "itemx"));
    }

    @Test
    void aStarAndAQuestionMarkAreLiteralInsideAClass() {
        assertTrue(matches("it[*]m", "it*m"));
        assertTrue(matches("it[?]m", "it?m"));
        assertFalse(matches("it[*]m", "item"));
    }

    @Test
    void aHyphenIsLiteralAtTheStartOrTheEndOfAClass() {
        assertTrue(matches("it[-a]m", "it-m"));
        assertTrue(matches("it[-a]m", "itam"));
        assertFalse(matches("it[-a]m", "itbm"));
        assertTrue(matches("it[a-]m", "it-m"));
        assertFalse(matches("it[a-]m", "itbm"));
        assertTrue(matches("it[-]m", "it-m"));
    }

    @Test
    void aClassHoldsSeveralRanges() {
        assertTrue(matches("it[a-c0-9]m", "itbm"));
        assertTrue(matches("it[a-c0-9]m", "it5m"));
        assertFalse(matches("it[a-c0-9]m", "itdm"));
    }

    @Test
    void aCaretThatIsNotTheFirstCharacterOfAClassIsLiteral() {
        assertTrue(matches("it[a^]m", "itam"));
        assertTrue(matches("it[a^]m", "it^m"));
        assertFalse(matches("it[a^]m", "itbm"));
    }

    @Test
    void theClosingBracketIsTheFirstOneAfterTheClass() {
        assertTrue(matches("it[a]]m", "ita]m"));
        assertFalse(matches("it[a]]m", "itam"));
    }

    @Test
    void severalClassesCanShareAGlob() {
        assertTrue(matches("li[n]e[1-3]", "line1"));
        assertTrue(matches("li[n]e[1-3]", "line3"));
        assertFalse(matches("li[n]e[1-3]", "line4"));
        assertFalse(matches("li[n]e[1-3]", "linex"));
        assertTrue(matches("li[n][e]1", "line1"));
    }

    @Test
    void aClassIsOneCharacterAmongTheOthersOfTheGlob() {
        assertTrue(matches("it[0-9]?m", "it7xm"));
        assertFalse(matches("it[0-9]?m", "it7m"));
        assertTrue(matches("[i]tem", "item"));
        assertTrue(matches("ite[m]", "item"));
    }

    @Test
    void aDotOfAQualifiedNameIsLiteral() {
        assertTrue(matches("com.acme.Invoice", "com.acme.Invoice"));
        // a dot that were a metacharacter would match the e of Invoice
        assertFalse(matches("com.acme.Invoic.", "com.acme.Invoice"));
        assertFalse(matches("comXacme.Invoice", "com.acme.Invoice"));
    }

    @Test
    void aDollarOfANestedClassNameIsLiteral() {
        assertTrue(matches("com.acme.Outer$Inner", "com.acme.Outer$Inner"));
        assertFalse(matches("com.acme.Outer$Inner", "com.acme.OuterXInner"));
    }

    @Test
    void aPairOfAmpersandsIsTheEmptyIntersectionOfAPattern() {
        // a pattern reads && inside a class as the intersection of two sets, so this
        // one holds
        // nothing at all. No generated name holds an &, so it is a typo either way, and
        // the selector
        // that carries it is reported as one.
        assertFalse(matches("item[a&&b]", "itema"));
        assertFalse(matches("item[a&&b]", "item&"));
        assertTrue(matches("item[a&b]", "item&"));
    }

    @Test
    void anUnclosedOrEmptyClassIsRejected() {
        assertTrue(matches("item[0-9]", "item7"));
        assertThrows(IllegalArgumentException.class, () -> Glob.of("item[0-9"));
        assertThrows(IllegalArgumentException.class, () -> Glob.of("item[]"));
        assertThrows(IllegalArgumentException.class, () -> Glob.of("item[]]"));
        assertThrows(IllegalArgumentException.class, () -> Glob.of("item[!]"));
        assertThrows(IllegalArgumentException.class, () -> Glob.of("item[^]"));
        // a class a pattern cannot read is reported as an error of the selector
        assertThrows(IllegalArgumentException.class, () -> Glob.of("item[[]"));
        assertThrows(IllegalArgumentException.class, () -> Glob.of("item[9-0]"));
        assertThrows(IllegalArgumentException.class, () -> Glob.of("item[a[b]"));
    }

    @Test
    void theErrorOfAClassThatCannotBeReadNamesTheGlob() {
        for (String glob : List.of("item[0-9", "item[]", "item[9-0]")) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> Glob.of(glob));

            assertTrue(thrown.getMessage().contains(glob), thrown.getMessage());
        }
    }

    private static boolean matches(String glob, String name) {
        return Glob.of(glob).matches(name);
    }
}

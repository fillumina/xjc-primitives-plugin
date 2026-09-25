package com.fillumina.xjc.primitives;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fillumina.xjc.primitives.Selectors.*;

public class Selectors_SelectorTest {

    @Test
    void shouldSelectorRejectAnEmptyGlob() {
        assertThrows(IllegalArgumentException.class, () -> {
            Selectors.Selector.parse(Option.include, "");
        });
    }

    @Test
    void shouldSelectorRejectAnGlobWithOnlyTheSeparator() {
        assertThrows(IllegalArgumentException.class, () -> {
            Selector.parse(Option.include, "#");
        });
    }

    @Test
    void shouldSelectorRejectAnGlobWithOnlyTheSeparatorWithinWhitespaces() {
        assertThrows(IllegalArgumentException.class, () -> {
            Selector.parse(Option.include, " #  ");
        });
    }

    @Test
    void shouldSelectorRejectAnGlobWith2Separators() {
        assertThrows(IllegalArgumentException.class, () -> {
            Selector.parse(Option.include, " ##");
        });
    }

    @Test
    void testIsUnmatched() {
        Selector fresh = Selector.parse(Option.include, "com.acme.Invoice");
        assertTrue(fresh.isUnmatched());

        Selector matched = Selector.parse(Option.include, "com.acme.Invoice");
        assertTrue(matched.matches("com.acme.Invoice", "amount"));
        assertFalse(matched.isUnmatched());

        // an attempt against another class leaves the selector unmatched
        Selector missed = Selector.parse(Option.include, "com.acme.Invoice");
        missed.matches("com.acme.Other", "amount");
        assertTrue(missed.isUnmatched());
    }

    @Test
    void testMatches() {
        Selector wholeClass = Selector.parse(Option.include, "com.acme.Invoice");
        assertTrue(wholeClass.matches("com.acme.Invoice", "amount"));
        assertTrue(wholeClass.matches("com.acme.Invoice", "legacyCode"));
        assertFalse(wholeClass.matches("com.acme.Other", "amount"));

        Selector oneField = Selector.parse(Option.include, "com.acme.Invoice#amount");
        assertTrue(oneField.matches("com.acme.Invoice", "amount"));
        assertFalse(oneField.matches("com.acme.Invoice", "count"));
        assertFalse(oneField.matches("com.other.Invoice", "amount"));

        Selector wildCards = Selector.parse(Option.include, "com.*#line[1-3]");
        assertTrue(wildCards.matches("com.acme.Invoice", "line1"));
        assertTrue(wildCards.matches("com.acme.Invoice", "line3"));
        assertFalse(wildCards.matches("com.acme.Invoice", "line4"));
        assertFalse(wildCards.matches("org.acme.Invoice", "line1"));
    }

    @Test
    void testParse() {
        Selector classOnly = Selector.parse(Option.exclude, "  com.acme.Invoice  ");
        assertTrue(classOnly.matches("com.acme.Invoice", "amount"));
        assertTrue(classOnly.matches("com.acme.Invoice", "anything"));
        assertFalse(classOnly.matches("com.acme.Other", "amount"));

        Selector classAndField = Selector.parse(Option.include, "com.acme.Invoice # amount");
        assertTrue(classAndField.matches("com.acme.Invoice", "amount"));
        assertFalse(classAndField.matches("com.acme.Invoice", "amountx"));

        // a broken glob is refused while the selector is read
        assertThrows(IllegalArgumentException.class, () -> Selector.parse(Option.include, "*#item[9-0]"));

        IllegalArgumentException missingClass = assertThrows(IllegalArgumentException.class,
                () -> Selector.parse(Option.include, "#amount"));
        assertEquals("include #amount: no class name before the #", missingClass.getMessage());

        IllegalArgumentException missingField = assertThrows(IllegalArgumentException.class,
                () -> Selector.parse(Option.exclude, "com.acme.Invoice#"));
        assertEquals("exclude com.acme.Invoice#: no field name after the #", missingField.getMessage());

        for (String empty : new String[] { null, "", "   " }) {
            assertEquals("no class name",
                    assertThrows(IllegalArgumentException.class,
                            () -> Selector.parse(Option.include, empty)).getMessage());
        }
    }

    @Test
    void testToString() {
        assertEquals("include=com.acme.Invoice",
                Selector.parse(Option.include, "com.acme.Invoice").toString());
        assertEquals("exclude=com.acme.Invoice#amount",
                Selector.parse(Option.exclude, "com.acme.Invoice#amount").toString());
        // as written on the command line: the surrounding whitespaces are trimmed away
        assertEquals("include=com.acme.Invoice",
                Selector.parse(Option.include, "  com.acme.Invoice  ").toString());
    }
}

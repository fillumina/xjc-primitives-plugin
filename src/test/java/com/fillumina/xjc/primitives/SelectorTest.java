package com.fillumina.xjc.primitives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * One selector of the {@code include} and {@code exclude} options: how its text
 * is read, which class and field it names, and what it reports when it named
 * nothing.
 */
class SelectorTest {

        private static final String INVOICE = "com.acme.Invoice";

        @Test
        void aSelectorIsReadFromItsText() {
                Selectors.Selector wholeClass = Selectors.Selector.parse(Selectors.Option.include, INVOICE);

                assertTrue(wholeClass.matches(INVOICE, "amount"));
                assertTrue(wholeClass.matches(INVOICE, "legacyCode"));
                assertFalse(wholeClass.matches("com.acme.Other", "amount"));

                Selectors.Selector oneField = Selectors.Selector.parse(Selectors.Option.include,
                                "com.acme.Invoice#amount");

                assertTrue(oneField.matches(INVOICE, "amount"));
                assertFalse(oneField.matches(INVOICE, "count"));
                assertFalse(oneField.matches("com.other.Invoice", "amount"));

                Selectors.Selector wildCards = Selectors.Selector.parse(Selectors.Option.include,
                                "com.*#line[1-3]");

                assertTrue(wildCards.matches(INVOICE, "line1"));
                assertTrue(wildCards.matches(INVOICE, "line3"));
                assertFalse(wildCards.matches(INVOICE, "line4"));
                assertFalse(wildCards.matches("org.acme.Invoice", "line1"));

                // the whitespaces around the class, the field and the # are trimmed away
                Selectors.Selector spaced = Selectors.Selector.parse(Selectors.Option.include,
                                " com.acme.Invoice # amount ");

                assertTrue(spaced.matches(INVOICE, "amount"));
                assertFalse(spaced.matches(INVOICE, "amountx"));
        }

        @Test
        void theErrorOfASelectorWithoutAClassOrAFieldNamesTheMissingPart() {
                for (String withoutAClass : new String[] { null, "", "   " }) {
                        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                                        () -> Selectors.Selector.parse(Selectors.Option.include, withoutAClass));

                        assertEquals("no class name", thrown.getMessage());
                }

                IllegalArgumentException beforeTheHash = assertThrows(IllegalArgumentException.class,
                                () -> Selectors.Selector.parse(Selectors.Option.include, "#amount"));

                assertEquals("no class name before the #", beforeTheHash.getMessage());

                IllegalArgumentException afterTheHash = assertThrows(IllegalArgumentException.class,
                                () -> Selectors.Selector.parse(Selectors.Option.exclude, "com.acme.Invoice#"));

                assertEquals("no field name after the #", afterTheHash.getMessage());
        }

        @Test
        void aSelectorThatNamedNothingIsUnmatched() {
                Selectors.Selector fresh = Selectors.Selector.parse(Selectors.Option.include, INVOICE);

                assertTrue(fresh.isUnmatched());

                // a match against the class and the field clears the verdict
                Selectors.Selector matched = Selectors.Selector.parse(Selectors.Option.include, INVOICE);

                assertTrue(matched.matches(INVOICE, "amount"));
                assertFalse(matched.isUnmatched());

                // an attempt against another class leaves the selector unmatched
                Selectors.Selector missed = Selectors.Selector.parse(Selectors.Option.include, INVOICE);

                assertFalse(missed.matches("com.acme.Other", "amount"));
                assertTrue(missed.isUnmatched());
        }

        @Test
        void aSelectorReadsAsItWasWritten() {
                Selectors.Selector wholeClass = Selectors.Selector.parse(Selectors.Option.include, INVOICE);
                Selectors.Selector excluded = Selectors.Selector.parse(Selectors.Option.exclude,
                                "com.acme.Invoice#amount");
                Selectors.Selector spaced = Selectors.Selector.parse(Selectors.Option.include,
                                "  com.acme.Invoice  ");

                assertEquals("include=com.acme.Invoice", wholeClass.toString());
                assertEquals("exclude=com.acme.Invoice#amount", excluded.toString());
                // the whitespaces around a class-only selector are trimmed away too
                assertEquals("include=com.acme.Invoice", spaced.toString());
        }
}

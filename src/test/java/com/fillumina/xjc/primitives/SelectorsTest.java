package com.fillumina.xjc.primitives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The selectors of the {@code include} and {@code exclude} options: which
 * fields they box, and what is reported when one of them names nothing.
 */
class SelectorsTest {

    private static final String INVOICE = "com.acme.Invoice";

    @Test
    void withoutASelectorEveryFieldIsBoxed() {
        Selectors selectors = Selectors.of(List.of(), List.of());

        assertTrue(selectors.accepts(INVOICE, "amount"));
        assertTrue(selectors.accepts("another.package.Thing", "flag"));
        assertTrue(selectors.unmatched().isEmpty());
    }

    @Test
    void anIncludeBoxesOnlyWhatItNames() {
        Selectors selectors = Selectors.of(List.of("com.acme.Invoice#amount"), List.of());

        assertTrue(selectors.accepts(INVOICE, "amount"));
        assertFalse(selectors.accepts(INVOICE, "legacyCode"));
        assertFalse(selectors.accepts("another.package.Thing", "amount"));
        assertTrue(selectors.unmatched().isEmpty());
    }

    @Test
    void anIncludeWithoutAFieldCoversTheWholeClass() {
        Selectors selectors = Selectors.of(List.of("com.acme.Invoice"), List.of());

        assertTrue(selectors.accepts(INVOICE, "amount"));
        assertTrue(selectors.accepts(INVOICE, "legacyCode"));
        assertFalse(selectors.accepts("com.acme.Other", "amount"));
    }

    @Test
    void aClassGlobHoldsWildcardsAndClasses() {
        for (String selector : List.of("com.acme.*", "*Invoice", "com.*.Invoic?", "com.acme.Invoic[e]",
                "com.acme.Invoic[!f]", "com.acme.Invoice#*")) {
            Selectors selectors = Selectors.of(List.of(selector), List.of());

            assertTrue(selectors.accepts(INVOICE, "amount"), selector);
            assertTrue(selectors.unmatched().isEmpty(), selector);
        }
    }

    @Test
    void aFieldGlobHoldsWildcardsAndClasses() {
        for (String selector : List.of("*#amount", "*#amoun?", "*#amoun[t]", "*#a[mn]ount",
                "*#amoun[!x]")) {
            Selectors selectors = Selectors.of(List.of(selector), List.of());

            assertTrue(selectors.accepts(INVOICE, "amount"), selector);
            assertTrue(selectors.unmatched().isEmpty(), selector);
        }
    }

    @Test
    void severalIncludesAddUp() {
        Selectors selectors = Selectors.of(List.of("*#amount", "*#count"), List.of());

        assertTrue(selectors.accepts(INVOICE, "amount"));
        assertTrue(selectors.accepts(INVOICE, "count"));
        assertFalse(selectors.accepts(INVOICE, "flag"));
        assertTrue(selectors.unmatched().isEmpty());
    }

    @Test
    void severalExcludesAddUp() {
        Selectors selectors = Selectors.of(List.of(), List.of("*#amount", "*#count"));

        assertFalse(selectors.accepts(INVOICE, "amount"));
        assertFalse(selectors.accepts(INVOICE, "count"));
        assertTrue(selectors.accepts(INVOICE, "flag"));
        assertTrue(selectors.unmatched().isEmpty());
    }

    @Test
    void aCharacterClassSelectsSeveralFields() {
        Selectors selectors = Selectors.of(List.of("*#line[1-3]"), List.of());

        assertTrue(selectors.accepts(INVOICE, "line1"));
        assertTrue(selectors.accepts(INVOICE, "line3"));
        assertFalse(selectors.accepts(INVOICE, "line4"));
    }

    @Test
    void anExcludeLeavesTheFieldAlone() {
        Selectors selectors = Selectors.of(List.of(), List.of("*#legacyCode"));

        assertFalse(selectors.accepts(INVOICE, "legacyCode"));
        assertTrue(selectors.accepts(INVOICE, "amount"));
        assertTrue(selectors.unmatched().isEmpty());
    }

    @Test
    void anExcludeWinsOverAnInclude() {
        Selectors selectors = Selectors.of(List.of("com.acme.*"), List.of("*#legacyCode"));

        assertTrue(selectors.accepts(INVOICE, "amount"));
        assertFalse(selectors.accepts(INVOICE, "legacyCode"));
        assertTrue(selectors.unmatched().isEmpty());
    }

    @Test
    void anExcludeIsStillAskedWhenAnIncludeHasAlreadyLeftTheFieldOut() {
        // the exclude names a field the include does not cover: it is asked all the
        // same, so that it
        // is not reported as a typo
        Selectors selectors = Selectors.of(List.of("*#amount"), List.of("*#legacyCode"));

        assertTrue(selectors.accepts(INVOICE, "amount"));
        assertFalse(selectors.accepts(INVOICE, "legacyCode"));
        assertTrue(selectors.unmatched().isEmpty(), selectors.unmatched().toString());
    }

    @Test
    void aSelectorThatNamesNothingIsReported() {
        Selectors selectors = Selectors.of(List.of("*#amout"), List.of("*#legacy"));

        assertFalse(selectors.accepts(INVOICE, "amount"));
        assertEquals(List.of("include=*#amout", "exclude=*#legacy"),
                selectors.unmatched().stream().map(Object::toString).toList());
    }

    @Test
    void aWellFormedSelectorIsValidated() {
        assertNull(Selectors.validate("com.acme.*#amount"));
        assertNull(Selectors.validate(" * "));
        assertNull(Selectors.validate("*#line[1-3]"));
        assertNull(Selectors.validate("*"));

        assertNotNull(Selectors.validate(""));
        assertNotNull(Selectors.validate("   "));
        assertNotNull(Selectors.validate("#amount"));
        assertNotNull(Selectors.validate("*#"));
        assertNotNull(Selectors.validate("*#item[0-9"));
        assertNotNull(Selectors.validate("*#item[9-0]"));
    }

    @Test
    void aSelectorThatCannotBeReadIsRefused() {
        for (String broken : List.of("", "   ", "#amount", "*#", "*#item[0-9", "*#item[9-0]")) {
            assertThrows(IllegalArgumentException.class,
                    () -> Selectors.of(List.of(broken), List.of()), broken);
            assertNotNull(Selectors.validate(broken), broken);
        }
    }

    @Test
    void aSelectorThatMatchedIsNotReported() {
        Selectors selectors = Selectors.of(List.of("*#amount"), List.of());

        assertTrue(selectors.accepts(INVOICE, "amount"));
        assertTrue(selectors.unmatched().isEmpty());
    }

    @Test
    void aSelectorReadsAsItWasWritten() {
        Selectors selectors = Selectors.of(List.of("*#amout"), List.of());

        assertFalse(selectors.accepts(INVOICE, "amount"));
        assertEquals("include=*#amout", selectors.unmatched().get(0).toString());
    }

}

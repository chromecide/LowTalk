package com.chromecide.lowtalk.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFunctionsTest {

    @Test
    void ordinals() {
        assertEquals("1st", TextFunctions.ordinal(1));
        assertEquals("2nd", TextFunctions.ordinal(2));
        assertEquals("3rd", TextFunctions.ordinal(3));
        assertEquals("4th", TextFunctions.ordinal(4));
        assertEquals("11th", TextFunctions.ordinal(11));
        assertEquals("12th", TextFunctions.ordinal(12));
        assertEquals("13th", TextFunctions.ordinal(13));
        assertEquals("21st", TextFunctions.ordinal(21));
        assertEquals("22nd", TextFunctions.ordinal(22));
        assertEquals("103rd", TextFunctions.ordinal(103));
        assertEquals("111th", TextFunctions.ordinal(111));
        assertEquals("0th", TextFunctions.ordinal(0));
    }

    @Test
    void plurals() {
        assertEquals("loaf", TextFunctions.plural(1, "loaf", "loaves"));
        assertEquals("loaves", TextFunctions.plural(2, "loaf", "loaves"));
        assertEquals("coins", TextFunctions.plural(0, "coin", null));
        assertEquals("coin", TextFunctions.plural(1, "coin", ""));
    }
}

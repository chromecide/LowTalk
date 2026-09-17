package com.chromecide.lowtalk.hytale.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The title call is chosen by reflection, so nothing at compile time can say it was chosen correctly. These run
 * against whichever server jar the build is pointed at, and so assert the pairing itself: the styled call exactly
 * when the style enum exists, the boolean call exactly when it does not, and never neither.
 */
class EventTitlesTest {

    private static final String STYLE = "com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle";

    private static boolean styleEnumOnClasspath() {
        try {
            Class.forName(STYLE);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    void aTitleCanAlwaysBeShownOnAServerWeBuildAgainst() {
        assertTrue(EventTitles.available(),
                "neither overload of EventTitleUtil.showEventTitleToPlayer was found, so <<title>> is dead");
        assertEquals(styleEnumOnClasspath() ? EventTitles.Flavour.STYLED : EventTitles.Flavour.BOOLEAN,
                EventTitles.flavour(),
                "the call chosen does not match what this server jar offers");
    }

    /**
     * The styled call is the one 0.7 means to keep, so where both could be resolved it has to win: falling back to
     * the boolean would compile, run, and quietly keep using an API marked for removal.
     */
    @Test
    void theStyledCallIsPreferredWhereItExists() {
        if (!styleEnumOnClasspath()) return;
        assertEquals(EventTitles.Flavour.STYLED, EventTitles.flavour());
    }

    /** Whichever way it resolved, the constants it needs have to have resolved with it. */
    @Test
    void theStyleConstantsExistWhenTheStyledCallIsUsed() throws Exception {
        if (EventTitles.flavour() != EventTitles.Flavour.STYLED) return;
        Class<?> style = Class.forName(STYLE);
        Object major = null;
        Object plain = null;
        for (Object c : style.getEnumConstants()) {
            String n = ((Enum<?>) c).name();
            if (n.equals("Major")) major = c;
            if (n.equals("Default")) plain = c;
        }
        assertNotNull(major, "EventTitleStyle has no Major, so <<title ... major>> has nothing to send");
        assertNotNull(plain, "EventTitleStyle has no Default, so an ordinary title has nothing to send");
    }
}

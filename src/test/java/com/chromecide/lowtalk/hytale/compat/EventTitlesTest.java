package com.chromecide.lowtalk.hytale.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** The styles reported are the game's own spelling, whichever call this server has. */
    @Test
    void theStylesReportedAreTheGamesOwn() {
        assertTrue(EventTitles.styleNames().contains("Default"));
        assertTrue(EventTitles.styleNames().contains("Major"));
        assertEquals("Default", EventTitles.defaultStyle());
        assertEquals("Major", EventTitles.resolveName("major"), "however a creator capitalised it");
        assertEquals("Default", EventTitles.resolveName("minor"), "what dialogues wrote before styles existed");
        assertEquals("Default", EventTitles.resolveName(null), "no style named means the default");
        assertEquals("Default", EventTitles.resolveName(""));
    }

    /**
     * A word that is not a style must not be taken for one. The style and the second line share a slot in
     * <<title>>, so a word waved through here would be read as the style and the line the creator wrote would
     * vanish: on the boolean call every unknown word once resolved to Major, which ate the second line of every
     * two-line title.
     */
    @Test
    void aWordThatIsNotAStyleIsRefused() {
        assertNull(EventTitles.resolveName("nonsense"));
        assertFalse(EventTitles.knows("nonsense"));
        assertNull(EventTitles.resolveName("you have entered"), "a second line is not a style");
        assertFalse(EventTitles.knows("you have entered"));
    }

    /** The themed styles belong to the versions that have them, and are refused where they do not. */
    @Test
    void aStyleThisServerDoesNotHaveIsRefusedRatherThanGuessed() {
        boolean has = EventTitles.styleNames().stream().anyMatch(n -> n.equalsIgnoreCase("GoblinBreach"));
        assertEquals(has, EventTitles.knows("GoblinBreach"));
        assertEquals(has, EventTitles.knows("goblinbreach"), "case must not change the answer");
        if (!has) assertNull(EventTitles.resolveName("GoblinBreach"));
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

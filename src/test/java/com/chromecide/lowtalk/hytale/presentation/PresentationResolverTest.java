package com.chromecide.lowtalk.hytale.presentation;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.DialogueParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PresentationResolverTest {

    private static Dialogue dialogue(String directives) {
        return DialogueParser.parse("t.talk", "npc: none\n" + directives + "\n== start\nHi.\n", null);
    }

    private static PresentationResolver resolver(String pack) {
        return new PresentationResolver(d -> pack, id -> pack);
    }

    @Test
    void builtInDefaultIsBottom() {
        Presentation p = resolver("").resolve(dialogue(""));
        assertEquals(DialogueLayout.BOTTOM, p.layout());
        assertTrue(p.hideHud().isEmpty());
    }

    @Test
    void serverConfigBeatsBuiltIn() {
        PresentationResolver r = resolver("");
        r.setServerDefaults(new Presentation.Defaults(DialogueLayout.WINDOW, List.of("Reticle")), null);
        Presentation p = r.resolve(dialogue(""));
        assertEquals(DialogueLayout.WINDOW, p.layout());
        assertEquals(List.of("Reticle"), List.copyOf(p.hideHud()));
    }

    @Test
    void apiDefaultsBeatServerConfig() {
        PresentationResolver r = resolver("Chromecide:Companions");
        r.setServerDefaults(new Presentation.Defaults(DialogueLayout.WINDOW, List.of("Reticle")), null);
        r.setApiDefaults("Chromecide:Companions", new Presentation.Defaults(DialogueLayout.TOP, null));
        Presentation p = r.resolve(dialogue(""));
        assertEquals(DialogueLayout.TOP, p.layout());
        assertEquals(List.of("Reticle"), List.copyOf(p.hideHud()), "an undecided field falls through to the next level");
    }

    @Test
    void packFileBeatsApiDefaults() {
        PresentationResolver r = resolver("Some:Pack");
        r.setApiDefaults("Some:Pack", new Presentation.Defaults(DialogueLayout.TOP, List.of("Hotbar")));
        r.setPackFiles(Map.of("Some:Pack", new Presentation.Defaults(DialogueLayout.WINDOW, null)));
        Presentation p = r.resolve(dialogue(""));
        assertEquals(DialogueLayout.WINDOW, p.layout());
        assertEquals(List.of("Hotbar"), List.copyOf(p.hideHud()));
    }

    @Test
    void directiveBeatsEverythingExceptForce() {
        PresentationResolver r = resolver("Some:Pack");
        r.setPackFiles(Map.of("Some:Pack", new Presentation.Defaults(DialogueLayout.WINDOW, null)));
        assertEquals(DialogueLayout.TOP, r.resolve(dialogue("layout: top")).layout());
        assertEquals(DialogueLayout.TOP, r.resolve(dialogue("layout: TOP")).layout(), "case-insensitive");
        r.setServerDefaults(Presentation.Defaults.NONE, DialogueLayout.BOTTOM);
        assertEquals(DialogueLayout.BOTTOM, r.resolve(dialogue("layout: top")).layout(), "ForceLayout wins");
    }

    @Test
    void unknownDirectiveValueFallsThrough() {
        PresentationResolver r = resolver("");
        r.setServerDefaults(new Presentation.Defaults(DialogueLayout.WINDOW, null), null);
        assertEquals(DialogueLayout.WINDOW, r.resolve(dialogue("layout: sideways")).layout());
    }

    @Test
    void otherPacksAreNotAffected() {
        PresentationResolver r = resolver("Other:Pack");
        r.setPackFiles(Map.of("Some:Pack", new Presentation.Defaults(DialogueLayout.WINDOW, List.of("Hotbar"))));
        Presentation p = r.resolve(dialogue(""));
        assertEquals(DialogueLayout.BOTTOM, p.layout());
        assertTrue(p.hideHud().isEmpty());
    }

    @Test
    void explainNamesTheDecidingLevel() {
        PresentationResolver r = resolver("Some:Pack");
        assertEquals("bottom, built in", r.explainLayoutDefault("x"));
        r.setServerDefaults(new Presentation.Defaults(DialogueLayout.WINDOW, null), null);
        assertEquals("window, the server config", r.explainLayoutDefault("x"));
        r.setApiDefaults("Some:Pack", new Presentation.Defaults(DialogueLayout.TOP, null));
        assertEquals("top, set by the pack's plugin", r.explainLayoutDefault("x"));
        r.setPackFiles(Map.of("Some:Pack", new Presentation.Defaults(DialogueLayout.BOTTOM, null)));
        assertEquals("bottom, from the pack's Settings.json", r.explainLayoutDefault("x"));
        assertEquals("the dialogue's layout: directive", r.explainLayout(dialogue("layout: window")));
    }

    @Test
    void layoutKeysParse() {
        assertEquals(DialogueLayout.WINDOW, DialogueLayout.parse(" Window "));
        assertNull(DialogueLayout.parse(null));
        assertNull(DialogueLayout.parse(""));
        assertNull(DialogueLayout.parse("middle"));
        assertEquals("window, bottom, top", DialogueLayout.keys());
        assertTrue(DialogueLayout.BOTTOM.isBar());
        assertFalse(DialogueLayout.WINDOW.isBar());
    }
}

package com.chromecide.lowtalk.editor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title styles are the game's own vocabulary, read off its style enum at start-up rather than listed here. These
 * are the rules that hold whatever the game reports: what a creator may write, what gets written back, and that a
 * dialogue from before styles existed still means what it meant.
 */
class TitleStyleTest {

    @AfterEach
    void restoreTheDefaults() {
        CommandSpecs.setTitleStyles(List.of("Default", "Major"));
    }

    @Test
    void theStylesEveryVersionHasAreOfferedBeforeAnyServerSpeaks() {
        assertTrue(CommandSpecs.titleStyles().contains("Default"));
        assertTrue(CommandSpecs.titleStyles().contains("Major"));
    }

    @Test
    void aStyleTheGameAddsIsAcceptedWithNoChangeHere() {
        CommandSpecs.setTitleStyles(List.of("Default", "Major", "GoblinBreach", "VoidEviction"));
        assertTrue(CommandSpecs.isTitleStyle("GoblinBreach"), "a style the running server reports must be offered");
        assertTrue(CommandSpecs.isTitleStyle("voideviction"), "however the creator capitalised it");
        assertEquals("GoblinBreach", CommandSpecs.canonicalTitleStyle("goblinbreach"));
        assertEquals(List.of("Default", "Major", "GoblinBreach", "VoidEviction"), CommandSpecs.titleStyles());
    }

    /** The two words dialogues used before the game had styles. They still have to mean something. */
    @Test
    void theOldSpellingsStillRead() {
        assertTrue(CommandSpecs.isTitleStyle("major"));
        assertTrue(CommandSpecs.isTitleStyle("minor"));
        assertEquals("Major", CommandSpecs.canonicalTitleStyle("major"));
        assertEquals("Default", CommandSpecs.canonicalTitleStyle("minor"), "minor was the default treatment");
    }

    @Test
    void aWordThatIsNotAStyleIsNotTreatedAsOne() {
        assertFalse(CommandSpecs.isTitleStyle("the road east"), "a second line is not a style");
        assertFalse(CommandSpecs.isTitleStyle(""));
        assertFalse(CommandSpecs.isTitleStyle(null));
        assertFalse(CommandSpecs.isTitleStyle("GoblinBreach"), "not until a server says it has it");
    }

    /** An empty or null list must not blank the vocabulary: the editor would offer nothing at all. */
    @Test
    void anEmptyReportIsIgnored() {
        CommandSpecs.setTitleStyles(List.of());
        assertTrue(CommandSpecs.titleStyles().contains("Major"));
        CommandSpecs.setTitleStyles(null);
        assertTrue(CommandSpecs.titleStyles().contains("Major"));
    }

    /** The row the editor lays out has to follow the list, or a style would be shown in the wrong field. */
    @Test
    void theEditorLaysOutAStyleWhereverItSits() {
        CommandSpecs.setTitleStyles(List.of("Default", "Major", "GoblinBreach"));
        CommandSpecs.Spec spec = CommandSpecs.of("title");
        assertEquals(4, spec.size());
        assertEquals(List.of("Chapter One", "the road east", "GoblinBreach", "5"),
                CommandSpecs.values(TitleStyleTest.cmd("title", "Chapter One", "5", "GoblinBreach", "the road east")));
    }

    private static com.chromecide.lowtalk.model.Statement.Command cmd(String name, String... args) {
        java.util.List<com.chromecide.lowtalk.model.Text> texts = new java.util.ArrayList<>();
        for (String a : args) texts.add(com.chromecide.lowtalk.model.Text.plain(a));
        return new com.chromecide.lowtalk.model.Statement.Command(
                new com.chromecide.lowtalk.model.Pos("test", 1), name, List.copyOf(texts));
    }
}

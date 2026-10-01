package com.chromecide.lowtalk.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model only sends what changed, which is what makes a page cheap to redraw — and is also the one way a
 * page can be wrong in a way nothing else notices. These pin the rules down.
 */
class UiModelTest {

    @Test
    void writingTheSameValueAgainSendsNothing() {
        UiModel model = new UiModel();
        model.set("title", "hello");
        assertTrue(model.changedProperties().contains("title"));
        model.accept();
        model.set("title", "hello");
        assertFalse(model.hasChanges(), "an unchanged value should not be re-sent");
    }

    /**
     * The case that broke the dialogue window: what the player typed exists only on the client, so the model
     * still holds the empty string it sent for the previous question. Setting it to empty again looks like no
     * change at all, and the second question arrives with the first answer still in the box.
     */
    @Test
    void resendPutsAControlBackWhenTheClientChangedItBehindOurBack() {
        UiModel model = new UiModel();
        model.set("inputValue", "");
        model.accept();
        model.set("inputValue", "");               // the player has typed since; the model cannot know
        assertFalse(model.hasChanges());
        model.resend("inputValue");
        assertTrue(model.changedProperties().contains("inputValue"),
                "resend is how a page empties a box the client has written into");
    }

    @Test
    void resendIgnoresAPropertyThatWasNeverSet() {
        UiModel model = new UiModel();
        model.resend("neverSet");
        assertFalse(model.hasChanges());
    }

    @Test
    void textComparesByValueSoTranslationsDoNotResendForever() {
        assertEquals(UiText.key("a.b").param("n", "1"), UiText.key("a.b").param("n", "1"));
        assertFalse(UiText.key("a.b").equals(UiText.key("a.b").color("#ff0000")));
        UiModel model = new UiModel();
        model.setKey("status", "a.b");
        model.accept();
        model.setKey("status", "a.b");
        assertFalse(model.hasChanges(), "the same translation twice is not a change");
    }

    @Test
    void aListOfChoicesComparesByValue() {
        UiModel model = new UiModel();
        model.set("entries", List.of(UiOption.of("a", "A"), UiOption.of("b", "B")));
        model.accept();
        model.set("entries", List.of(UiOption.of("a", "A"), UiOption.of("b", "B")));
        assertFalse(model.hasChanges(), "an unchanged dropdown should not be re-sent");
    }

    /**
     * Plain strings and the server's own types are refused on purpose: a String would hide whether text is
     * literal or translated, and the server's message and entry types have no equals, so holding either would
     * re-send everything on every draw without ever saying so.
     */
    @Test
    void valuesThatCannotBeComparedAreRefused() {
        UiModel model = new UiModel();
        assertThrows(IllegalArgumentException.class, () -> model.set("title", (Object) "a plain string"));
        assertThrows(IllegalArgumentException.class, () -> model.set("entries", List.of(new Object())));
    }

    @Test
    void oneFieldOfOneRowTravelsWithoutRedrawingTheList() {
        UiModel model = new UiModel();
        model.setItems("rows", List.of(UiItem.of("r").with("a", "1"), UiItem.of("r").with("a", "2")));
        model.accept();
        model.setItemField("rows", 1, "a", UiText.of("9"));
        assertFalse(model.changedLists().contains("rows"), "the whole list should not be marked");
        assertTrue(model.changedCells().contains(new UiModel.Cell("rows", 1, "a")));
        assertEquals(UiText.of("9"), model.items("rows").get(1).get("a"));
    }

    @Test
    void settingARowFieldToWhatItAlreadyHoldsSendsNothing() {
        UiModel model = new UiModel();
        model.setItems("rows", List.of(UiItem.of("r").with("a", "1")));
        model.accept();
        model.setItemField("rows", 0, "a", UiText.of("1"));
        assertFalse(model.hasChanges());
    }

    @Test
    void aRebuiltPageSendsEverythingBecauseTheClientStartsEmpty() {
        UiModel model = new UiModel();
        model.set("title", "hello");
        model.setItems("rows", List.of(UiItem.of("r")));
        model.accept();
        assertFalse(model.hasChanges());
        model.markAllChanged();
        assertTrue(model.changedProperties().contains("title"));
        assertTrue(model.changedLists().contains("rows"));
    }
}

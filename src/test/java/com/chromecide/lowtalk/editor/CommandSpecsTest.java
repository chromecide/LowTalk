package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The in-game editor shows a command as named fields; these are the rules for getting between the two. */
class CommandSpecsTest {

    private static Statement.Command cmd(String name, String... args) {
        List<Text> list = new ArrayList<>();
        for (String a : args) list.add(Text.plain(a));
        return new Statement.Command(new Pos("test", 1), name, List.copyOf(list));
    }

    private static String round(String name, String... args) {
        Statement.Command c = cmd(name, args);
        List<String> values = CommandSpecs.values(c);
        assertNotNull(values, name + " should fit its fields");
        return CommandSpecs.join(CommandSpecs.of(name), values);
    }

    @Test
    void everyArgumentGetsAField() {
        assertEquals(List.of("Food_Bread", "2"), CommandSpecs.values(cmd("give", "Food_Bread", "2")));
    }

    @Test
    void argumentsNotWrittenYetAreStillShown() {
        // the point of the fields: an author who has typed only the particle can see that a scale and a number of
        // seconds may follow, which the old single box of text never told them
        assertEquals(List.of("Smoke", "", "", ""), CommandSpecs.values(cmd("vfx", "Smoke")));
        assertEquals("Smoke", round("vfx", "Smoke"));
    }

    @Test
    void emptyArgumentsInTheMiddleKeepTheirPlace() {
        Statement.Command c = cmd("vfx", "Smoke");
        List<String> values = CommandSpecs.values(c);
        values.set(2, "4");
        assertEquals("Smoke \"\" 4", CommandSpecs.join(CommandSpecs.of("vfx"), values));
    }

    @Test
    void whereAParticlePlaysIsFoundWhereverItSits() {
        assertEquals(List.of("Smoke", "", "", "player"), CommandSpecs.values(cmd("vfx", "Smoke", "player")));
        assertEquals(List.of("Smoke", "2", "1", "player"), CommandSpecs.values(cmd("vfx", "Smoke", "2", "player", "1")));
        assertEquals("Smoke 2 1 player", round("vfx", "Smoke", "2", "player", "1"));
    }

    @Test
    void aStyleIsFoundWhereverItSits() {
        assertEquals(List.of("Saved", "", "success"), CommandSpecs.values(cmd("notify", "Saved", "success")));
        assertEquals(List.of("Saved", "your progress", "success"),
                CommandSpecs.values(cmd("notify", "Saved", "success", "your progress")));
    }

    @Test
    void aTitleSortsItsSizeAndItsSeconds() {
        assertEquals(List.of("Chapter One", "the road east", "major", "5"),
                CommandSpecs.values(cmd("title", "Chapter One", "5", "major", "the road east")));
    }

    @Test
    void textWithSpacesIsQuotedOnTheWayBack() {
        assertEquals("\"Well met\" \"traveller of the north\"", round("notify", "Well met", "traveller of the north"));
    }

    @Test
    void anObjectiveWithNoVerbIsAStart() {
        assertEquals(List.of("start", "Find_The_Elder"), CommandSpecs.values(cmd("objective", "Find_The_Elder")));
        assertEquals(List.of("cancel", "Find_The_Elder"), CommandSpecs.values(cmd("objective", "cancel", "Find_The_Elder")));
        assertEquals(List.of("start", ""), CommandSpecs.values(cmd("objective", "start")));
    }

    @Test
    void whatAnObjectiveOffersDependsOnTheVerb() {
        var spec = CommandSpecs.of("objective");
        assertEquals(CommandSpecs.OBJECTIVES, CommandSpecs.datasetFor(spec, 1, List.of("start", "")));
        assertEquals(CommandSpecs.OBJECTIVE_LINES, CommandSpecs.datasetFor(spec, 1, List.of("line", "")));
        assertNull(CommandSpecs.datasetFor(spec, 1, List.of("task", "")), "task ids live inside an objective");
    }

    @Test
    void thePickerFillsTheArgumentThatIsAnId() {
        // the reputation picker used to write a group name into the amount, and the objective picker offered
        // dialogue ids; the slot a command's picker fills is now the one its spec says is an id
        assertEquals(1, CommandSpecs.assetSlot(CommandSpecs.of("reputation")));
        assertEquals(0, CommandSpecs.assetSlot(CommandSpecs.of("give")));
        assertEquals(1, CommandSpecs.assetSlot(CommandSpecs.of("objective")));
        assertEquals(-1, CommandSpecs.assetSlot(CommandSpecs.of("notify")), "a style is a choice, not an id");
        assertEquals(-1, CommandSpecs.assetSlot(CommandSpecs.of("title")));
    }

    @Test
    void anythingThatDoesNotFitFallsBackToPlainText() {
        assertNull(CommandSpecs.values(cmd("teleport", "10", "64", "20")), "coordinates are not one warp name");
        assertNull(CommandSpecs.values(cmd("some_other_mods_command", "x")));
        assertNull(CommandSpecs.of("nonsense"));
    }

    @Test
    void aCommandWithNoArgumentsShowsNoFields() {
        assertEquals(List.of(), CommandSpecs.values(cmd("despawn")));
    }

    /**
     * A command that takes no arguments must say so, not say nothing.
     *
     * <p>{@code of()} returning null means "the editor has no argument names for this", and the editor then
     * falls back to the plain text box — which offers a field for an argument the command does not take.
     * {@code <<calm>>} shipped that way for an hour and a tester asked whether it had a parameter.
     */
    @Test
    void aCommandWithNoArgumentsHasAnEmptySpecRatherThanNone() {
        CommandSpecs.Spec spec = CommandSpecs.of("calm");
        assertNotNull(spec, "calm has no arguments, which is not the same as having no spec");
        assertEquals(0, spec.size());

        Statement.Command calm = cmd("calm");
        List<String> values = CommandSpecs.values(calm);
        assertNotNull(values, "an empty argument list fits an empty spec");
        assertTrue(values.isEmpty(), "so the row draws no argument fields at all");
    }
}

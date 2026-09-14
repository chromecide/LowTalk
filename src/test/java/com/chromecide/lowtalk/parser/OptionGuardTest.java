package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An option's condition may compare numbers.
 *
 * The pattern that lifts {@code <<if>>} off the end of an option used to refuse any condition containing an angle
 * bracket, so {@code <<if stat("Health") < max_stat("Health")>>} was not a condition at all: the option was always
 * offered and the player was shown the raw marker as part of the option's words. One of the shipped examples did
 * exactly that.
 */
class OptionGuardTest {

    private static Option only(String source) {
        Dialogue d = DialogueParser.parse("t.talk", "npc: none\n== start\n" + source + "\n", null);
        Statement s = d.nodes().get("start").body().get(0);
        return ((Statement.Choice) s).options().get(0);
    }

    @Test
    void aConditionMayCompareNumbers() {
        Option o = only("-> I'm hurt, Elder. <<if stat(\"Health\") < max_stat(\"Health\")>>\n    Elder: Hold still.");
        assertEquals("I'm hurt, Elder.", Printer.text(o.text()));
        assertNotNull(o.guard(), "the condition is a condition, not part of the words");
        assertEquals("stat(\"Health\") < max_stat(\"Health\")", Printer.expr(o.guard()));
    }

    @Test
    void greaterThanWorksTheSameWay() {
        Option o = only("-> I am rich. <<show if count(\"Coin\") > 10>>\n    <<end>>");
        assertEquals("I am rich.", Printer.text(o.text()));
        assertNotNull(o.showGuard());
    }

    @Test
    void severalModifiersStillComeOffInAnyOrder() {
        Option o = only("-> Take it. <<if $met>> <<once>>\n    <<end>>");
        assertEquals("Take it.", Printer.text(o.text()));
        assertNotNull(o.guard());
        assertTrue(o.once());
    }

    @Test
    void wordsThatAreNotAMarkerAreLeftAlone() {
        Option o = only("-> Is 5 > 3?\n    <<end>>");
        assertEquals("Is 5 > 3?", Printer.text(o.text()));
        assertNull(o.guard());
    }

    @Test
    void theShippedExampleHasNoComplaints() {
        // the warning that found this said the option's words contained a command; with the condition parsed it goes
        Dialogue d = DialogueParser.parse("t.talk",
                "npc: none\n== start\n-> I'm hurt. <<if stat(\"Health\") < max_stat(\"Health\")>>\n    <<heal>>\n", null);
        assertTrue(new Validator(Set.of(), Set.of()).validate(d).stream().noneMatch(p -> p.message().contains("command")),
                "no complaint about a command hidden in the words");
    }
}

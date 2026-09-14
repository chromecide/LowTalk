package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.parser.ExprParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The editor shows a condition as a thing, a comparison and a value; these are the rules for reading it back. */
class ConditionShapesTest {

    private static Expr expr(String source) {
        return ExprParser.parse(source, new Pos("test", 1));
    }

    private static ConditionShapes.Shape shape(String source) {
        return ConditionShapes.read(expr(source));
    }

    /** Read a condition into fields and write it back: what a creator sees must not change what it means. */
    private static void roundTrip(String source) {
        ConditionShapes.Shape s = shape(source);
        assertNotNull(s, source + " should fit the fields");
        assertEquals(source, ConditionShapes.write(s));
    }

    @Test
    void aVariableIsSetOrNot() {
        ConditionShapes.Shape s = shape("$met");
        assertNotNull(s);
        assertEquals(ConditionShapes.Kind.VARIABLE, s.kind());
        assertEquals("$met", s.arg());
        assertEquals("", s.op());
        roundTrip("$met");
        roundTrip("not $met");
    }

    @Test
    void aVariableCanAlsoBeComparedWithSomething() {
        ConditionShapes.Shape s = shape("$coins > 3");
        assertNotNull(s);
        assertEquals(">", s.op());
        assertEquals("3", s.value());
        roundTrip("$coins > 3");
        roundTrip("$name == \"Bob\"");
    }

    @Test
    void holdingAnItemIsAYesOrNo() {
        ConditionShapes.Shape s = shape("has(\"Food_Bread\")");
        assertNotNull(s);
        assertEquals(ConditionShapes.Kind.ITEM, s.kind());
        assertEquals("Food_Bread", s.arg());
        roundTrip("has(\"Food_Bread\")");
        roundTrip("not has(\"Food_Bread\")");
    }

    @Test
    void theShapesWithAnIdAndAValue() {
        roundTrip("objective(\"Find_The_Elder\") == \"complete\"");
        roundTrip("count(\"Coin\") >= 10");
        roundTrip("stat(\"Health\") < 20");
    }

    @Test
    void theShapesWithNoIdAtAll() {
        roundTrip("attitude() == \"friendly\"");
        roundTrip("hour() < 6");
        roundTrip("weather() == \"Rain\"");
    }

    @Test
    void aGroupIsOptional() {
        roundTrip("reputation() >= 50");
        roundTrip("reputation(\"Kweebec\") >= 50");
    }

    @Test
    void aChanceKeepsItsFraction() {
        ConditionShapes.Shape s = shape("chance(0.33)");
        assertNotNull(s);
        assertEquals("0.33", s.arg());
        roundTrip("chance(0.33)");
    }

    @Test
    void anythingWithMoreThanOneIdeaStaysAsText() {
        assertNull(shape("$met and $paid"), "and is not one of the shapes");
        assertNull(shape("hour() > 6 and hour() < 20"));
        assertNull(shape("not ($met == true)"), "a negated comparison is not one of the shapes");
        assertNull(shape("stat(\"Health\") < max_stat(\"Health\")"), "compared with another call, not a value");
        assertNull(shape("$coins + 1 > 3"));
    }

    @Test
    void aYesOrNoShapeComparedWithSomethingIsNotOurs() {
        assertNull(shape("has(\"Food_Bread\") == true"));
    }

    @Test
    void aComparisonSurvivesBeingPickedBeforeTheValueIsFilledIn() {
        // choosing "is" from the comparison menu used to be thrown away, because a blank value made it write the
        // condition back as a plain yes, which read back as "yes" and snapped the menu shut again
        ConditionShapes.Shape half = new ConditionShapes.Shape(ConditionShapes.Kind.OBJECTIVE, "Find_The_Elder", "==", "");
        String written = ConditionShapes.write(half);
        assertEquals("objective(\"Find_The_Elder\") == \"\"", written);
        ConditionShapes.Shape back = shape(written);
        assertNotNull(back);
        assertEquals("==", back.op());
        assertEquals("", back.value());
    }

    @Test
    void aKindThatNeedsAValueStartsWithOne() {
        ConditionShapes.Shape hour = new ConditionShapes.Shape(ConditionShapes.Kind.HOUR, "", "<", "");
        assertEquals("hour() < 0", ConditionShapes.write(hour));
        assertNotNull(shape(ConditionShapes.write(hour)));
    }

    @Test
    void turningAComparisonBackIntoAPlainYes() {
        ConditionShapes.Shape yes = new ConditionShapes.Shape(ConditionShapes.Kind.VARIABLE, "$met", "", "3");
        assertEquals("$met", ConditionShapes.write(yes), "with no comparison there is nothing to compare with");
        ConditionShapes.Shape no = new ConditionShapes.Shape(ConditionShapes.Kind.VARIABLE, "$met", "not", "");
        assertEquals("not $met", ConditionShapes.write(no));
    }

    @Test
    void whatIsWrittenBackIsQuotedTheWayTheLanguageWantsIt() {
        ConditionShapes.Shape objective = new ConditionShapes.Shape(
                ConditionShapes.Kind.OBJECTIVE, "Find_The_Elder", "==", "complete");
        assertEquals("objective(\"Find_The_Elder\") == \"complete\"", ConditionShapes.write(objective));
        ConditionShapes.Shape hour = new ConditionShapes.Shape(ConditionShapes.Kind.HOUR, "", "<", "6");
        assertEquals("hour() < 6", ConditionShapes.write(hour));
        ConditionShapes.Shape blank = new ConditionShapes.Shape(ConditionShapes.Kind.VARIABLE, "", "", "");
        assertEquals("$flag", ConditionShapes.write(blank), "a half-written condition is still readable");
    }
}

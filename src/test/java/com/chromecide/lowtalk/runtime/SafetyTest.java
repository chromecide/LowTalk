package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.DialogueParser;
import com.chromecide.lowtalk.parser.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Guards against players abusing dialogues: <<run>> backstops and farmable-reward warnings. */
class SafetyTest {

    private final FakeContext ctx = new FakeContext();

    private Conversation conv(String src) {
        return new Conversation(DialogueParser.parse("t.talk", src), ctx);
    }

    @Test
    void runRefusesTypedText() {
        Conversation c = conv("== a\n<<input $tmp.name \"Name?\">>\n<<run \"/give {$tmp.name} Food_Bread\">>\nDone.\n");
        assertInstanceOf(Step.Ask.class, c.start().step());
        RuntimeError e = assertThrows(RuntimeError.class, () -> c.answer("Chromecide; op add me"));
        assertTrue(e.getMessage().contains("typed"), e.getMessage());
    }

    @Test
    void runRefusesValuesThatAreNotPlainIds() {
        ctx.vars.put("player.title", "x /op add me");
        Conversation c = conv("== a\n<<run \"/say {$player.title}\">>\nDone.\n");
        RuntimeError e = assertThrows(RuntimeError.class, c::start);
        assertTrue(e.getMessage().contains("plain name or id"), e.getMessage());

        FakeContext ok = new FakeContext();
        ok.vars.put("player.rank", "Knight_2");
        Conversation c2 = new Conversation(DialogueParser.parse("t.talk", "== a\n<<run \"/title {player} {$player.rank}\">>\nDone.\n"), ok);
        Conversation.Result r = c2.start();
        assertEquals(1, r.effects().size());
        assertEquals("/title Chromecide Knight_2", r.effects().get(0).args().get(0));
    }

    @Test
    void runRefusesVariation() {
        Conversation c = conv("== a\n<<run \"/say [hi|bye]\">>\nDone.\n");
        assertThrows(RuntimeError.class, c::start);
    }

    @Test
    void validatorWarnsAboutFarmableRewards() {
        String farmable = """
                npc: X
                == start
                Hub.
                -> Gift please
                    <<give Food_Bread 5>>
                    <<jump start>>
                -> Leave
                    <<end>>
                """;
        List<Validator.Problem> problems = new Validator().validate(DialogueParser.parse("t.talk", farmable));
        assertTrue(problems.stream().anyMatch(p -> p.message().contains("picking it again")), problems.toString());

        String[] fine = {
                "-> Gift please <<once>>\n    <<give Food_Bread 5>>\n    <<jump start>>\n",
                "-> Gift please <<if not $gifted>>\n    <<set $gifted = true>>\n    <<give Food_Bread 5>>\n    <<jump start>>\n",
                "-> Gift please\n    <<set $gifted = true>>\n    <<give Food_Bread 5>>\n    <<jump start>>\n",
                "-> Trade\n    <<take Ingot_Iron 1>>\n    <<give Food_Bread 5>>\n    <<jump start>>\n",
                "-> Gift and go\n    <<give Food_Bread 5>>\n    <<end>>\n",
                "-> Scold me\n    <<reputation -5>>\n    <<jump start>>\n",
        };
        for (String option : fine) {
            String src = "npc: X\n== start\nHub.\n" + option + "-> Leave\n    <<end>>\n";
            List<Validator.Problem> ps = new Validator().validate(DialogueParser.parse("t.talk", src));
            assertTrue(ps.stream().noneMatch(p -> p.message().contains("picking it again")), option + " -> " + ps);
        }
    }
}

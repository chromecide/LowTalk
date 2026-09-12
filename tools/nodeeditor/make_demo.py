#!/usr/bin/env python3
"""Write the demo dialogue as a laid-out Node Editor graph (see graphgen.py for how).

Usage: python3 tools/nodeeditor/make_demo.py [output.json]
Default output: tools/nodeeditor/examples/Demo_Graph.json
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from graphgen import node, stmt, with_body, write_graph  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "examples", "Demo_Graph.json")


# ---- the demo dialogue: a guide (any NPC tagged with /lowtalk tag guide) who remembers you and hands out one job
start_body = [
    stmt("Say", Speaker="", Text="[Well met|Good day], {player}. Welcome to the valley."),
    stmt("Set", Var="$met", Value="true"),
    node("Choice", {}, [("Options", [
        with_body("Option", [
            stmt("Say", Speaker="", Text="The Rootlings farm the terraces; the ruins to the north are best left alone."),
            stmt("Jump", Node="start"),
        ], Text="Tell me about this place.", If="", ShowIf="", Once=False),
        with_body("Option", [
            stmt("Say", Speaker="", Text="Take this bread to the miller. Tell him the guide sent you."),
            stmt("Give", Item="Food_Bread", Count=1),
            stmt("Set", Var="$player.errand", Value="true"),
            stmt("Notify", Text="New errand: bread for the miller", Detail="", Style="success"),
            stmt("Jump", Node="start"),
        ], Text="Any work for me?", If="not $player.errand", ShowIf="", Once=True),
        with_body("Option", [
            stmt("Say", Speaker="", Text="Safe travels."),
            stmt("End"),
        ], Text="Goodbye.", If="", ShowIf="", Once=False),
    ])]),
]

returning_body = [
    stmt("Say", Speaker="", Text="[Back again|Hello again], {player}."),
    node("If", {}, [("Branches", [
        with_body("Branch", [stmt("Say", Speaker="", Text="How is the errand going? The miller is waiting.")], When="$player.errand"),
        with_body("Branch", [stmt("Say", Speaker="", Text="Still looking for work? Ask me.")], When=""),
    ])]),
    stmt("Jump", Node="start"),
]

dialogue = node("Dialogue", {
    "Npc": ["@guide"],
    "Speaker": "Guide",
    "Title": "The Valley Guide",
    "Scope": "",
    "Portrait": "",
    "On": "",
}, [
    ("Start", [stmt("Start", Node="returning", When="$met"), stmt("Start", Node="start", When="")]),
    ("Nodes", [with_body("Node", start_body, Name="start"), with_body("Node", returning_body, Name="returning")]),
])


write_graph(dialogue, [OUT])

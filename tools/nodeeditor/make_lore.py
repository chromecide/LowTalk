#!/usr/bin/env python3
"""Write the lore-keeper example as a laid-out Node Editor graph.

The simplest useful shape of a branching dialogue: a hub node that offers topics, one node per topic that returns
to the hub, and a farewell. One touch of state at the end: an option that only appears once every topic has been
heard, using visited(). The same dialogue is in examples/lore_keeper.talk as text.

Usage: python3 tools/nodeeditor/make_lore.py
Writes tools/nodeeditor/examples/Lore_Keeper.json and examples/Lore_Keeper.json (the copy shipped in the pack).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from graphgen import say, jump, choice, option, stmt, dialogue, write_graph  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUTS = [os.path.join(HERE, "examples", "Lore_Keeper.json"),
        os.path.join(HERE, "..", "..", "examples", "Lore_Keeper.json")]

# ---- the hub: a greeting and the topics
start = [
    say("Sit a while, {player}. My roots are old and my memory is long. What would you hear of?"),
    choice([
        option("Tell me about the village.", [jump("village")]),
        option("What are the ruins to the north?", [jump("ruins")]),
        option("Who fought the old war?", [jump("war")]),
        # Only once all three tales have been heard: visited() is true for a node the player has been through.
        option("What should I do now?", [jump("counsel")],
               show='visited("village") and visited("ruins") and visited("war")'),
        option("That is enough for today.", [jump("farewell")]),
    ]),
]

# ---- one node per topic, each ending with a jump back to the hub
village = [
    say("The Kweebec raised these terraces when the river still ran high. Every stone was carried by hand."),
    say("We farm what the slopes allow and trade the rest. The merchant by the well will tell you the prices."),
    jump("start"),
]
ruins = [
    say("Older than us. Older than the Trork, whatever they claim."),
    say("The walls hum at night. Nobody who went looking for the reason came back to explain it."),
    jump("start"),
]
war = [
    say("The Trork came down the pass in numbers we had never seen. We held the bridge for nine days."),
    say("On the tenth the river rose and took the bridge, and them with it. Some say the grove asked it to."),
    jump("start"),
]

# ---- the reward for listening, and the way out
counsel = [
    say("You have heard the three tales, so you know what matters here: the river, the ruins, and the pass."),
    say("Keep to the terraces by day. If the walls hum, walk away. And if the Trork come again, find me first."),
    jump("start"),
]
farewell = [
    say("Go well, {player}. The roots remember those who listen."),
    stmt("End"),
]

lore_keeper = dialogue(
    npc=["Kweebec_Elder"],
    speaker="Elder",
    title="The Lore Keeper",
    starts=[("start", "")],
    nodes=[("start", start), ("village", village), ("ruins", ruins), ("war", war), ("counsel", counsel), ("farewell", farewell)],
)

write_graph(lore_keeper, OUTS)

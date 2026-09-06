#!/usr/bin/env python3
"""Write a demo dialogue as a laid-out Node Editor graph.

The output is an ordinary LowTalk JSON dialogue asset with the Node Editor's bookkeeping added: a $NodeId on
every node object and canvas positions under $NodeEditorMetadata. The server ignores those keys, so the file
loads like any other dialogue, and the Node Editor opens it as a tidy graph.

Usage: python3 tools/nodeeditor/make_demo.py [output.json]
Default output: tools/nodeeditor/examples/Demo_Graph.json
"""
import json
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "examples", "Demo_Graph.json")

positions = {}   # $NodeId -> {"$Position": {...}, "$Title"?}
X_STEP, Y_STEP = 520, 170


def node(kind, fields, children=()):
    """A graph node: kind is the node type id (Type value, or Node/Option/Branch/Alternative/Start/Dialogue).
    children is a list of (jsonKey, [child nodes]) wired through output pins."""
    return {"kind": kind, "fields": fields, "children": list(children)}


def stmt(kind, **fields):
    return node(kind, fields)


def with_body(kind, body, **fields):
    return node(kind, fields, [("Body", body)])


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


# ---- layout: x by depth, y by a tidy post-order walk, parents centred on their children
_next_y = [0]


def layout(n, depth):
    child_ys = []
    for _, kids in n["children"]:
        for k in kids:
            child_ys.append(layout(k, depth + 1))
    if child_ys:
        y = sum(child_ys) / len(child_ys)
    else:
        y = _next_y[0]
        _next_y[0] += Y_STEP
    n["_pos"] = (depth * X_STEP, round(y))
    return y


def emit(n, is_root=False):
    nid = f"{n['kind']}-{uuid.uuid4()}"
    out = {"$NodeId": nid}
    if n["kind"] not in ("Dialogue", "Start", "Node", "Option", "Branch", "Alternative"):
        out["Type"] = n["kind"]
    out.update(n["fields"])
    for key, kids in n["children"]:
        out[key] = [emit(k) for k in kids]
    meta = {"$Position": {"$x": n["_pos"][0], "$y": n["_pos"][1]}}
    if is_root:
        meta["$Title"] = "[ROOT] Dialogue"
    positions[nid] = meta
    return out


layout(dialogue, 0)
doc = emit(dialogue, is_root=True)
doc["$NodeEditorMetadata"] = {
    "$Nodes": positions,
    "$FloatingNodes": [],
    "$Links": {},
    "$Groups": [],
    "$Comments": [],
    "$WorkspaceID": "LowTalk - Dialogue",
}

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(doc, f, indent=2)
    f.write("\n")
print(f"wrote {OUT} ({len(positions)} nodes)")

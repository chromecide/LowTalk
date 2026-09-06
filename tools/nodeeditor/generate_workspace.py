#!/usr/bin/env python3
"""Generate the LowTalk workspace for Hytale's standalone Node Editor.

The Node Editor (shipped inside the Hytale client) is data-driven: a workspace folder holds
_Workspace.json plus one JSON file per node type. This script writes the "LowTalk Dialogue"
workspace, whose saved files are exactly the JSON dialogue assets LowTalk loads
(Server/LowTalk/Dialogues/*.json). Run it after changing the JSON statement types, then commit
the generated folder. install.sh copies the folder into the client.

Usage: python3 tools/nodeeditor/generate_workspace.py
"""
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "LowTalk Dialogue")

# Pin types (the wires): a pin only connects to a pin of the same Type.
PIN_START, PIN_NODE, PIN_STMT, PIN_OPTION, PIN_BRANCH, PIN_ALT = "Start", "DialogueNode", "Statement", "Option", "Branch", "Alternative"
COLORS = {PIN_START: "Grey", PIN_NODE: "Olive", PIN_STMT: "Blue", PIN_OPTION: "Aqua", PIN_BRANCH: "Purple", PIN_ALT: "Purple"}

EXPR = " A LowTalk expression, e.g. $met, not $player.done, has(\"Food_Bread\", 2), chance(0.5)."
TEXT = " Text may use {player}, {npc}, {$var}, {expr ? a : b} and [one|of|these]."

nodes = []
variants = {}      # JSON "Type" value -> node id
categories = {}    # category -> [node ids]


def widget(wid, wtype, label, description="", default=None, width=None, height=None, values=None, list_type=None):
    opts = {"Label": label}
    if description:
        opts["Description"] = description
    if default is not None:
        opts["Default"] = default
    if width:
        opts["Width"] = width
    if height:
        opts["Height"] = height
    if values:
        opts["Values"] = values
    if list_type:
        opts["Type"] = list_type
    return {"Id": wid, "Type": wtype, "Options": opts}


def small(wid, label, description="", default="", width=200):
    return widget(wid, "SmallString", label, description, default, width)


def text(wid, label, description="", default="", width=350, height=80):
    return widget(wid, "String", label, description, default, width, height)


def check(wid, label, description="", default=False):
    return widget(wid, "Checkbox", label, description, default)


def integer(wid, label, description="", default=0, width=100):
    return widget(wid, "Int", label, description, default, width)


def number(wid, label, description="", default=0.0, width=100):
    return widget(wid, "Float", label, description, default, width)


def enum(wid, label, values, description="", default=None, width=160):
    return widget(wid, "Enum", label, description, default or values[0], width, values=values)


def string_list(wid, label, description="", width=350):
    return widget(wid, "List", label, description, None, width, list_type="String")


def in_pin(pin_type, pid="In", label=None):
    p = {"Id": pid, "Type": pin_type, "Color": COLORS[pin_type], "Multiple": False}
    if label:
        p["Label"] = label
    return p


def out_pin(pid, pin_type, label, multiple=True):
    return {"Id": pid, "Type": pin_type, "Color": COLORS[pin_type], "Multiple": multiple, "Label": label}


def add(node, category):
    nodes.append(node)
    categories.setdefault(category, []).append(node["Id"])


def statement(type_name, title, description, content=(), outputs=(), schema_extra=None, category="Commands", color="Blue"):
    """A statement node: input pin of type Statement, emits {"Type": type_name, ...fields}."""
    schema = {"Type": type_name}
    for w in content:
        schema[w["Id"]] = w["Id"]
    if schema_extra:
        schema.update(schema_extra)
    node = {
        "Id": type_name,
        "Title": title,
        "Description": description,
        "Color": color,
        "Inputs": [in_pin(PIN_STMT)],
        "Content": list(content),
        "Outputs": list(outputs),
        "Schema": schema,
    }
    add(node, category)
    variants[type_name] = type_name
    return node


def body_output(pid="Body", label="Body"):
    return out_pin(pid, PIN_STMT, label)


def body_schema(key="Body", pid="Body"):
    return {key: {"Node": "StatementVariants", "Pin": pid}}


# ---- root: the dialogue file itself
add({
    "Id": "Dialogue",
    "Title": "Dialogue",
    "Description": "A LowTalk dialogue. Save into Server/LowTalk/Dialogues of an asset pack; the file name is the dialogue id.",
    "Color": "Grey",
    "Inputs": [],
    "Content": [
        string_list("Npc", "NPCs", "Which NPCs use this dialogue: role ids (Kweebec_Merchant) or @tags set with /lowtalk tag. Empty means command only."),
        small("Speaker", "Speaker", "Default speaker for lines without one. Defaults to the NPC's name."),
        small("Title", "Title", "Window title. Defaults to the speaker."),
        small("Scope", "Scope", "Variable namespace shared with other dialogues that declare the same scope. Defaults to the dialogue id."),
        small("Portrait", "Portrait", "Image beside the text, a path inside Common/UI/Custom of any pack.", width=300),
        small("On", "On", "join opens this dialogue by itself when a player joins.", width=120),
    ],
    "Outputs": [
        out_pin("StartPin", PIN_START, "Start rules"),
        out_pin("NodesPin", PIN_NODE, "Nodes"),
    ],
    "Schema": {
        "Npc": "Npc", "Speaker": "Speaker", "Title": "Title", "Scope": "Scope", "Portrait": "Portrait", "On": "On",
        "Start": {"Node": "Start", "Pin": "StartPin"},
        "Nodes": {"Node": "Node", "Pin": "NodesPin"},
    },
}, "Structure")

add({
    "Id": "Start",
    "Title": "Start rule",
    "Description": "Begin at Node when When holds (or always, if When is empty). Guarded rules are tried before the unguarded one. With no rules the first node is the start.",
    "Color": COLORS[PIN_START],
    "Inputs": [in_pin(PIN_START)],
    "Content": [small("Node", "Node", "Node to begin at."), small("When", "When", "Only when this is true." + EXPR, width=300)],
    "Outputs": [],
    "Schema": {"Node": "Node", "When": "When"},
}, "Structure")

add({
    "Id": "Node",
    "Title": "Node",
    "Description": "A named stretch of conversation. Jump and Start refer to it by name.",
    "Color": COLORS[PIN_NODE],
    "Inputs": [in_pin(PIN_NODE)],
    "Content": [small("Name", "Name", "Letters, digits and underscores.", default="start")],
    "Outputs": [body_output()],
    "Schema": {"Name": "Name", **body_schema()},
}, "Structure")

# ---- structure statements
statement("Say", "Say", "A spoken line. Consecutive lines get a Continue button; the last line before options is shown with them.",
          [small("Speaker", "Speaker", "Who says it. Empty for the dialogue's default speaker."),
           text("Text", "Text", "What is said." + TEXT)], category="Talk", color="Blue")

statement("Choice", "Choice", "Options shown together as buttons (at most eight).",
          outputs=[out_pin("Options", PIN_OPTION, "Options")],
          schema_extra={"Options": {"Node": "Option", "Pin": "Options"}}, category="Talk", color="Aqua")

add({
    "Id": "Option",
    "Title": "Option",
    "Description": "One button. Its Body runs when chosen; if the body does not Jump or End, the options are shown again.",
    "Color": COLORS[PIN_OPTION],
    "Inputs": [in_pin(PIN_OPTION)],
    "Content": [
        text("Text", "Text", "Button text." + TEXT, height=50),
        small("If", "If", "Hide unless true." + EXPR, width=300),
        small("ShowIf", "Show if", "Show greyed out unless true." + EXPR, width=300),
        check("Once", "Once", "Hide for good once the player has picked it."),
    ],
    "Outputs": [body_output()],
    "Schema": {"Text": "Text", "If": "If", "ShowIf": "ShowIf", "Once": "Once", **body_schema()},
}, "Talk")

statement("If", "If", "if / elseif / else. Branches are tried in order; a last branch with an empty When is the else.",
          outputs=[out_pin("Branches", PIN_BRANCH, "Branches")],
          schema_extra={"Branches": {"Node": "Branch", "Pin": "Branches"}}, category="Flow", color="Purple")

add({
    "Id": "Branch",
    "Title": "Branch",
    "Description": "One branch of an If.",
    "Color": COLORS[PIN_BRANCH],
    "Inputs": [in_pin(PIN_BRANCH)],
    "Content": [small("When", "When", "Condition; leave empty on the last branch for an else." + EXPR, width=300)],
    "Outputs": [body_output()],
    "Schema": {"When": "When", **body_schema()},
}, "Flow")

statement("Once", "Once", "Runs the first time this player reaches it with this NPC, and never again.",
          outputs=[body_output()], schema_extra=body_schema(), category="Flow", color="Purple")

statement("Random", "Random", "Runs exactly one alternative, chosen at random each time.",
          outputs=[out_pin("Alternatives", PIN_ALT, "Alternatives")],
          schema_extra={"Alternatives": {"Node": "Alternative", "Pin": "Alternatives"}}, category="Flow", color="Purple")

add({
    "Id": "Alternative",
    "Title": "Alternative",
    "Description": "One alternative of a Random block.",
    "Color": COLORS[PIN_ALT],
    "Inputs": [in_pin(PIN_ALT)],
    "Content": [],
    "Outputs": [body_output()],
    "Schema": body_schema(),
}, "Flow")

statement("Set", "Set variable", "Store a value in a variable.",
          [small("Var", "Variable", "$x (this player with this NPC), $player.x, $npc.x, $world.x or $tmp.x.", default="$met"),
           small("Value", "Value", "The value." + EXPR, default="true", width=300)], category="Flow", color="Purple")
statement("Jump", "Jump", "Continue at another node.", [small("Node", "Node", "Name of the node.")], category="Flow", color="Purple")
statement("End", "End", "Close the window.", category="Flow", color="Purple")
statement("Input", "Text input", "Ask the player to type something and store it.",
          [small("Var", "Variable", "Where to store the text.", default="$tmp.answer"),
           text("Prompt", "Prompt", "Shown above the text box." + TEXT, height=50)], category="Talk", color="Blue")
statement("Wait", "Wait", "Pause before the next line; the previous line shows with no Continue button.",
          [small("Seconds", "Seconds", "0 to 30; may be an expression.", default="2", width=100)], category="Flow", color="Purple")

# ---- commands
statement("Give", "Give items", "Put items in the player's inventory.",
          [small("Item", "Item", "Item id, e.g. Food_Bread."), integer("Count", "Count", "How many.", 1)], color="Green")
statement("Take", "Take items", "Remove items; fails the option if the player lacks them, so guard with has().",
          [small("Item", "Item", "Item id."), integer("Count", "Count", "How many.", 1)], color="Green")
statement("Shop", "Open shop", "Open a barter shop; ends the conversation.",
          [small("Shop", "Shop", "Barter shop id; empty for this NPC's own shop.")], color="Green")
statement("Attitude", "Set attitude", "Set this NPC's attitude toward the player for a while.",
          [enum("Attitude", "Attitude", ["friendly", "neutral", "ignore", "hostile", "revered"])], color="Orange")
statement("Anim", "Play animation", "Play an animation on the NPC.",
          [small("Animation", "Animation", "Animation name on the NPC's model, e.g. Wave."),
           enum("Slot", "Slot", ["Emote", "Status", "Action", "Movement", "Face", "ServerAction"], "Emote by default; Status is what the game uses for greetings.")],
          color="Orange")
statement("Sound", "Play sound", "Play a sound event at the NPC.", [small("Sound", "Sound", "Sound event id.")], color="Orange")
statement("NpcName", "Rename NPC", "Rename this NPC (kept with the NPC).", [small("Name", "Name", "New name, or clear to remove it.")], color="Orange")
statement("State", "Set NPC state", "Put this NPC's role into one of its states.",
          [small("State", "State", "State name from the role JSON."), small("SubState", "Sub-state", "Optional.")], color="Orange")
statement("Despawn", "Despawn NPC", "End the conversation and retire this NPC.", color="Orange")
statement("Spawn", "Spawn NPC", "Spawn an NPC near the player, facing them.",
          [small("Role", "Role", "NPC role id, e.g. Kweebec_Merchant."),
           number("Right", "Right", "Blocks to the player's right.", 0.0), number("Up", "Up", "Blocks up.", 0.0),
           number("Forward", "Forward", "Blocks in front of the player.", 2.0)], color="Orange")
statement("Objective", "Start objective", "Start an objective for the player.", [small("Objective", "Objective", "Objective id.")], color="Yellow")
statement("ObjectiveLine", "Start objective line", "Start a chain of objectives.", [small("Line", "Objective line", "Objective line id.")], color="Yellow")
statement("ObjectiveCancel", "Cancel objective", "Abandon one of the player's active objectives.", [small("Objective", "Objective", "Objective id.")], color="Yellow")
statement("ObjectiveTask", "Complete NPC task", "Advance a talk-to-this-NPC task of an active objective.", [small("Task", "Task", "Task id.")], color="Yellow")
statement("Reputation", "Change reputation", "Change the player's standing with a reputation group.",
          [small("Change", "Change", "+10 or -5.", default="+10", width=100), small("Group", "Group", "Empty for this NPC's own group.")], color="Yellow")
statement("Learn", "Teach recipe", "Teach the player a crafting recipe.", [small("Recipe", "Recipe", "Recipe id.")], color="Yellow")
statement("Notify", "Notification", "A toast notification in the corner of the screen.",
          [text("Text", "Text", "Main text." + TEXT, height=50), small("Detail", "Detail", "Smaller second line.", width=300),
           enum("Style", "Style", ["default", "success", "warning", "danger"])], color="Pink")
statement("Title", "Screen title", "A cinematic title across the screen.",
          [text("Primary", "Primary", "Big text." + TEXT, height=50), small("Secondary", "Secondary", "Smaller text underneath.", width=300),
           check("Major", "Major", "The larger title style."), number("Seconds", "Seconds", "How long it stays; 0 for the default.", 0.0)], color="Pink")
statement("Effect", "Apply effect", "Apply an entity effect to the player.", [small("Effect", "Effect", "Entity effect id.")], color="Red")
statement("Cure", "Remove effect", "Remove an entity effect from the player.", [small("Effect", "Effect", "Entity effect id.")], color="Red")
statement("Heal", "Heal", "Restore health.", [small("Amount", "Amount", "Empty for a full heal.", width=100)], color="Red")
statement("Stat", "Change stat", "Add to, set, or max out an entity stat.",
          [small("Stat", "Stat", "e.g. Health, Stamina.", default="Health"), small("Value", "Value", "+20 to add, 50 to set, or max.", default="max", width=100)], color="Red")
statement("Teleport", "Teleport", "Move the player; ends the conversation.", [small("Target", "Target", "A warp name, or x y z.")], color="Green")
statement("Weather", "Weather", "Change the weather for the world, or this player only.",
          [small("Weather", "Weather", "A weather id, or clear.", default="clear"), check("PlayerOnly", "Player only", "Only this player sees it.")], color="Green")
statement("Time", "Time of day", "Set the time, or pause and resume the clock.",
          [small("Time", "Time", "dawn, noon, dusk, midnight, an hour 0-24, pause or resume.", default="noon", width=140),
           small("FadeSeconds", "Fade seconds", "Fade over this many seconds; empty for instant.", width=100)], color="Green")
statement("Run", "Run command", "Run a server command as the console. Never include text the player typed.",
          [small("Command", "Command", "e.g. /give {player} Food_Bread 1", width=350)], color="Red")
statement("Command", "Plugin command", "A command added by another plugin, by name. Built-in commands have their own nodes.",
          [small("Name", "Name", "Command name as in <<name ...>>."), string_list("Args", "Arguments", "One per entry, as they would appear inside <<...>>.")],
          color="Grey")

workspace = {
    "WorkspaceName": "LowTalk Dialogue",
    "FileFormat": "json",
    "ExportDefaults": True,
    "Roots": {
        "Dialogue": {"RootNodeType": "Dialogue", "MenuName": "LowTalk - Dialogue"},
    },
    "NodeCategories": categories,
    "Variants": {
        "StatementVariants": {"VariantFieldName": "Type", "Variants": variants},
    },
}

if os.path.isdir(OUT):
    shutil.rmtree(OUT)
os.makedirs(OUT)
with open(os.path.join(OUT, "_Workspace.json"), "w", encoding="utf-8") as f:
    json.dump(workspace, f, indent=2)
    f.write("\n")
for n in nodes:
    with open(os.path.join(OUT, n["Id"] + ".json"), "w", encoding="utf-8") as f:
        json.dump(n, f, indent=2)
        f.write("\n")
print(f"wrote {len(nodes)} node files and _Workspace.json to {OUT}")

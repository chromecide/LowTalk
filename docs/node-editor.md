# Editing dialogues in the Node Editor

Hytale ships a standalone Node Editor alongside the game client. It is the
tool Hypixel uses for world generation graphs, scripted brushes and NPC roles,
and it is data-driven: a workspace folder describes the node types, and the
editor saves plain JSON. LowTalk provides a workspace whose saved files are
exactly its JSON dialogue assets, so a dialogue drawn as a graph loads like
any other.

The workspace lives in `tools/nodeeditor/LowTalk Dialogue/` and is generated
by `tools/nodeeditor/generate_workspace.py` from the same list of statement
types the JSON asset uses. A test keeps the two in step.

## Installing the workspace

The editor reads workspaces from the client install, so this is a per-machine
step. **Every client update replaces the install and deletes the workspace**
(confirmed on 0.6.5 and 0.7.0-pre.2: only Hypixel's three workspaces came
back). If the Node Editor stops offering `LowTalk - Dialogue`, run the script
again; it is safe to repeat.

```
sh tools/nodeeditor/install.sh
```

With no argument the script installs into every Hytale client it finds,
release and pre-release (`Hytale.app/Contents/Resources/NodeEditor/Workspaces/`
on macOS). Pass a `NodeEditor/Workspaces` folder as the argument to target one
client, or a client somewhere else.

There is no other way in. The editor scans exactly one folder, inside the
install; it has no user-level workspace folder, no preference for extra
paths, and asset packs cannot carry workspaces (checked against the 0.7.0-pre.2
editor). Files it saves carry a `$WorkspaceID`, so a LowTalk JSON file opens
in the right workspace without being asked, as long as the workspace is
installed.

## Example graphs

Two dialogues ship as laid-out graphs in `tools/nodeeditor/examples/`. Copy one
into a pack's `Server/LowTalk/Dialogues/` and open it in the Node Editor to see
how the pieces wire together, or run `/lowtalk open <name>` in game to play it.

- `Lore_Keeper.json` is the one to start with: a Kweebec elder with a hub passage
  that offers three topics, one passage per topic that jumps back to the hub, and
  a farewell. Say, Choice, Option, Jump and End are all it uses, plus one
  touch of state: a "What should I do now?" option that only appears once all
  three topics have been heard, through `visited()`. The same dialogue is in
  `examples/lore_keeper.talk` as text, so the two can be compared side by
  side. `tools/nodeeditor/make_lore.py` regenerates it.
- `Demo_Graph.json` is busier: a guide who remembers you between visits, hands
  out an errand with an item and a notification, and greets you differently
  once met. `tools/nodeeditor/make_demo.py` regenerates it.

Both generators share `graphgen.py`, which lays the passages out on the canvas.

## Using it

1. Launch the Node Editor (next to the game client, `NodeEditor` inside the
   Hytale app bundle on macOS).
2. On the start screen (0.7.0-pre.2 and later; older builds go straight to
   File > New) create a new file and choose the `LowTalk - Dialogue`
   workspace. The editor reopens on the workspace you used last. The root
   passage is the dialogue: its NPCs, default speaker, title and start rules.
3. Add `Node` passages for each named stretch of conversation and wire them to
   the root's Passages pin. Each Passage has a Body pin; connect statements to it in
   order: Say, Choice, If, Set, Jump, Give and so on.
4. A `Choice` takes `Option` passages, each with its own Body. `If` takes
   `Branch` passages; `Random` takes `Alternative` passages.
5. Save into your asset pack as `Server/LowTalk/Dialogues/Some_Name.json`.
   The file name is the dialogue id. The server loads it on save, or on
   `/lowtalk reload`, and the Asset Editor's form can open the same file.

Expressions and text are typed the same way as in `.talk` files:
`{player}`, `{$gold}`, `has("Food_Bread")`, `[Hi|Hello]`. Fields left empty
are treated as absent.

The passage canvas positions are stored in the file under `$NodeEditorMetadata`
and are ignored by the server, so a graph-edited file and a form-edited file
are the same asset.

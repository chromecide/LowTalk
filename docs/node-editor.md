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
step, and a game update may remove it. Run it again when that happens.

```
sh tools/nodeeditor/install.sh
```

On macOS this copies the folder into
`Hytale.app/Contents/Resources/NodeEditor/Workspaces/`. On other systems pass
the path to your client's `NodeEditor/Workspaces` folder as the argument.

## Using it

1. Launch the Node Editor (next to the game client, `NodeEditor` inside the
   Hytale app bundle on macOS).
2. Create a new file and choose the `LowTalk - Dialogue` workspace. The root
   node is the dialogue: its NPCs, default speaker, title and start rules.
3. Add `Node` nodes for each named stretch of conversation and wire them to
   the root's Nodes pin. Each Node has a Body pin; connect statements to it in
   order: Say, Choice, If, Set, Jump, Give and so on.
4. A `Choice` takes `Option` nodes, each with its own Body. `If` takes
   `Branch` nodes; `Random` takes `Alternative` nodes.
5. Save into your asset pack as `Server/LowTalk/Dialogues/Some_Name.json`.
   The file name is the dialogue id. The server loads it on save, or on
   `/lowtalk reload`, and the Asset Editor's form can open the same file.

Expressions and text are typed the same way as in `.talk` files:
`{player}`, `{$gold}`, `has("Food_Bread")`, `[Hi|Hello]`. Fields left empty
are treated as absent.

The node canvas positions are stored in the file under `$NodeEditorMetadata`
and are ignored by the server, so a graph-edited file and a form-edited file
are the same asset.

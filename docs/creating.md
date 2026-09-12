# Creating and editing dialogues

A LowTalk dialogue is one thing: an NPC's conversation, made of nodes, lines, options and the commands that make
things happen. There are four ways to make one, and they all produce the same thing. Pick the one that suits how
you like to work, and switch whenever you want: a dialogue started in the game can be finished in the Asset
Editor, a text file can be opened as a graph, and so on.

| You want to... | Use | Needs |
|----------------|-----|-------|
| Build a conversation while standing next to the NPC, no files, no syntax | [In game, with the LowTalk tool](#in-game-with-the-lowtalk-tool) | creator permission |
| Fill in forms with pickers and tooltips in the game's own editor | [The Asset Editor's form](#in-the-asset-editors-form) | Creative mode |
| See the whole conversation as a graph and wire it up visually | [The Node Editor](#in-the-node-editor) | the standalone Node Editor |
| Write it like a script, keep it in git, diff it in pull requests | [A text file](#as-a-text-file) | any text editor, or the Asset Editor's text mode |

Everything below is available in every method: the same commands, conditions, variables, pickers and the layout
setting (bottom bar, top bar or window; see [format.md](format.md#layout)). Where a
method has a limit, it says so.

## In game, with the LowTalk tool

The quickest way to make an NPC talk. Nothing to install, nothing to type in a file.

1. `/lowtalk tool` puts the tool in your hand. It is also in the creative Tools tab.
2. Click an NPC.
   - If it already has a dialogue, the window opens in **edit mode**: the same window a player sees, one node at a
     time, but every line and option is a field.
   - If it has none, a small form asks for a name, the speaker, whether the dialogue belongs to every NPC of this
     kind (by role) or only this one (by tag), and where to save it (the server's folder or an asset pack). Create
     opens the new dialogue in edit mode with a first line ready.
3. Change what is said by typing into the line. Add lines, options and everything else with the **Add** dropdown:
   a line, an option, a command, set a variable, if/else, once, random, ask the player, wait, jump, end.
4. An option has a **target**: a node to go to, "(end)", "(back to these options)" for a hub, or "+ new node",
   which creates a node and takes you into it. **Go** walks into the target the way a player's click would.
   The **...** button opens the option's settings: only-if, grey-unless, once, and "Edit what happens", which
   opens the option's own body so you can put commands there.
5. A command row has a dropdown of every command with its usage as a tooltip, an arguments field, and a
   **picker** when the game has a list for that argument: animations, weathers, NPC roles, music, particles,
   camera effects, stats, recipes, warps, reputation groups, shops, notification styles, times.
6. If/else, once and random blocks show one row per branch with an **Edit body** button; conditions are typed
   in the row. The breadcrumb at the top shows where you are; **Back** climbs out.
7. **Dialogue...** edits the settings: which NPCs open it (with a picker of roles and tags), speaker, title,
   start node, whether it opens when a player joins, portrait and shared memory.
8. **Test here** plays your unsaved draft from the node you are on, with this NPC. **Save** writes the file where
   it lives and reloads it. **Discard** throws the draft away.

Limits: the editor works one node at a time, so there is no overview of the whole conversation; the Node Editor
gives you that. Saving rewrites the file in a standard layout, so comments in a hand-written `.talk` file are
dropped (the editor tells you when that happens).

## The shipped examples

LowTalk's own asset pack carries the example dialogues under `Server/LowTalk/Dialogues/Examples/`, so they are
always visible in the Asset Editor for reference: a merchant with a memory, a unique village elder, a fortune
teller that asks your name (all three as text) and a valley guide as JSON. They are unbound there (`npc: none`),
so shipping them changes no NPC. To start from one, use **Copy Asset** in the create dialog, save the copy into
your pack, and set its `npc:` to a role id or an `@tag`. The same three text examples are also copied into the
plugin's own `dialogues` folder on first run, bound to real roles, so a fresh server has working conversations.

## In the Asset Editor's form

Hytale's Asset Editor (Creative mode, Tools) is where the game's own content is edited, and LowTalk registers its
dialogues there as a real asset type.

1. Open the Asset Editor and create or open a writable asset pack.
2. Create the folder `Server/LowTalk/Dialogues` and add a `.json` asset in it. The file name is the dialogue id.
3. The editor shows a form: the NPCs it binds to (with autocomplete of roles and tags), speaker, title, start
   rules, and a list of nodes. Each node holds statements, each with its own fields: a Say has a speaker and text,
   a Choice holds options, a Give has an item and count, and so on. Every statement type in the format is there,
   with tooltips, and fields that name game content get autocomplete from the game's own lists.
4. Every save is validated and loaded live. Problems appear as editor notifications with the field concerned;
   selecting a file shows what is loaded from it.
5. Talk to a bound NPC, or `/lowtalk open <id>`, to try it.

The form and the text file are two spellings of the same model. `/lowtalk convert json <id> <pack>` writes a
text dialogue as a form-editable asset, and `/lowtalk convert talk <id> <pack>` goes the other way.

## In the Node Editor

Hytale ships a standalone Node Editor next to the game client, the tool Hypixel uses for world generation and NPC
roles. LowTalk provides a workspace for it, so a dialogue can be drawn as a graph: the dialogue as the root, one
node per stretch of conversation, statements wired into bodies, options and branches fanning out. What it saves
is the same JSON asset the form edits, so the server loads it like any other, and the form can open it afterwards.

See [node-editor.md](node-editor.md) for installing the workspace, a demo graph, and how the pieces connect.
The workspace lives inside the client install, so every game update removes it; rerun
`sh tools/nodeeditor/install.sh` when the editor stops offering `LowTalk - Dialogue`.

## As a text file

For people who write dialogue the way they write a script. A `.talk` file is short, readable and diffs well:

```
npc: Kweebec_Merchant
speaker: Merchant

== start
Well met, {player}.
-> What do you sell?
    A little of everything.
    <<shop>>
-> I should be going.
    <<end>>
```

- Put it in the plugin's `dialogues` folder and run `/lowtalk reload`, or put it in an asset pack under
  `Server/LowTalk/Dialogues` and edit it in the Asset Editor's **text mode**, where each save is validated and
  loaded live with line-numbered notifications.
- `./gradlew validate --args="folder"` checks files from the shell without a server; `/lowtalk help <name>`
  shows any command or function's usage in chat; `/lowtalk info <id>` prints a dialogue's outline.
- Files starting with `_` are shared includes, not dialogues.

The whole syntax is in [format.md](format.md).

## Trying what you made

- Talk to the NPC. Bound NPCs show the game's "Press [key] to talk" prompt.
- `/lowtalk open <id>` opens any dialogue with the NPC you are looking at, or with no NPC at all.
- `/lowtalk test <id> [choices...]` plays it headlessly with scripted choices, for checking logic quickly.
- The test corridor ([testing.md](testing.md)) is a world of NPCs that exercise every feature.

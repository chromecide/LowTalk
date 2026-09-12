# LowTalk

Branching dialogue for Hytale NPCs.

LowTalk gives any NPC a conversation: lines, choices, conditions, memory, and consequences that use Hytale's own
systems (items, barter shops, objectives, attitudes, animations, weather, music) rather than reinventing them. It
runs entirely server-side, remembers what each player has said to each NPC, and is MIT licensed. Fork it, extend
it, ship it with your adventure map.

## Four ways to make a dialogue

They all make the same thing, and you can switch between them at any point.

- **In game.** `/lowtalk tool`, click an NPC, and its conversation opens in edit mode: the window a player sees,
  with every line and option editable, an Add menu for everything the format can say, and pickers fed by the
  game's own lists. Click an NPC with no dialogue and it offers to create one. No files, no syntax.
- **In the Asset Editor.** Dialogues are a registered asset type. Edit them as a form with tooltips and
  autocomplete, or as text, in any asset pack; every save is validated and loaded live.
- **In the Node Editor.** Hytale's standalone graph editor gets a LowTalk workspace, so a whole conversation can
  be drawn and wired visually. It saves the same asset the form edits.
- **As a text file.** A small script-like format that is pleasant to type, diff and keep in git:

```
npc: Kweebec_Merchant
start: returning when $met
start: first_meeting

== first_meeting
Well met, traveler. I'm the Rootling Merchant, and this is my humble stall.
<<set $met = true>>
<<jump hub>>

== returning
Ah, {player}. Back again.
<<jump hub>>

== hub
-> What do you sell?
    A little of everything, and most of it useful.
    <<shop>>
-> I'm hungry and have no coin. <<if not $got_bread>>
    Take this, then. One loaf, and don't tell the Elder I'm going soft.
    <<give Food_Bread 1>>
    <<set $got_bread = true>>
-> I should be going.
    Safe travels.
    <<end>>
```

[Creating and editing dialogues](docs/creating.md) walks through each of the four.

## Features

- **Bottom bar, top bar or window.** Conversations sit in a bar with the NPC in view by default; options are
  numbered and the number keys pick them. Each dialogue, each mod's pack and the server can choose the layout.
- Branching dialogue: options with conditions, if/elseif/else, hubs, jumps, once-blocks, random variation,
  text input, timed pauses, `{interpolation}` and `[a|b]` variation in text.
- Memory: per-NPC variables (`$met`), per-player variables that follow the player between NPCs
  (`$player.stage`), per-NPC counters shared by everyone (`$npc.visitors`), and world state (`$world.season`).
- Native hooks: `give`, `take`, `shop`, `objective` (start, cancel, lines, tasks), `reputation`, `attitude`,
  `anim`, `sound`, `notify`, `title`, `effect`, `heal`, `stat`, `learn`, `teleport`, `weather`, `time`, `music`,
  `vfx`, `camera`, `npc_name`, `state`, `spawn`, `despawn`, `run`; checks like `has()`, `count()`, `objective()`,
  `reputation()`, `rank()`, `stat()`, `hour()`, `weather()`, `visited()`, `chance()`, and `t()` for the game's
  translations.
- Binding by NPC role, or by tagging one specific NPC in game. Dialogues can also start from the Trigger Volume
  Tool, from any `OpenCustomUI` interaction, from shop-style choice pages, from an NPC's own role, or when a player
  joins; a quest can have "talk to this NPC" as a task with a marker over their head.
- A validator with file and line numbers, usable in game, from the editors and from the shell; a headless test
  runner; a test corridor world that exercises every feature.
- An API for other plugins: add functions and commands (with help text and pickers so they look native in every
  editor), listen to conversations, open dialogues, bind dialogues to NPCs at run time. [Companions](https://github.com/chromecide/LowTalkCompanions)
  is built on it.
- Graceful degradation: if a plugin that added commands is removed, options that needed them are hidden and the
  rest of the dialogue keeps working.

## Documentation

- [Creating and editing dialogues](docs/creating.md): in game, Asset Editor, Node Editor, text files.
- [The dialogue format](docs/format.md), the full reference for text and JSON.
- [The Node Editor workspace](docs/node-editor.md). Reinstall it after every client update with
  `sh tools/nodeeditor/install.sh`; updates wipe it.
- [API for other plugins](docs/api.md).
- [Testing](docs/testing.md): unit tests, the headless runner, and the in-game test corridor.
- [Design](docs/DESIGN.md), how it works and why.
- [Examples](examples/): a merchant, a unique village elder, a fortune teller that asks your name.

## Commands

| Command | What it does |
|---------|--------------|
| `/lowtalk reload` | Re-read every dialogue file and report problems |
| `/lowtalk list` | Loaded dialogues and their bindings |
| `/lowtalk open <id>` | Open a dialogue with the NPC you're looking at |
| `/lowtalk tag <name>` / `untag <name>` | Bind a specific NPC to `npc: @name` dialogues |
| `/lowtalk tags` | Show an NPC's tags and dialogues |
| `/lowtalk vars` | Your saved variables |
| `/lowtalk reset` | Forget everything every dialogue knows about you |
| `/lowtalk test <id> [apply] [choices...]` | Play a dialogue headlessly with scripted choices |
| `/lowtalk testworld build` / `go` | Build or visit the test corridor world |
| `/lowtalk thaw` | Unfreeze the NPC you're looking at |
| `/lowtalk testworld leave` | Leave the test corridor: main world, old game mode, world unloaded when empty |
| `/lowtalk tool` | Get the LowTalk tool: click an NPC with it to edit its dialogue in place (creator) |
| `/lowtalk stop` | Leave your current conversation |
| `/lowtalk help [name]` | The format reference in chat: commands, functions, keywords, or one entry |
| `/lowtalk info <id>` | Outline of a dialogue: passages, options, variables, unreachable passages |
| `/lowtalk convert json\|talk <id> <pack>` | Write a dialogue in the other format into an asset pack (`server` = plugin folder for talk) |

Permissions: `lowtalk.creator` covers authoring (`list`, `open`, `tags`, `vars`,
`reset`, `test`, `help`, `info`); `lowtalk.admin` covers server operation (`reload`, `tag`,
`untag`, `thaw`, `testworld`). Give admins `lowtalk.*`. Players need nothing:
they reach dialogues only by using an NPC, and `stop` is open to everyone.

## Removing the mod

LowTalk registers no entity components, so a world that had LowTalk installed keeps loading without it. What it
leaves behind degrades quietly: the game keeps unknown item ids as a placeholder item (the LowTalk tool), trigger
volumes skip effect and condition types they no longer know, objectives whose asset is gone are dropped with a
warning, and an NPC still carrying one of LowTalk's own roles (the test corridor's stations) is logged as a missing
role and lost rather than crashing the chunk. Dialogue memory, tags and bindings live in `lowtalk.json` and the
plugin's `data` folder and can be deleted with the plugin. Mods built on LowTalk may have their own step; Companions,
for example, asks you to run `/companions dismissall` first. Dialogues that used another mod's commands keep working
without it: options that need a missing command are hidden and conditions on missing functions read as false.

## Hytale versions

| LowTalk | Hytale release line | Hytale pre-release line |
|---------|---------------------|-------------------------|
| 0.2.0   | 0.6.3 to 0.6.5 (`LowTalk-0.2.0.jar`) | 0.7.0-pre.2 (`LowTalk-0.2.0+hytale.0.7.0-pre.2.jar`) |
| 0.1.1   | 0.6.3 to 0.6.5 (`LowTalk-0.1.1.jar`) | 0.7.0-pre.2 (`LowTalk-0.1.1+hytale.0.7.0-pre.2.jar`) |
| 0.1.0   | 0.6.3, 0.6.4 (`LowTalk-0.1.0.jar`) | 0.7.0-pre.1 (`LowTalk-0.1.0+hytale.0.7.0-pre.1.jar`) |

Each release ships one jar per Hytale line; the server refuses a jar built for the other line. Both come from
the same commit: `./gradlew buildAll` writes them to `build/dist/`. See [CONTRIBUTING.md](CONTRIBUTING.md) for how
branches and tags follow Hytale's patchlines.


### Known Hytale issue: 0.7.0-pre.2 boot failure

Some servers on 0.7.0-pre.2 stop at asset validation with:

```
FAIL: Asset 'Rope' of type com.hypixel.hytale.builtin.beam.asset.Beam doesn't exist!
Asset validation FAILED with 1 reason(s): Assets Hytale:Hytale failed to load.
```

This is a Hytale bug, not a mod bug: the new beam interaction is validated against the Beam asset store while
the vanilla Hookshot loads, but no store declares that it must load after Beam, and the server visits stores in
hash-map order. Whether it hits you depends on which mods are installed and how they are loaded, and once it hits
it hits every boot. LowTalk 0.1.1 and later contain a workaround (`AssetLoadOrderFix`): during setup it adds the
missing edge, Interaction after Beam, so everything that embeds interactions loads after Beam too. It logs one line,
"Interaction assets now load after Beam assets", does nothing on the 0.6.x line, and does nothing once Hypixel
declares the edge. If you see the error with LowTalk installed, check that the jar is 0.1.1 or later and that the
log line appears before the error.

## Configuration

`lowtalk.json` in the plugin folder:

- `RoleBindingMode` and `TagBindingMode`: `replace` (default; the interact key
  opens the dialogue, and `<<shop>>` reaches the store) or `crouch` (crouch
  and interact opens the dialogue, plain interact keeps the native behaviour).
- `UseHook` (default true): open bound dialogues when a player uses an NPC by
  intercepting the game's use event. Set false to route every conversation
  through NPC roles (`LowTalkOpenDialogue`) and interaction JSON instead.
- `ShowHint` (default true): bound NPCs show the game's interaction prompt,
  "Press [key] to talk"; `HintKey` names the translation key of that text
  (default `server.lowtalk.hint.talk`).
- `Layout` (default `bottom`): where conversations appear unless a pack, a
  plugin or the dialogue says otherwise: `bottom` or `top` (a bar, the NPC
  stays visible, number keys pick options) or `window` (centred, dimmed
  screen). `ForceLayout` (default empty) overrides every mod and dialogue.
  `HideHudDuringDialogue` (default `Reticle`, `Hotbar`) lists the HUD parts
  hidden while a dialogue is open. `History` (default `full`) keeps the whole
  transcript on screen; `latest` shows only the NPC's current line. Mod authors set their own defaults in their
  pack's `Server/LowTalk/Settings.json`; see [docs/format.md](docs/format.md#layout).
- `HoldNpcDuringDialogue` (default true): freeze and face the NPC while talking.
- `ClearSkyWeather` (default `Default_Flat`): what `<<weather clear>>` shows in
  worlds that have no natural weather.
- `DialoguesFolder`, `CopyExamplesOnFirstRun`, `LogConversations`, `InfoColor`.

## Translating

The mod's own words, the dialogue window, the editor, chat feedback and Asset Editor notices, are translation
keys in `Server/Languages/en-US/server.lang`. To add a language, copy that file to
`Server/Languages/<language>/server.lang` in any asset pack (the language codes are the game's, such as `de-DE`)
and translate the right-hand sides; `{name}` fill-ins stay as they are. The game sends each player the table for
their language. Dialogue text is written in one language by its author; translating dialogues is not supported yet.

## Building

Requires Java 25. The Gradle wrapper is included.

```
./gradlew build                          # builds build/libs/LowTalk-<version>.jar
./gradlew test                           # parser and runtime tests, no server needed
./gradlew validate --args="examples"     # check .talk files from the shell
./gradlew runServer                      # local dev server with the plugin loaded
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Contributors accept a short
[CLA](CLA.md) so the project stays easy to relicense as a whole.

## License

MIT. See [LICENSE](LICENSE).

## AI Use Disclosure

LowTalk was made by one person, Chromecide, working with an AI coding agent, Claude Code. It is worth being
plain about what that means.

- The idea, the design decisions, what to build next and what to leave out came from a person. So did every
  test in the game: each feature was played through by hand, and the ones that did not hold up were reworked.
- Most of the Java, the tests and these documents were written by the agent under that direction, in a
  terminal, with the person reading the results in the game rather than the code. Hytale's decompiled server
  sources were read to learn the API, never copied; the rule is in [CONTRIBUTING.md](CONTRIBUTING.md).
- There is no AI in the mod. Every line a player reads was written by a dialogue author. The plugin makes no
  network calls and sends nothing anywhere.
- No generative AI imagery, ever. The only images the mod ships today are two tiny frame textures drawn by a
  script, pixel by pixel, from numbers. Icons, portraits and any other art will be made by people, and
  contributions containing AI-generated images are rejected; see [CONTRIBUTING.md](CONTRIBUTING.md).

If that is not something you want to run on your server, that is a fair choice, and the whole repository is here
to read. Bugs are ours whichever of us typed them; please report them. Contributions are welcome from people
working with or without such tools, on the same terms.

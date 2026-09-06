# LowTalk

Hand-written branching dialogue for Hytale NPCs.

LowTalk lets you give any NPC a conversation: lines, choices, conditions, and
consequences, written in a small plain-text format that is pleasant to type,
diff, and version. It runs entirely server-side, remembers what each player
has said to each NPC, and plugs into Hytale's own systems (items, barter
shops, objectives, attitudes, animations) rather than reinventing them.

It is MIT licensed. Fork it, extend it, ship it with your adventure map.

## What a dialogue looks like

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

Put that in `dialogues/rootling_merchant.talk` inside the LowTalk plugin
folder, run `/lowtalk reload`, and every Kweebec merchant has the
conversation. Each one remembers each player separately.

## Features

- Branching dialogue: options with guards, if/elseif/else, hubs, jumps,
  once-blocks, text input, `{interpolation}`.
- Memory: per-NPC variables (`$met`), per-player variables that follow the
  player between NPCs (`$player.stage`), per-NPC counters shared by everyone
  (`$npc.visitors`), and world state (`$world.season`).
- Native hooks: `give`, `take`, `shop`, `objective` (start, cancel, lines,
  tasks), `reputation`, `attitude`, `anim`, `sound`, `notify`, `title`,
  `effect`, `heal`, `stat`, `learn`, `teleport`, `weather`, `time`,
  `npc_name`, `state`, `spawn`, `despawn`, `run`; checks like `has()`,
  `count()`, `objective()`, `reputation()`, `rank()`, `stat()`, `hour()`,
  `weather()`, `visited()`, `chance()`, and `t()` for the game's translations.
- Writers' tools: `[a|b]` text variation, `<<random>>` blocks, self-hiding
  `<<once>>` options, `cond ? a : b`, `<<wait>>` pauses, `include:` of
  shared files.
- Binding by NPC role, or by tagging one specific NPC in game; dialogues can
  also start from the in-game Trigger Volume Tool, from any `OpenCustomUI`
  interaction, from shop-style choice pages, or when a player joins.
- A validator with file and line numbers, usable in game and from the shell.
- Creators author in the game's own Asset Editor: `.talk` is a registered
  asset type, edited in the editor's text mode under
  `Server/LowTalk/Dialogues` in any asset pack, hot-loaded on save with
  problems shown as editor notifications. The same dialogues can be written
  as `.json` assets and edited in the editor's form mode, with pickers and
  tooltips; `/lowtalk convert` moves between the two. A workspace for
  Hytale's standalone Node Editor draws the same files as a graph
  ([docs/node-editor.md](docs/node-editor.md)).
- An API for other plugins: add functions and commands, listen to
  conversations, open dialogues.

## Documentation

- [The dialogue format](docs/format.md), the full reference.
- [Design](docs/DESIGN.md), how it works and why.
- [API for other plugins](docs/api.md).
- [Testing](docs/testing.md): unit tests, the headless `/lowtalk test` runner, and the in-game test corridor.
- [Examples](examples/): a merchant, a unique village elder, a fortune
  teller that asks your name.

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
| `/lowtalk stop` | Leave your current conversation |
| `/lowtalk help [name]` | The format reference in chat: commands, functions, keywords, or one entry |
| `/lowtalk info <id>` | Outline of a dialogue: nodes, options, variables, unreachable nodes |
| `/lowtalk convert json\|talk <id> <pack>` | Write a dialogue in the other format into an asset pack (`server` = plugin folder for talk) |

Permissions: `lowtalk.creator` covers authoring (`list`, `open`, `tags`, `vars`,
`reset`, `test`, `help`, `info`); `lowtalk.admin` covers server operation (`reload`, `tag`,
`untag`, `thaw`, `testworld`). Give admins `lowtalk.*`. Players need nothing:
they reach dialogues only by using an NPC, and `stop` is open to everyone.

## Editing in game

LowTalk registers `.talk` with Hytale's Asset Editor, so dialogue authoring
uses the game's own tooling rather than a separate editor:

1. Open the Asset Editor and create or open a writable asset pack.
2. Create the folder `Server/LowTalk/Dialogues` and add a `.talk` file there.
   Files starting with `_` are shared includes and are not dialogues by
   themselves.
3. Edit it in the editor's text mode. Each save is parsed, validated and
   loaded live; errors and warnings arrive as editor notifications with line
   numbers, and selecting a file shows what is loaded from it.
4. Talk to a bound NPC, or use `/lowtalk open <id>`, to try it.

Dialogues in the plugin's own `dialogues` folder keep working alongside the
pack ones; ids must be unique across both.

## Configuration

`lowtalk.json` in the plugin folder:

- `RoleBindingMode` and `TagBindingMode`: `replace` (default; the interact key
  opens the dialogue, and `<<shop>>` reaches the store) or `crouch` (crouch
  and interact opens the dialogue, plain interact keeps the native behaviour).
- `UseHook` (default true): open bound dialogues when a player uses an NPC by
  intercepting the game's use event. Set false to route every conversation
  through NPC roles (`LowTalkOpenDialogue`) and interaction JSON instead.
- `HoldNpcDuringDialogue` (default true): freeze and face the NPC while talking.
- `ClearSkyWeather` (default `Default_Flat`): what `<<weather clear>>` shows in
  worlds that have no natural weather.
- `DialoguesFolder`, `CopyExamplesOnFirstRun`, `LogConversations`, `InfoColor`.

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

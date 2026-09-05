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
- Native hooks: `give`, `take`, `shop`, `objective`, `attitude`, `anim`,
  `sound`, `run`; checks like `has()`, `count()`, `objective()`,
  `attitude()`, `hour()`, `visited()`, `chance()`.
- Binding by NPC role, or by tagging one specific NPC in game.
- A validator with file and line numbers, usable in game and from the shell.
- An API for other plugins: add functions and commands, listen to
  conversations, open dialogues.

## Documentation

- [The dialogue format](docs/format.md), the full reference.
- [Design](docs/DESIGN.md), how it works and why.
- [API for other plugins](docs/api.md).
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
| `/lowtalk stop` | Leave your current conversation |

Everything except `stop` needs the `lowtalk.admin` permission.

## Configuration

`lowtalk.json` in the plugin folder:

- `RoleBindingMode` and `TagBindingMode`: `replace` (default; the interact key
  opens the dialogue, and `<<shop>>` reaches the store) or `crouch` (crouch
  and interact opens the dialogue, plain interact keeps the native behaviour).
- `DialoguesFolder`, `CopyExamplesOnFirstRun`, `LogConversations`.

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

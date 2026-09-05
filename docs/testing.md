# Testing LowTalk

Three layers, cheapest first.

## 1. Unit tests (no server)

```
./gradlew test
```

Covers the parser, expression language, validator, interpreter, and the `.ui`
layout files (checked against vocabulary harvested from Hytale's own layouts,
because a bad word in a layout disconnects every joining player).

## 2. Headless play-through (server, no clicking)

```
/lowtalk test <dialogue> [apply] [choice ...]
```

Plays a dialogue with the NPC you're looking at (or none), printing every
line, choice, and effect to chat and the log. Choices are 1-based numbers or a
prefix of the option text; a text prompt consumes the next token as the
answer. Without `apply`, effects are listed but not run.

```
/lowtalk test rootling_merchant 2 3 1 4
/lowtalk test fortune_teller Chromecide 1 1
/lowtalk test rootling_merchant apply 2
```

## 3. The test corridor (server, in game)

A flat world with one straight corridor. Each station is an NPC whose
nameplate says what it tests and whose dialogue tells you what to do and what
should happen. Build it once, then visit it whenever you like:

```
/lowtalk testworld build     # creates the world "lowtalk_test", builds the corridor, spawns the stations
/lowtalk reload              # picks up the station dialogues copied into dialogues/tests/
/lowtalk testworld go        # teleports you to the corridor entrance
```

Walk east. Stations, in order:

| # | Nameplate | Exercises |
|---|-----------|-----------|
| 1 | Basics | lines, Continue only between consecutive lines, hubs, hidden vs greyed options, jump, end/Leave |
| 2A / 2B | Memory | per-NPC `$met`, `$player.` counters, `once`, `visited()`, guarded starts; talk to A then B |
| 3 | Text input | `<<input>>`, Enter and OK, interpolation, `plural()` |
| 4 | Items | `give`, `take`, `has()`, `count()`, narration lines |
| 5 | Feedback | `notify` styles, minor and major `title`, `sound`, `anim` |
| 6 | Body | `heal`, `stat` set and add, `effect`, `cure`, `stat()`, `max_stat()`, `effect()` |
| 7 | Progress | `objective` start and `objective()` state, `reputation` and `rank()` |
| 8 | Shop and travel | `<<shop>>` hand-off on a real merchant, `<<teleport>>` |
| 9 | Random and time | `chance()`, `random()`, `ordinal()`, `hour()`, `$npc.` counters |

The station dialogues live in `examples/tests/` and are copied to
`dialogues/tests/` by the build command, overwriting, so they always match the
plugin version. Edit them freely to add checks; `/lowtalk reload` picks up
changes.

To start over, stop the server and delete `universe/worlds/lowtalk_test` in
the server folder, then remove the `built` flag with `/lowtalk vars` in mind:
it lives in `data/world.json` under the `lowtalk_test` scope. Or simply build
a fresh server folder.

## When something fails

- Dialogue problems: `/lowtalk reload` prints file and line.
- A window that misbehaves: turn on `LogConversations` in `lowtalk.json` and
  read the `[LowTalk|P]` lines.
- A player disconnected on join right after a layout change: read the newest
  client log in the client's `UserData/Logs` folder; it names the file, line,
  and property the client could not parse.

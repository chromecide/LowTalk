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
/lowtalk testworld build     # creates the world "lowtalk_test", builds the corridor, spawns the stations; run again to rebuild in place
/lowtalk reload              # picks up the station dialogues copied into dialogues/tests/
/lowtalk testworld go        # teleports you to the corridor entrance
/lowtalk testworld respawn   # fresh run: forgets your dialogue memory, resets standing with LowTalk_Testers,
                             # cancels your active objectives, restores health, clears effects, removes the
                             # bread the stations gave you, respawns every station NPC and returns you to the entrance
/lowtalk testworld probe     # stand near an NPC: reports every gate the game checks before it reacts to you
                             # (frozen, role state, view sector, attitude, interactable mark, UseNPC wiring)
/lowtalk testworld freeze    # freezes any station NPC that is still wandering
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
| 7 | Progress | `objective` start and `objective()` state, `reputation` and `rank()`. The base game defines no reputation groups or ranks, so LowTalk ships a `LowTalk_Testers` group (only the `LowTalk_Tester` role) and three ranks, `LowTalk_Disliked` (below 0), `LowTalk_Neutral` (0-19) and `LowTalk_Liked` (20+), under `Server/NPC/Reputation/`. Ranks are global in Hytale, so remove these files if your server defines its own ranks. |
| 8 | Shop and travel | `<<shop>>` hand-off on a real merchant, `<<teleport>>` |
| 9 | Random and time | `chance()`, `random()`, `ordinal()`, `hour()`, `$npc.` counters |
| 10 | Format extras | `[a|b]` text variation, `<<random>>` blocks, `<<once>>` options, `? :` in text, `<<wait>>`, `include:` of `_shared.talk` |
| 11 | Weather, time, translation | `<<weather>>` for the world and for one player, `<<time>>` by name, with a fade, pause and resume, `weather()`, `hour()`, `t()` from the pack's language file |
| 13 | Music, effects, camera | `<<music>>`, `<<vfx>>`, `<<camera>>` |
| 14 | Opened by the role | the `LowTalk_Talker` role's own interaction instruction uses the `LowTalkOpenDialogue` action; LowTalk's use hook is not involved (`npc: none`) |
| 12 | NPC control and objectives | `<<npc_name>>`, `<<spawn>>`, `<<despawn>>`, `<<objective cancel>>`, `<<objective line>>`, `objective_line()`, and the `LowTalkNode` task type: start `Objective_LowTalk_Talk` here, then talk to station 1 to complete it |

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


## Station 14 and game mode

Station 14 is a live NPC whose own role opens the dialogue, the way a shipped NPC would. The game's NPC brains ignore Creative players unless "Allow NPC detection" is turned on in the creative settings, so in Creative the Talker shows no hint and does not react. Switch with `/gamemode adventure` (the other stations work in any mode because LowTalk's use hook, not the NPC brain, opens them and shows their prompt). `/lowtalk testworld go` and `probe` tell you when you are undetectable.

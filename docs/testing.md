# Testing LowTalk

Three layers, cheapest first.

## 1. Unit tests (no server)

```
./gradlew test
```

Covers the parser, expression language, validator, interpreter, and the `.ui`
layout files (checked against vocabulary harvested from Hytale's own layouts,
because a bad word in a layout disconnects every joining player).

A command has to be registered in about seven places to work everywhere, and
`<<calm>>` reached a test server missing two of them. Four of those places are
now checked against the one list they should agree with, and fail the build if
a command is absent: the in-game reference, the editor's Add menu, the
editor's argument specs, and the generated Node Editor workspace.

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
/lowtalk testworld leave     # back to the main world in your old game mode; unloads the test world when empty
/lowtalk testworld probe     # stand near an NPC: reports every gate the game checks before it reacts to you
                             # (frozen flags, role state, view sector, attitude, interactable mark, UseNPC wiring)
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
| 7 | Progress | `objective` start and `objective()` state (see the objective warning below), `reputation` and `rank()`. The base game defines no reputation groups or ranks, so LowTalk ships a `LowTalk_Testers` group (only the `LowTalk_Tester` role) and three ranks, `LowTalk_Disliked` (below 0), `LowTalk_Neutral` (0-19) and `LowTalk_Liked` (20+), under `Server/NPC/Reputation/`. Ranks are global in Hytale, so remove these files if your server defines its own ranks. |
| 8 | Shop and travel | `<<shop>>` hand-off on a real merchant, `<<teleport>>` |
| 9 | Random and time | `chance()`, `random()`, `ordinal()`, `hour()`, `$npc.` counters |
| 10 | Format extras | `[a|b]` text variation, `<<random>>` blocks, `<<once>>` options, `? :` in text, `<<wait>>`, `include:` of `_shared.talk` |
| 11 | Weather, time, translation | `<<weather>>` for the world and for one player, `<<time>>` by name, with a fade, pause and resume, `weather()`, `hour()`, `t()` from the pack's language file |
| 12 | NPC control and objectives | `<<npc_name>>` (rename it, then check the name is still there after a server restart), `<<spawn Kweebec_Merchant @spawned_helper 2 0 0>>`, `<<objective cancel>>`, `<<objective line>>`, `objective_line()`, and the `LowTalkNode` task type: start `Objective_LowTalk_Talk` here, then talk to station 1 to complete it (see the objective warning below) |
| 12a | Spawned helper | the NPC station 12 spawns. It is tagged `@spawned_helper` and `test_spawned.talk` binds to that tag, so an NPC that did not exist a moment ago arrives with a dialogue of its own rather than its role's. "Send me away" runs its `<<despawn>>` on itself, so the walk never loses a station. |
| 13 | Music, effects, camera | `<<music>>`, `<<vfx>>`, `<<camera>>` |
| 14 | Opened by the role | the `LowTalk_Talker` role's own interaction instruction uses the `LowTalkOpenDialogue` action; LowTalk's use hook is not involved (`npc: none`) |

> **Stations 7 and 12 start real objectives, and Hytale's objective system is rough.** We have seen the client
> crash with an index error while it updated the objective tracker, including on objectives LowTalk had no part
> in, so it is a game bug rather than this mod's. If the client drops during those two stations, that is the most
> likely cause: rejoin, run `/lowtalk testworld respawn` to cancel any active objectives, and carry on with the
> other stations. Nothing else in the test world depends on them.

The station dialogues live in `examples/tests/` and are copied to
`dialogues/tests/` by the build command, overwriting, so they always match the
plugin version. Edit them freely to add checks; `/lowtalk reload` picks up
changes.

To start over, stop the server and delete `universe/worlds/lowtalk_test` in
the server folder, then remove the `built` flag with `/lowtalk vars` in mind:
it lives in `data/world.json` under the `lowtalk_test` scope. Or simply build
a fresh server folder.

## What the corridor does not reach

Worth saying plainly, because a corridor you can walk end to end is easy to mistake for full coverage. None of
these has a station:

- **`on: join`** — needs a disconnect and a reconnect, which no dialogue can ask for.
- **Bound blocks and props** — a station is an NPC. Bind a dialogue to a door or a chest with `/lowtalk tool`
  and use it; the prop path is the same tool on an entity spawned from the game's Entity Spawn page.
- **Trigger volumes** — LowTalk registers `LowTalkDialogue`, `LowTalkCondition` and `LowTalkSetVariable` with
  the trigger volume plugin on every boot, and nothing in the corridor places a volume.
- **`<<run>>`** — off unless you turn `AllowRunCommand` on, so testing it means editing `lowtalk.json` twice.
- **`<<calm>>` and typed number input** (`<<input $n "How many?" number>>`) — both want an NPC that is trying
  to kill you and a box you can type the wrong thing into, neither of which belongs in a corridor a creator
  walks through.
- **The layout chain** — dialogue, pack, API, server config, and `ForceLayout` over all of them. Checking the
  order means editing config between attempts.
- **The in-game editor** — the largest surface in the mod, and nothing automated touches it. The walkthrough
  below is the whole of its coverage.

## When something fails

- Dialogue problems: `/lowtalk reload` prints file and line.
- A window that misbehaves: turn on `LogConversations` in `lowtalk.json` and
  read the `[LowTalk|P]` lines.
- A player disconnected on join right after a layout change: read the newest
  client log in the client's `UserData/Logs` folder; it names the file, line,
  and property the client could not parse.


## Station 14 and game mode

Station 14 is a live NPC whose own role opens the dialogue, the way a shipped NPC would. The game's NPC brains ignore Creative players unless "Allow NPC detection" is turned on in the creative settings, so in Creative the Talker shows no hint and does not react. `/lowtalk testworld go` and `respawn` switch you to Adventure mode on arrival for that reason (the other stations work in any mode because LowTalk's use hook, not the NPC brain, opens them and shows their prompt; station 8 is a real merchant, so its own role shows the trade prompt and only when it can see you). `/lowtalk testworld go` and `probe` tell you when you are undetectable.

## Editing a station in place

`/lowtalk tool`, then click any station with the tool in hand. The station's dialogue opens in edit
mode. Change a line, add an option pointing at "+ new passage", press Go to walk into it, write a
line there, add a command with the Add dropdown and pick its argument, open an option's "..." to
give it a condition, then Test here to play the draft from that passage, and Save to write it to
`dialogues/tests/`. The next `/lowtalk testworld build` copies the bundled tests back over your edits.

Click an NPC with no dialogue (spawn one with `/npc spawn Kweebec_Merchant` outside the corridor, or
any vanilla NPC) to see the new-dialogue form.

The test world is one shared world named `lowtalk_test`, not a per-player instance: everyone who runs `go` lands in
the same corridor. Dialogue memory is per player, so several people can test at once, but the station NPCs are
shared (a despawned station is gone for all until `respawn`).

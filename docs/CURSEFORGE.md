# CurseForge listing draft

**Title:** LowTalk — hand-written NPC dialogue

**Summary (one line):** Give any NPC a real conversation: branching dialogue
written in plain text, with memory, choices, and native Hytale rewards. Open
source.

**Description:**

LowTalk lets you write conversations for NPCs the way you'd write a script,
in a small text format that's pleasant to type and easy to keep in git:

```
npc: Kweebec_Merchant

== greeting
<<if $met>>
  Ah, {player}. Back again.
<<else>>
  Well met, traveler.
  <<set $met = true>>
<<endif>>
-> What do you sell?
    <<shop>>
-> I'm hungry and have no coin. <<if not $got_bread>>
    Take this. Don't tell the Elder I'm going soft.
    <<give Food_Bread 1>>
    <<set $got_bread = true>>
-> I should be going.
    <<end>>
```

Drop the file in the LowTalk folder, run `/lowtalk reload`, and every
Kweebec merchant on your server has that conversation. Each one remembers
what each player said to it.

**What it does**

- Branching dialogue with choices, conditions, and hubs that just work.
- NPCs remember players: per-NPC memory, plus variables that follow a player
  between NPCs, plus world state.
- Native rewards and hooks: give and take items, open the NPC's own barter
  shop, start Hytale objectives and read their state, change the NPC's
  attitude, play animations and sounds, run commands.
- Text input, so an NPC can ask the player's name or pose a riddle.
- Bind a dialogue to every NPC of a role, or tag one specific NPC in game.
- A validator that tells you the file and line of every mistake.
- An API so other plugins can add their own functions and commands.
- Everything is server side. Players need nothing installed.

**Not a quest engine.** LowTalk hands out Hytale's own objectives and reads
their state. It leaves journals and trackers to the mods that do those well.

**Open source, MIT.** Fork it, extend it, ship it with your adventure map.

**Commands:** `/lowtalk reload`, `list`, `open <id>`, `tag <name>`,
`untag <name>`, `tags`, `vars`, `reset`, `stop`. Admin commands need the
`lowtalk.admin` permission.

**Requirements:** Hytale 0.6.x server. No dependencies.

**Screenshots to capture before publishing:**

1. The window mid-conversation with four options visible.
2. A merchant handing over bread, with the narration line showing.
3. A text-input prompt (the fortune teller asking for a name).
4. A `.talk` file open in a text editor next to the in-game result.
5. `/lowtalk reload` output with a deliberate error, showing the line number.

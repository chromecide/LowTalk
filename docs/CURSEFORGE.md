# CurseForge listing draft

**Title:** LowTalk — NPC dialogue, made in game or in your editor

**Summary (one line):** Give any NPC a real conversation: branching dialogue with memory, choices and native
Hytale rewards. Build it in game with a tool, in the Asset Editor, in the Node Editor, or as a text file. Open
source.

**Description:**

LowTalk lets you give any NPC a conversation that remembers the player: greetings that change once you have
met, choices that hand out items or start objectives, secrets that only unlock after another NPC has been spoken
to. You make it whichever way suits you:

- **In game:** `/lowtalk tool`, click an NPC, and its conversation opens as an editable version of the dialogue
  window. Add lines, options, commands with pickers, conditions and branches without touching a file.
- **In the Asset Editor:** dialogues are a registered asset type, edited as a form with tooltips and autocomplete
  or as text, validated and loaded on every save.
- **In the Node Editor:** a workspace lets you draw the whole conversation as a graph.
- **As a text file:** a small script-like format for writers who like to type:

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
`untag <name>`, `tags`, `vars`, `reset`, `stop`. Authoring commands need
`lowtalk.creator`, server operation needs `lowtalk.admin` (give admins
`lowtalk.*`); players need nothing.

**Requirements:** a Hytale server; one jar per Hytale line (release and pre-release) is attached to each GitHub
release. No dependencies.

**AI Use Disclosure**

LowTalk was designed, directed and play-tested by one person; most of the code
and documentation was written by Claude Code under that direction. There is no AI inside the mod: every line a
player reads is written by a dialogue author, and the plugin makes no network calls. The repository is public so
you can see exactly what you are running.

**Screenshots** (in `docs/screenshots/`, ready to upload; captions are the suggested CurseForge captions)

![The dialogue window: the Rootling Merchant greets the player, four options below](screenshots/dialogue-window.png)
*Talking to an NPC. The window is the game's own UI style; options appear as the conversation branches.*

![A returning visit: the merchant remembers the player, hands over bread with a "You receive 1 Bread" line, and the options remain](screenshots/dialogue-give-bread.png)
*Memory and consequences: the merchant greets a returning player by name, and a choice hands over real bread with the game's own narration line.*

![The merchant's barter shop opened from the conversation](screenshots/merchant-shop.png)
*"What do you sell?" opens the NPC's own barter shop. LowTalk hands off to the game's systems rather than replacing them.*

![The in-game editor on a node: a line, a set-variable row and a jump, each an editable field](screenshots/ingame-editor-node.png)
*The in-game editor: click an NPC with the LowTalk tool and its conversation opens as fields. This node greets the player, remembers the meeting and jumps to the hub.*

![The in-game editor on the hub node: four options, each with a target dropdown](screenshots/ingame-editor-options.png)
*Options in the in-game editor. Each has a target: another node, the end, back to the options, or a new node; Go walks into it.*

![The Node Editor showing a whole dialogue as a wired graph](screenshots/node-editor-graph.png)
*The same kind of dialogue drawn in Hytale's Node Editor with the LowTalk workspace: nodes, options, branches and commands wired up.*

![The Asset Editor's form view of a dialogue: an option with its condition, once flag and body](screenshots/asset-editor-form.png)
*The Asset Editor's form: a dialogue as fields. This option is shown only until the errand is taken, once, and its body hands over bread.*

![The Asset Editor's text mode with a .talk file open and LowTalk's load report in the corner](screenshots/asset-editor-text.png)
*Text mode in the Asset Editor. Every save is validated and loaded; the notification says what was loaded and which NPCs it binds to.*

Still to capture: the fortune teller's text-input prompt (retake after the portrait fix); `/lowtalk reload` output showing a line-numbered error.

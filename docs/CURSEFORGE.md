# CurseForge listing draft

**Title:** LowTalk — dialogue for NPCs, props and blocks, made in game or in your editor

**Summary (one line):** Give any NPC, prop or block a real conversation: branching dialogue with memory, choices
and native Hytale rewards. Build it in game with one tool, in the Asset Editor, in the Node Editor, or as a text
file. Open source.

**Description:**

LowTalk lets you give any NPC a conversation that remembers the player: greetings that change once you have
met, choices that hand out items or start objectives, secrets that only unlock after another NPC has been spoken
to. A book on a table, a statue, a signpost or a door can talk too. You make it whichever way suits you:

- **In game:** `/lowtalk tool`, use it on an NPC, a prop or a block, and a page shows what it says: bind a
  dialogue, edit it, or create one already bound to it. Edit opens the conversation as an editable version of the
  dialogue window: add lines, options, commands with pickers, conditions and branches without touching a file.
  Use the tool on nothing for a browser of every dialogue on the server.
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
- Bind a dialogue to every NPC of a role, or to one specific NPC, from the tool's page.
- Talking props: spawn any block or item as a prop (the game's Entity Spawn page), bind a dialogue with the tool,
  and it gets a "Press F to read" prompt. Move it with the Entity Tool; the dialogue follows.
- Clickable blocks: doors, chests, signs, benches and levers can open a dialogue instead of, or as well as, their
  own action.
- Conversations in a bar at the bottom or top of the screen with the NPC in view, or in a window; full history
  or latest line only; the HUD out of the way while talking. Each mod or pack picks its own defaults.
- A validator that tells you the file and line of every mistake.
- An API so other plugins can add their own functions and commands.
- Everything is server side. Players need nothing installed.

**Not a quest engine.** LowTalk hands out Hytale's own objectives and reads
their state. It leaves journals and trackers to the mods that do those well.

**Open source, MIT.** Fork it, extend it, ship it with your adventure map.

**Commands:** `/lowtalk tool`, `browse`, `reload`, `list`, `open <id>`, `tag <name>`,
`untag <name>`, `tags`, `vars`, `reset`, `stop`, `block bind|unbind|list`, `prop list|unbind`. Authoring commands need
`lowtalk.creator`, server operation needs `lowtalk.admin` (give admins
`lowtalk.*`); players need nothing.

**Requirements:** a Hytale server. **Supported Hytale versions:** release line 0.6.3 to 0.6.5 (`LowTalk-0.3.1.jar`),
pre-release line 0.7.0-pre.2 (`LowTalk-0.3.1+hytale.0.7.0-pre.2.jar`); one jar per line is attached to each GitHub
release and the server refuses the wrong one. No dependencies. LowTalk 0.1.1+ also carries a workaround for the 0.7.0-pre.2
boot failure `Asset 'Rope' of type Beam doesn't exist` (a Hytale asset load-order bug; see the README).

**Credits**

The tool icon, a speech bubble being clicked, was drawn by [@Trix8ea](https://x.com/Trix8ea) on X and is used
with their permission. The mod is MIT; the icon is not, and stays the artist's.

**AI Use Disclosure**

LowTalk was designed, directed and play-tested by one person; most of the code
and documentation was written by Claude Code under that direction. There is no AI inside the mod: every line a
player reads is written by a dialogue author, and the plugin makes no network calls. No generative AI is used for
imagery: the mod's art is made by people, and contributions with AI-generated images are rejected. The repository
is public so you can see exactly what you are running.

**Version notes for 0.3.1** (the "changelog" box on the file upload)

The LowTalk tool has its own icon: a speech bubble being clicked, drawn by @Trix8ea. The mod stays MIT; the
icon stays the artist's, and the layered source, both colourways and their checksums are in the repository so
the provenance of every image the mod ships can be checked. No other change: 0.3.0 jars behave identically.

**Version notes for 0.3.0** (the "changelog" box on the file upload)

One tool, one flow: the LowTalk tool opens a bind page for whatever it is used on (NPC, prop, block, or nothing
for the dialogue browser), each with Bind, Unbind, Edit and New. Talking props with prompts. Clickable blocks. A
browser of every dialogue with Edit and Test. Tool reach matches the game's editor tools. Fixes the editor's "Test
here" opening a dead window. Booted on plain 0.6.5 and 0.7.0-pre.2 servers before release.

**Screenshots** (in `docs/screenshots/`, ready to upload; captions are the suggested CurseForge captions)

![The Rootling Merchant greets a returning player, with four numbered options in the dialogue bar below](screenshots/dialogue-bar.png)
*A conversation as players see it. The bar is the default presentation, the options are numbered, and the HUD
steps out of the way while you talk. The merchant knows this player has been here before.*

![The merchant hands over bread: a "You receive 1 Bread." line sits above his reply](screenshots/dialogue-give-bread.png)
*Choices with consequences. An option hands over real bread and the game's own narration line says so. The
option that asked for it is not offered again.*

![The merchant's barter shop, opened from the conversation](screenshots/merchant-shop.png)
*"What do you sell?" opens the NPC's own barter shop. LowTalk hands off to the game's systems rather than
replacing them, and the conversation picks up where it left off when the shop closes.*

![The in-game editor on the first_meeting passage: a line, a set-variable row and a jump, each an editable field](screenshots/ingame-editor-passage.png)
*The in-game editor. Use the LowTalk tool on an NPC, press Edit, and its conversation opens as fields: this
passage greets the player, remembers the meeting and jumps to the hub. Test here plays it from this point.*

![Hytale's Asset Editor in text mode with a .talk dialogue open in an asset pack](screenshots/asset-editor-text.png)
*The same dialogues are assets. Hytale's Asset Editor opens a `.talk` file from any pack, and every save is
validated and loaded into the running server.*

![The Node Editor showing a whole dialogue as a wired graph](screenshots/node-editor-graph.png)
*Or draw it. Hytale's Node Editor with the LowTalk workspace: passages, options, branches and commands wired
together.*

Worth capturing later, not needed for the listing: the bind pages for an NPC, a prop and a block; a talking prop
with its "Press F to read" prompt; the dialogue browser; the window and top layouts beside the bar; and
`/lowtalk reload` printing a line-numbered error.

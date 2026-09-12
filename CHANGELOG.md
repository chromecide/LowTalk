# Changelog

## Unreleased

### Tool targeting

- The LowTalk tool now reaches 128 blocks, the same distance the game's own editor tools use. The Entity Tool's
  NPC outline is part of the client's built-in tool and cannot be enabled on a custom item. Nothing else about the
  tool changed: the interact key still opens the editor.

### Clickable blocks

- A dialogue can be bound to a placed block: click it with the LowTalk tool for a small page, or look at it and
  run `/lowtalk block bind <dialogue> [instead|also]`. Using the block opens the dialogue with no NPC; `instead`
  suppresses the block's own action, `also` keeps it (levers, buttons, doors). Only block types with a Use
  interaction can be bound, since the server reports no use for other blocks. Bindings are stored in the plugin's
  `blocks.json`, not in the world.

## 0.2.1 (2026-09-12)

Hotfix. **0.2.0 does not boot**: its shipped `Example_Lore_Keeper.json` was left malformed by the packaging step
(the "unbind the example" filter rewrote only the first line of a multi-line NPC array), and the game treats a
broken asset inside a mod jar as fatal. The filter now drops the whole array, and the build parses every shipped
example before the jar is made. Also in this release: every fixed message LowTalk shows is a translation key.

### Translations

- Every fixed message LowTalk shows, in the dialogue window, the editor, chat feedback and Asset Editor
  notifications, is now a translation key in `Server/Languages/en-US/server.lang` (about 115 keys). A translator
  ships `Server/Languages/<language>/server.lang` with the same keys in any asset pack; the game picks the player's
  language. Dialogue text itself stays in the language it was written in. Command output that is data rather than
  a sentence (dialogue lists, variable dumps, validator messages, outlines) is not translated.

## 0.2.0 (2026-09-12)

The presentation release: conversations sit in a bar with the NPC in view, each dialogue, pack and server can
choose the look, the shop comes back to the conversation, and the named parts of a dialogue are now passages.
JSON files written by 0.1.x keep loading. Reinstall the Node Editor workspace (`tools/nodeeditor/install.sh`):
its root node, its Jump and Start fields and its Say node changed.

### History

- `history: latest` shows only what the NPC is saying now, with no transcript and no echo of the player's
  answers; `full` (the default) is the scrolling transcript. Set per dialogue, per pack (`History` in
  `Settings.json` or `setPackDefaults`), or in `lowtalk.json`, like the layout.

### Shop

- `<<shop>>` no longer ends the conversation. The game's shop page opens as before; its Back button (or Escape)
  returns to the dialogue, which carries on with the statements after the command, transcript intact. A
  `<<shop>>` with nothing after it still ends the conversation as the shop opens.

### Passages (was: nodes)

- The named stretches of conversation are now called passages everywhere: the Node Editor type is `Passage`, JSON
  uses `Passages` on the dialogue and `Passage` on Jump, Start and the `LowTalkNode` objective task, the in-game
  editor says passage, and so do the docs and error messages. Old files keep loading: `Nodes` and `Node` are still
  read, never written. `.talk` files are untouched (`== name`, `<<jump name>>`, `visited("name")`), and the
  objective task keeps its `LowTalkNode` type id.

### Lines

- A line can name its Continue button: `text => Go on` in `.talk`, a `Button` field on Say in JSON, the form and
  the Node Editor, and a third field on line rows in the in-game editor. Empty keeps Continue.
- Node Editor workspace: every pin now carries a description (shown as a tooltip), and Say's speaker is marked
  optional.

### Examples

- `Lore_Keeper`: a simpler Node Editor example, a Kweebec elder with a hub of three lore topics, one node each,
  and an option that appears once all three are heard (`visited()`). Shipped in the pack as an Asset Editor
  reference, as a graph in `tools/nodeeditor/examples/`, and as `examples/lore_keeper.talk`.
- The Node Editor install script now installs into every Hytale client it finds.

### Layout: bottom bar, top bar or window

- Conversations now default to a bar along the bottom of the screen with no dimming overlay, so the NPC stays in
  view. Options are numbered and the number keys (1 to 8, keypad too) pick them. `layout: top` and
  `layout: window` (the old look) are the alternatives; the directive is `Layout` in JSON, a dropdown in the
  in-game editor and a field in the Node Editor workspace.
- Per-pack defaults: `Server/LowTalk/Settings.json` in an asset pack, or `LowTalkApi.setPackDefaults` from a
  plugin, set the layout and hidden HUD parts for every dialogue in that pack. Precedence: dialogue directive, pack
  file, API defaults, server config; the server's new `ForceLayout` overrides all of them.
- The configured HUD parts (`HideHudDuringDialogue`, default reticle and hotbar) are hidden while a dialogue is
  open and restored when it closes, whichever way it closes.

### Hytale 0.6.5 notes

Checked 2026-09-11. Eight server classes changed (connection registry and packet handlers, kick and ban
plumbing, the Asset Editor packet handler's pending-packet queue, world-map image builder); none is touched by
either mod. Classes compiled against 0.6.4 and 0.6.5 are identical, both mods boot clean, and the 0.1.1 release
jar's `>=0.6.3 <0.7.0` range already covers it.

## 0.1.1 (2026-09-11)

Compatibility release. No dialogue-facing changes.

- Boots reliably on Hytale 0.7.0-pre.2: LowTalk injects the Interaction-after-Beam asset load-order edge the
  server forgets to declare (see the pre.2 notes below). No effect on the 0.6.x release line.
- Verified on 0.6.4 (release) and 0.7.0-pre.2 (pre-release); jars for both lines.

### Hytale 0.7.0-pre.2 notes

Checked 2026-09-11. Both mods compile and boot. 363 server classes changed, mostly world generation; the only new
deprecation is the boolean `showEventTitleToPlayer` overload (marked for removal, replaced by `EventTitleStyle` and
`EventTitleConfig` variants that do not exist on 0.6.x, so the shared source keeps the old call until 0.7 reaches
the release line). One real bug: the new `AttachBeam` interaction validates its beam reference as soon as the asset embedding it
(the vanilla Hookshot's ProjectileConfig) is decoded, but no store declares a load order against the Beam store,
and stores sit in a hash map, so whether the server boots depends on class identity hashes. Vanilla and a plain `mods/` install happened to pass; the Gradle dev layout failed every time with
"Asset 'Rope' of type Beam doesn't exist". LowTalk now injects the missing edge during setup (`AssetLoadOrderFix`),
which is a no-op on 0.6.x and once Hypixel fixes it. Bug report drafted for Hypixel.

The Node Editor update (start screen, minimap, themes, snapping, copy/paste and unknown-node fixes) changes
nothing in our workspace format: every key we use is still one Hypixel's own workspaces use. But the client update
deleted the installed `LowTalk - Dialogue` workspace on both lines, as every client update will; rerun
`sh tools/nodeeditor/install.sh` (pass the pre-release client's `NodeEditor/Workspaces` path for that line).

### Hytale 0.6.4 notes

Checked 2026-09-08 the day the release-line update landed. The 0.6.4 server differs from 0.6.3 in fifteen classes
(block-entity anchor migration, prefab paste offsets, explosion radius guard, respawn-page validity check, ICE
keepalives, world-gen prop distribution); none is touched by LowTalk or Companions. Both mods compile unchanged,
boot clean on 0.6.4 and every asset, NPC role and UI macro they reference is still present. The release jar keeps
its `>=0.6.3 <0.7.0` range because the two builds are identical.

### Hytale 0.7.0-pre.1 notes

Dry run 2026-09-06 against the pre-release line. One compile break: the event-title packet's boolean became a
style enum (fixed by using `EventTitleUtil`, present in both lines). Deprecated for removal: `World`'s chunk getters
(now `ChunkStore.getChunkReferenceAsync(index, 4)`; the flag matters, without it chunks accept blocks but not
entities) and the `Warp` class (still returned by the teleport plugin). All imported classes and all UI macros we
use still exist. Both mods boot, load their assets and pass the test corridor.

## 0.1.0 (2026-09-06)

First working version.

- `.talk` dialogue format: nodes, lines, options with guards, if/elseif/else,
  once-blocks, set, jump, end, text input, interpolation, expressions.
- Five variable scopes: local (player with NPC), player, npc, world, tmp.
- Built-in functions: has, count, visited, objective, attitude, perm, hour,
  random, chance, ordinal, plural, reputation, rank, stat, max_stat, effect,
  knows.
- Built-in commands: give, take, shop, attitude, objective, anim, sound, run,
  reputation, notify, title, effect, cure, heal, stat, learn, teleport.
- Bind dialogues to NPC roles or to individual NPCs by tag; replace or
  crouch interaction modes.
- Dialogue window with up to eight options, text input, optional portrait.
- `/lowtalk reload | list | open | tag | untag | tags | vars | reset | stop`.
- Validator with line-numbered errors, also runnable from the command line;
  flags unreachable nodes and unset variables; reload checks asset ids.
- Format extras: `[a|b|c]` text variation, `<<random>>`/`<<or>>`/`<<endrandom>>`
  blocks, self-hiding `<<once>>` options, `cond ? a : b` expressions,
  `<<wait seconds>>` pauses, and `include:` of shared `_name.talk` files.
- World and NPC control through the game's own systems: `weather`, `time`,
  `npc_name`, `state`, `despawn`, `spawn`; objective lines, cancel and task
  completion; `t()` translations, `weather()`, `objective_line()`.
- Test corridor: `go` switches to Adventure, `leave` returns you and unloads the world, `build` reloads dialogues.
- Asset Editor type ids read LowTalkText and LowTalkJson (the JSON asset class was renamed; files and
  folders are unchanged).
- Graceful degradation: options needing a command no plugin provides are hidden, unknown
  functions read as false, and unknown names are warnings rather than load errors.
- In-game dialogue editor: `/lowtalk tool`, click an NPC, edit lines and options in place, walk
  into nodes, Save (rewrites the file, hot-reloads) or Test the draft from the current node.
  Covers the whole format: option conditions and once, commands with game-list pickers, set,
  if/else, once, random, input, wait, jump, end, dialogue settings, and creating a dialogue for
  an NPC that has none.
- Bound NPCs show the game's own "Press [key] to talk" prompt (`ShowHint`, `HintKey`).
- `UseHook` config switch to disable the use-event binding; `npc: none` for
  dialogues opened only by roles, interactions, triggers or commands; a
  `LowTalk_Talker` test role built on the role action.
- NPC role components: the `LowTalkOpenDialogue` action and `LowTalkCondition`
  sensor, so role behaviour trees can start and gate conversations.
- `music`, `vfx` and `camera` commands, using the game's music tracker, particle
  utility and camera-shake effects; window labels come from language keys.
- `LowTalkNode` objective task type: quests complete when the player reaches a
  node of a dialogue; objectives started from a dialogue use the NPC as marker.
- Dialogue variables in the game's own tools: a `LowTalkCondition` trigger
  condition and choice requirement, and a `LowTalkSetVariable` trigger effect.
- Tab completion and did-you-mean for dialogue ids in `/lowtalk` commands.
- A Node Editor workspace (`tools/nodeeditor/`) so dialogues can be drawn as
  graphs in Hytale's standalone node editor and saved straight into a pack.
- JSON dialogue assets: the same model as `.talk`, registered as a game asset
  store so the Asset Editor's form editor (tooltips, item/sound/effect/
  objective pickers, undo) can author them; `/lowtalk convert` translates
  between `.talk` and `.json` losslessly.
- Creator aids: "did you mean" suggestions for misspelt commands, functions,
  node names and dialogue ids; curly quotes are called out; `/lowtalk help`
  reference and `/lowtalk info` outlines, also shown when selecting a file in
  the Asset Editor.
- `.talk` is an Asset Editor asset type: creators edit dialogues in the game's
  own editor under `Server/LowTalk/Dialogues`, with live loading and
  line-numbered feedback as editor notifications.
- Hooks into official extension points: a `LowTalkDialogue` trigger-volume
  effect, a `LowTalk` page for `OpenCustomUI` interactions, a `LowTalkDialogue`
  choice interaction, and `on: join` dialogues.
- Conversations end cleanly when their NPC is removed.
- Creator/admin permission split (`lowtalk.creator`, `lowtalk.admin`); runtime
  backstop against player text reaching `<<run>>`; validator warns about
  farmable reward options.
- Test corridor (`/lowtalk testworld`) with ten labelled stations; `respawn`
  resets the player's test state. Ships a `LowTalk_Testers` reputation group
  and three `LowTalk_*` ranks for testing, since the base game defines none.
- `/lowtalk test` plays a dialogue headlessly with scripted choices.
- Layout files are checked in the test suite against Hytale's own UI vocabulary.
- NPCs stand still and face the player during a conversation.
- Public API for other plugins: functions, commands, listeners, opening
  dialogues, reading and writing variables.
- Three bundled examples, copied into the dialogues folder on first run.

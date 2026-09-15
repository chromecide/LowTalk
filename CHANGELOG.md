# Changelog

## Unreleased

## 0.3.1 (2026-09-14)

### Fixed

- **Dropdowns that stretched wider than their own panel would not open at all.** Choosing a passage for a
  "they have seen the passage" condition showed a list with nothing in it: the control was the only visible
  field in its slot, so it grew to the width of the whole row while the panel it was told to draw stayed 300
  wide, and the client silently declined to open it. Every dropdown in the editor now has a width that matches
  its panel. The same fault was waiting in the command rows, where `<<attitude>>` — whose only argument is a
  choice — would have behaved identically.

### Security review before release

- **The jar contains this mod's code and nothing else.** The build plugin was adding a five-class asset-editor
  runtime to every jar; nothing here ever called it, and a jar anyone can unzip and check is worth more than an
  assurance that the extra code is harmless. It is no longer bundled.
- LowTalk makes no network connections of any kind, and the README now says so plainly.

- **Text a player typed can no longer reach `<<run>>` by way of a saved variable.** The guard that kept player
  input out of console commands lived on the conversation, so walking away and starting another one laundered it:
  text typed into a saved variable in one dialogue was no longer recognised as the player's in the next, and the
  validator could not see it either, because it reads one file at a time. The mark now belongs to the variable,
  is saved with it, and survives restarts; an ordinary `<<set>>` by the author clears it, so a variable the author
  takes back is theirs again.
- **An interpolated `<<run>>` value may no longer start with a dash.** The game reads `--name` and `--name=value`
  as an optional argument, and `AbstractTargetPlayersCommand` has `--all`; a player who typed `--all` into a
  variable used by `<<run>>` would have aimed a console command at everybody. `@` is refused too, since the game
  has no selector syntax for it to mean.
- **An answer to `<<input>>` is cut to 256 characters.** It is saved in a variable that is written to disk, and
  what arrives is whatever the client sent, so there was no limit on how much a player could store.
- **`<<run>>` is now off unless a server owner turns it on.** *This is a breaking change for anyone using it.* It
  executes a server command with the console's authority, and dialogues arrive in asset packs that can come from
  anybody; a file downloaded to add a shopkeeper should not be able to hand its author the server. Set
  `AllowRunCommand: true` in `lowtalk.json` to allow it. The server names the dialogues that use it at startup,
  and refusing one says so in the conversation rather than failing quietly.
- **`AllowPlayerInput`** switches off `<<input>>` for an owner who would rather not keep player-written text at
  all. It stays on by default.
- The server now lists at startup which dialogues use `<<run>>`, since those act with the console's authority.
- A reward whose name the player typed — `<<give {$their_answer}>>` and the like — is warned about rather than
  refused, since an author may mean to give a reward the player picked from a list.

Built against Hytale 0.6.6. That release changes only the QUIC transport, NAT traversal and singleplayer; the
objective, asset, language and UI systems this mod uses are byte-identical to 0.6.5, and the supported range is
unchanged at >=0.6.3 <0.7.0.

### The in-game editor explains itself

- Commands are edited as named fields instead of one box of text. A `vfx` row now reads "particle", "scale" and
  "seconds" rather than `Cinematic_Pink_Smoke 1`, a `give` row reads "item" and "count", and arguments a command
  accepts but the author has not written yet are shown empty rather than being invisible. Arguments the runtime
  accepts in any order, such as a notification's style or a title's size and duration, are sorted into their own
  fields. A command whose arguments cannot be laid out that way, or one another mod added without naming its
  arguments, keeps the plain text box.
- Fixed three pickers that filled the wrong argument. The picker on a `reputation` row offered group ids but wrote
  them over the amount; on `notify` and `title` it offered a style but overwrote the author's second line of text;
  on `objective` it offered dialogue ids, which are not objectives. Each command's picker now fills the argument
  that is actually an id, and a style is chosen from a list on the row itself.
- Items, sounds, entity effects, objectives and objective lines can be picked from the game's own lists. These are
  the arguments of the most-used commands, and until now every one of them had to be typed from memory. An
  argument that is an id is one control: a list that searches as you type, using the search box the game's own
  dropdowns have. There is no separate text box to type into, and no picker sitting at the far end of the row.
  A text box appears only where a list cannot be the whole answer: an argument whose list is a suggestion rather
  than the truth, such as the time of day, and one whose value is not a plain id, such as one built from a
  variable.
- A list too long for a dropdown gets a page of its own. A dropdown is handed its entries once, so its search box
  can only filter what it was given, and a stock server has more than three thousand items. An argument like an
  item or a sound is a button showing what is chosen; pressing it opens a picker whose list follows what you type,
  a keystroke at a time, and picking one puts you back in the editor with the passage and the unsaved draft as you
  left them. It is the same shape as the game's own particle, sound and entity pickers.
- The lists of passages, of NPC roles and tags, and of command names search as you type too.
- The Add menu says what each kind of row does, and offers the commands people reach for by name: give an item,
  start an objective, open the shop, play an animation, play a sound, show a title, show a notification, play a
  particle effect. Picking one inserts that command ready to fill in, instead of hiding it behind "Command".
- Mistakes are shown while they are made. The editor checks the draft on every change and marks the row with "!"
  for something that must be fixed and "?" for a warning, with a count and the first message under the rows. It
  used to check only when Save or Test was pressed, and reported a file and line number that a creator working in
  a window could not act on.
- Conditions are chosen rather than written. A condition is now what it is about, its own argument, a comparison
  and a value: "the player has | Food_Bread | yes", "the objective | Find_The_Elder | is | complete", "this NPC's
  attitude to them | is | friendly". Sixteen shapes are offered, covering variables, items and how many of them,
  passages seen, objectives and objective lines, attitude, reputation and rank, stats, effects, recipes,
  permissions, the hour, the weather and a chance. Each argument gets the same treatment as a command's: a list
  where the game has one, and the picker page where that list runs to thousands. The dialogue's own variables and
  passages are offered in their lists.
- A condition that is none of those shapes, with an "and", an "or", brackets or arithmetic in it, keeps its text
  box, and "(write it by hand)" in the menu turns any condition back into one. An option's two conditions each
  have a row of their own now, so they have room for the fields.
- Other mods can name their own commands' arguments with `registerCommandArgs`, and get the same named fields and
  pickers as the built-in commands.

### Where a new dialogue goes

- A dialogue made in game is an asset in a pack by default. It used to be a `.talk` file in the server's own
  dialogues folder, where nothing but LowTalk could see it: not the Asset Editor, not the Node Editor, and it
  travelled with nothing. It is now written as JSON into an asset pack, which gives it a form in the Asset Editor,
  a graph in the Node Editor, fields in here, and a home that ships. The New page keeps `.talk` as a choice for
  people who write by hand, and still offers the server's folder, which takes text files only.
  A dialogue written as an asset is named the way the game names assets, Capitalised_Words, since anything else
  makes the asset store log a warning about it on every load; the page says so when it renames what was typed. A
  text file keeps the name as written, because no asset store reads one.
  The page lists the packs the server started with. A pack made while the server is running, in the Asset Editor
  or anywhere else, is only picked up on the next start, which is the game's own rule: registering one with a
  running server takes a lock held for the server's lifetime.

### Names that match what you see

- The objective list says what each objective asks for. An id such as `Objective_Gather` says nothing about what
  the player will be told to do, and the dozen objectives Hytale ships are samples, so starting one and being
  asked for three dirt comes as a surprise. Each entry now reads "Objective_Gather - Gather 3 Soil_Dirt", and an
  objective line says how many objectives it strings together. The format guide now also says plainly that an
  objective is an asset you write, and where it goes.

- `<<title>>`'s second line is named for where it lands. The game draws it small and above the main line, the way
  it announces a zone, but the editor called it "under it" and the reference called it a secondary title. The
  field now reads "above it", and the reference and the format guide say where it goes. Nothing about what the
  command does has changed.

### Where things happen

- A dialogue bound to a block now knows where that block is, so what it does happens there. `<<vfx>>` played its
  particles on whoever opened the conversation, because a block is not an entity and the position it was used on
  was thrown away; a talking campfire lit its sparks around the player's feet instead of at the fire. `<<sound>>`
  was played flat into the player's ears for the same reason, and now comes from the block, for everyone near it.
  Dialogues on NPCs and props were always placed correctly and are unchanged.
- `<<vfx>>` takes `player` to put the effect on the player instead of on whatever is speaking, which is what you
  want for something that happens to them, such as a healing sparkle. The word can sit anywhere among the
  arguments, so `<<vfx Heal_Sparkle player>>` needs no placeholders, and the editor shows it as an "at" field.

- The tool no longer works the block it is used on. Pointing it at a lantern lit and unlit it, a door opened and
  closed, because the tool's use ran the game's own UseBlock step to find out which block was clicked, and that
  step runs the block's interaction. The tool finds the block itself now, so using it on a block only opens the
  bind page. Blocks with no use of their own still cannot be bound, since nothing would ever open them.

### Fixes carried in this release

- An option's condition may compare numbers again. `-> I'm hurt. <<if stat("Health") < max_stat("Health")>>` was
  not read as a condition at all, because the pattern that lifts a trailing `<<if>>` off an option refused any
  condition containing an angle bracket. The option was offered to everyone and the player was shown the raw
  `<<if ...>>` as part of the words. The shipped village elder example did exactly this. Found by the new warning
  about commands hidden in a line's words.
- A command typed into a line's words no longer breaks the dialogue. Writing `<<wait 2>>` at the end of a line of
  speech, rather than on a line of its own, produced a file the parser refused; the dialogue then dropped out of
  the registry, disappeared from the browser, and the open editor said "no longer loaded; nothing was saved" with
  no way to rescue the work on screen. Such a line is now kept as text, the validator warns which snippet looks
  like a command, and the editor falls back to the file the dialogue came from if the registry has lost it.
- Installing the mod no longer puts words in vanilla NPCs' mouths. `CopyExamplesOnFirstRun` now defaults to
  false, so a fresh server has no bound dialogue until someone writes or copies one. The examples still ship
  inside the asset pack, bound to nothing, ready to open in the Asset Editor or through `/lowtalk browse`.
  Set it to true in `lowtalk.json` before first run to get the old behaviour.
- `/lowtalk testworld build` no longer spawns a second set of station NPCs on top of the first. It looked for the
  old ones in the spatial index, which is filled by a ticking system and is still empty for entities that have
  just been loaded with their chunks, so a build straight after a server start found nothing to clean up. It now
  walks the world's entity store instead, and says how many it removed. `/lowtalk testworld respawn` used the
  same lookup and is fixed with it.
- New screenshots throughout, taken on 0.6.5 with the current UI: the old ones still showed the centre window
  from before the dialogue bar became the default. The CurseForge listing draft uses the new set and no longer
  points at an image that was not in the repository.
- The LowTalk tool has its own icon: a speech bubble being clicked, drawn by
  [@Trix8ea](https://x.com/Trix8ea). It replaces the borrowed builder-tools icon. The layered source, both
  colourways and their checksums are in `docs/art/`, so the provenance of every image the mod ships can be
  checked; the art is hand made, as `CONTRIBUTING.md` requires.
- The mod stays MIT, but the icon is carved out of it: it remains the artist's, used by LowTalk with permission.
  The terms are in `docs/art/README.md`, and the README's License section names the exception.

## 0.3.0 (2026-09-12)

### Tool targeting

- The LowTalk tool now reaches 128 blocks, the same distance the game's own editor tools use, and using it on a
  block opens the bind page, as the block-binding notes below describe. Its use chain now runs the game's UseBlock
  interaction before UseEntity, the same order as bare hands. The Entity Tool's NPC outline is part of the
  client's built-in tool and cannot be enabled on a custom item.

### One flow for every target

- The LowTalk tool now opens a bind page for whatever it is used on: an NPC, a prop, a block, or nothing (the
  browser). Every bind page has Bind, Unbind, Edit (the bound dialogue in the editor) and New (create a dialogue
  already bound to this target, then edit it).
- NPCs: the page lists what the NPC says now, with why (only this NPC, or every NPC of its role), and binds a
  dialogue either to this one NPC (a run-time tag, no file changed, works for dialogues from mod packs) or to
  every NPC of its role (written to the dialogue's `npc:` line, so the file must be writable). The old behaviour
  of jumping straight into the editor is gone; Edit is one click away on the page.

### Dialogue browser

- The LowTalk tool used on nothing (or on a plain block) opens a browser of every loaded dialogue: where it lives,
  what it is attached to (NPC roles and @tags, on join, prop, block) or "not attached to anything", with Edit and
  Test for each, a filter, a switch for the test-corridor dialogues, New dialogue and Reload files. Also
  `/lowtalk browse`. New dialogues made here start with `npc: none`, ready to be bound to a prop, block, trigger
  or role.
- The new-dialogue page's "Save in" list now offers only the server's dialogues folder and asset packs that are
  plain folders on disk (not the base game, core mods, archives, or LowTalk itself).
- Fixed: "Test here" in the editor, and now the browser's Edit, Test and New, opened a page whose buttons did
  nothing. Pages now replace each other directly instead of closing first, which left the game waiting for a
  close acknowledgement and dropping every event.

### Talking props

- Any block or item spawned as a prop (Entity Spawn page, `/npc spawn page`) can carry a dialogue: use the LowTalk
  tool on it, pick the dialogue and an optional speaker name. Using the prop opens the dialogue. The binding
  follows the prop when the Entity Tool moves it, and is stored in the plugin's `props.json`. The prop gains the
  game's own interactions component so the interact key reaches it; without LowTalk that component is inert.
  `/lowtalk prop list` and `/lowtalk prop unbind <uuid>` clean up after deleted props.
- A bound prop shows a prompt when a player comes close, chosen when binding: talk, read, examine, listen, use, or
  none. It uses the same marker and prompt packet the game's talkative NPCs use.
- The tool's use chain ends in a LowTalk interaction type (`LowTalkTarget`), registered the same way the game
  registers its own, so the tool reaches entities the stock UseEntity step ignores.

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

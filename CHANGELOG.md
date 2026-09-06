# Changelog

## Unreleased

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
- In-game dialogue editor: `/lowtalk tool`, click an NPC, edit lines and options in place, walk
  into nodes, Save (rewrites the file, hot-reloads) or Test the draft from the current node.
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

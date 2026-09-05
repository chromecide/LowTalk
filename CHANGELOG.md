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
- `/lowtalk test` plays a dialogue headlessly with scripted choices.
- Layout files are checked in the test suite against Hytale's own UI vocabulary.
- NPCs stand still and face the player during a conversation.
- Public API for other plugins: functions, commands, listeners, opening
  dialogues, reading and writing variables.
- Three bundled examples, copied into the dialogues folder on first run.

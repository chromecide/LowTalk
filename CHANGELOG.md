# Changelog

## Unreleased

First working version.

- `.talk` dialogue format: nodes, lines, options with guards, if/elseif/else,
  once-blocks, set, jump, end, text input, interpolation, expressions.
- Five variable scopes: local (player with NPC), player, npc, world, tmp.
- Built-in functions: has, count, visited, objective, attitude, perm, hour,
  random, chance.
- Built-in commands: give, take, shop, attitude, objective, anim, sound, run.
- Bind dialogues to NPC roles or to individual NPCs by tag; replace or
  crouch interaction modes.
- Dialogue window with up to eight options, text input, optional portrait.
- `/lowtalk reload | list | open | tag | untag | tags | vars | reset | stop`.
- Validator with line-numbered errors, also runnable from the command line.
- Public API for other plugins: functions, commands, listeners, opening
  dialogues, reading and writing variables.
- Three bundled examples, copied into the dialogues folder on first run.

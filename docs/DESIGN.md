# LowTalk design

## Goals

1. **Dialogue is the product.** Not a quest engine with a dialogue tab.
   Writers should be able to make an NPC feel like a person: conditional
   greetings, memory of earlier choices, moods, hubs, secrets.
2. **Hand-written first.** A text format you can author in any editor, keep in
   git, and diff in a pull request. Tooling comes after the format, not instead
   of it.
3. **Native where possible.** Give items, change attitudes, start objectives,
   open barter shops, and trigger role actions using Hytale's own systems, so
   LowTalk content coexists with everything else on the server.
4. **Open.** MIT licensed, with an API other plugins can call and extend.
   Contributors sign a short CLA so the project can be relicensed or
   transferred as a whole if that ever makes sense.
5. **Deterministic.** No language model in the loop. Every line a player sees
   was written by a person.
6. **Idiomatic.** Written the way Hypixel writes their own plugins: codec
   defined config, ECS systems for entity hooks, custom UI pages, the
   standard logger and command base classes. If LowTalk were ever folded into
   the game, it should read like it was always there. No decompiled code is
   ever copied; only the public API is used.

## Non-goals

- A general quest journal or objective tracker. Hytale has objectives; other
  mods have journals. LowTalk hands out objectives and reads their state.
- An in-game visual editor in the first release. A validator and a live
  reload are enough to iterate quickly.
- Client-side anything. Hytale streams server mods to players; LowTalk is a
  server plugin only.

## The landscape

At the time of writing there are two established options. NPC Dialog offers
linear pages with command buttons, edited in game, all rights reserved.
QuestLines Core offers a capable branching tree inside a large proprietary
quest engine. Neither is open source, neither treats dialogue as a craft, and
neither builds on Hytale's native objectives, attitudes, or shops. That is the
gap LowTalk aims at.

## Architecture

```
com.chromecide.lowtalk
  parser/     text -> AST. No Hytale imports. Fully unit tested.
  model/      AST types: Dialogue, Node, Line, Option, Conditional, Command, Expr.
  runtime/    Interpreter: walks a node for one player, evaluates expressions,
              produces "what to show next" and a list of effects to apply.
              No Hytale imports; effects and functions are interfaces.
  store/      Variable persistence: per player, per NPC, world. JSON files.
  hytale/     Everything that touches the server API:
                bindings (role / tag -> dialogue), the use-entity hook,
                effect implementations (give, take, shop, attitude, ...),
                function implementations (has, objective, attitude, ...),
                the dialogue window, commands.
  api/        LowTalkApi: register functions, commands, and listeners.
  LowTalkPlugin  wires it together.
```

The split matters: the parser and interpreter know nothing about Hytale, so
they can be tested with plain JUnit in milliseconds and could be reused by a
future editor or a command-line tool.

### Runtime model

A **Conversation** is one player talking to one NPC through one dialogue. It
holds a cursor (node, statement index, pending options) and a temporary
variable scope. The interpreter advances the cursor until it reaches something
the player must see: a line, a set of options, an input box, or the end.

Every advance yields a **Step**:

- `Say(speaker, text)` show a line with a Continue button
- `Choose(prompt lines, options[])` show buttons
- `Ask(prompt, variable)` show a text box
- `Finish` close the window

and a list of **Effects** to apply before showing it. Effects are simple
records (`Give(item, n)`, `SetAttitude(a)`, `Jump(node)`, ...) that the
Hytale layer executes on the world thread.

Expressions are evaluated against a **Context** that exposes variables and
functions. Built-in functions live in the Hytale layer; plugins add more
through the API.

### Persistence

Variables are the only state. Three JSON files per world under the plugin's
data folder:

- `players/<uuid>.json` per-player variables, keyed by scope then name, plus
  the set of visited nodes
- `npcs/<uuid>.json` per-NPC variables
- `world.json` world variables

Writes are batched and atomic (write to a temp file, then move). A
conversation ending, a server save, and a shutdown all flush.

### Binding dialogues to NPCs

1. **By role.** `npc: Kweebec_Merchant` applies to every NPC of that role.
   Cheap, needs no in-world setup, right for shopkeepers and guards.
2. **By tag.** `npc: @elder` applies to NPCs an admin has tagged with
   `/lowtalk tag elder` while looking at them. Tags are stored by entity UUID
   in `npcs/<uuid>.json`. Right for unique characters.
3. **By command.** `/lowtalk open <dialogue> [player]` starts a dialogue from
   anywhere, for cutscenes, signs, or other plugins.

If several dialogues bind the same NPC, the one with a matching `start when`
guard wins, then the most specific binding (tag beats role).

### Interaction

LowTalk hooks the same use-entity event as the NPC's native interaction.
Configurable per dialogue and globally:

- `replace` (default for tagged NPCs): the dialogue opens, the native use
  (shop, etc.) does not. `<<shop>>` re-opens it on demand.
- `crouch` (default for role bindings): crouch and use opens the dialogue,
  plain use keeps the native behavior.

### The window

A server-driven custom UI page: title, an optional portrait area, the current
line, and a row of buttons. Options render as buttons; lines get a single
Continue. Text input uses a text field and an OK button, with Enter handled.
The same page is reused across steps to avoid flicker; only the changed
elements are updated. Movement is frozen while a page is open, which is the
right feel for a conversation.

### Reloading

`/lowtalk reload` re-parses every file, reports errors with file and line, and
swaps the dialogue set atomically. Conversations already in progress keep the
old tree until they end.

## The format

See [format.md](format.md). Design notes on the choices:

- **Yarn-like, not Yarn.** Yarn Spinner's shape (`==` nodes, `->` options,
  `<<commands>>`) is familiar and reads well. We keep the shape and drop the
  parts that need a full language: no functions defined in dialogue, no
  arbitrary code, a small fixed expression grammar.
- **Speaker is optional.** Most lines belong to the NPC; making the speaker
  name mandatory is noise. `Name: text` covers the rest.
- **Hubs by default.** An option body that does not jump or end returns to
  the same options. This matches how most NPC conversations actually work and
  avoids a `<<jump self>>` on every branch.
- **Four variable scopes.** Player, NPC, world, and temporary. Explicit
  prefixes beat implicit rules.
- **Effects are commands, checks are functions.** `<<give>>` does something;
  `has()` asks something. Never the same word for both.

## Alignment with Hypixel's conventions

Observed in the shipped plugins and followed here:

- Config objects are plain classes with a `BuilderCodec`; the plugin calls
  `withConfig` in its constructor and `save()` in `setup()`.
- Entity hooks are `EntityEventSystem`s registered on the entity store
  registry, not ad hoc listeners.
- Windows are `InteractiveCustomUIPage`s with an event data codec; layouts
  are `.ui` files shipped in the asset pack.
- Commands extend the command base classes and declare arguments with
  `withRequiredArg` and friends, with permissions from `HytalePermissions`.
- Player-facing text goes through `Message`; console output through
  `HytaleLogger`.
- Asset-like data (dialogues) is loaded from files in the plugin folder and
  reloadable, the way NPC roles and objectives are.

## Safety and permissions

- `<<run>>` executes as the console. Only trusted admins should edit dialogue
  files, which is already true of anything in the server folder.
- Text input is stored verbatim as a string variable and never executed.
  Interpolating it into `<<run>>` is disallowed by the validator.
- Dialogue files live in the server folder, not in player-reachable places.

## Roadmap

**M1, parser.** Lexer, parser, AST, validator with line-numbered errors. Unit
tests covering the whole format. `validate` Gradle task. No server code yet.

**M2, runtime and window.** Interpreter, lines, options, jumps, end,
conditionals, set, player variables, the window. Bind by role, open on
crouch-use. Enough to ship the merchant example.

**M3, effects and functions.** give, take, has, count, shop, attitude,
objective, anim, sound, run, input, once, visited. NPC and world scopes.
Tags and `/lowtalk tag`, `/lowtalk open`, `/lowtalk reload`, `/lowtalk vars`.

**M4, polish.** Portrait area, greyed options, per-dialogue interaction mode,
hour and chance functions, localisation of UI strings, documentation pass,
CurseForge listing.

**M5, API.** Public registration of functions, commands, and listeners.
Events for dialogue start, option chosen, dialogue end. Example integration
plugin.

## Open questions

- Should `start when` guards be evaluated on every open, or cached per player
  until a variable they read changes? Start with every open; it is cheap.
- Do we want a `<<wait seconds>>` for timed lines? Probably later, and only
  if the client can re-render a page without user input.
- Portraits: Hytale UI can show textures from packs. Whether a dialogue can
  reference an NPC's own model render is unknown.

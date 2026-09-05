# The LowTalk dialogue format

A dialogue is a plain text file with the extension `.talk`, encoded as UTF-8.
It is made of a short header and one or more nodes. Indentation is two spaces
and is significant only inside options and conditionals.

The format is deliberately close to Yarn Spinner, which many writers already
know, but it is not Yarn and does not try to be.

## Header

Lines before the first node are directives, `key: value`, one per line.

| Directive | Meaning |
|-----------|---------|
| `npc:` | Which NPCs use this dialogue. A role id (`Kweebec_Merchant`) binds every NPC of that role. A tag (`@elder`) binds NPCs tagged in-game with `/lowtalk tag elder`. Repeatable. |
| `start:` | The node to begin at. Defaults to the first node. May be repeated with a guard: `start: returning when $met` is tried before an unguarded `start:`. |
| `speaker:` | Default speaker name for bare lines. Defaults to the NPC's in-game name. |
| `title:` | Shown in the window header. Defaults to the speaker. |
| `scope:` | Variable namespace shared with other files. Defaults to the file name. |
| `portrait:` | An image shown beside the text, as a path inside `Common/UI/Custom/` of any loaded asset pack, e.g. `Portraits/elder.png` from your own pack. |
| `include:` | Pull the nodes of another file into this one, e.g. `include: _shared`. See [Includes](#includes). Repeatable. |

Comments start with `#` and run to the end of the line.

## Nodes

```
== node_name
...body...
```

A node runs from `==` to the next `==` or the end of the file. Names are
letters, digits, and underscores. The body is a sequence of statements.

## Statements

**Line.** Text the NPC says. A line is shown together with the options that
follow it; when one line follows another, the first gets a Continue button.
The last line of a conversation gets a Leave button.

```
Well met, traveler.
Elder: Sit, child. There is much to tell.
```

A bare line uses the default speaker. `Name: text` overrides it for that line.
A line that happens to start with a word and a colon, like `Note: bring bread`,
would be read as a speaker; put a backslash in front (`\Note: bring bread`) to
keep it as text. The same escape works for a line that must start with `->`.
Text may include `{player}` (the player's name), `{npc}` (the NPC's name), and
`{$var}` (a variable's value), or any expression such as
`{$met ? "Back again" : "Hello"}`.

**Variation.** `[one|of|these]` inside text picks one alternative at random
each time the line is shown, so greetings do not repeat word for word:

```
[Well met|Greetings|Good to see you], {player}. [Fine weather.|Cold, isn't it?]
```

Alternatives may contain `{...}`. Write `[[` and `]]` for literal brackets.

**Option.** A choice offered to the player. Options that follow one another
are shown together as buttons. The indented body runs when chosen.

```
-> What do you sell?
    A little of everything.
-> I should go. <<if $time_of_day == "night">>
    Mind the dark.
    <<end>>
```

A trailing `<<if expr>>` hides the option unless the expression is true. Use
`<<show if expr>>` instead to show it greyed out. A trailing `<<once>>` hides
the option for good once the player has picked it (per player and NPC, like
once-blocks); the modifiers can be combined in any order:

```
-> Tell me about the ruins. <<once>>
-> Any work for me? <<once>> <<if $player.level >= 3>>
```

A choice can show at most eight options at once. If an option's body does not
`jump` or `end`, the node's options are shown again, which makes hubs easy.

**Conditional.**

```
<<if $met>>
  Back again?
<<elseif has("Food_Bread")>>
  You brought bread. Good.
<<else>>
  Well met.
<<endif>>
```

**Once block.** Runs the first time a player reaches it and never again.

```
<<once>>
  You're new here. Let me explain how things work.
<<endonce>>
```

**Random block.** Runs exactly one of its alternatives, chosen at random each
time. Any statements are allowed inside, not just lines.

```
<<random>>
  Busy day. Lots of travellers.
<<or>>
  Quiet day. You're the first I've seen.
  <<set $tmp.quiet = true>>
<<or>>
  Same as every day.
<<endrandom>>
```

**Command.** Anything in `<<...>>` that is not a conditional or once block.

| Command | Effect |
|---------|--------|
| `<<set $var = expr>>` | Store a value. |
| `<<jump node>>` | Continue at another node. |
| `<<end>>` | Close the window. |
| `<<give Item_Id [count]>>` | Put items in the player's inventory. |
| `<<take Item_Id [count]>>` | Remove items. Fails the option if the player lacks them; guard with `has()`. |
| `<<shop>>` | Open this NPC's native barter shop. |
| `<<attitude friendly>>` | Set this NPC's attitude toward the player: ignore, hostile, neutral, friendly, revered. |
| `<<objective Objective_Id>>` | Start a native objective for the player. |
| `<<anim Id>>`, `<<anim Id Slot>>`, `<<sound Id>>` | Play an animation on the NPC (slot Emote by default; Status is what the game uses for its own greetings) or a sound at the NPC. |
| `<<run "/command args">>` | Run a server command as the console. `{player}` is expanded. |
| `<<input $var "Prompt">>` | Show a text box and store what the player types. |
| `<<once>>` ... `<<endonce>>` | The block between runs at most once per player. |
| `<<random>>` ... `<<or>>` ... `<<endrandom>>` | One alternative runs, chosen at random. |
| `<<wait 2>>` | Pause that many seconds before what follows. The line before it shows without a Continue button and the next line appears by itself. Keep it short (0 to 30); the player can still Leave. |
| `<<reputation +10>>`, `<<reputation -5 Group_Id>>` | Change the player's standing with this NPC's reputation group, or a named group. An NPC belongs to a group when a `Server/NPC/Reputation/Groups/*.json` asset lists one of its NPC groups; the base game ships none, so without such an asset (or a named group) this raises an error. |
| `<<notify "Text" ["Detail"] [success\|warning\|danger]>>` | A toast notification in the corner of the screen. |
| `<<title "Primary" ["Secondary"] [major] [seconds]>>` | A cinematic title across the screen. |
| `<<effect Effect_Id>>`, `<<cure Effect_Id>>` | Apply or remove an entity effect (regeneration, poison, speed, ...). |
| `<<heal [amount]>>` | Restore health, fully or by an amount. |
| `<<stat Health +20>>`, `<<stat Stamina 50>>`, `<<stat Health max>>` | Add to, set, or max out any stat. |
| `<<learn Recipe_Id>>` | Teach the player a crafting recipe. |
| `<<teleport warp_name>>`, `<<teleport x y z>>` | Move the player. Ends the conversation. |

Plugins can register additional commands.

## Variables

| Form | Scope | Persists |
|------|-------|----------|
| `$name` | this player with this NPC: "the merchant remembers me" | yes |
| `$player.name` | this player everywhere: story progress that any NPC can see | yes |
| `$npc.name` | this NPC, all players: "how many people have asked" | yes |
| `$world.name` | every player and NPC | yes |
| `$tmp.name` | this conversation only | no |

Two NPCs of the same role each keep their own `$name` variables about a
player, so meeting one merchant does not make every merchant act as if it
knows you. Use `$player.name` for anything that should carry over. Visited
nodes and once-blocks are also tracked per player and NPC.

All persistent scopes are additionally namespaced by the dialogue's `scope:`
(default: the file name), so two files only share variables if they declare
the same scope.

Values are numbers, strings, or booleans. Unset variables read as `false`.

## Expressions

Used in `if`, `elseif`, option guards, `set`, and `start when`.

- Literals: `1`, `2.5`, `"text"`, `true`, `false`
- Operators: `+ - * /`, `== != < <= > >=`, `and or not`, parentheses
- Conditional: `cond ? when_true : when_false`, e.g. `{$gold > 100 ? "rich" : "poor"}`
- Functions:
  - `has("Item_Id", count = 1)` player holds at least that many
  - `count("Item_Id")` how many the player holds
  - `visited("node")` player has seen a node in this dialogue
  - `objective("Objective_Id")` returns `"none"`, `"active"`, or `"complete"`
  - `attitude()` this NPC's attitude toward the player as a string
  - `perm("node.name")` player has a permission
  - `hour()` in-game hour, 0 to 23
  - `random(n)` integer from 0 to n-1
  - `chance(p)` true with probability p, 0 to 1
  - `reputation()` standing with this NPC's group; `reputation("Group_Id")` with a named group (0 when the NPC has no group)
  - `rank()` / `rank("Group_Id")` the current rank id, e.g. "Friendly"
  - `stat("Health")`, `max_stat("Health")` any entity stat
  - `effect("Effect_Id")` the player currently has that entity effect
  - `knows("Recipe_Id")` the player has learned that recipe
  - `ordinal(n)` "1st", "2nd", "3rd", "11th", "21st"
  - `plural(n, "loaf", "loaves")` the right word for the count; the third argument is optional and defaults to adding an s

Plugins can register additional functions.

## Includes

`include: name` merges the nodes of `name.talk`, found next to the including
file, into this dialogue, so several NPCs can share a farewell, a rumour mill,
or a shop pitch. Nodes defined in the including file win over included ones.
Included files may include others; loops are reported as errors.

Name shared files with a leading underscore, such as `_shared.talk`. Files
starting with `_` are never loaded as dialogues on their own, so they need no
header and produce no "no npc: binding" warning.

```
# _shared.talk
== goodbye
Safe travels, {player}.
<<end>>

# merchant.talk
npc: Kweebec_Merchant
include: _shared
== start
Buying or selling?
-> Neither.
    <<jump goodbye>>
```

## A complete example

See [examples/rootling_merchant.talk](../examples/rootling_merchant.talk)
and [examples/village_elder.talk](../examples/village_elder.talk).

## Validation

`./gradlew validate --args="path/to/file.talk"` parses a file and reports
errors with line numbers, without starting a server. `/lowtalk reload` on a
running server does the same for every file in the dialogues folder and
prints the results to the console.

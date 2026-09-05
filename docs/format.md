# The Parley dialogue format

A dialogue is a plain text file with the extension `.parley`, encoded as UTF-8.
It is made of a short header and one or more nodes. Indentation is two spaces
and is significant only inside options and conditionals.

The format is deliberately close to Yarn Spinner, which many writers already
know, but it is not Yarn and does not try to be.

## Header

Lines before the first node are directives, `key: value`, one per line.

| Directive | Meaning |
|-----------|---------|
| `npc:` | Which NPCs use this dialogue. A role id (`Kweebec_Merchant`) binds every NPC of that role. A tag (`@elder`) binds NPCs tagged in-game with `/parley tag elder`. Repeatable. |
| `start:` | The node to begin at. Defaults to the first node. May be repeated with a guard: `start: returning when $met` is tried before an unguarded `start:`. |
| `speaker:` | Default speaker name for bare lines. Defaults to the NPC's in-game name. |
| `title:` | Shown in the window header. Defaults to the speaker. |
| `scope:` | Variable namespace shared with other files. Defaults to the file name. |

Comments start with `#` and run to the end of the line.

## Nodes

```
== node_name
...body...
```

A node runs from `==` to the next `==` or the end of the file. Names are
letters, digits, and underscores. The body is a sequence of statements.

## Statements

**Line.** Text the NPC says. Shown one at a time with a Continue button.

```
Well met, traveler.
Elder: Sit, child. There is much to tell.
```

A bare line uses the default speaker. `Name: text` overrides it for that line.
Text may include `{player}` (the player's name), `{npc}` (the NPC's name), and
`{$var}` (a variable's value).

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
`<<show if expr>>` instead to show it greyed out. If an option's body does not
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

**Command.** Anything in `<<...>>` that is not a conditional.

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
| `<<anim Id>>`, `<<sound Id>>` | Play an animation on the NPC or a sound at the NPC. |
| `<<run "/command args">>` | Run a server command as the console. `{player}` is expanded. |
| `<<input $var "Prompt">>` | Show a text box and store what the player types. |
| `<<once>>` | The rest of this node runs at most once per player. |

Plugins can register additional commands.

## Variables

| Form | Scope | Persists |
|------|-------|----------|
| `$name` | this player, this dialogue's scope | yes |
| `$npc.name` | this NPC entity, all players | yes |
| `$world.name` | every player and NPC | yes |
| `$tmp.name` | this conversation only | no |

Values are numbers, strings, or booleans. Unset variables read as `false`.

## Expressions

Used in `if`, `elseif`, option guards, `set`, and `start when`.

- Literals: `1`, `2.5`, `"text"`, `true`, `false`
- Operators: `+ - * /`, `== != < <= > >=`, `and or not`, parentheses
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

Plugins can register additional functions.

## A complete example

See [examples/rootling_merchant.parley](../examples/rootling_merchant.parley)
and [examples/village_elder.parley](../examples/village_elder.parley).

## Validation

`./gradlew validate --args="path/to/file.parley"` parses a file and reports
errors with line numbers, without starting a server. `/parley reload` on a
running server does the same for every file in the dialogues folder and
prints the results to the console.

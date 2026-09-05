# LowTalk

Hand-written branching dialogue for Hytale NPCs.

LowTalk lets you give any NPC a conversation: lines, choices, conditions, and
consequences, written in a small plain-text format that is pleasant to type,
diff, and version. It runs entirely server-side, remembers what each player
has said and done, and plugs into Hytale's own systems (items, attitudes,
objectives, barter shops, role actions) rather than reinventing them.

It is MIT licensed. Fork it, extend it, ship it with your adventure map.

## What a dialogue looks like

```
npc: Kweebec_Merchant
start: greeting

== greeting
<<if $met>>
  Ah, {player}. Back again.
<<else>>
  Well met, traveler. I'm the Rootling Merchant.
  <<set $met = true>>
<<endif>>

-> What do you sell?
    A little of everything, and most of it useful.
    <<shop>>
-> Where can I find the temple? <<if not $told_temple>>
    <<jump temple>>
-> I should be going.
    Safe travels.
    <<end>>

== temple
North, past the whispering woods. Look for the jagged peaks.
<<set $told_temple = true>>
-> Thank you.
    <<jump greeting>>
```

See [docs/format.md](docs/format.md) for the full format and
[docs/DESIGN.md](docs/DESIGN.md) for how it works and why.

## Status

Early development. Nothing is released yet. The roadmap is in
[docs/DESIGN.md](docs/DESIGN.md#roadmap).

## Building

Requires Java 25. The Gradle wrapper is included.

```
./gradlew build          # builds build/libs/LowTalk-<version>.jar
./gradlew test           # parser and runtime tests, no server needed
./gradlew runServer      # local dev server with the plugin loaded
```

## License

MIT. See [LICENSE](LICENSE).

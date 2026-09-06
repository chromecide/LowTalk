# Contributing to LowTalk

Thanks for wanting to help. A few things to know before your first pull
request.

## The agreement

LowTalk is MIT licensed, and it is important that it stays easy to relicense
or transfer as a whole in future. So every contributor accepts the short
[Contributor License Agreement](CLA.md) once, by adding this line to their
first pull request:

    I have read the CLA in CLA.md and I agree to its terms.

Pull requests without it can't be merged, however good they are.

## No copied server code

We learn the Hytale API by reading it, and that's fine. Copying decompiled
server code into this repository is not. Write your own implementation
against the public API. If a piece of behaviour can only be achieved by
copying, open an issue and we'll find another way or ask Hypixel.

## Code layout

- `parser/`, `model/`, and `runtime/` must not import anything from
  `com.hypixel`. They are tested with plain JUnit and must stay that way.
- Everything that touches the server lives under `hytale/`.
- Public extension points live under `api/`. Changing them is a breaking
  change and needs a note in the changelog.

## Style

Follow the conventions Hypixel uses in their own plugins where they exist:
codec-defined config, ECS systems for entity hooks, custom UI pages for
windows, `HytaleLogger` for logging. The aim is that LowTalk reads like it
belongs next to the built-in plugins. The conventions we have confirmed so
far are listed in `docs/DESIGN.md` under "Alignment with Hypixel's
conventions"; add to that list when you learn a new one the hard way.

## Tests and docs

- New syntax needs parser tests and a paragraph in `docs/format.md`.
- New effects or functions need runtime tests with a fake context and a row in
  the tables in `docs/format.md`.
- `./gradlew test` must pass before you open the pull request.

## Working with AI tools

Most of this project was written by an AI coding agent under a person's direction, and contributions made the
same way are welcome. The terms are the same as for any other contribution: you have read and understood what
you are submitting, the tests pass, the CLA line is in the pull request, and nothing is copied from the game's
decompiled sources. Say in the pull request if a tool did most of the typing; it helps the reviewer know where to
look.

## Reporting bugs

Include the `.talk` file (or a minimal cut-down version), the server log lines
from `[LowTalk|P]`, and the Hytale version.


## Branches, tags and Hytale patchlines

Hytale has a release line and a pre-release line, and the pre-release becomes the next release. A mod jar carries
a server-version range in its manifest, so we owe one jar per line.

- **`main`** targets the current Hytale release. Code that works on both lines goes here; prefer the game's own
  helpers over building packets or reaching into internals, because the helpers are what stay stable.
- **`prerelease`** targets the pre-release line: only what cannot compile on the release line lives there, plus a
  separate dev-server folder (`../lowtalk-pre`, a git worktree) so pre-release worlds never touch release worlds.
  Merge `main` into it often. When Hytale promotes a pre-release, merge `prerelease` into `main`, bump the
  properties, tag, and start the next `prerelease` from `main`.
- **Versions** are the mod's own (`version` in gradle.properties, semantic). On `main`, `./gradlew build` makes the
  release-line jar and `./gradlew buildAll` also makes a pre-release jar from the same commit (`buildPreRelease`,
  driven by the `prerelease_*` properties) into `build/dist/`. The jars published for the pre-release line are built
  on the `prerelease` branch, whose `version` carries the game version as build metadata
  (`0.1.0+hytale.0.7.0-pre.1`), so a plain `./gradlew build` there produces the correctly named jar; `buildAll` on
  `main` is the shortcut while the branches do not differ in code.
- **Tags** `vX.Y.Z` mark releases on `main`. A GitHub release carries both jars and states the Hytale versions.
- **Dry runs** against a new pre-release go in the changelog under a "Hytale <version> notes" heading: what broke,
  what was deprecated, what held. A replacement API that compiles can still behave differently, so walk the test
  corridor after every port.

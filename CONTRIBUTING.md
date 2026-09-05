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
belongs next to the built-in plugins.

## Tests and docs

- New syntax needs parser tests and a paragraph in `docs/format.md`.
- New effects or functions need runtime tests with a fake context and a row in
  the tables in `docs/format.md`.
- `./gradlew test` must pass before you open the pull request.

## Reporting bugs

Include the `.talk` file (or a minimal cut-down version), the server log lines
from `[LowTalk|P]`, and the Hytale version.

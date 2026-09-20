# tools

Things that are not part of the mod but are part of keeping it honest.

## Hytale version tracking

Two scripts that exist because **the Hytale launcher installs into `.../game/latest` and replaces it in
place**. The moment a new build lands, the previous server jar and its ~3.2GB `Assets.zip` are gone — and an
API diff against the version you were on is the only way to tell a quiet patch from a breaking one. It has
already cost us once: 0.6.6's assets were overwritten before any of this existed, and only Gradle's cache
happened to still hold the jars.

They are macOS-oriented (`~/Library/Application Support/Hytale`, `stat -f`) and use no credentials of any
kind. `CONTRIBUTING.md` describes what they do in enough detail to follow the process without them.

**The order matters:**

```
hytale-check-versions.sh     # 1. is there a new build?  (asks Maven, not the launcher)
hytale-archive.sh            # 2. save what you have, BEFORE the launcher updates
                             # 3. only now let the launcher update
```

### `hytale-check-versions.sh`

Asks `maven.hytale.com/<channel>/com/hypixel/hytale/Server/maven-metadata.xml` what the newest published build
is on each line, and compares it with the installed jar's own manifest version and with what is archived. No
launcher, no login, one HTTP request per channel — so you find out a new version exists while the one you have
is still on disk.

Exit status is `0` nothing new, `1` something to do, `2` could not tell, so it works from cron or a shell
prompt: `hytale-check-versions.sh -q || notify ...`

```
hytale-check-versions.sh                     report both channels
hytale-check-versions.sh -q                  print only when something needs doing
hytale-check-versions.sh --list              also list every published version
hytale-check-versions.sh --channel release   one channel
```

`HYTALE_INSTALL_ROOT`, `HYTALE_ARCHIVE` and `HYTALE_MAVEN` override the paths it reads.

Note the Maven metadata is not a complete history — pre-release builds get pruned upstream, so versions you
archived may no longer be listed. The archive is the only full record.

### `hytale-archive.sh`

Copies each build out of the launcher into `~/hytale-archive/<channel>/<version>/` (server jar, `Assets.zip`,
manifest, and an `info.txt` with sha256s). Idempotent — a version already archived and intact is skipped, so
running it twice costs a checksum rather than a copy. Stages through `.partial` so an interrupted copy never
passes for a good one, and prunes to the newest `--keep N` per channel.

```
hytale-archive.sh                 archive both channels, then prune
hytale-archive.sh --list          show what is archived
hytale-archive.sh --jars-only     skip Assets.zip, for a quick catch-up
hytale-archive.sh --dry-run       say what it would do and do nothing
```

`HYTALE_ARCHIVE` overrides where it writes.

## Node Editor workspace

`nodeeditor/` holds the LowTalk workspace for Hytale's standalone Node Editor, and the installers that put it
where the editor will find it. See [docs/node-editor.md](../docs/node-editor.md) — including the note that the
Windows installer has not been run on Windows yet.

#!/usr/bin/env bash
#
# Tell me when Hytale publishes a new build, before the launcher installs it.
#
# The launcher replaces .../game/latest in place, so the first sign of a new version is usually the old one
# already being gone. This asks Hytale's Maven repository instead — no launcher, no login, one HTTP request per
# channel — so a new build can be seen while the current one is still on disk and still archivable.
#
# The order that matters:
#   1. this script says a new version exists
#   2. hytale-archive.sh saves the build you still have
#   3. only then let the launcher update
#
# Exit status: 0 nothing new, 1 something new upstream, 2 could not tell (network, parse, bad install).
# That makes it usable from cron or a shell prompt: `hytale-check-versions.sh -q || notify ...`
#
#   hytale-check-versions.sh             report both channels
#   hytale-check-versions.sh -q          print only when something needs doing
#   hytale-check-versions.sh --list      also list every version the repository offers
#   hytale-check-versions.sh --channel pre-release    just one channel
#
set -euo pipefail

MAVEN_BASE="${HYTALE_MAVEN:-https://maven.hytale.com}"
MAVEN_PATH="com/hypixel/hytale/Server/maven-metadata.xml"
INSTALL_ROOT="${HYTALE_INSTALL_ROOT:-$HOME/Library/Application Support/Hytale/install}"
ARCHIVE="${HYTALE_ARCHIVE:-$HOME/hytale-archive}"
CHANNELS=(release pre-release)
TIMEOUT=25

QUIET=0
LIST=0

HERE="$(cd "$(dirname "$0")" && pwd)"
ARCHIVE_SCRIPT="$HERE/hytale-archive.sh"

die() { printf 'error: %s\n' "$*" >&2; exit 2; }
say() { [ "$QUIET" = 1 ] || printf '%s\n' "$*"; }
loud() { printf '%s\n' "$*"; }

while [ $# -gt 0 ]; do
    case "$1" in
        -q|--quiet)  QUIET=1; shift ;;
        --list)      LIST=1; shift ;;
        --channel)   [ -n "${2:-}" ] || die "--channel needs a name"; CHANNELS=("$2"); shift 2 ;;
        -h|--help)   sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *)           die "unknown option: $1" ;;
    esac
done

command -v curl >/dev/null 2>&1 || die "curl is required"

# The version a server jar reports, from its own manifest. The launcher directory is always called "latest",
# whatever is in it, so the manifest is the only honest answer.
installed_version() {
    local jar="$INSTALL_ROOT/$1/package/game/latest/Server/HytaleServer.jar"
    [ -f "$jar" ] || return 1
    unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null \
        | tr -d '\r' \
        | awk -F': ' '$1=="Implementation-Version" {print $2; exit}'
}

# Newest version the repository offers. Prefers <latest>; falls back to the last <version>, which Maven writes
# in publication order.
newest_upstream() {
    local xml="$1" latest
    latest=$(printf '%s' "$xml" | tr '<' '\n' | sed -n 's|^latest>||p' | tail -1)
    [ -n "$latest" ] || latest=$(printf '%s' "$xml" | tr '<' '\n' | sed -n 's|^version>||p' | tail -1)
    printf '%s' "$latest"
}

all_upstream() {
    printf '%s' "$1" | tr '<' '\n' | sed -n 's|^version>||p'
}

exit_code=0

for channel in "${CHANNELS[@]}"; do
    url="$MAVEN_BASE/$channel/$MAVEN_PATH"
    say "== $channel"

    if ! xml=$(curl -fsS --max-time "$TIMEOUT" "$url" 2>/dev/null); then
        loud "  could not reach $url"
        exit_code=2
        continue
    fi

    upstream=$(newest_upstream "$xml")
    [ -n "$upstream" ] || { loud "  could not parse a version out of $url"; exit_code=2; continue; }

    if installed=$(installed_version "$channel"); then :; else installed="(none installed)"; fi

    archived_dir="$ARCHIVE/$channel"
    if [ -d "$archived_dir/$installed" ]; then archived="yes"; else archived="NO"; fi

    say "  newest published : $upstream"
    say "  installed        : $installed"
    say "  installed archived: $archived"

    if [ "$LIST" = 1 ]; then
        say "  published versions:"
        all_upstream "$xml" | sed 's/^/    /' | while IFS= read -r v; do say "$v"; done
    fi

    if [ "$upstream" != "$installed" ]; then
        exit_code=1
        loud ""
        loud "  NEW: $channel $upstream is published; you have $installed."
        if [ "$archived" = "NO" ] && [ "$installed" != "(none installed)" ]; then
            loud "  Archive $installed BEFORE letting the launcher update - it overwrites in place:"
            loud "      $ARCHIVE_SCRIPT"
        else
            loud "  Your current build is archived, so the launcher may update safely."
        fi
        loud ""
    else
        if [ "$archived" = "NO" ] && [ "$installed" != "(none installed)" ]; then
            exit_code=1
            loud ""
            loud "  $channel is up to date at $installed, but that build is NOT archived."
            loud "      $ARCHIVE_SCRIPT"
            loud ""
        else
            say "  up to date, and archived"
        fi
    fi
    say ""
done

exit "$exit_code"

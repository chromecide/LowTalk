#!/usr/bin/env bash
#
# Keep a copy of each Hytale build before the launcher overwrites it.
#
# The launcher installs into .../game/latest and replaces it in place, so the moment a new version lands the old
# server jar and its 3.2GB Assets.zip are gone. That matters twice over: an API diff is the only way to tell a
# quiet patch from a breaking one, and a mod built against a version nobody can install again cannot be tested.
#
# Run it after any update (or on a schedule). It is idempotent: a version already archived and intact is skipped,
# so running it twice costs a checksum, not a copy.
#
#   hytale-archive.sh                 archive both channels, then prune
#   hytale-archive.sh --list          show what is archived
#   hytale-archive.sh --keep 6        keep the 6 newest per channel (default 4)
#   hytale-archive.sh --jars-only     skip Assets.zip (for a quick catch-up)
#   hytale-archive.sh --backfill      also pull older server jars out of the Gradle cache
#   hytale-archive.sh --dry-run       say what it would do and do nothing
#
set -euo pipefail

INSTALL_ROOT="$HOME/Library/Application Support/Hytale/install"
ARCHIVE="${HYTALE_ARCHIVE:-$HOME/hytale-archive}"
GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1/com.hypixel.hytale/Server"
CHANNELS=(release pre-release)

KEEP=4
JARS_ONLY=0
BACKFILL=0
DRY_RUN=0
LIST_ONLY=0
# Refuse to start a copy that would leave the disk under this much room.
MIN_FREE_GB=20

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
say() { printf '%s\n' "$*"; }
run() { if [ "$DRY_RUN" = 1 ]; then printf '  would: %s\n' "$*"; else "$@"; fi; }

while [ $# -gt 0 ]; do
    case "$1" in
        --keep)      KEEP="${2:-}"; [ -n "$KEEP" ] || die "--keep needs a number"; shift 2 ;;
        --jars-only) JARS_ONLY=1; shift ;;
        --backfill)  BACKFILL=1; shift ;;
        --dry-run)   DRY_RUN=1; shift ;;
        --list)      LIST_ONLY=1; shift ;;
        -h|--help)   sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *)           die "unknown option: $1" ;;
    esac
done

case "$KEEP" in ''|*[!0-9]*) die "--keep must be a whole number, got '$KEEP'" ;; esac
[ "$KEEP" -ge 1 ] || die "--keep must be at least 1"

# The version a server jar reports, straight from its own manifest. Nothing else is authoritative: the launcher
# directory is always called "latest", whatever is in it.
jar_field() {
    unzip -p "$1" META-INF/MANIFEST.MF 2>/dev/null \
        | tr -d '\r' \
        | awk -v k="$2" -F': ' '$1==k {print $2; exit}'
}

free_gb() { df -g "$1" 2>/dev/null | awk 'NR==2 {print $4}'; }

archived_versions() {  # channel
    local dir="$ARCHIVE/$1"
    [ -d "$dir" ] || return 0
    find "$dir" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; 2>/dev/null | sort -V
}

list_archive() {
    local total
    for channel in "${CHANNELS[@]}"; do
        say "$channel:"
        local found=0
        while IFS= read -r v; do
            [ -n "$v" ] || continue
            found=1
            local d="$ARCHIVE/$channel/$v" size rev
            size=$(du -sh "$d" 2>/dev/null | cut -f1)
            rev=$(awk -F': ' '/^revision/{print substr($2,1,8)}' "$d/info.txt" 2>/dev/null)
            local assets="jar only"
            [ -f "$d/Assets.zip" ] && assets="with assets"
            printf '  %-18s %6s  %-12s %s\n' "$v" "$size" "$assets" "$rev"
        done < <(archived_versions "$channel")
        [ "$found" = 1 ] || say "  (nothing archived)"
    done
    if [ -d "$ARCHIVE" ]; then
        total=$(du -sh "$ARCHIVE" 2>/dev/null | cut -f1)
        say ""
        say "total: ${total:-0} in $ARCHIVE"
    fi
}

# Copy to a temporary name and move into place, so an interrupted copy never looks like a finished archive.
copy_into() {  # src dest
    local src="$1" dest="$2" tmp="$2.partial"
    run rm -f "$tmp"
    run cp "$src" "$tmp"
    run mv "$tmp" "$dest"
}

verify() {  # src dest -> 0 when identical
    [ -f "$2" ] || return 1
    local a b
    a=$(shasum -a 256 "$1" | cut -d' ' -f1)
    b=$(shasum -a 256 "$2" | cut -d' ' -f1)
    [ "$a" = "$b" ]
}

archive_channel() {  # channel
    local channel="$1"
    local latest="$INSTALL_ROOT/$channel/package/game/latest"
    local jar="$latest/Server/HytaleServer.jar"
    local assets="$latest/Assets.zip"

    if [ ! -f "$jar" ]; then
        say "$channel: no server jar installed, skipping"
        return 0
    fi

    local version revision
    version=$(jar_field "$jar" Implementation-Version)
    revision=$(jar_field "$jar" Implementation-Revision-Id)
    [ -n "$version" ] || die "$channel: could not read a version out of $jar"

    local dest="$ARCHIVE/$channel/$version"
    local want_assets=1
    { [ "$JARS_ONLY" = 1 ] || [ ! -f "$assets" ]; } && want_assets=0

    # Already done? Check the bytes, not just the directory: a half-finished copy must not pass for a good one.
    if [ -f "$dest/HytaleServer.jar" ] && verify "$jar" "$dest/HytaleServer.jar"; then
        if [ "$want_assets" = 0 ] || { [ -f "$dest/Assets.zip" ] && [ "$(stat -f%z "$assets")" = "$(stat -f%z "$dest/Assets.zip")" ]; }; then
            say "$channel $version: already archived"
            return 0
        fi
    fi

    local need_gb=1
    [ "$want_assets" = 1 ] && need_gb=$(( ($(stat -f%z "$assets") / 1073741824) + 1 ))
    local have_gb
    have_gb=$(free_gb "$HOME")
    if [ -n "$have_gb" ] && [ "$have_gb" -lt $((need_gb + MIN_FREE_GB)) ]; then
        die "$channel $version: needs ~${need_gb}GB and only ${have_gb}GB free (keeping ${MIN_FREE_GB}GB spare). Prune with --keep."
    fi

    say "$channel $version: archiving${want_assets:+ }$([ "$want_assets" = 1 ] && echo 'jar + assets' || echo 'jar')"
    run mkdir -p "$dest"
    copy_into "$jar" "$dest/HytaleServer.jar"
    [ "$want_assets" = 1 ] && copy_into "$assets" "$dest/Assets.zip"

    if [ "$DRY_RUN" = 0 ]; then
        unzip -p "$jar" META-INF/MANIFEST.MF > "$dest/MANIFEST.MF" 2>/dev/null || true
        {
            echo "channel:  $channel"
            echo "version:  $version"
            echo "revision: $revision"
            echo "archived: $(date '+%Y-%m-%d %H:%M:%S %z')"
            echo "jar:      $(shasum -a 256 "$dest/HytaleServer.jar" | cut -d' ' -f1)"
            [ -f "$dest/Assets.zip" ] && echo "assets:   $(shasum -a 256 "$dest/Assets.zip" | cut -d' ' -f1)"
        } > "$dest/info.txt"
        verify "$jar" "$dest/HytaleServer.jar" || die "$channel $version: the archived jar does not match the original"
    fi
    say "$channel $version: done"
}

# Server jars Gradle happened to keep. Their assets are already gone, so these are jar-only by nature: enough to
# diff an API, not enough to run.
backfill_from_gradle_cache() {
    [ -d "$GRADLE_CACHE" ] || { say "backfill: no Gradle cache at $GRADLE_CACHE"; return 0; }
    local jar version channel dest
    while IFS= read -r jar; do
        version=$(jar_field "$jar" Implementation-Version)
        [ -n "$version" ] || continue
        case "$version" in *pre*) channel=pre-release ;; *) channel=release ;; esac
        dest="$ARCHIVE/$channel/$version"
        if [ -f "$dest/HytaleServer.jar" ]; then continue; fi
        say "backfill $channel $version: jar only (its assets are gone)"
        run mkdir -p "$dest"
        copy_into "$jar" "$dest/HytaleServer.jar"
        if [ "$DRY_RUN" = 0 ]; then
            unzip -p "$jar" META-INF/MANIFEST.MF > "$dest/MANIFEST.MF" 2>/dev/null || true
            {
                echo "channel:  $channel"
                echo "version:  $version"
                echo "revision: $(jar_field "$jar" Implementation-Revision-Id)"
                echo "archived: $(date '+%Y-%m-%d %H:%M:%S %z') (backfilled from the Gradle cache)"
                echo "jar:      $(shasum -a 256 "$dest/HytaleServer.jar" | cut -d' ' -f1)"
                echo "assets:   not archived - overwritten before this archive existed"
            } > "$dest/info.txt"
        fi
    done < <(find "$GRADLE_CACHE" -name "*.jar" ! -name "*sources*" 2>/dev/null)
}

# Oldest first by version order, so pruning drops the ones least likely to be wanted.
prune_channel() {  # channel
    local channel="$1"
    local -a versions=()
    while IFS= read -r v; do [ -n "$v" ] && versions+=("$v"); done < <(archived_versions "$channel")
    local n=${#versions[@]}
    [ "$n" -gt "$KEEP" ] || return 0
    local drop=$((n - KEEP)) i
    for ((i = 0; i < drop; i++)); do
        say "$channel ${versions[$i]}: pruning (keeping the $KEEP newest)"
        run rm -rf "$ARCHIVE/$channel/${versions[$i]}"
    done
}

if [ "$LIST_ONLY" = 1 ]; then
    list_archive
    exit 0
fi

[ -d "$INSTALL_ROOT" ] || die "no Hytale install found at $INSTALL_ROOT"
run mkdir -p "$ARCHIVE"

for channel in "${CHANNELS[@]}"; do
    archive_channel "$channel"
done

[ "$BACKFILL" = 1 ] && backfill_from_gradle_cache

for channel in "${CHANNELS[@]}"; do
    prune_channel "$channel"
done

say ""
list_archive

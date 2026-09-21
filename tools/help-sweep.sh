#!/bin/sh
# Run --help on every command in a tree and fail if any of it renders a raw translation key.
#
# Why this exists: command and argument descriptions are translation keys resolved through I18nModule. A
# key with no entry in the .lang file does not fail the build, does not fail a test and logs no warning --
# it prints the key, in game, where only a human looking at it would notice.
#
#   ./tools/help-sweep.sh [root-command] [server-dir]
#
# Defaults to the lowtalk command on the scratch server at ../../lowtalk-firstrun, which must already be
# running with a console.in fifo (see its run.sh).
#
# The command list comes from the server's own help output, not from the source or the .lang file, so a
# missing key cannot hide by also being missing from the list of things to check.
#
# Exit 0 clean, 1 if anything rendered a raw key, 2 if it could not run.
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=${1:-lowtalk}
SERVER_DIR=${2:-${HYTALE_SERVER_DIR:-$HERE/../../lowtalk-firstrun}}

[ -p "$SERVER_DIR/console.in" ] || { echo "No console.in in $SERVER_DIR -- is the server running?"; exit 2; }
[ -f "$SERVER_DIR/server.log" ] || { echo "No server.log in $SERVER_DIR"; exit 2; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
: > "$WORK/bad"

# Run one command's --help and leave the cleaned reply in $WORK/out.
#
# Waits for the log to stop growing rather than sleeping a fixed time: with a fixed sleep a slow reply
# lands after the tail is taken and then turns up in the NEXT command's capture.
ask() {
  start=$(wc -l < "$SERVER_DIR/server.log")
  printf '%s --help\n' "$1" > "$SERVER_DIR/console.in"
  prev=-1
  waited=0
  while [ "$waited" -lt 40 ]; do
    sleep 0.2
    waited=$((waited + 1))
    now=$(wc -l < "$SERVER_DIR/server.log")
    [ "$now" -gt "$start" ] && [ "$now" -eq "$prev" ] && break
    prev=$now
  done
  tail -n +$((start + 1)) "$SERVER_DIR/server.log" \
    | sed 's/\x1b\[[0-9;]*m//g' \
    | grep -v 'executed command:' \
    | sed 's/[[:space:]]*$//' > "$WORK/out"
}

# The root's help prints the whole tree at once, two spaces of indent per level, with argument spec after
# the name. So the command list is one call, not a walk.
ask "$ROOT"
[ -s "$WORK/out" ] || { echo "No reply from /$ROOT -- is the command registered?"; exit 2; }

awk -v root="$ROOT" '
  /^(Required Arguments|Optional Arguments|Argument Types|Examples):/ { exit }
  {
    line = $0
    match(line, /^ */)
    depth = RLENGTH / 2
    name = line; sub(/^ */, "", name); sub(/[ <[].*/, "", name)
    if (name == "" || depth < 1) next
    path[depth] = name
    out = root
    for (i = 1; i <= depth; i++) out = out " " path[i]
    print out
  }' "$WORK/out" > "$WORK/paths"

# The root itself is a command too.
{ echo "$ROOT"; cat "$WORK/paths"; } | awk '!seen[$0]++' > "$WORK/all"

while IFS= read -r cmd; do
  [ -n "$cmd" ] || continue
  ask "$cmd"
  if grep -q 'server\.commands\.' "$WORK/out"; then
    printf 'UNRESOLVED  /%s\n' "$cmd"
    sed 's/^/            /' "$WORK/out"
    echo "$cmd" >> "$WORK/bad"
  else
    printf '  ok        /%s\n' "$cmd"
  fi
done < "$WORK/all"

CHECKED=$(wc -l < "$WORK/all" | tr -d ' ')
BAD=$(wc -l < "$WORK/bad" | tr -d ' ')
echo
echo "$CHECKED command(s) checked, $BAD with unresolved keys"
[ "$BAD" -eq 0 ] || exit 1

#!/bin/sh
# Copies the LowTalk Dialogue workspace into Hytale's standalone Node Editor.
# The editor only reads workspaces from inside the client install, and every client update replaces that install,
# so run this again whenever the editor stops offering "LowTalk - Dialogue".
# Usage: sh tools/nodeeditor/install.sh [path-to-a-Workspaces-folder]
# With no argument it installs into every Hytale client it can find (release and pre-release).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/LowTalk Dialogue"

install_into() {
  DEST="$1"
  rm -rf "$DEST/LowTalk Dialogue"
  cp -R "$SRC" "$DEST/LowTalk Dialogue"
  echo "Installed the LowTalk Dialogue workspace into $DEST"
}

if [ -n "$1" ]; then
  if [ ! -d "$1" ]; then
    echo "Not a folder: $1"
    echo "Pass the path to the NodeEditor/Workspaces folder of your Hytale client."
    exit 1
  fi
  install_into "$1"
else
  FOUND=0
  case "$(uname -s)" in
    Darwin) ROOT="$HOME/Library/Application Support/Hytale/install" ;;
    *) ROOT="$APPDATA/Hytale/install" ;;
  esac
  for line in release pre-release; do
    for CAND in "$ROOT/$line/package/game/latest/Client/Hytale.app/Contents/Resources/NodeEditor/Workspaces" \
                "$ROOT/$line/package/game/latest/Client/NodeEditor/Workspaces"; do
      if [ -d "$CAND" ]; then
        install_into "$CAND"
        FOUND=1
      fi
    done
  done
  if [ "$FOUND" = 0 ]; then
    echo "No Hytale client found under $ROOT"
    echo "Pass the path to your client's NodeEditor/Workspaces folder as the first argument."
    exit 1
  fi
fi
echo "Open the Node Editor and pick 'LowTalk - Dialogue' when creating a file; saved files remember it."

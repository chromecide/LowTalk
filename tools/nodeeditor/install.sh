#!/bin/sh
# Copies the LowTalk Dialogue workspace into Hytale's standalone Node Editor.
# The editor reads workspaces from the client install, so this is per machine and a game update may remove it;
# just run it again. Usage: sh tools/nodeeditor/install.sh [path-to-Workspaces-folder]
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/LowTalk Dialogue"
if [ -n "$1" ]; then
  DEST="$1"
else
  case "$(uname -s)" in
    Darwin) DEST="$HOME/Library/Application Support/Hytale/install/release/package/game/latest/Client/Hytale.app/Contents/Resources/NodeEditor/Workspaces" ;;
    *) DEST="$APPDATA/Hytale/install/release/package/game/latest/Client/NodeEditor/Workspaces" ;;
  esac
fi
if [ ! -d "$DEST" ]; then
  echo "Node Editor workspaces folder not found at: $DEST"
  echo "Pass the path to the NodeEditor/Workspaces folder of your Hytale client as the first argument."
  exit 1
fi
rm -rf "$DEST/LowTalk Dialogue"
cp -R "$SRC" "$DEST/LowTalk Dialogue"
echo "Installed the LowTalk Dialogue workspace into $DEST"
echo "Open the Node Editor, choose 'LowTalk - Dialogue', and save files into <your pack>/Server/LowTalk/Dialogues/Name.json"

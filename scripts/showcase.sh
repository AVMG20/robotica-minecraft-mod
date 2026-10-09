#!/bin/sh
# Runs the showcase client on this Mac (Homebrew JDK 21): it opens a window, builds the scenes, saves screenshots in
# run-showcase/screenshots and quits (about 8 minutes). Then scripts/shots.py turns the chosen ones into docs/shots/*.jpg.
# Leave the mouse alone while it runs. caffeinate keeps the display awake: with a sleeping display LWJGL finds no monitor.
# Rebuild the wiki afterwards if shots were added: python3 scripts/wiki/build_wiki.py
#
# Usage: scripts/showcase.sh                         every scene, GUI and Codex shot
#        scripts/showcase.sh core_reactor bank ...   only the shots whose name contains one of the words (e.g.
#                                                    19_core_reactor and gui_98_core_reactor_formed); only those are
#                                                    converted, the other docs/shots stay as they are
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
rm -rf run-showcase/screenshots
caffeinate -u -t 5
if [ $# -gt 0 ]; then
    ONLY=$(printf '%s,' "$@")
    caffeinate -d -i ./gradlew --console=plain runShowcase "-Pshowcase.only=${ONLY%,}"
else
    caffeinate -d -i ./gradlew --console=plain runShowcase
fi
python3 scripts/shots.py "$@"

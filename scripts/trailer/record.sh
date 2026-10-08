#!/bin/sh
# Records gameplay scenes on this Mac (Homebrew JDK 21): opens a window, builds each scene in a fresh world, records every
# rendered frame at tick rate 5 and quits. Then retime.py makes real-speed 1920x1080 60 fps clips in run-trailer/footage.
# Usage: scripts/trailer/record.sh [scene1,scene2]   (no argument = all scenes). Real time per scene: about a minute.
# Leave the mouse alone while it runs. caffeinate keeps the display awake: with a sleeping display LWJGL finds no monitor.
set -e
cd "$(dirname "$0")/../.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
rm -rf run-trailer/saves
mkdir -p run-trailer/footage
if [ -n "$1" ]; then
    for id in $(echo "$1" | tr ',' ' '); do rm -f "run-trailer/footage/${id}_raw.mp4" "run-trailer/footage/${id}_times.txt" "run-trailer/footage/${id}.mp4"; done
    SCENES="-Ptrailer.scenes=$1"
fi
caffeinate -u -t 5
caffeinate -d -i ./gradlew --console=plain runTrailer $SCENES
if [ -n "$1" ]; then
    python3 scripts/trailer/retime.py $(echo "$1" | tr ',' ' ')
else
    python3 scripts/trailer/retime.py
fi

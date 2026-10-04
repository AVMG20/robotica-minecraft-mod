#!/bin/sh
# Builds Robotica inside Docker, no local Java needed.
#   scripts/docker-build.sh                    -> ./gradlew build (jar in build/libs/)
#   scripts/docker-build.sh runGameTestServer  -> headless server + game tests
# Gradle downloads (Minecraft, NeoForge) are cached in the docker volume "robotica-gradle".
set -e
cd "$(dirname "$0")/.."
docker build -q -t robotica-build . >/dev/null
exec docker run --rm -v "$PWD":/work -v robotica-gradle:/gradle robotica-build "${@:-build}"

#!/usr/bin/env bash
# Serves the cached Minecraft version metadata on localhost so Fabric Loom can
# configure the project while the Mojang endpoints are unreachable from this
# machine. Run it in a separate terminal before ./gradlew build:
#
#   ./tools/loom-mirror.sh
#
# It is picked up via loomCustomMetadataUrl in gradle.properties.
set -euo pipefail

PORT="${1:-8765}"
CACHE="$HOME/.gradle/caches/fabric-loom/1.21.11/mojang_minecraft_info.json"
MIRROR_DIR="$(mktemp -d)"

if [ ! -f "$CACHE" ]; then
    echo "Cached version metadata not found: $CACHE" >&2
    exit 1
fi

cp "$CACHE" "$MIRROR_DIR/1.21.11.json"

echo "Serving 1.21.11 metadata at http://127.0.0.1:$PORT/1.21.11.json (Ctrl+C to stop)"
exec python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$MIRROR_DIR"

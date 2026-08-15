#!/usr/bin/env bash
#
# Liga o servidor. Nao precisa de Java instalado no sistema: o JDK 25 exigido
# pelo Paper 26.2 vai junto em runtime/.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA="$ROOT/runtime/jdk-25/bin/java"
SERVER="$ROOT/server"

MEMORY="${MEMORY:-6G}"

if [[ ! -x "$JAVA" ]]; then
    echo "JDK 25 nao encontrado em $JAVA" >&2
    exit 1
fi

if [[ ! -f "$SERVER/plugins/WorldReset-1.0.0.jar" ]]; then
    echo "Plugin ausente. Rode ./build.sh primeiro." >&2
    exit 1
fi

cd "$SERVER"

echo "Iniciando Paper 26.2 com ${MEMORY} de heap..."
echo "Conecte em localhost:25565 (online-mode=false)."
echo

exec "$JAVA" \
    -Xms"$MEMORY" -Xmx"$MEMORY" \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+DisableExplicitGC \
    -XX:+AlwaysPreTouch \
    -XX:G1HeapWastePercent=5 \
    -XX:G1MixedGCCountTarget=4 \
    -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:G1RSetUpdatingPauseTimePercent=5 \
    -XX:SurvivorRatio=32 \
    -XX:+PerfDisableSharedMem \
    -XX:MaxTenuringThreshold=1 \
    -jar paper.jar --nogui

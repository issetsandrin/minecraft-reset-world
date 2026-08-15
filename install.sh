#!/usr/bin/env bash
#
# Instalação do servidor WorldReset.
#
# Monta um servidor Paper completo e pronto para ligar, sem exigir nada instalado
# na máquina além de curl, tar e unzip. Baixa o JDK 25, o Paper, o EssentialsX,
# compila o plugin e escreve as configurações.
#
# Uso:
#   ./install.sh
#   MEMORY=4G ./install.sh      # heap do servidor (padrão 2G)
#
set -euo pipefail

# --------------------------------------------------------------- versões fixas
#
# 26.1.2 e não a 26.2 de propósito: é a versão mais nova que o EssentialsX 2.22.0
# declara suportar. Na 26.2 ele carrega mas avisa "unsupported server version".
#
PAPER_VERSION="26.1.2"
PAPER_API_BUILD="26.1.2.build.74-stable"
ESSENTIALS_VERSION="2.22.0"
JDK_MAJOR="25"
PLUGIN_VERSION="1.0.0"

MEMORY="${MEMORY:-2G}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME="$ROOT/runtime"
SERVER="$ROOT/server"

# ------------------------------------------------------------------- utilidades

info()  { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
ok()    { printf '\033[1;32m  ok\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m  !!\033[0m %s\n' "$*" >&2; }
die()   { printf '\033[1;31merro:\033[0m %s\n' "$*" >&2; exit 1; }

need() {
    command -v "$1" >/dev/null 2>&1 || die "'$1' é necessário e não está instalado."
}

# Baixa só se o destino ainda não existir, para o script ser re-executável.
fetch() {
    local url="$1" dest="$2"
    if [[ -s "$dest" ]]; then
        ok "$(basename "$dest") já existe, pulando download"
        return
    fi
    curl -fSL --retry 3 --retry-delay 2 --progress-bar -o "$dest.part" "$url" \
        || die "falha ao baixar $url"
    mv "$dest.part" "$dest"
    ok "$(basename "$dest")"
}

need curl
need tar
need unzip
need python3

# ------------------------------------------------------------------------ JDK

install_jdk() {
    info "JDK $JDK_MAJOR (o Paper $PAPER_VERSION exige, e não usamos o Java do sistema)"
    mkdir -p "$RUNTIME"

    if [[ -x "$RUNTIME/jdk-$JDK_MAJOR/bin/java" ]]; then
        ok "já instalado"
        return
    fi

    local arch
    case "$(uname -m)" in
        x86_64)  arch="x64" ;;
        aarch64) arch="aarch64" ;;
        *)       die "arquitetura $(uname -m) não suportada por este script" ;;
    esac

    fetch "https://api.adoptium.net/v3/binary/latest/${JDK_MAJOR}/ga/linux/${arch}/jdk/hotspot/normal/eclipse" \
          "$RUNTIME/jdk.tar.gz"

    tar xzf "$RUNTIME/jdk.tar.gz" -C "$RUNTIME"
    local extracted
    extracted="$(find "$RUNTIME" -maxdepth 1 -type d -name "jdk-${JDK_MAJOR}*" | head -1)"
    [[ -n "$extracted" ]] || die "não encontrei o JDK extraído"
    [[ "$extracted" == "$RUNTIME/jdk-$JDK_MAJOR" ]] || mv "$extracted" "$RUNTIME/jdk-$JDK_MAJOR"
    rm -f "$RUNTIME/jdk.tar.gz"
    ok "$("$RUNTIME/jdk-$JDK_MAJOR/bin/java" -version 2>&1 | head -1)"
}

# ------------------------------------------------- dependências de compilação

install_build_deps() {
    info "Dependências de compilação do plugin"
    mkdir -p "$RUNTIME/libs"

    fetch "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/${PAPER_API_BUILD}/paper-api-${PAPER_API_BUILD}.jar" \
          "$RUNTIME/paper-api.jar"

    local central="https://repo1.maven.org/maven2"
    fetch "$central/net/kyori/adventure-api/5.2.0/adventure-api-5.2.0.jar"                         "$RUNTIME/libs/adventure-api.jar"
    fetch "$central/net/kyori/adventure-key/5.2.0/adventure-key-5.2.0.jar"                         "$RUNTIME/libs/adventure-key.jar"
    fetch "$central/net/kyori/adventure-text-minimessage/5.2.0/adventure-text-minimessage-5.2.0.jar" "$RUNTIME/libs/adventure-minimessage.jar"
    fetch "$central/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar"                     "$RUNTIME/libs/examination-api.jar"
    fetch "$central/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar"                       "$RUNTIME/libs/annotations.jar"
    fetch "$central/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"                                "$RUNTIME/libs/jspecify.jar"
    fetch "$central/com/google/guava/guava/33.3.1-jre/guava-33.3.1-jre.jar"                        "$RUNTIME/libs/guava.jar"
    fetch "$central/net/md-5/bungeecord-chat/1.20-R0.2/bungeecord-chat-1.20-R0.2.jar"              "$RUNTIME/libs/bungeecord-chat.jar"
}

# --------------------------------------------------------------------- servidor

install_paper() {
    info "Paper $PAPER_VERSION"
    mkdir -p "$SERVER/plugins"

    if [[ -s "$SERVER/paper.jar" ]]; then
        ok "já instalado"
        return
    fi

    local url
    url="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${PAPER_VERSION}/builds" \
           -H 'accept: application/json' \
        | python3 -c "
import json, sys
builds = json.load(sys.stdin)
stable = [b for b in builds if b.get('channel') == 'STABLE'] or builds
print(stable[0]['downloads']['server:default']['url'])
")" || die "não consegui descobrir a build do Paper $PAPER_VERSION"

    fetch "$url" "$SERVER/paper.jar"
}

install_essentials() {
    info "EssentialsX $ESSENTIALS_VERSION"
    local base="https://github.com/EssentialsX/Essentials/releases/download/${ESSENTIALS_VERSION}"
    fetch "$base/EssentialsX-${ESSENTIALS_VERSION}.jar"      "$SERVER/plugins/EssentialsX-${ESSENTIALS_VERSION}.jar"
    fetch "$base/EssentialsXSpawn-${ESSENTIALS_VERSION}.jar" "$SERVER/plugins/EssentialsXSpawn-${ESSENTIALS_VERSION}.jar"
}

# ------------------------------------------------------------------- configuração

write_configs() {
    info "Configuração do servidor"

    echo "eula=true" > "$SERVER/eula.txt"
    ok "eula.txt (aceito em seu nome — veja https://aka.ms/MinecraftEULA)"

    # Só escreve se ainda não existir: um server.properties editado por você não
    # pode ser sobrescrito por uma reexecução do instalador.
    if [[ -f "$SERVER/server.properties" ]]; then
        ok "server.properties já existe, preservado"
    else
        cat > "$SERVER/server.properties" <<EOF
# O mundo principal PRECISA ser o lobby. O WorldReset o cria vazio e nunca o
# apaga; os mundos jogaveis vivem em outros nomes e sao destruidos a cada morte.
level-name=lobby

# Somente contas originais (premium). A Mojang autentica cada login.
online-mode=true

motd=§6WorldReset §8| §7morreu, mundo novo
max-players=10
server-port=25565
spawn-protection=0
view-distance=8
simulation-distance=6
difficulty=normal
gamemode=survival
pvp=true
allow-nether=true
allow-flight=false
enable-command-block=false
sync-chunk-writes=false
EOF
        ok "server.properties"
    fi

    if [[ -f "$SERVER/bukkit.yml" ]] && grep -q "generator: WorldReset" "$SERVER/bukkit.yml"; then
        ok "bukkit.yml já configurado"
    else
        cat > "$SERVER/bukkit.yml" <<'EOF'
# Faz o lobby nascer vazio em vez de gerar um mundo inteiro que ninguem usa.
worlds:
  lobby:
    generator: WorldReset

settings:
  allow-end: true
  shutdown-message: Servidor encerrado
EOF
        ok "bukkit.yml"
    fi
}

# ------------------------------------------------------------------------ build

build_plugin() {
    info "Compilando o WorldReset"
    "$ROOT/build.sh" >/dev/null || die "a compilação falhou; rode ./build.sh para ver o erro"
    ok "server/plugins/WorldReset-${PLUGIN_VERSION}.jar"
}

# -------------------------------------------------------------------------- main

echo
echo "  WorldReset — instalação do servidor"
echo "  Paper $PAPER_VERSION · EssentialsX $ESSENTIALS_VERSION · JDK $JDK_MAJOR"
echo

install_jdk
install_build_deps
install_paper
install_essentials
write_configs
build_plugin

echo
info "Pronto."
echo
echo "  Ligar o servidor:   ./start.sh"
echo "  Heap diferente:     MEMORY=4G ./start.sh"
echo "  Recompilar plugin:  ./build.sh"
echo
echo "  O primeiro boot baixa o jar da Mojang (~50 MB) e pré-gera o próximo"
echo "  mundo; leva alguns minutos. Do segundo em diante sobe em ~15s."
echo

#!/usr/bin/env bash
#
# Compila o plugin e instala em server/plugins/.
#
# Usa o JDK 25 de runtime/ e as dependencias ja baixadas em runtime/libs/, entao
# funciona sem Maven e sem rede. Se preferir Maven, o pom.xml na raiz faz o
# mesmo com "mvn clean package".
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVAC="$ROOT/runtime/jdk-25/bin/javac"
JAR="$ROOT/runtime/jdk-25/bin/jar"
BUILD="$ROOT/target"
VERSION="1.0.0"

CLASSPATH="$ROOT/runtime/paper-api.jar"
for lib in "$ROOT"/runtime/libs/*.jar; do
    CLASSPATH="$CLASSPATH:$lib"
done

echo "Compilando..."
rm -rf "$BUILD/classes"
mkdir -p "$BUILD/classes"
find "$ROOT/src/main/java" -name '*.java' > "$BUILD/sources.txt"
"$JAVAC" -nowarn -encoding UTF-8 -cp "$CLASSPATH" -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "Aplicando os placeholders dos resources..."
DESCRIPTION='Recria o mundo inteiro sempre que qualquer jogador online morre.'
for file in "$ROOT"/src/main/resources/*; do
    sed -e "s/\${project.version}/$VERSION/g" \
        -e "s/\${project.description}/$DESCRIPTION/g" \
        "$file" > "$BUILD/classes/$(basename "$file")"
done

echo "Empacotando..."
rm -f "$BUILD/WorldReset-$VERSION.jar"
"$JAR" --create --file "$BUILD/WorldReset-$VERSION.jar" -C "$BUILD/classes" .

mkdir -p "$ROOT/server/plugins"
cp "$BUILD/WorldReset-$VERSION.jar" "$ROOT/server/plugins/"

echo "OK: server/plugins/WorldReset-$VERSION.jar"

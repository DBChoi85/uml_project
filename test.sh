#!/bin/bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src"
OUT="$ROOT/out"

mkdir -p "$OUT"
find "$OUT" -type f -name '*.class' -delete
find "$SRC" -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d "$OUT"
java -cp "$OUT" diagrameditor.Main --self-test

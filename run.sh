#!/bin/bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src"
OUT="$ROOT/out"

if ! command -v javac >/dev/null 2>&1; then
  echo "[ERROR] javac not found. Install JDK 17 or newer first."
  echo "Example with Homebrew: brew install openjdk@21"
  exit 1
fi

mkdir -p "$OUT"
find "$OUT" -type f -name '*.class' -delete

find "$SRC" -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d "$OUT"
java -cp "$OUT" diagrameditor.Main

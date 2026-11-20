#!/bin/sh
# Compile and run RailYatra (Mac / Linux)
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac -d out $(find src -name "*.java") || exit 1
java -cp out railyatra.Main

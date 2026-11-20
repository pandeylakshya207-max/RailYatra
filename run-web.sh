#!/bin/sh
# RailYatra web app (Mac / Linux). Then open http://localhost:8080
cd "$(dirname "$0")" || exit 1
mkdir -p out-web
javac -d out-web $(find src web/src -name "*.java") || exit 1
java -cp out-web railyatra.web.WebServer

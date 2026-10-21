#!/bin/bash
# Compiles (if a JDK is installed) and starts Elf.
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/*.java 2>/dev/null || echo "javac not available, using the ready-made classes in out/"
java -cp out ElfChatBot

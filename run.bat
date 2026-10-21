@echo off
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\*.java 2>nul || echo javac not available, using the ready-made classes in out\
java -cp out ElfChatBot
pause

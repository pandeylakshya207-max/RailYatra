@echo off
chcp 65001 >nul
if not exist out mkdir out
javac -d out src\railyatra\*.java src\railyatra\exceptions\*.java src\railyatra\model\*.java src\railyatra\people\*.java src\railyatra\service\*.java src\railyatra\util\*.java
if errorlevel 1 (
  echo Compilation failed.
  pause
  exit /b 1
)
java -cp out railyatra.Main
pause

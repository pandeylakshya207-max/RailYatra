@echo off
REM RailYatra web app - compiles the same Java code plus the web server, then opens the browser
if not exist out-web mkdir out-web
javac -d out-web src\railyatra\*.java src\railyatra\exceptions\*.java src\railyatra\model\*.java src\railyatra\people\*.java src\railyatra\service\*.java src\railyatra\util\*.java web\src\railyatra\web\*.java
if errorlevel 1 (
  echo Compilation failed.
  pause
  exit /b 1
)
start "" cmd /c "timeout /t 2 >nul & start http://localhost:8080"
java -cp out-web railyatra.web.WebServer
pause

@echo off
REM Compiles AS-HTS against the bundled CloudSim jar. Needs only a JDK (8 or newer).
cd /d "%~dp0\.."
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
set SRC=src\main\java\edu\manipal\ashts
javac -encoding UTF-8 -nowarn -cp "lib\cloudsim-3.0.3-core.jar" -d build\classes ^
  %SRC%\*.java %SRC%\core\*.java %SRC%\workload\*.java %SRC%\datacenter\*.java ^
  %SRC%\broker\*.java %SRC%\policy\*.java %SRC%\classifier\*.java ^
  %SRC%\metrics\*.java %SRC%\experiment\*.java
if errorlevel 1 exit /b 1
echo Build OK -^> build\classes

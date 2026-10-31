@echo off
REM Runs the AS-HTS command-line tool. Builds first if needed.
REM   scripts\run.bat validate
REM   scripts\run.bat demo --scenario PHASED --load 0.9
REM   scripts\run.bat experiment --config config\experiment.properties
cd /d "%~dp0\.."
if not exist build\classes\edu call scripts\build.bat
java -cp "build\classes;lib\cloudsim-3.0.3-core.jar" edu.manipal.ashts.Main %*

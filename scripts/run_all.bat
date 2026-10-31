@echo off
REM One-shot reproduction: build -> validate -> full experiment grid -> plots.
cd /d "%~dp0\.."
call scripts\build.bat || exit /b 1
call scripts\run.bat validate
call scripts\run.bat experiment --config config\experiment.properties
python analysis\plot_results.py --results results --figures results\figures
echo.
echo Done. Open results\figures\ for the graphs and results\summary_table.md for the tables.

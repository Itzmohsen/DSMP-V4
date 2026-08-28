@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not "%PYTHON_CMD%"=="" goto run_with_env

where py >nul 2>nul
if %ERRORLEVEL%==0 (
    for %%V in (3.12 3.11) do (
        py -%%V -c "import sys; raise SystemExit(0 if sys.version_info[:2] in ((3,11),(3,12)) else 1)" >nul 2>nul
        if !ERRORLEVEL!==0 (
            set "PYTHON_CMD=py -%%V"
            goto run_with_env
        )
    )
)

where python >nul 2>nul
if !ERRORLEVEL!==0 (
    python -c "import sys; raise SystemExit(0 if sys.version_info[:2] in ((3,11),(3,12)) else 1)" >nul 2>nul
    if !ERRORLEVEL!==0 (
        set "PYTHON_CMD=python"
        goto run_with_env
    )
)

where python3 >nul 2>nul
if !ERRORLEVEL!==0 (
    python3 -c "import sys; raise SystemExit(0 if sys.version_info[:2] in ((3,11),(3,12)) else 1)" >nul 2>nul
    if !ERRORLEVEL!==0 (
        set "PYTHON_CMD=python3"
        goto run_with_env
    )
)

echo Could not find supported Python 3.11 or 3.12.
echo Install Python from https://www.python.org/downloads/ and rerun this file.
pause
exit /b 1

:run_with_env
echo Using Python: %PYTHON_CMD%
%PYTHON_CMD% build.py launch %*
set EXIT_CODE=%ERRORLEVEL%
if not "%EXIT_CODE%"=="0" pause
exit /b %EXIT_CODE%

@echo off
setlocal

set "SCRIPT_DIR=%~dp0"

where py >nul 2>nul
if %ERRORLEVEL%==0 (
  py -3 -c "import tkinter" >nul 2>nul
  if %ERRORLEVEL%==0 (
    py -3 "%SCRIPT_DIR%smsgate_testkit_gui.py"
  ) else (
    echo Tkinter is not available in this Python build. Starting browser GUI fallback...
    py -3 "%SCRIPT_DIR%smsgate_testkit_web.py"
  )
  exit /b %ERRORLEVEL%
)

python -c "import tkinter" >nul 2>nul
if %ERRORLEVEL%==0 (
  python "%SCRIPT_DIR%smsgate_testkit_gui.py"
) else (
  echo Tkinter is not available in this Python build. Starting browser GUI fallback...
  python "%SCRIPT_DIR%smsgate_testkit_web.py"
)
exit /b %ERRORLEVEL%

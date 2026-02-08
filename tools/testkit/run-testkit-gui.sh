#!/usr/bin/env sh

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if python3 -c "import tkinter" >/dev/null 2>&1; then
  python3 "$SCRIPT_DIR/smsgate_testkit_gui.py"
else
  printf "Tkinter is not available in this Python build. Starting browser GUI fallback...\n"
  python3 "$SCRIPT_DIR/smsgate_testkit_web.py"
fi

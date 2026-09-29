#!/usr/bin/env bash
# Create robot/.venv and install robot/requirements.txt. PYTHON=... picks the interpreter.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
VENV="$HERE/.venv"
VENV_PYTHON="$VENV/bin/python"
REQUIREMENTS="$HERE/requirements.txt"
PYTHON="${PYTHON:-python3}"

if [[ ! -f "$REQUIREMENTS" ]]; then
    echo "Requirements file missing: $REQUIREMENTS" >&2
    exit 1
fi

if [[ ! -x "$VENV_PYTHON" ]]; then
    echo "Creating virtual environment at $VENV ..."
    "$PYTHON" -m venv "$VENV"
else
    echo "Virtual environment already exists at $VENV"
fi

echo "Upgrading pip ..."
"$VENV_PYTHON" -m pip install --upgrade pip -q

echo "Installing requirements from $REQUIREMENTS ..."
"$VENV_PYTHON" -m pip install -r "$REQUIREMENTS" -q

echo
echo "Done. Robot Framework is installed in $VENV"
# robot --version exits 251
"$VENV/bin/robot" --version || rc=$?
if [[ "${rc:-0}" -ne 0 && "${rc:-0}" -ne 251 ]]; then
    exit "${rc}"
fi

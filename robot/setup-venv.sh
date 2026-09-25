#!/usr/bin/env bash
# One-shot setup for the project-local Python virtual environment used by the
# Robot Framework smoke suite. Creates robot/.venv if missing, then installs
# robot/requirements.txt into it. Idempotent. Re-run safely after pulling
# requirement changes.
#
# Usage:
#     bash robot/setup-venv.sh
#     PYTHON=/usr/bin/python3.11 bash robot/setup-venv.sh
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
# 'robot --version' exits 251 by design; tolerate that here.
"$VENV/bin/robot" --version || rc=$?
if [[ "${rc:-0}" -ne 0 && "${rc:-0}" -ne 251 ]]; then
    exit "${rc}"
fi

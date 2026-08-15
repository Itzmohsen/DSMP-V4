#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

find_python() {
  for candidate in python3 python python3.12 python3.11 python3.10 python3.9; do
    if command -v "$candidate" >/dev/null 2>&1; then
      if "$candidate" - <<'PY' >/dev/null 2>&1
import sys
raise SystemExit(0 if sys.version_info >= (3, 9) else 1)
PY
      then
        printf '%s\n' "$candidate"
        return 0
      fi
    fi
  done
  return 1
}

PYTHON_CMD="${PYTHON_CMD:-}"
if [ -z "$PYTHON_CMD" ]; then
  if ! PYTHON_CMD="$(find_python)"; then
    echo "Could not find Python 3.9 or newer."
    echo "Install Python from https://www.python.org/downloads/ and rerun this script."
    exit 1
  fi
fi

exec "$PYTHON_CMD" build.py launch "$@"

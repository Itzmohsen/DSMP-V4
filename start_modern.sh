#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

find_python() {
  # Prefer explicit supported versions. A generic `python3` may point to 3.13+
  # where the pinned NumPy 1.x runtime has no compatible wheel.
  for candidate in python3.12 python3.11 python3 python; do
    if command -v "$candidate" >/dev/null 2>&1; then
      if "$candidate" - <<'PY' >/dev/null 2>&1
import sys
raise SystemExit(0 if (3, 11) <= sys.version_info[:2] < (3, 13) else 1)
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
    echo "Could not find supported Python 3.11 or 3.12."
    echo "Install Python from https://www.python.org/downloads/ and rerun this script."
    exit 1
  fi
fi

exec "$PYTHON_CMD" build.py launch "$@"

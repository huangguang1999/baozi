# Best-effort "is TCP PORT unbound right now?" probe.
# Exit 0 means "looks free", exit 1 means "in use". If no probe tool is
# available we conservatively report free (exit 0) — picking a maybe-busy
# port is recoverable; refusing to ever pick one is not.
port={{PORT}}
if command -v lsof >/dev/null 2>&1; then
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | grep . >/dev/null 2>&1; then
    exit 1
  fi
  exit 0
fi
if command -v ss >/dev/null 2>&1; then
  if ss -ltn "sport = :$port" 2>/dev/null | awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }'; then
    exit 1
  fi
  exit 0
fi
if command -v netstat >/dev/null 2>&1; then
  if netstat -ltn 2>/dev/null | awk -v p="$port" '$4 ~ ("[:.]" p "$") { found = 1 } END { exit found ? 0 : 1 }'; then
    exit 1
  fi
  exit 0
fi
exit 0

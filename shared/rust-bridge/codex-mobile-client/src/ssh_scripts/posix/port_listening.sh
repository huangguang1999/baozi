# Detect whether anything is listening on TCP PORT. Walks the lsof, ss,
# netstat fallback chain (whichever is present). Output is the listener pid
# (lsof) or a connection line; emptiness = nothing listening. Exit code is
# always 0 — the caller checks stdout.
if command -v lsof >/dev/null 2>&1; then
  lsof -nP -iTCP:{{PORT}} -sTCP:LISTEN -t 2>/dev/null | head -n 1
elif command -v ss >/dev/null 2>&1; then
  ss -ltn "sport = :{{PORT}}" 2>/dev/null | tail -n +2 | head -n 1
elif command -v netstat >/dev/null 2>&1; then
  netstat -ltn 2>/dev/null | awk '{print $4}' | grep -E '[:\.]{{PORT}}$' | head -n 1
fi

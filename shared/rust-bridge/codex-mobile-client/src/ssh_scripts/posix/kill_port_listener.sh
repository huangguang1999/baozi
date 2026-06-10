# Kill any listener on TCP PORT. Tries lsof first, falls back to fuser.
# Emits a single status line documenting the outcome:
#   no_listener / killing / force_killed / stopped
# Always exits 0 even if nothing was listening.
pids=""
if command -v lsof >/dev/null 2>&1; then
  pids="$(lsof -nP -iTCP:{{PORT}} -sTCP:LISTEN -t 2>/dev/null | sort -u)"
fi
if [ -z "$pids" ] && command -v fuser >/dev/null 2>&1; then
  pids="$(fuser {{PORT}}/tcp 2>/dev/null | tr ' ' '\n' | sort -u)"
fi
if [ -z "$pids" ]; then
  printf 'litter_restart_app_server no_listener port={{PORT}}\n'
  exit 0
fi
printf 'litter_restart_app_server killing port={{PORT}} pids=%s\n' "$pids"
kill $pids 2>/dev/null || true
sleep 1
alive=""
for pid in $pids; do
  if kill -0 "$pid" 2>/dev/null; then
    alive="$alive $pid"
  fi
done
if [ -n "$alive" ]; then
  kill -9 $alive 2>/dev/null || true
  printf 'litter_restart_app_server force_killed port={{PORT}} pids=%s\n' "$alive"
else
  printf 'litter_restart_app_server stopped port={{PORT}}\n'
fi

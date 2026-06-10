# Spawn opencode in $HOME/.litter/sessions/SESSION_ID/, redirect its
# stdout/stderr to per-session log files, and stash its pid for later. Exits
# non-zero if the process dies before the script returns; in that case the
# tail of out.log + err.log is dumped to stderr so the caller sees why.
{{PROFILE_INIT}}
session_dir="$HOME/.litter/sessions/{{SESSION_ID}}"
mkdir -p "$session_dir"
: >"$session_dir/out.log"
: >"$session_dir/err.log"
if command -v setsid >/dev/null 2>&1; then
  nohup setsid {{BIN}} serve --port={{PORT}} </dev/null >"$session_dir/out.log" 2>"$session_dir/err.log" &
else
  nohup {{BIN}} serve --port={{PORT}} </dev/null >"$session_dir/out.log" 2>"$session_dir/err.log" &
fi
pid=$!
echo "$pid" >"$session_dir/agent.pid"
sleep 0.05
if ! kill -0 "$pid" 2>/dev/null; then
  echo "opencode exited immediately after launch" >&2
  echo "--- out.log ---" >&2
  (tail -n 120 "$session_dir/out.log" 2>/dev/null || true) >&2
  echo "--- err.log ---" >&2
  (tail -n 120 "$session_dir/err.log" 2>/dev/null || true) >&2
  exit 1
fi
printf '%s\n' "$session_dir"

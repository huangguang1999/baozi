# Tail the most recent stdout / stderr from a remote opencode session.
{{PROFILE_INIT}}
session_dir="$HOME/.litter/sessions/{{SESSION_ID}}"
echo "--- out.log ---"
tail -n 120 "$session_dir/out.log" 2>/dev/null || true
echo "--- err.log ---"
tail -n 120 "$session_dir/err.log" 2>/dev/null || true

# Poll http://127.0.0.1:PORT/global/health on the remote until opencode
# reports healthy or the underlying process dies. Without curl we fall
# through to "assume healthy after a brief delay" since the alternative is
# blocking the bootstrap on a host with no http client.
{{PROFILE_INIT}}
port={{PORT}}
session_dir="$HOME/.litter/sessions/{{SESSION_ID}}"
url="http://127.0.0.1:${port}/global/health"
has_curl=0
if command -v curl >/dev/null 2>&1; then
  has_curl=1
fi

i=0
while [ "$i" -lt 100 ]; do
  i=$((i + 1))
  if [ "$has_curl" -eq 1 ]; then
    body=$(curl -fsS --max-time 1 "$url" 2>/dev/null || true)
    case "$body" in
      *'"healthy":true'*|*'"healthy": true'*)
        exit 0
        ;;
    esac
  fi

  pid=$(cat "$session_dir/agent.pid" 2>/dev/null || true)
  if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
    echo "opencode exited before reporting healthy at $url" >&2
    echo "--- out.log ---" >&2
    (tail -n 120 "$session_dir/out.log" 2>/dev/null || true) >&2
    echo "--- err.log ---" >&2
    (tail -n 120 "$session_dir/err.log" 2>/dev/null || true) >&2
    exit 1
  fi
  if [ "$has_curl" -ne 1 ] && [ "$i" -ge 10 ]; then
    exit 0
  fi
  sleep 0.1
done

echo "opencode did not become healthy at $url" >&2
echo "--- out.log ---" >&2
(tail -n 120 "$session_dir/out.log" 2>/dev/null || true) >&2
echo "--- err.log ---" >&2
(tail -n 120 "$session_dir/err.log" 2>/dev/null || true) >&2
exit 1

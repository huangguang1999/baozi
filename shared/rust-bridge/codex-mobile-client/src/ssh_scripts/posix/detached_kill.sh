# Tear down a detached agent: SIGTERM the agent, SIGTERM the keeper, then
# rm -rf the session directory. All steps best-effort — caller already
# decided the session is gone.
agent_pid="$(cat {{AGENT_PID}} 2>/dev/null || true)"
keeper_pid="$(cat {{KEEPER_PID}} 2>/dev/null || true)"
[ -n "$agent_pid" ] && kill -TERM "$agent_pid" 2>/dev/null || true
[ -n "$keeper_pid" ] && kill -TERM "$keeper_pid" 2>/dev/null || true
rm -rf {{ROOT}}

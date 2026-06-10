# Probe npm / pnpm / bun for their global binary directories.
# Sets these variables for downstream scripts to read:
#   _litter_npm_global_bin
#   _litter_pnpm_global_bin
#   _litter_bun_global_bin
# Requires PROFILE_INIT to have run first.
_litter_npm_prefix=""
_litter_npm_global_bin=""
_litter_pnpm_global_bin=""
_litter_bun_global_bin=""
if command -v npm >/dev/null 2>&1; then
  _litter_npm_prefix="$(npm config get prefix 2>/dev/null || true)"
  case "$_litter_npm_prefix" in
    "" | "undefined" | "null")
      _litter_npm_prefix=""
      ;;
    *)
      _litter_npm_global_bin="$_litter_npm_prefix/bin"
      ;;
  esac
fi
if command -v pnpm >/dev/null 2>&1; then
  _litter_pnpm_global_bin="$(pnpm bin -g 2>/dev/null || true)"
fi
if command -v bun >/dev/null 2>&1; then
  _litter_bun_global_bin="$(bun pm bin -g 2>/dev/null || true)"
fi

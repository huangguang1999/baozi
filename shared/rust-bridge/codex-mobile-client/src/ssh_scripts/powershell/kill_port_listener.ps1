# Kill the process(es) holding TCP PORT. Always exits 0 even if nothing
# was listening.
$connections = Get-NetTCPConnection -LocalPort {{PORT}} -State Listen -ErrorAction SilentlyContinue
$pids = @($connections | Select-Object -ExpandProperty OwningProcess -Unique)
if ($pids.Count -eq 0) {
  Write-Host 'litter_restart_app_server no_listener port={{PORT}}'
  exit 0
}
Write-Host "litter_restart_app_server killing port={{PORT}} pids=$($pids -join ',')"
foreach ($processId in $pids) {
  Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
}
Write-Host 'litter_restart_app_server stopped port={{PORT}}'

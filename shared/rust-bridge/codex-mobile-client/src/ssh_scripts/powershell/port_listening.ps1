# Detect whether anything is listening on TCP PORT. Output is the port
# number (LocalPort) when something is listening, empty otherwise.
Get-NetTCPConnection -LocalPort {{PORT}} -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty LocalPort

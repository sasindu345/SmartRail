#!/usr/bin/env bash
# Run a SmartRail process from the project root.
#   scripts/run.sh server
#   scripts/run.sh train TR02 [-Dclient.transport=TLS]
#   scripts/run.sh controller
# Any -Dkey=value argument overrides config/smartrail.properties for that process.
set -euo pipefail
cd "$(dirname "$0")/.."

role="${1:-}"
[ $# -gt 0 ] && shift
case "$role" in
  server)     main=com.smartrail.app.ServerApp ;;
  train)      main=com.smartrail.app.TrainClientApp ;;
  controller) main=com.smartrail.app.ControllerApp ;;
  *) echo "usage: scripts/run.sh server|train [TRAIN_ID]|controller [-Dkey=value ...]" >&2; exit 1 ;;
esac

mvn -q -DskipTests compile

jvm=(); app=()
for a in "$@"; do
  case "$a" in -D*) jvm+=("$a") ;; *) app+=("$a") ;; esac
done
exec java ${jvm[@]+"${jvm[@]}"} -cp target/classes "$main" ${app[@]+"${app[@]}"}

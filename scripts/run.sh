#!/usr/bin/env bash
# Run a SmartRail process from the project root.
#   scripts/run.sh controlcenter
#   scripts/run.sh server
#   scripts/run.sh train TR02 [-Dclient.transport=TLS]
# Any -Dkey=value argument overrides config/smartrail.properties for that process.
set -euo pipefail
cd "$(dirname "$0")/.."

role="${1:-}"
[ $# -gt 0 ] && shift
case "$role" in
  server)        main=com.smartrail.app.ServerApp ;;
  train)         main=com.smartrail.app.TrainClientApp ;;
  controlcenter) main=com.smartrail.app.ControlCenterApp ;;
  *) echo "usage: scripts/run.sh controlcenter|server|train [TRAIN_ID] [-Dkey=value ...]" >&2; exit 1 ;;
esac

mvn -q -DskipTests compile dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency

jvm=(); app=()
for a in "$@"; do
  case "$a" in -D*) jvm+=("$a") ;; *) app+=("$a") ;; esac
done
exec java ${jvm[@]+"${jvm[@]}"} -cp "target/classes:target/dependency/*" "$main" ${app[@]+"${app[@]}"}

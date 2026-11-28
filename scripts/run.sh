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

MVN="${MAVEN_HOME:-}" 
if [ -z "$MVN" ]; then
  # Try the Maven wrapper location used by this project on Windows
  WRAPPER="$HOME/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn"
  if [ -f "$WRAPPER" ]; then MVN="$WRAPPER"
  elif command -v mvn &>/dev/null; then MVN="mvn"
  else echo "ERROR: mvn not found. Set MAVEN_HOME or add mvn to PATH." >&2; exit 1
  fi
fi
"$MVN" -q -DskipTests compile dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency

jvm=(); app=()
for a in "$@"; do
  case "$a" in -D*) jvm+=("$a") ;; *) app+=("$a") ;; esac
done
exec java ${jvm[@]+"${jvm[@]}"} -cp "target/classes:target/dependency/*" "$main" ${app[@]+"${app[@]}"}

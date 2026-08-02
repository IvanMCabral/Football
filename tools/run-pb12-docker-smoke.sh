#!/usr/bin/env bash
set -Eeuo pipefail

RUN_ID="$(printenv GITHUB_RUN_ID 2>/dev/null || true)"
if [[ -z "$RUN_ID" ]]; then RUN_ID="local-$(date +%s)"; fi
RUNNER_TEMP_VALUE="$(printenv RUNNER_TEMP 2>/dev/null || true)"
if [[ -z "$RUNNER_TEMP_VALUE" ]]; then RUNNER_TEMP_VALUE=target; fi
RUN_DIR="$(printenv PB12_RUN_DIR 2>/dev/null || echo "$RUNNER_TEMP_VALUE/pb12-docker-smoke-$RUN_ID")"
mkdir -p "$RUN_DIR" target
chmod 700 "$RUN_DIR"
IMAGE="$(printenv PB12_IMAGE 2>/dev/null || echo manager-backend:pb12)"
NETWORK="manager-pb12-smoke-$RUN_ID"
POSTGRES="manager-pb12-postgres-$RUN_ID"
REDIS="manager-pb12-redis-$RUN_ID"
BACKEND="manager-pb12-backend-$RUN_ID"
RESTART_BACKEND="manager-pb12-backend-restart-$RUN_ID"
HOST_PORT="$(printenv PB12_HOST_PORT 2>/dev/null || echo 18080)"
RESTART_PORT="$(printenv PB12_RESTART_PORT 2>/dev/null || echo 18081)"

DB_NAME="$(printenv DB_NAME 2>/dev/null || echo football_manager)"
DB_USER="$(printenv DB_USER 2>/dev/null || echo manager)"
DB_PASSWORD="$(printenv DB_PASSWORD 2>/dev/null || true)"
REDIS_PASSWORD="$(printenv REDIS_PASSWORD 2>/dev/null || true)"
REDIS_USERNAME="$(printenv REDIS_USERNAME 2>/dev/null || echo default)"
JWT_SECRET="$(printenv JWT_SECRET 2>/dev/null || true)"
if [[ -z "$DB_PASSWORD" || -z "$REDIS_PASSWORD" || -z "$JWT_SECRET" ]]; then echo "required ephemeral variables are missing" >&2; exit 1; fi

status=PASS; failure_reason=""
docker_available=false; image_built=false; image_id=""; image_digest=""; image_size_bytes=0; image_layers=0; image_architecture=""
runtime_user=""; runtime_uid=0; java_pid1=false; healthcheck_healthy=false
liveness=0; readiness=0; registered=false; login=false; me=false; career_created=false; career_recovered=false
flyway_run1=0; flyway_run2=0; second_startup=false; docker_stop_used=false; docker_kill_used=false
graceful_shutdown_observed=false; shutdown_marker_order_valid=false; shutdown_duration_ms=0
container_exit_code=125
redis_down_liveness=0; redis_down_readiness=0; postgres_down_liveness=0; postgres_down_readiness=0
unexpected_filesystem_writes=0; secret_leaks_detected=0; critical_vulnerabilities=0; high_vulnerabilities=0
residual_containers=0; residual_networks=0; cleanup_verified=false; cleanup_force_used=false
AUTH_TMP=""; auth_temp_exists=false; auth_temp_cleanup_verified=false; final_artifact_secret_scan_passed=false

BUILD_LOG="$RUN_DIR/docker-build.log"; IMAGE_INSPECT="$RUN_DIR/docker-image-inspect.json"; IMAGE_HISTORY="$RUN_DIR/docker-image-history.txt"
IMAGE_FILES="$RUN_DIR/image-files.txt"; STARTUP_LOG="$RUN_DIR/startup.log"; RESTART_LOG="$RUN_DIR/restart.log"
SHUTDOWN_LOG="$RUN_DIR/shutdown.log"; NEGATIVE_LOG="$RUN_DIR/negative-readiness.log"; CLEANUP_REPORT="$RUN_DIR/cleanup-report.json"
FINAL_SECRET_SCAN_REPORT="$RUN_DIR/pb12-final-secret-scan.json"
RESULT="$RUN_DIR/pb12-docker-smoke-result.json"

record_unexpected_error() {
  local rc=$?
  if [[ -z "$failure_reason" ]]; then
    failure_reason="unexpected command failure at line ${BASH_LINENO[0]:-unknown}"
  fi
  return "$rc"
}

write_result() {
  jq -n --arg status "$status" --arg failureReason "$failure_reason" --arg imageId "$image_id" --arg imageDigest "$image_digest" \
    --arg runtimeUser "$runtime_user" --arg architecture "$image_architecture" --arg baseImage "eclipse-temurin:21.0.11_10-jre-alpine-3.23" \
    --argjson dockerAvailable "$docker_available" --argjson imageBuilt "$image_built" --argjson imageSizeBytes "$image_size_bytes" \
    --argjson imageLayers "$image_layers" --argjson runtimeUid "$runtime_uid" --argjson javaPid1 "$java_pid1" \
    --argjson healthcheckHealthy "$healthcheck_healthy" --argjson liveness "$liveness" --argjson readiness "$readiness" \
    --argjson registered "$registered" --argjson login "$login" --argjson me "$me" --argjson careerCreated "$career_created" \
    --argjson careerRecovered "$career_recovered" --argjson flywayMigrationsRun1 "$flyway_run1" --argjson secondStartup "$second_startup" \
    --argjson flywayMigrationsRun2 "$flyway_run2" --argjson dockerStopUsed "$docker_stop_used" --argjson dockerKillUsed "$docker_kill_used" \
    --argjson gracefulShutdownObserved "$graceful_shutdown_observed" --argjson shutdownMarkerOrderValid "$shutdown_marker_order_valid" \
    --argjson shutdownDurationMs "$shutdown_duration_ms" --argjson containerExitCode "$container_exit_code" \
    --argjson redisDownLiveness "$redis_down_liveness" --argjson redisDownReadiness "$redis_down_readiness" \
    --argjson postgresDownLiveness "$postgres_down_liveness" --argjson postgresDownReadiness "$postgres_down_readiness" \
    --argjson unexpectedFilesystemWrites "$unexpected_filesystem_writes" --argjson secretLeaksDetected "$secret_leaks_detected" \
    --argjson criticalVulnerabilities "$critical_vulnerabilities" --argjson highVulnerabilities "$high_vulnerabilities" \
    --argjson residualContainers "$residual_containers" --argjson residualNetworks "$residual_networks" --argjson cleanupVerified "$cleanup_verified" \
    --argjson cleanupForceUsed "$cleanup_force_used" --argjson authTempExists "$auth_temp_exists" \
    --argjson authTempCleanupVerified "$auth_temp_cleanup_verified" --argjson finalArtifactSecretScanPassed "$final_artifact_secret_scan_passed" \
    '{status:$status,failureReason:$failureReason,dockerAvailable:$dockerAvailable,imageBuilt:$imageBuilt,imageId:$imageId,imageDigest:$imageDigest,imageSizeBytes:$imageSizeBytes,imageLayers:$imageLayers,architecture:$architecture,baseImage:$baseImage,runtimeUser:$runtimeUser,runtimeUid:$runtimeUid,javaPid1:$javaPid1,healthcheckHealthy:$healthcheckHealthy,liveness:$liveness,readiness:$readiness,registered:$registered,login:$login,me:$me,careerCreated:$careerCreated,careerRecovered:$careerRecovered,flywayMigrationsRun1:$flywayMigrationsRun1,secondStartup:$secondStartup,flywayMigrationsRun2:$flywayMigrationsRun2,dockerStopUsed:$dockerStopUsed,dockerKillUsed:$dockerKillUsed,gracefulShutdownObserved:$gracefulShutdownObserved,shutdownMarkerOrderValid:$shutdownMarkerOrderValid,shutdownDurationMs:$shutdownDurationMs,containerExitCode:$containerExitCode,redisDownLiveness:$redisDownLiveness,redisDownReadiness:$redisDownReadiness,postgresDownLiveness:$postgresDownLiveness,postgresDownReadiness:$postgresDownReadiness,unexpectedFilesystemWrites:$unexpectedFilesystemWrites,secretLeaksDetected:$secretLeaksDetected,criticalVulnerabilities:$criticalVulnerabilities,highVulnerabilities:$highVulnerabilities,residualContainers:$residualContainers,residualNetworks:$residualNetworks,cleanupVerified:$cleanupVerified,cleanupForceUsed:$cleanupForceUsed,authTempExists:$authTempExists,authTempCleanupVerified:$authTempCleanupVerified,finalArtifactSecretScanPassed:$finalArtifactSecretScanPassed}' > "$RESULT"
}
fail() { status=FAIL; if [[ -z "$failure_reason" ]]; then failure_reason="$1"; else failure_reason="$failure_reason; $1"; fi; }
container_exists() { docker container inspect "$1" >/dev/null 2>&1; }

cleanup() {
  set +e
  mkdir -p "$RUN_DIR"
  for c in "$BACKEND" "$RESTART_BACKEND" "$POSTGRES" "$REDIS"; do
    if container_exists "$c"; then
      docker logs "$c" > "$RUN_DIR/$c.log" 2>&1
      docker inspect --format '{{.State.Status}} {{.State.ExitCode}}' "$c" > "$RUN_DIR/$c.state" 2>&1
      docker diff "$c" > "$RUN_DIR/$c.diff" 2>&1
    fi
  done
  for c in "$BACKEND" "$RESTART_BACKEND" "$POSTGRES" "$REDIS"; do
    if container_exists "$c"; then
      docker stop --time 30 "$c" >/dev/null 2>&1
      if [[ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" == true ]]; then
        cleanup_force_used=true; docker rm -f "$c" >/dev/null 2>&1
      else
        docker rm "$c" >/dev/null 2>&1
      fi
    fi
  done
  docker network rm "$NETWORK" >/dev/null 2>&1
  if [[ -n "$AUTH_TMP" ]]; then
    auth_temp_exists=false
    if [[ "${PB12_FORCE_AUTH_TMP_DELETE_FAILURE:-false}" == true ]]; then
      auth_temp_cleanup_verified=false
    else
      rm -rf -- "$AUTH_TMP" >/dev/null 2>&1 || true
      if [[ -e "$AUTH_TMP" ]]; then auth_temp_cleanup_verified=false; else auth_temp_cleanup_verified=true; fi
    fi
    if [[ -e "$AUTH_TMP" ]]; then auth_temp_exists=true; fi
  else
    auth_temp_cleanup_verified=true
  fi
  residual_containers="$(docker ps -aq --filter "name=^$POSTGRES$" --filter "name=^$REDIS$" --filter "name=^$BACKEND$" --filter "name=^$RESTART_BACKEND$" | sed '/^$/d' | wc -l | tr -d ' ')"
  residual_networks="$(docker network ls -q --filter "name=^$NETWORK$" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ "$residual_containers" == 0 && "$residual_networks" == 0 && "$cleanup_force_used" == false ]]; then cleanup_verified=true; else cleanup_verified=false; fi
  jq -n --argjson residualContainers "$residual_containers" --argjson residualNetworks "$residual_networks" --argjson cleanupVerified "$cleanup_verified" --argjson cleanupForceUsed "$cleanup_force_used" \
    --argjson authTempExists "$auth_temp_exists" --argjson authTempCleanupVerified "$auth_temp_cleanup_verified" \
    '{residualContainers:$residualContainers,residualNetworks:$residualNetworks,cleanupVerified:$cleanupVerified,cleanupForceUsed:$cleanupForceUsed,authTempExists:$authTempExists,authTempCleanupVerified:$authTempCleanupVerified}' > "$CLEANUP_REPORT"
  if [[ "$cleanup_verified" != true ]]; then status=FAIL; if [[ -z "$failure_reason" ]]; then failure_reason="cleanup verification failed"; else failure_reason="$failure_reason; cleanup verification failed"; fi; fi
  if [[ "$auth_temp_cleanup_verified" != true || "$auth_temp_exists" == true ]]; then status=FAIL; if [[ -z "$failure_reason" ]]; then failure_reason="auth temporary directory cleanup failed"; else failure_reason="$failure_reason; auth temporary directory cleanup failed"; fi; fi
  final_artifact_secret_scan_passed=true
  secret_matches="$(grep -RIlE 'Authorization:[[:space:]]*Bearer[[:space:]]+[A-Za-z0-9._~-]{20,}|JWT_SECRET[=:][^[:space:]]+|REDIS_PASSWORD[=:][^[:space:]]+|DB_PASSWORD[=:][^[:space:]]+|postgres(ql)?://[^[:space:]]+:[^[:space:]]+@|redis://[^[:space:]]+:[^[:space:]]+@|(^|/)(\.env|.*\.cookie)$' "$RUN_DIR" --exclude='pb12-docker-smoke-result.json' --exclude='pb12-final-secret-scan.json' 2>/dev/null || true)"
  if [[ -n "$secret_matches" ]]; then
    secret_leaks_detected="$(printf '%s\n' "$secret_matches" | sed '/^$/d' | wc -l | tr -d ' ')"
    final_artifact_secret_scan_passed=false
    status=FAIL
    if [[ -z "$failure_reason" ]]; then failure_reason="final artifact secret scan detected a sensitive pattern"; else failure_reason="$failure_reason; final artifact secret scan detected a sensitive pattern"; fi
  fi
  if [[ "$auth_temp_cleanup_verified" != true || "$auth_temp_exists" == true ]]; then final_artifact_secret_scan_passed=false; fi
  jq -n --argjson passed "$final_artifact_secret_scan_passed" --argjson leaks "$secret_leaks_detected" \
    '{passed:$passed,secretLeaksDetected:$leaks,scope:"final uploaded artifact set",details:"match details intentionally withheld"}' > "$FINAL_SECRET_SCAN_REPORT"
  write_result
  if [[ "$status" != PASS ]]; then
    echo "PB12 smoke failure: $failure_reason" >&2
    echo "::error title=PB12 Docker smoke failure::$failure_reason"
  fi
}
trap record_unexpected_error ERR
trap cleanup EXIT

if ! command -v docker >/dev/null 2>&1 || ! docker version > "$RUN_DIR/docker-version.txt" 2>&1 || ! docker info > "$RUN_DIR/docker-info.txt" 2>&1; then fail "docker daemon unavailable"; exit 1; fi
docker_available=true
if ! docker network create "$NETWORK" > "$RUN_DIR/network-create.txt" 2>&1; then fail "cannot create smoke network"; exit 1; fi
if ! docker build --pull --no-cache -t "$IMAGE" . > "$BUILD_LOG" 2>&1; then fail "docker build failed"; exit 1; fi
image_built=true
image_id="$(docker image inspect -f '{{.Id}}' "$IMAGE")"; image_digest="$(docker image inspect -f '{{if .RepoDigests}}{{index .RepoDigests 0}}{{end}}' "$IMAGE")"
image_size_bytes="$(docker image inspect -f '{{.Size}}' "$IMAGE")"; image_layers="$(docker image inspect -f '{{len .RootFS.Layers}}' "$IMAGE")"
image_architecture="$(docker image inspect -f '{{.Architecture}}' "$IMAGE")"; runtime_user="$(docker image inspect -f '{{.Config.User}}' "$IMAGE")"
docker image inspect "$IMAGE" > "$IMAGE_INSPECT"; docker history --no-trunc "$IMAGE" > "$IMAGE_HISTORY"
if [[ -z "$runtime_user" || "$runtime_user" == root || "$runtime_user" == 0 ]]; then fail "image runtime user is root or unspecified"; exit 1; fi
healthcheck_config="$(docker image inspect -f '{{json .Config.Healthcheck}}' "$IMAGE")"
if [[ "$healthcheck_config" != *curl* ]]; then fail "image healthcheck is missing curl"; exit 1; fi
if ! docker run --rm --entrypoint sh "$IMAGE" -c 'id; pwd; test -f /app/app.jar; find /app -maxdepth 2 -type f -print' > "$IMAGE_FILES" 2>&1; then fail "image filesystem inspection failed"; exit 1; fi
runtime_uid="$(docker run --rm --entrypoint sh "$IMAGE" -c 'id -u')"
if [[ "$runtime_uid" == 0 ]] || grep -Eiq '(^|/)(\.env|.*\.log)$|D:/|C:/|/app/.*(password|secret|token)' "$IMAGE_FILES"; then fail "image static inspection found root or forbidden files"; exit 1; fi

docker run -d --name "$POSTGRES" --network "$NETWORK" -e POSTGRES_DB="$DB_NAME" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  --health-cmd='pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' --health-interval=2s --health-timeout=3s --health-start-period=5s --health-retries=20 postgres:16.4-alpine > "$RUN_DIR/postgres-id.txt"
docker run -d --name "$REDIS" --network "$NETWORK" -e REDIS_PASSWORD="$REDIS_PASSWORD" \
  --health-cmd='redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping | grep -q PONG' --health-interval=2s --health-timeout=3s --health-start-period=5s --health-retries=20 \
  redis:7.2.5-alpine sh -c 'exec redis-server --appendonly no --requirepass "$REDIS_PASSWORD"' > "$RUN_DIR/redis-id.txt"

wait_healthy() {
  local c="$1" i state
  for i in $(seq 1 60); do
    state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}' "$c" 2>/dev/null || true)"
    if [[ "$state" == healthy ]]; then return 0; fi
    if [[ "$state" == unhealthy ]]; then return 1; fi
    sleep 2
  done
  return 1
}
if ! wait_healthy "$POSTGRES" || ! wait_healthy "$REDIS"; then fail "dependency healthcheck did not become healthy"; exit 1; fi

start_backend() {
  local c="$1" port="$2" importer="$3"
  local import_arg=()
  local java_opts=""
  if [[ "$importer" == true ]]; then
    import_arg=(--app.world.import.three-league=true)
    java_opts=-Dapp.world.import.three-league=true
  fi
  docker run -d --name "$c" --network "$NETWORK" -p "$port:8080" --memory=768m --read-only --tmpfs /tmp:rw,noexec,nosuid,size=128m --security-opt=no-new-privileges \
    -e SPRING_PROFILES_ACTIVE=prod -e PORT=8080 -e SERVER_ADDRESS=0.0.0.0 \
    -e DB_HOST="$POSTGRES" -e DB_PORT=5432 -e DB_NAME="$DB_NAME" -e DB_USER="$DB_USER" -e DB_PASSWORD="$DB_PASSWORD" \
    -e REDIS_HOST="$REDIS" -e REDIS_PORT=6379 -e REDIS_USERNAME="$REDIS_USERNAME" -e REDIS_PASSWORD="$REDIS_PASSWORD" -e REDIS_SSL=false \
    -e JWT_SECRET="$JWT_SECRET" -e APP_CORS_ALLOWED_ORIGINS=http://localhost:4200 -e APP_WORLD_IMPORT_THREE_LEAGUE="$importer" -e JAVA_OPTS="$java_opts" "$IMAGE" "${import_arg[@]}" >/dev/null
}
http_code() { curl --silent --show-error --connect-timeout 3 --max-time 10 -o "$2" -w '%{http_code}' "$1" || true; }
wait_ready() {
  local port="$1" i live ready
  for i in $(seq 1 90); do
    live="$(http_code "http://127.0.0.1:$port/api/v1/health/liveness" "$RUN_DIR/liveness-$port.json")"
    ready="$(http_code "http://127.0.0.1:$port/api/v1/health/readiness" "$RUN_DIR/readiness-$port.json")"
    if [[ "$live" == 200 && "$ready" == 200 ]]; then return 0; fi
    sleep 2
  done
  return 1
}

startup_started="$(date +%s%3N)"; start_backend "$BACKEND" "$HOST_PORT" true
if ! wait_ready "$HOST_PORT"; then docker logs "$BACKEND" > "$STARTUP_LOG" 2>&1 || true; fail "backend did not become ready"; exit 1; fi
startup_finished="$(date +%s%3N)"; startup_duration_ms=$((startup_finished - startup_started)); liveness=200; readiness=200
for i in $(seq 1 60); do
  if [[ "$(docker inspect -f '{{.State.Health.Status}}' "$BACKEND" 2>/dev/null || true)" == healthy ]]; then healthcheck_healthy=true; break; fi
  sleep 2
done
if [[ "$healthcheck_healthy" != true ]]; then fail "backend Docker HEALTHCHECK did not become healthy"; exit 1; fi

AUTH_TMP="$RUN_DIR/auth-tmp"; (umask 077 && mkdir -p "$AUTH_TMP"); chmod 700 "$AUTH_TMP"
AUTH_EMAIL="pb12-$RUN_ID@example.invalid"; AUTH_USERNAME="pb12_$RUN_ID"
AUTH_PASSWORD="$(openssl rand -hex 24)"
register_code="$(curl --silent --show-error --connect-timeout 3 --max-time 20 -o "$AUTH_TMP/register.json" -w '%{http_code}' -X POST "http://127.0.0.1:$HOST_PORT/api/v1/auth/register" -H 'Content-Type: application/json' -d "{\"email\":\"$AUTH_EMAIL\",\"username\":\"$AUTH_USERNAME\",\"password\":\"$AUTH_PASSWORD\"}")"
if [[ "$register_code" != 200 ]]; then fail "register returned $register_code"; exit 1; fi
registered=true
login_code="$(curl --silent --show-error --connect-timeout 3 --max-time 20 -o "$AUTH_TMP/token.json" -w '%{http_code}' -X POST "http://127.0.0.1:$HOST_PORT/api/v1/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$AUTH_EMAIL\",\"password\":\"$AUTH_PASSWORD\"}")"
if [[ "$login_code" != 200 ]]; then fail "login returned $login_code"; exit 1; fi
ACCESS_TOKEN="$(jq -r '.accessToken // empty' "$AUTH_TMP/token.json")"; if [[ -z "$ACCESS_TOKEN" ]]; then fail "login response did not contain an access token"; exit 1; fi
login=true
me_code="$(curl --silent --show-error --connect-timeout 3 --max-time 20 -o "$AUTH_TMP/me.json" -w '%{http_code}' "http://127.0.0.1:$HOST_PORT/api/v1/auth/me" -H "Authorization: Bearer $ACCESS_TOKEN")"
if [[ "$me_code" != 200 ]]; then fail "auth me returned $me_code"; exit 1; fi
me=true; USER_ID="$(jq -r '.id // empty' "$AUTH_TMP/me.json")"; if [[ -z "$USER_ID" ]]; then fail "auth me did not contain user id"; exit 1; fi
LEAGUE_ID=""
leagues_code=0
for i in $(seq 1 60); do
  leagues_code="$(curl --silent --show-error --connect-timeout 3 --max-time 30 -o "$AUTH_TMP/leagues.json" -w '%{http_code}' "http://127.0.0.1:$HOST_PORT/api/v1/world/leagues?userId=$USER_ID" -H "Authorization: Bearer $ACCESS_TOKEN")"
  LEAGUE_ID="$(jq -r '.[0].realLeagueId // empty' "$AUTH_TMP/leagues.json" 2>/dev/null || true)"
  if [[ "$leagues_code" == 200 && -n "$LEAGUE_ID" ]]; then break; fi
  sleep 2
done
if [[ "$leagues_code" != 200 || -z "$LEAGUE_ID" ]]; then fail "world league lookup failed (status=$leagues_code)"; exit 1; fi
TEAM_ID=""
teams_code=0
for i in $(seq 1 60); do
  teams_code="$(curl --silent --show-error --connect-timeout 3 --max-time 30 -o "$AUTH_TMP/teams.json" -w '%{http_code}' "http://127.0.0.1:$HOST_PORT/api/v1/world/leagues/$LEAGUE_ID/teams?userId=$USER_ID" -H "Authorization: Bearer $ACCESS_TOKEN")"
  TEAM_ID="$(jq -r '.[0].worldTeamId // empty' "$AUTH_TMP/teams.json" 2>/dev/null || true)"
  if [[ "$teams_code" == 200 && -n "$TEAM_ID" ]]; then break; fi
  sleep 2
done
if [[ "$teams_code" != 200 || -z "$TEAM_ID" ]]; then fail "world team lookup failed (status=$teams_code)"; exit 1; fi
game_code="$(curl --silent --show-error --connect-timeout 3 --max-time 60 -o "$AUTH_TMP/game.json" -w '%{http_code}' -X POST "http://127.0.0.1:$HOST_PORT/api/v1/games" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" -d "{\"leagueId\":\"$LEAGUE_ID\",\"teamId\":\"$TEAM_ID\",\"name\":\"PB12 Docker Smoke\",\"difficulty\":\"NORMAL\",\"gameSpeed\":\"NORMAL\",\"teamsPerDivision\":5}")"
if [[ "$game_code" != 201 ]]; then fail "career creation returned $game_code"; exit 1; fi
career_created=true; flyway_run1="$(docker exec "$POSTGRES" psql -U "$DB_USER" -d "$DB_NAME" -Atc "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true" | tr -d '[:space:]')"
if ! [[ "$flyway_run1" =~ ^[1-9][0-9]*$ ]]; then fail "Flyway run 1 did not produce a positive migration count"; exit 1; fi

stop_started="$(date +%s%3N)"
if ! docker stop --time 30 "$BACKEND" > "$RUN_DIR/docker-stop-run1.txt" 2>&1; then fail "docker stop run 1 failed"; exit 1; fi
docker_stop_used=true; stop_finished="$(date +%s%3N)"; shutdown_duration_ms=$((stop_finished - stop_started)); container_exit_code="$(docker inspect -f '{{.State.ExitCode}}' "$BACKEND" 2>/dev/null || echo 125)"; docker logs "$BACKEND" > "$SHUTDOWN_LOG" 2>&1 || true
first_shutdown_line="$(grep -inm1 'Commencing graceful shutdown' "$SHUTDOWN_LOG" | cut -d: -f1 || true)"
last_shutdown_line="$(grep -inm1 'Graceful shutdown complete' "$SHUTDOWN_LOG" | cut -d: -f1 || true)"
if [[ -n "$first_shutdown_line" && -n "$last_shutdown_line" ]]; then graceful_shutdown_observed=true; if (( first_shutdown_line < last_shutdown_line )); then shutdown_marker_order_valid=true; fi; fi
if [[ "$graceful_shutdown_observed" != true ]]; then fail "graceful shutdown markers were not observed"; exit 1; fi
if [[ "$shutdown_marker_order_valid" != true ]]; then fail "graceful shutdown markers were out of order"; exit 1; fi
if [[ "$container_exit_code" != 0 && "$container_exit_code" != 143 ]]; then fail "graceful shutdown exit code invalid ($container_exit_code)"; exit 1; fi

start_backend "$RESTART_BACKEND" "$RESTART_PORT" false
if ! wait_ready "$RESTART_PORT"; then docker logs "$RESTART_BACKEND" > "$RESTART_LOG" 2>&1 || true; fail "second startup did not become ready"; exit 1; fi
second_startup=true
pid1_command="$(docker exec "$RESTART_BACKEND" sh -c 'tr "\000" " " < /proc/1/cmdline' 2>/dev/null || true)"
if [[ "$pid1_command" == *java* ]]; then java_pid1=true; else fail "Java is not PID 1 inside the container"; exit 1; fi
restart_login_code="$(curl --silent --show-error --connect-timeout 3 --max-time 20 -o "$AUTH_TMP/restart-login.json" -w '%{http_code}' -X POST "http://127.0.0.1:$RESTART_PORT/api/v1/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$AUTH_EMAIL\",\"password\":\"$AUTH_PASSWORD\"}")"
if [[ "$restart_login_code" != 200 ]]; then fail "login after restart returned $restart_login_code"; exit 1; fi
restart_games_code="$(curl --silent --show-error --connect-timeout 3 --max-time 20 -o "$AUTH_TMP/restart-games.json" -w '%{http_code}' "http://127.0.0.1:$RESTART_PORT/api/v1/games" -H "Authorization: Bearer $ACCESS_TOKEN")"; [[ "$restart_games_code" == 200 ]] && career_recovered=true
flyway_run2="$(docker exec "$POSTGRES" psql -U "$DB_USER" -d "$DB_NAME" -Atc "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true" | tr -d '[:space:]')"
if ! [[ "$flyway_run2" =~ ^[1-9][0-9]*$ ]] || [[ "$flyway_run2" != "$flyway_run1" ]]; then fail "Flyway migration count changed across restart"; exit 1; fi

docker stop --time 10 "$REDIS" >/dev/null; sleep 3
redis_down_liveness="$(http_code "http://127.0.0.1:$RESTART_PORT/api/v1/health/liveness" "$RUN_DIR/redis-down-liveness.json")"
redis_down_readiness="$(http_code "http://127.0.0.1:$RESTART_PORT/api/v1/health/readiness" "$RUN_DIR/redis-down-readiness.json")"; printf 'redis_down_liveness=%s redis_down_readiness=%s\n' "$redis_down_liveness" "$redis_down_readiness" >> "$NEGATIVE_LOG"
if [[ "$redis_down_liveness" != 200 || "$redis_down_readiness" != 503 ]]; then fail "Redis-down readiness matrix failed"; exit 1; fi
docker start "$REDIS" >/dev/null; if ! wait_healthy "$REDIS"; then fail "Redis did not recover"; exit 1; fi
docker stop --time 10 "$POSTGRES" >/dev/null; sleep 3
postgres_down_liveness="$(http_code "http://127.0.0.1:$RESTART_PORT/api/v1/health/liveness" "$RUN_DIR/postgres-down-liveness.json")"
postgres_down_readiness="$(http_code "http://127.0.0.1:$RESTART_PORT/api/v1/health/readiness" "$RUN_DIR/postgres-down-readiness.json")"; printf 'postgres_down_liveness=%s postgres_down_readiness=%s\n' "$postgres_down_liveness" "$postgres_down_readiness" >> "$NEGATIVE_LOG"
if [[ "$postgres_down_liveness" != 200 || "$postgres_down_readiness" != 503 ]]; then fail "PostgreSQL-down readiness matrix failed"; exit 1; fi

docker diff "$RESTART_BACKEND" > "$RUN_DIR/runtime.diff" 2>&1 || true
unexpected_filesystem_writes="$( { grep -Ev '^$|^C /tmp|^A /tmp|^D /tmp' "$RUN_DIR/runtime.diff" || true; } | wc -l | tr -d ' ')"
if [[ "$unexpected_filesystem_writes" != 0 ]]; then fail "unexpected runtime filesystem writes detected"; exit 1; fi
if grep -RInE 'D:/|C:/|Authorization: Bearer|JWT_SECRET=|REDIS_PASSWORD=|DB_PASSWORD=' "$RUN_DIR" --exclude='pb12-docker-smoke-result.json' --exclude='cleanup-report.json' --exclude='pb12-final-secret-scan.json' --exclude-dir='auth-tmp' >/dev/null 2>&1; then secret_leaks_detected=1; fail "secret or local-path pattern found in smoke evidence"; exit 1; fi
write_result
exit 0

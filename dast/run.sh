#!/usr/bin/env bash
# Runs the DAST scans (OWASP ZAP and Nuclei) against a throwaway copy of the app.
#
#   dast/run.sh
#
# 1. Builds and starts the stack in compose.dast.yaml (CockroachDB, backend, nginx front end).
# 2. Signs in as a seeded user to get a session, so the scans reach the protected API.
# 3. Runs ZAP (OpenAPI import, spider, active scan) and Nuclei.
# 4. Writes the reports to dast/reports/ and tears the stack down.
#
# Exits non-zero if ZAP finds a High alert or Nuclei a high/critical finding. Medium ZAP
# alerts are printed as warnings. Uses `podman compose`, or `docker compose` in CI (GitHub
# Actions sets CI); set COMPOSE to choose.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -z "${COMPOSE:-}" ]]; then
	if [[ -n "${CI:-}" ]]; then COMPOSE="docker compose"; else COMPOSE="podman compose"; fi
fi
compose() { $COMPOSE -f compose.dast.yaml -p dast "$@"; }

port="${DAST_WEB_PORT:-8088}"
base="http://127.0.0.1:${port}"
user="${DAST_USER:-david}"
password="${DAST_PASSWORD:-david}"
reports=dast/reports

cleanup() {
	echo "==> Stopping the DAST stack"
	compose --profile scan down -v --remove-orphans >/dev/null 2>&1 || true
	rm -f "$jar"
}
jar="$(mktemp)"
trap cleanup EXIT

rm -rf "$reports"
mkdir -p "$reports"
# The scanner containers run as their own users and write here.
chmod a+rwx "$reports"

echo "==> Building and starting the app ($COMPOSE)"
compose up -d --build --wait cockroachdb backend web

echo "==> Signing in as ${user}"
cookie() { awk -v name="$1" '$6 == name { value = $7 } END { print value }' "$jar"; }
curl -sf -c "$jar" -b "$jar" "${base}/api/auth/csrf" >/dev/null
curl -sf -c "$jar" -b "$jar" -H "X-XSRF-TOKEN: $(cookie XSRF-TOKEN)" \
	--data-urlencode "username=${user}" --data-urlencode "password=${password}" \
	"${base}/api/auth/login" >/dev/null
# The CSRF token is replaced at login, so read both cookies after it.
export DAST_SESSION DAST_XSRF
DAST_SESSION="$(cookie JSESSIONID)"
DAST_XSRF="$(cookie XSRF-TOKEN)"
if [[ -z "$DAST_SESSION" || -z "$DAST_XSRF" ]]; then
	echo "Sign-in failed: no session or CSRF cookie" >&2
	exit 1
fi

session_alive() {
	curl -sf -o /dev/null -H "Cookie: JSESSIONID=${DAST_SESSION}" "${base}/api/auth/me"
}

echo "==> Running ZAP"
zap_status=0
compose --profile scan run --rm zap || zap_status=$?

if ! session_alive; then
	echo "WARNING: the session ended during the ZAP scan, so part of it ran unauthenticated" >&2
fi

echo "==> Running Nuclei"
nuclei_status=0
compose --profile scan run --rm nuclei || nuclei_status=$?

echo
echo "==> Results (reports in ${reports}/)"
failed=0

case "$zap_status" in
	0) echo "ZAP: no Medium or High alerts" ;;
	2) echo "ZAP: Medium alerts found (warning), see ${reports}/zap-report.html" ;;
	1) echo "ZAP: High alerts found, see ${reports}/zap-report.html"; failed=1 ;;
	*) echo "ZAP: the scan failed to run (exit ${zap_status})"; failed=1 ;;
esac

if [[ "$nuclei_status" -ne 0 ]]; then
	echo "Nuclei: the scan failed to run (exit ${nuclei_status})"
	failed=1
else
	nuclei_findings="$(grep -c . "${reports}/nuclei.jsonl" 2>/dev/null || true)"
	nuclei_severe="$(grep -cE '"severity":"(high|critical)"' "${reports}/nuclei.jsonl" 2>/dev/null || true)"
	echo "Nuclei: ${nuclei_findings:-0} finding(s), ${nuclei_severe:-0} high or critical"
	if [[ "${nuclei_severe:-0}" -gt 0 ]]; then failed=1; fi
fi

exit "$failed"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BRANCH="${BRANCH:-$(git branch --show-current 2>/dev/null || true)}"
[[ -z "$BRANCH" ]] && BRANCH="main"

REMOTE_URL="$(git remote get-url origin 2>/dev/null || true)"
if [[ -z "$REMOTE_URL" ]]; then
  echo "[ERROR] لا يوجد remote باسم origin داخل هذا المجلد." >&2
  exit 1
fi

REPO_SLUG="${REPO_SLUG:-}"
if [[ -z "$REPO_SLUG" ]]; then
  REPO_SLUG="$(python3 - <<'PY'
import re, subprocess, sys
url = subprocess.check_output(['git', 'remote', 'get-url', 'origin'], text=True).strip()
for pat in [r'github\.com[:/](.+?)(?:\.git)?$', r'github\.com/(.+?)(?:\.git)?$']:
    m = re.search(pat, url)
    if m:
        print(m.group(1))
        sys.exit(0)
print('')
PY
)"
fi

if [[ -z "$REPO_SLUG" ]]; then
  echo "[ERROR] تعذر استخراج owner/repo من remote. اضبط REPO_SLUG يدويًا." >&2
  exit 1
fi

TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"
if [[ -z "$TOKEN" ]]; then
  echo "[ERROR] عيّن GITHUB_TOKEN أو GH_TOKEN أولًا." >&2
  exit 1
fi

for bin in git python3 curl unzip; do
  command -v "$bin" >/dev/null || { echo "[ERROR] $bin غير مثبت." >&2; exit 1; }
done

MSG="${1:-build: sync ScanXfer Pro}"

git add -A
if ! git diff --cached --quiet; then
  git commit -m "$MSG"
fi

git push origin "$BRANCH"

echo "[INFO] بانتظار GitHub Actions ..."
workflow_file="android-apk.yml"
run_id=""
status=""
conclusion=""

for _ in $(seq 1 60); do
  json="$(curl -fsSL -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$REPO_SLUG/actions/workflows/$workflow_file/runs?branch=$BRANCH&per_page=1")"
  read -r run_id status conclusion <<<"$(python3 -c 'import json,sys; obj=json.load(sys.stdin); runs=obj.get("workflow_runs", []); r=runs[0] if runs else {}; print(r.get("id", ""), r.get("status", ""), r.get("conclusion", ""))' <<<"$json")"
  if [[ -n "$run_id" ]]; then
    echo "[INFO] run=$run_id status=${status:-unknown} conclusion=${conclusion:-unknown}"
    [[ "$status" == "completed" ]] && break
  fi
  sleep 10
done

if [[ -z "$run_id" ]]; then
  echo "[ERROR] لم أتمكن من العثور على workflow run." >&2
  exit 1
fi

if [[ "$conclusion" != "success" ]]; then
  echo "[ERROR] البناء لم ينجح. status=$status conclusion=$conclusion" >&2
  exit 1
fi

artifacts_json="$(curl -fsSL -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$REPO_SLUG/actions/runs/$run_id/artifacts?per_page=100")"
artifact_url="$(python3 -c 'import json,sys; obj=json.load(sys.stdin); arts=obj.get("artifacts", []); pref=[a for a in arts if "release" in a.get("name", "").lower()] + [a for a in arts if "debug" in a.get("name", "").lower()]; pref = pref or arts; print(pref[0].get("archive_download_url", "") if pref else "")' <<<"$artifacts_json")"

if [[ -z "$artifact_url" ]]; then
  echo "[ERROR] لم يتم العثور على artifact." >&2
  exit 1
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

curl -fL -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" "$artifact_url" -o "$workdir/artifact.zip"
unzip -q "$workdir/artifact.zip" -d "$workdir/out"

apk_path="$(find "$workdir/out" -type f -name '*.apk' | head -n 1 || true)"
if [[ -z "$apk_path" ]]; then
  echo "[ERROR] لم أجد APK داخل artifact." >&2
  exit 1
fi

DOWNLOADS_DIR="${DOWNLOADS_DIR:-$HOME/storage/downloads}"
mkdir -p "$DOWNLOADS_DIR"
cp -f "$apk_path" "$DOWNLOADS_DIR/ScanXferPro.apk"

echo "[OK] تم تنزيل APK إلى: $DOWNLOADS_DIR/ScanXferPro.apk"

#!/usr/bin/env bash
#
# 백엔드 테스트 실행 (MDP-844 — 한글 경로 우회)
#
# 저장소 경로에 한글이 있으면(C:\강주원\...) Gradle 테스트 워커의 클래스로더가
# 테스트 클래스를 찾지 못해 ClassNotFoundException 으로 단 한 건도 실행되지 않는다.
# 컴파일은 성공하고 .class 파일도 생성되며 javap 로는 로드되는데 워커만 실패한다.
#
# 2026-09-17 검증 — 같은 커밋·같은 명령인데 경로만 다르면 결과가 갈린다:
#   C:\강주원\20.Project\...   → BUILD FAILED, 0개 실행
#   C:\tmp-mdp844\repo         → BUILD SUCCESSFUL, 402개 전부 통과
#
# 심볼릭 링크(정션)로는 해결되지 않는다. Gradle 이 실제 경로로 되돌려 해석한다(실측).
# 그래서 소스를 ASCII 경로로 복사해 거기서 실행한다.
#
# 커밋하지 않은 변경도 그대로 반영된다 (git 이 아니라 파일 동기화라서).
#
# 사용법:
#   bash scripts/run-tests.sh                        # 전체 테스트
#   bash scripts/run-tests.sh --tests "*MailListener*"  # gradle 인자 그대로 전달
#   BULC_TEST_DIR=D:/work/bulc-test bash scripts/run-tests.sh
#
set -uo pipefail

# 작업 디렉터리는 반드시 ASCII 여야 한다 — 이 스크립트의 존재 이유다.
WORK_DIR="${BULC_TEST_DIR:-/c/bulc-test}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_BACKEND="$REPO_ROOT/backend"

if [ ! -d "$SRC_BACKEND" ]; then
  echo "ERROR: backend 디렉터리를 찾을 수 없습니다: $SRC_BACKEND" >&2
  exit 2
fi

# 경로에 비-ASCII 가 있는지 확인해, 우회가 실제로 필요한 상황인지 알린다.
if printf '%s' "$WORK_DIR" | grep -qP '[^\x00-\x7F]' 2>/dev/null; then
  echo "ERROR: 작업 디렉터리에 비-ASCII 문자가 있습니다: $WORK_DIR" >&2
  echo "       한글 경로를 피하는 것이 이 스크립트의 목적입니다." >&2
  exit 2
fi

echo "=========================================="
echo " 백엔드 테스트 (ASCII 경로 우회)"
echo "=========================================="
echo "  원본: $SRC_BACKEND"
echo "  작업: $WORK_DIR/backend"
echo ""

mkdir -p "$WORK_DIR/backend"

# ---- 소스 동기화 ----
# build/ 와 .gradle/ 은 제외한다. 원본(한글 경로)에서 만들어진 산출물을 가져오면
# 절대경로가 섞여 캐시가 어긋난다. 작업 디렉터리에서 새로 빌드하게 둔다.
echo "[1/2] 소스 동기화..."
WIN_SRC=$(cd "$SRC_BACKEND" && pwd -W 2>/dev/null | sed 's|/|\\|g')
WIN_DST=$(cd "$WORK_DIR/backend" && pwd -W 2>/dev/null | sed 's|/|\\|g')

# robocopy 종료 코드: 0~7 = 정상(파일 복사/스킵 등), 8 이상 = 실패
robocopy "$WIN_SRC" "$WIN_DST" //MIR //XD build .gradle //NFL //NDL //NJH //NJS //NP > /dev/null 2>&1
RC=$?
if [ "$RC" -ge 8 ]; then
  echo "ERROR: robocopy 실패 (exit $RC)" >&2
  exit 1
fi

# gradle-wrapper.jar 은 .gitignore 의 *.jar 에 걸려 저장소에 없다.
# 원본 작업 트리에는 있으므로 //MIR 로 복사되지만, 없으면 gradlew 가 바로 실패하므로 확인한다.
if [ ! -f "$WORK_DIR/backend/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "ERROR: gradle-wrapper.jar 이 없습니다. 원본에도 있는지 확인하세요." >&2
  exit 1
fi
echo "  OK"

# ---- 테스트 실행 ----
echo ""
echo "[2/2] 테스트 실행..."
cd "$WORK_DIR/backend" || exit 1
./gradlew test "$@"
TEST_RC=$?

# ---- 결과 요약 ----
echo ""
if command -v python >/dev/null 2>&1; then
  python - "$WORK_DIR/backend" <<'PYEOF'
import glob, io, os, re, sys
base = sys.argv[1]
files = glob.glob(os.path.join(base, 'build/test-results/test/*.xml'))
t = f = e = s = 0
failed = []
for p in files:
    x = io.open(p, encoding='utf-8', errors='replace').read()
    for key, add in (('tests', 't'), ('failures', 'f'), ('errors', 'e'), ('skipped', 's')):
        m = re.search(r'%s="(\d+)"' % key, x)
        if m:
            v = int(m.group(1))
            if add == 't': t += v
            elif add == 'f': f += v
            elif add == 'e': e += v
            else: s += v
    for m in re.finditer(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*>\s*<(?:failure|error)', x):
        failed.append('%s.%s' % (m.group(2), m.group(1)))

if not files:
    print('  (테스트 결과 없음)')
else:
    print('  총 %d개 · 실패 %d · 오류 %d · 건너뜀 %d' % (t, f, e, s))
    for name in failed[:15]:
        print('    FAIL  %s' % name)
    if len(failed) > 15:
        print('    ... 외 %d건' % (len(failed) - 15))
PYEOF
fi

echo ""
if [ "$TEST_RC" -eq 0 ]; then
  echo "=========================================="
  echo " 테스트 통과"
  echo "=========================================="
else
  echo "=========================================="
  echo " 테스트 실패 — 리포트:"
  echo "   $WORK_DIR/backend/build/reports/tests/test/index.html"
  echo "=========================================="
fi
exit $TEST_RC

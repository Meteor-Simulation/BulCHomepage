#!/usr/bin/env node
/**
 * 토스페이먼츠 클라이언트 키 구성 점검 (MDP-937).
 *
 * Vite 는 `import.meta.env.VITE_*` 를 **빌드 시점에 코드로 박는다**. 그래서 키를 바꾸려면
 * 환경변수만 고치는 것으로는 안 되고 반드시 재빌드해야 한다. 거꾸로, 환경변수를 깜빡하면
 * 코드 기본값(테스트 키)이 그대로 배포되는데 화면은 멀쩡해 보인다 — 결제창도 열리고 승인도
 * 성공하지만 대금이 들어오지 않는다.
 *
 * 이 스크립트는 그 상태를 빌드 로그에 드러낸다.
 *
 * ## 점검 대상은 gck 다
 *
 * 프론트는 결제위젯(주문서형·결제창형)을 쓴다. 그 세트의 클라이언트 키는 `gck` 로 시작한다.
 * API 개별 연동 키(`ck`)를 넣으면 위젯이 뜨지 않고, 계좌이체·가상계좌·간편결제가 통째로
 * 막힌다 — 상점관리자의 "결제 UI 설정" 이 gck 세트에만 적용되기 때문이다. 세트를 잘못 넣는
 * 것이 가장 흔한 사고라 형식까지 본다.
 *
 * ## 왜 빌드를 실패시키지 않는가
 *
 * 테스트 키로 빌드해야 할 때가 있다(로컬 확인, 심사 대기). 여기서 빌드를 막으면 결제와
 * 무관한 프론트 수정조차 배포할 수 없게 된다. 그래서 기본은 경고다.
 *
 * 실키 전환이 끝나면 `REQUIRE_LIVE_KEY=1` 로 빌드해 되돌림을 막을 수 있다. Cloudflare Pages
 * 환경변수에 넣어 두면 이후 모든 배포가 테스트 키를 거부한다.
 */

const KEY = process.env.VITE_TOSS_WIDGET_CLIENT_KEY?.trim() ?? '';
const REQUIRE_LIVE = process.env.REQUIRE_LIVE_KEY === '1';

const line = '─'.repeat(72);
const prefix = KEY.startsWith('live_') ? 'live_' : KEY.startsWith('test_') ? 'test_' : null;
/** 위젯 세트인지 — live_gck_ / test_gck_ 여야 한다. ck 를 넣는 사고를 여기서 잡는다. */
const isWidgetKey = /^(live|test)_gck_/.test(KEY);

/** 키 값은 절대 출력하지 않는다 — 빌드 로그는 보존되고 공유된다. */
function describe() {
  if (!KEY) return '미설정 (코드 기본값 사용 → 테스트 키)';
  if (!isWidgetKey) return `${prefix ?? '알 수 없는'}*** — 결제위젯 키(gck)가 아니다`;
  if (prefix === 'live_') return 'live_gck_*** (실결제)';
  return 'test_gck_*** (테스트)';
}

console.log(`\n토스 결제위젯 클라이언트 키 점검: ${describe()}`);

// 세트가 틀리면 결제수단이 통째로 안 뜬다. 값이 들어 있는데 형식이 다르면 항상 막는다.
if (KEY && !isWidgetKey) {
  console.error(line);
  console.error('VITE_TOSS_WIDGET_CLIENT_KEY 가 결제위젯 키가 아닙니다.');
  console.error('토스 대시보드 > 내 개발정보 > 주문서형·결제창형 연동 키 의 클라이언트 키를 넣으세요.');
  console.error('live_gck_ / test_gck_ 로 시작해야 합니다.');
  console.error('(API 개별 연동 키 live_ck_ 는 자동결제용이고, 서버에서만 씁니다)');
  console.error(line);
  process.exit(1);
}

const isLive = prefix === 'live_';

if (REQUIRE_LIVE && !isLive) {
  console.error(line);
  console.error('REQUIRE_LIVE_KEY=1 인데 라이브 키가 아닙니다 — 빌드를 중단합니다.');
  console.error(`현재: ${describe()}`);
  console.error('Cloudflare Pages 환경변수 VITE_TOSS_WIDGET_CLIENT_KEY 를 live_gck_ 키로 설정하세요.');
  console.error(line);
  process.exit(1);
}

if (!isLive) {
  console.warn(line);
  console.warn('  ⚠  테스트 키로 빌드합니다 — 실제 결제·정산이 일어나지 않습니다.');
  if (!KEY) {
    console.warn('     VITE_TOSS_WIDGET_CLIENT_KEY 가 설정되지 않아 코드 기본값으로 떨어졌습니다.');
    console.warn('     Cloudflare Pages > Settings > Environment variables 를 확인하세요.');
  }
  console.warn('     실키 전환 후에는 REQUIRE_LIVE_KEY=1 을 설정해 되돌림을 막으세요 (MDP-547).');
  console.warn(line);
} else {
  console.log('  OK — 라이브 키로 빌드합니다.\n');
}

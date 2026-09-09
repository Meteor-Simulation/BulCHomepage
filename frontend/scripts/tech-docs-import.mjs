#!/usr/bin/env node
/**
 * 외부에서 받은 기술문서 HTML을 public/tech-docs/ 로 반입하면서 CSP 호환으로 변환한다. (MDP-839)
 *
 * 운영 CSP 의 script-src 에는 'unsafe-inline' 이 없어(frontend/nginx.conf) 인라인 <script> 가
 * 전부 차단된다. style-src 에는 'unsafe-inline' 이 있어 레이아웃은 멀쩡해 보이므로 육안으로
 * 안 잡힌다 — MDP-768 로 추가한 문서 2건이 한 달 넘게 차트·애니메이션 미동작이었다.
 *
 * 인라인 <script> 를 외부 .js 로 빼면 'self' 로 통과한다. CSP 를 완화하지 않는다.
 *
 * 사용법:
 *   node scripts/tech-docs-import.mjs <원본.html> <출력이름>
 *   node scripts/tech-docs-import.mjs "C:/.../보고서.html" technical-doc-report
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { basename, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = join(__dirname, '..', 'public', 'tech-docs');

/**
 * 여러 문서가 같은 라이브러리 번들을 각자 인라인으로 들고 있는 경우가 많다.
 * 시그니처가 맞으면 공용 파일 하나로 모아 중복을 없앤다.
 */
const SHARED_LIBS = [
  { test: /Chart\.js v[\d.]+/, file: 'chart.umd.min.js', label: 'Chart.js' },
  { test: /\bKaTeX\b.*\bversion\b/i, file: 'katex.min.js', label: 'KaTeX' },
];

/** src 속성이 없는 <script> 만 매칭한다 (이미 외부 파일이면 건드릴 필요 없음). */
const INLINE_SCRIPT = /<script(?![^>]*\bsrc=)([^>]*)>([\s\S]*?)<\/script>/gi;

/**
 * 인라인 이벤트 핸들러도 CSP 에 걸린다. 스크립트만 빼내면 버튼·슬라이더가 죽는다.
 *
 * 다행히 이 문서들의 핸들러는 규칙적이다.
 *   <button id="play_t22" onclick="togglePlay('t22')">
 *   <input  id="sl_t22"   oninput="setFrame('t22', this.value)">
 * id 에 키가 이미 들어 있으므로 속성을 제거하고 id 기준으로 addEventListener 를 붙인다.
 */
const INLINE_HANDLER = /\son(click|input|change)\s*=\s*"([^"]*)"/gi;

/** 제거한 핸들러를 대신할 바인딩 코드. id 접두사 → 이벤트/호출 규칙. */
const HANDLER_BINDER = `
/* MDP-839: 인라인 onclick/oninput 을 CSP 호환으로 대체. 원본 동작과 동일하게 바인딩한다. */
(function () {
  function bind() {
    document.querySelectorAll('[id^="play_"]').forEach(function (el) {
      var key = el.id.slice(5);
      el.addEventListener('click', function () { togglePlay(key); });
    });
    document.querySelectorAll('[id^="sl_"]').forEach(function (el) {
      var key = el.id.slice(3);
      el.addEventListener('input', function () { setFrame(key, el.value); });
    });
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bind);
  } else {
    bind();
  }
})();
`;

function main() {
  const [srcPath, outName] = process.argv.slice(2);
  if (!srcPath || !outName) {
    console.error('사용법: node scripts/tech-docs-import.mjs <원본.html> <출력이름>');
    process.exit(2);
  }
  if (!existsSync(srcPath)) {
    console.error(`원본을 찾을 수 없습니다: ${srcPath}`);
    process.exit(2);
  }
  mkdirSync(OUT_DIR, { recursive: true });

  const raw = readFileSync(srcPath, 'utf8');
  const written = [];
  let inlineCount = 0;

  // 1) 인라인 이벤트 핸들러 제거. 대체 바인딩으로 재현 가능한 형태만 건드린다.
  //    모르는 핸들러는 남겨서 check-csp.mjs 가 잡도록 한다 (조용히 깨뜨리지 않기 위함).
  let handlersStripped = 0;
  let unknownHandlers = 0;
  let boundHere = null;
  const html = raw.replace(INLINE_HANDLER, (full, evt, code) => {
    const known =
      /^\s*togglePlay\(\s*'[^']+'\s*\)\s*;?\s*$/.test(code) ||
      /^\s*setFrame\(\s*'[^']+'\s*,\s*this\.value\s*\)\s*;?\s*$/.test(code);
    if (!known) {
      unknownHandlers += 1;
      console.warn(`  ! 알 수 없는 인라인 핸들러라 그대로 둡니다: on${evt}="${code.slice(0, 50)}"`);
      return full;
    }
    handlersStripped += 1;
    return '';
  });

  const converted = html.replace(INLINE_SCRIPT, (full, attrs, body) => {
    // 빈 블록은 그대로 둔다 (제거하면 문서 구조가 바뀔 수 있다)
    if (!body.trim()) return full;

    inlineCount += 1;
    const shared = SHARED_LIBS.find((lib) => lib.test.test(body));
    const fileName = shared ? shared.file : `${outName}.inline${inlineCount}.js`;
    const target = join(OUT_DIR, fileName);

    // togglePlay/setFrame 을 정의하는 블록 뒤에 대체 바인딩을 붙인다.
    // 정의보다 먼저 실행되면 안 되므로 같은 파일 끝에 두는 게 안전하다.
    const definesHandlers = /function\s+(togglePlay|setFrame)\b/.test(body);
    if (handlersStripped && definesHandlers) {
      body += HANDLER_BINDER;
      boundHere = fileName;
    }

    if (shared && existsSync(target)) {
      // 이미 같은 라이브러리가 반입돼 있다. 내용이 다르면 버전 충돌이므로 알린다.
      const existing = readFileSync(target, 'utf8');
      if (md5(existing) !== md5(body)) {
        console.warn(
          `  ! ${fileName} 이 이미 있는데 내용이 다릅니다 (${shared.label} 버전 충돌 가능). 덮어쓰지 않았습니다.`
        );
      } else {
        console.log(`  = ${fileName} (${shared.label}, 기존과 동일 — 재사용)`);
      }
    } else {
      writeFileSync(target, body, 'utf8');
      written.push({ fileName, size: Buffer.byteLength(body, 'utf8'), shared: !!shared });
      console.log(
        `  + ${fileName} (${fmt(Buffer.byteLength(body, 'utf8'))})${shared ? ` — ${shared.label} 공용` : ''}`
      );
    }

    const keptAttrs = attrs.trim() ? ` ${attrs.trim()}` : '';
    return `<script${keptAttrs} src="${fileName}"></script>`;
  });

  const outHtml = join(OUT_DIR, `${outName}.html`);
  writeFileSync(outHtml, converted, 'utf8');

  const before = Buffer.byteLength(raw, 'utf8');
  const after = Buffer.byteLength(converted, 'utf8');
  console.log(`  → ${basename(outHtml)} (${fmt(before)} → ${fmt(after)})`);
  console.log(`  인라인 스크립트 ${inlineCount}개 추출, 파일 ${written.length}개 생성`);

  if (handlersStripped) {
    if (boundHere) {
      console.log(`  인라인 핸들러 ${handlersStripped}개 제거 → ${boundHere} 에 addEventListener 바인딩 삽입`);
    } else {
      // 핸들러는 지웠는데 붙일 곳을 못 찾았다 = 버튼이 죽는다. 조용히 넘어가면 안 된다.
      console.error(
        `  ! 인라인 핸들러 ${handlersStripped}개를 제거했으나 togglePlay/setFrame 정의부를 찾지 못했습니다.\n` +
          `    바인딩이 삽입되지 않아 버튼·슬라이더가 동작하지 않습니다. 확인이 필요합니다.`
      );
      process.exitCode = 1;
    }
  }
  if (unknownHandlers) {
    console.warn(`  ! 처리하지 못한 인라인 핸들러 ${unknownHandlers}개 — check-csp.mjs 가 검출합니다.`);
  }
}

const md5 = (s) => createHash('md5').update(s, 'utf8').digest('hex');
const fmt = (n) => (n > 1048576 ? `${(n / 1048576).toFixed(1)}MB` : `${(n / 1024).toFixed(0)}KB`);

main();

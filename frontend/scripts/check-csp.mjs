#!/usr/bin/env node
/**
 * public/tech-docs/ 의 HTML 이 운영 CSP 를 위반하는지 검사한다. (MDP-839)
 *
 * 변환 스크립트(tech-docs-import.mjs)가 자동으로 고칠 수 있는 건 인라인 <script> 뿐이다.
 * CDN 참조·인라인 이벤트 핸들러·외부 iframe 은 사람이 판단해야 하므로, 최소한 배포 전에
 * 걸리도록 여기서 검출해 빌드를 실패시킨다.
 *
 * 운영 CSP (frontend/nginx.conf):
 *   script-src  'self' https://js.tosspayments.com     ← 'unsafe-inline' 없음
 *   style-src   'self' 'unsafe-inline' ...             ← 인라인 스타일은 허용
 *   img-src     'self' data: https:                    ← 이미지는 허용
 *   connect-src 'self' https://api.msimul.com https://*.tosspayments.com
 *   frame-src   https://*.tosspayments.com
 */

import { readdirSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DOC_DIR = join(__dirname, '..', 'public', 'tech-docs');

/** script-src 에서 허용된 출처. 여기 없는 외부 스크립트는 차단된다. */
const ALLOWED_SCRIPT_HOSTS = ['js.tosspayments.com'];
/** frame-src 에서 허용된 출처. */
const ALLOWED_FRAME_HOSTS = ['tosspayments.com'];

const RULES = [
  {
    id: 'inline-script',
    label: '인라인 <script> (script-src 에 unsafe-inline 없음)',
    hint: 'node scripts/tech-docs-import.mjs 로 외부 .js 추출',
    find: (html) => count(html, /<script(?![^>]*\bsrc=)[^>]*>[\s\S]*?<\/script>/gi, (m) => m.replace(/\s+/g, ' ').slice(0, 60)),
  },
  {
    id: 'external-script',
    label: '허용되지 않은 외부 스크립트',
    hint: '자체 호스팅으로 전환 (tech-docs/ 에 파일을 두고 상대경로 참조)',
    find: (html) =>
      count(html, /<script[^>]*\bsrc=["'](https?:\/\/[^"']+)["'][^>]*>/gi, (m) => m, (m) => {
        const url = /src=["'](https?:\/\/[^"']+)["']/i.exec(m)?.[1] ?? '';
        return !ALLOWED_SCRIPT_HOSTS.some((h) => url.includes(h));
      }),
  },
  {
    id: 'inline-handler',
    label: '인라인 이벤트 핸들러 (onclick 등)',
    hint: 'addEventListener 로 옮기기 — 자동 변환 불가',
    find: (html) => count(html, /<[^>]+\son(?:click|load|error|change|submit|input|mouseover)\s*=/gi, (m) => m.slice(0, 60)),
  },
  {
    id: 'external-frame',
    label: '허용되지 않은 외부 iframe (YouTube 등)',
    hint: 'frame-src 는 토스만 허용 — 링크로 대체하거나 CSP 검토 필요',
    find: (html) =>
      count(html, /<iframe[^>]*\bsrc=["'](https?:\/\/[^"']+)["'][^>]*>/gi, (m) => m.slice(0, 80), (m) => {
        const url = /src=["'](https?:\/\/[^"']+)["']/i.exec(m)?.[1] ?? '';
        return !ALLOWED_FRAME_HOSTS.some((h) => url.includes(h));
      }),
  },
  {
    id: 'eval',
    label: 'eval() / new Function() (unsafe-eval 없음)',
    hint: '해당 라이브러리를 교체하거나 사용 부분 제거',
    find: (html) => count(html, /\b(?:eval\s*\(|new\s+Function\s*\()/g, (m) => m.trim()),
  },
];

function count(html, re, fmt, filter) {
  const hits = [];
  for (const m of html.matchAll(re)) {
    if (filter && !filter(m[0])) continue;
    hits.push(fmt(m[0]));
  }
  return hits;
}

function main() {
  let files;
  try {
    files = readdirSync(DOC_DIR).filter((f) => f.endsWith('.html'));
  } catch {
    console.log('tech-docs 디렉토리가 없습니다 — 검사 건너뜀');
    return;
  }

  let violations = 0;
  for (const file of files) {
    const html = readFileSync(join(DOC_DIR, file), 'utf8');
    const found = RULES.map((r) => ({ rule: r, hits: r.find(html) })).filter((x) => x.hits.length);

    if (!found.length) {
      console.log(`  OK  ${file}`);
      continue;
    }
    console.log(`  !!  ${file}`);
    for (const { rule, hits } of found) {
      violations += hits.length;
      console.log(`        ${rule.label} — ${hits.length}건`);
      console.log(`          해결: ${rule.hint}`);
      for (const h of hits.slice(0, 3)) console.log(`          · ${h}`);
      if (hits.length > 3) console.log(`          · ... 외 ${hits.length - 3}건`);
    }
  }

  if (violations) {
    console.error(`\nCSP 위반 ${violations}건. 배포하면 해당 기능이 조용히 동작하지 않습니다.`);
    process.exit(1);
  }
  console.log(`\ntech-docs CSP 검사 통과 (${files.length}개 문서)`);
}

main();

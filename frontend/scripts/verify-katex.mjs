#!/usr/bin/env node
/**
 * tech-docs 문서의 수식을 KaTeX 로 실제 렌더링해 깨지는 것을 찾는다. (MDP-874)
 *
 * CSP 검사(check-csp.mjs)는 "스크립트가 로드되는가"만 본다. CDN MathJax 를 자체호스팅
 * KaTeX 로 치환하면 CSP 는 통과하지만, 두 엔진의 지원 문법이 달라 일부 수식이 깨질 수 있다.
 * 브라우저를 띄우지 않고 그 차이를 잡기 위한 검증이다.
 *
 * katex-init.js 와 같은 설정(throwOnError: false)이라 렌더 실패가 페이지를 멈추지는 않는다.
 * 실패한 수식만 원문이 빨간색으로 보이므로, 배포 전에 목록을 확인하는 것이 목적이다.
 *
 * 사용법:
 *   node scripts/verify-katex.mjs                       # tech-docs 전체
 *   node scripts/verify-katex.mjs public/tech-docs/a.html
 */

import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import katex from 'katex';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DOC_DIR = join(__dirname, '..', 'public', 'tech-docs');

/** katex-init.js 의 delimiters 와 같은 구분자. */
const PATTERNS = [
  { re: /\$\$([\s\S]+?)\$\$/g, display: true, label: '$$' },
  { re: /\\\[([\s\S]+?)\\\]/g, display: true, label: '\\[' },
];

function extract(html) {
  // <script> · <style> · <code> · <pre> 안의 $ 는 수식이 아니다
  const body = html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<pre[\s\S]*?<\/pre>/gi, '')
    .replace(/<code[\s\S]*?<\/code>/gi, '');

  const found = [];
  for (const { re, display, label } of PATTERNS) {
    for (const m of body.matchAll(re)) {
      found.push({ tex: m[1].trim(), display, label });
    }
  }
  return found;
}

function verify(file) {
  const html = readFileSync(file, 'utf8');
  const formulas = extract(html);
  const failures = [];

  for (const f of formulas) {
    try {
      katex.renderToString(f.tex, { displayMode: f.display, throwOnError: true });
    } catch (e) {
      failures.push({ tex: f.tex, reason: String(e.message).replace(/\s+/g, ' ').slice(0, 110) });
    }
  }

  const name = basename(file);
  if (!formulas.length) {
    console.log(`  --  ${name} (수식 없음)`);
    return 0;
  }
  if (!failures.length) {
    console.log(`  OK  ${name} — 수식 ${formulas.length}개 전부 렌더 성공`);
    return 0;
  }

  console.log(`  !!  ${name} — 수식 ${formulas.length}개 중 ${failures.length}개 렌더 실패`);
  for (const f of failures.slice(0, 10)) {
    console.log(`        · ${f.tex.slice(0, 70)}`);
    console.log(`          ${f.reason}`);
  }
  if (failures.length > 10) console.log(`        ... 외 ${failures.length - 10}건`);
  return failures.length;
}

const args = process.argv.slice(2);
const targets = args.length
  ? args
  : readdirSync(DOC_DIR)
      .filter((f) => f.endsWith('.html'))
      .sort()
      .map((f) => join(DOC_DIR, f));

let total = 0;
for (const t of targets) total += verify(t);

console.log('');
if (total) {
  console.log(`KaTeX 렌더 실패 ${total}건. 해당 수식은 빨간 원문으로 표시됩니다.`);
  process.exit(1);
}
console.log(`KaTeX 렌더 검증 통과 (문서 ${targets.length}개)`);

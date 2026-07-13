/* KaTeX auto-render 초기화 — 인라인 MathJax를 대체 (CSP 'self' 통과용 외부 스크립트).
   본문의 $$...$$ (디스플레이) 및 $...$ (인라인) 수식을 KaTeX로 렌더한다.
   게시판(board)과 동일한 KaTeX 엔진 사용. */
(function () {
  function render() {
    if (!window.renderMathInElement) return;
    window.renderMathInElement(document.body, {
      delimiters: [
        { left: '$$', right: '$$', display: true },
        { left: '$', right: '$', display: false },
        { left: '\\[', right: '\\]', display: true },
        { left: '\\(', right: '\\)', display: false }
      ],
      // \tag 등 KaTeX 미지원 명령이 있어도 페이지가 멈추지 않도록 오류는 건너뜀
      throwOnError: false,
      // 렌더 실패 시 해당 수식만 빨간색 원문 표시(디버깅용), 페이지는 정상
      errorColor: '#cc0000'
    });
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', render);
  } else {
    render();
  }
})();

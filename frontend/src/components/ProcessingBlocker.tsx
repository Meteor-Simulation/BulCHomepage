import React from 'react';
import './ProcessingBlocker.css';

/**
 * 처리 중 화면을 덮어 클릭을 막는다.
 *
 * <p>왜 버튼 disabled 만으로는 부족한가 — PG 호출은 수 초가 걸린다(토스 승인 실측 7~9초).
 * 그 사이 화면이 그대로면 사용자는 "안 눌렸다" 고 판단해 다시 누르거나 창을 닫으려 한다.
 * 실제로 결제가 두 번 승인돼 100원이 두 번 청구된 일이 두 차례 있었고(2026-10-07·10-12),
 * 두 번 모두 버튼 disabled 와 상태 기반 가드가 이미 들어가 있었다.
 *
 * <p>그래서 막아야 할 것은 버튼이 아니라 <b>화면</b>이다. 반투명·blur 로 아래가 선명히 보이지
 * 않게 하는 것도 의도다 — 비쳐 보이면 아직 조작 가능한 것처럼 느껴진다.
 *
 * <p><b>이것은 보조 수단이다.</b> 최종 방어선은 서버다(결제는 멱등키 + DB 유니크, 환불 요청은
 * 결제당 대기 1건 제약). 화면은 사용자가 다시 누를 이유를 없애는 역할만 한다.
 */
interface ProcessingBlockerProps {
  /** false 면 아무것도 렌더하지 않는다 */
  isActive: boolean;
  /** 무엇을 하고 있는지 — "환불을 처리하고 있습니다" 처럼 진행형으로 적는다 */
  title: string;
  /** 얼마나 걸리는지·무엇을 하지 말아야 하는지. 개행(\n)을 쓸 수 있다 */
  description?: string;
  /**
   * true 면 화면 전체를 덮는다(모달이 없는 흐름 — 목록에서 바로 처리하는 경우).
   * false(기본)면 부모를 덮으므로 <b>부모에 position: relative 가 있어야 한다.</b>
   */
  fullscreen?: boolean;
}

const ProcessingBlocker: React.FC<ProcessingBlockerProps> = ({
  isActive, title, description, fullscreen = false,
}) => {
  if (!isActive) return null;

  return (
    <div
      className={`processing-blocker${fullscreen ? ' processing-blocker--fullscreen' : ''}`}
      // 스크린리더에 진행 상황을 알린다. aria-busy 로 "지금 조작할 수 없음" 을 전달한다.
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <div className="processing-blocker-spinner" aria-hidden="true" />
      <p className="processing-blocker-title">{title}</p>
      {description && <small className="processing-blocker-desc">{description}</small>}
    </div>
  );
};

export default ProcessingBlocker;

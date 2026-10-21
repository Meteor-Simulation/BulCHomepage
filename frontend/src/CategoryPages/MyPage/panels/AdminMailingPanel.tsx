import React, { useRef, useState } from 'react';
import { getApiBaseUrl } from '../../../utils/api';
import { useAlert } from '../../../components/AlertProvider';
import './AdminMailingPanel.css';

/**
 * 메일 발송 — 백엔드 mail 모듈 대응 화면 (MDP-908).
 *
 * 컨택 자체의 등록·수정·수신거부 관리는 '컨택 관리'(AdminContactsPanel)로 떼어냈다.
 * 이 화면은 "누구에게 무엇을 보낼지"만 다룬다 — 대상 선정 조건(수신 동의 여부 등)은
 * 서버가 판단하고, 여기서는 회원/컨택 포함 여부만 고른다.
 *
 * <p>모달이 아니라 페이지에 폼을 그대로 둔다. 전용 화면이 된 이상 버튼 하나만 놓고
 * 모달을 띄우는 것은 한 단계를 괜히 늘리는 셈이다.
 */

const API = getApiBaseUrl();

// 발송 분류(email_log) 키 — 백엔드 ALLOWED_TEMPLATE_KEYS 와 일치해야 함
const SEND_TEMPLATE_KEYS = [
  { value: 'program_update', label: '프로그램 업데이트 안내' },
  { value: 'terms_change', label: '약관 변경 안내' },
  { value: 'security_notice', label: '보안 공지' },
  // 전시회·이벤트 참가자에게 체험 라이선스 코드를 보낼 때.
  // 본인이 요청한 코드를 이행하는 발송이므로 안내성으로 보낸다 (아래 안내 문구 참고).
  { value: 'redeem_code', label: '체험 라이선스 코드 안내' },
];

/**
 * 본문에 쓸 수 있는 개인화 항목.
 *
 * 치환은 서버(MailPersonalizer)가 한다. 여기 목록은 화면 안내용이고, 둘이 어긋나면
 * 관리자가 쓴 자리표시자가 메일에 그대로 나간다 — 추가할 때 양쪽을 같이 고쳐야 한다.
 *
 * `ready: false` 는 아직 서버가 치환하지 못하는 항목이다. 목록에서 빼지 않고 남겨 두는
 * 이유는, 없으면 "쿠폰은 어떻게 넣나" 를 매번 다시 묻게 되기 때문이다. 대신 쓰면 경고한다.
 */
const MAIL_PLACEHOLDERS: Array<{
  token: string;
  label: string;
  fallback: string;
  ready: boolean;
  note?: string;
}> = [
  { token: '{name}', label: '수신자 이름', fallback: '고객', ready: true },
  { token: '{company}', label: '소속', fallback: '빈칸', ready: true },
  {
    token: '{coupon}',
    label: '체험 라이선스 코드',
    fallback: '-',
    ready: false,
    note: '1인당 1개씩 발급해 넣는 방식이라 코드 발급 연동이 필요합니다.',
  },
];

interface SendForm {
  mailType: 'operational' | 'promotional';
  templateKey: string;
  subject: string;
  title: string;
  contentHtml: string;
  includeMembers: boolean;
  includeContacts: boolean;
}

const emptySendForm = (): SendForm => ({
  mailType: 'operational',
  templateKey: 'program_update',
  subject: '',
  title: '',
  contentHtml: '',
  includeMembers: false,
  includeContacts: true,
});

const AdminMailingPanel: React.FC = () => {
  const { showAlert } = useAlert();

  const [sendForm, setSendForm] = useState<SendForm>(emptySendForm());
  const contentRef = useRef<HTMLTextAreaElement | null>(null);
  const [isSending, setIsSending] = useState(false);
  const [sendResult, setSendResult] = useState<{ targetCount: number; sentCount: number; failedCount: number } | null>(null);

  const resetForm = () => {
    setSendForm(emptySendForm());
    setSendResult(null);
  };

  const handleSend = async () => {
    if (!sendForm.subject.trim() || !sendForm.title.trim() || !sendForm.contentHtml.trim()) {
      showAlert({ message: '메일 제목 · 본문 제목 · 본문 내용을 모두 입력하세요', type: 'error' });
      return;
    }
    if (!sendForm.includeMembers && !sendForm.includeContacts) {
      showAlert({ message: '발송 대상을 한 가지 이상 선택하세요', type: 'error' });
      return;
    }
    const isPromo = sendForm.mailType === 'promotional';
    const targetLabel = [
      sendForm.includeMembers && '홈페이지 회원',
      sendForm.includeContacts && '직접 등록 컨택',
    ].filter(Boolean).join(', ');
    const typeLabel = isPromo ? '광고성' : '안내성';
    const promoNote = isPromo ? '\n수신 동의자에게만 발송되며 제목에 "(광고)"가 자동 표기됩니다.' : '';
    if (!window.confirm(`${typeLabel} 메일을 발송하시겠습니까?\n발송 대상: ${targetLabel}${promoNote}`)) return;

    setIsSending(true);
    setSendResult(null);
    try {
      const res = await fetch(`${API}/api/admin/mails/operational`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          mailType: sendForm.mailType,
          templateKey: sendForm.templateKey,
          subject: sendForm.subject.trim(),
          title: sendForm.title.trim(),
          contentHtml: sendForm.contentHtml,
          includeMembers: sendForm.includeMembers,
          includeContacts: sendForm.includeContacts,
        }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: '발송 실패' }));
        showAlert({ message: err.error || err.message || '발송 실패', type: 'error' });
        return;
      }
      const data = await res.json();
      setSendResult({ targetCount: data.targetCount, sentCount: data.sentCount, failedCount: data.failedCount });
      showAlert({
        message: `발송 완료 — 대상 ${data.targetCount} · 성공 ${data.sentCount} · 실패 ${data.failedCount}`,
        type: 'success',
      });
    } catch {
      showAlert({ message: '발송 중 오류가 발생했습니다', type: 'error' });
    } finally {
      setIsSending(false);
    }
  };

  /**
   * 자리표시자를 본문의 커서 위치에 끼워 넣는다.
   *
   * 끝에 덧붙이면 문장 중간에 넣고 싶을 때 다시 잘라 옮겨야 한다. 삽입 후 커서를
   * 넣은 값 뒤로 옮겨 두어 이어서 타이핑할 수 있게 한다.
   */
  const insertPlaceholder = (token: string) => {
    const el = contentRef.current;
    const text = sendForm.contentHtml;
    const at = el ? el.selectionStart : text.length;
    const next = text.slice(0, at) + token + text.slice(el ? el.selectionEnd : text.length);
    setSendForm({ ...sendForm, contentHtml: next });
    // 상태 반영 뒤에 커서를 옮긴다 — 같은 틱에 하면 React 가 값을 덮어쓴다.
    requestAnimationFrame(() => {
      if (!el) return;
      el.focus();
      el.setSelectionRange(at + token.length, at + token.length);
    });
  };

  // 아직 치환되지 않는 항목이 본문에 들어가 있으면 그대로 발송된다.
  // 보낸 뒤에는 되돌릴 수 없으므로 발송 전에 알린다.
  const unreadyInBody = MAIL_PLACEHOLDERS.filter(
    p => !p.ready && sendForm.contentHtml.includes(p.token)
  );

  return (
    <div className="info-card admin-section-card wide">
      <div className="card-header">
        <h2 className="card-title">메일 발송</h2>
        <span className="amp-hint amp-hint--inline">홈페이지 회원과 직접 등록한 컨택에게 안내성·광고성 메일을 발송합니다. (광고성은 수신 동의자에게만 발송)</span>
      </div>

      {/*
        발송 폼은 공용 레이아웃 클래스(.form-group, .admin-modal-radio-group)를 쓰지 않는다.
        MyPage.css 에 `.mypage-container .form-group label { width: 100px }` 이 있어 선택지
        라벨까지 100px 로 눌리고, 한글은 글자 단위로 꺾여 한 글자씩 끊겨 보였다. 특이도가
        같아(둘 다 0,2,1) 덮어쓰려 해도 나중에 로드되는 MyPage.css 가 이긴다.

        그래서 레이아웃은 amp- 전용 클래스로만 짠다. 항목 이름을 <label> 이 아니라
        <span> 으로 두는 것도 같은 이유다 — 공용 규칙이 label 을 겨냥하고 있다.
        입력 상자(.admin-modal-input)와 버튼은 단독 클래스라 간섭이 없어 그대로 쓴다.
      */}
      <div className="amp-form">
        <div className="amp-field">
          <span className="amp-field__label">메일 종류</span>
          <div className="amp-options">
            <label className="amp-option">
              <input type="radio" name="amp-mailtype" checked={sendForm.mailType === 'operational'} onChange={() => setSendForm({ ...sendForm, mailType: 'operational' })} />
              <span>안내성 <em className="amp-note">수신동의 불필요</em></span>
            </label>
            <label className="amp-option">
              <input type="radio" name="amp-mailtype" checked={sendForm.mailType === 'promotional'} onChange={() => setSendForm({ ...sendForm, mailType: 'promotional' })} />
              <span>광고성 <em className="amp-note">동의자 한정 · 제목에 "(광고)" 표기</em></span>
            </label>
          </div>
        </div>

        <div className="amp-field">
          <span className="amp-field__label">분류</span>
          <select className="admin-modal-input" value={sendForm.templateKey} onChange={e => setSendForm({ ...sendForm, templateKey: e.target.value })}>
            {SEND_TEMPLATE_KEYS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
          </select>
          {/* 전시회 QR 로 받은 컨택은 광고성 수신 동의가 없다. 코드 안내를 광고성으로
              보내면 한 명에게도 가지 않는데, 발송 결과만 보면 "대상 0명" 이라 이유를
              알기 어렵다. 고르는 자리에서 미리 알린다. */}
          {sendForm.templateKey === 'redeem_code' && sendForm.mailType === 'promotional' && (
            <p className="amp-warn">
              코드 안내는 <strong>안내성</strong>으로 보내세요. 전시회에서 받은 컨택은 광고성 수신 동의가 없어
              광고성으로는 발송되지 않습니다.
            </p>
          )}
        </div>

        <div className="amp-field">
          <span className="amp-field__label">발송 대상 <span className="amp-req">*</span></span>
          <div className="amp-options">
            <label className="amp-option">
              <input type="checkbox" checked={sendForm.includeMembers} onChange={e => setSendForm({ ...sendForm, includeMembers: e.target.checked })} />
              <span>홈페이지 회원 <em className="amp-note">{sendForm.mailType === 'promotional' ? '광고 수신동의 회원' : '활성 회원 전체'}</em></span>
            </label>
            <label className="amp-option">
              <input type="checkbox" checked={sendForm.includeContacts} onChange={e => setSendForm({ ...sendForm, includeContacts: e.target.checked })} />
              <span>직접 등록 컨택 <em className="amp-note">{sendForm.mailType === 'promotional' ? '광고 동의 + 활성' : '안내성 동의 + 활성'}</em></span>
            </label>
          </div>
        </div>

        <div className="amp-field">
          <span className="amp-field__label">메일 제목 <span className="amp-req">*</span></span>
          <input type="text" className="admin-modal-input" value={sendForm.subject} onChange={e => setSendForm({ ...sendForm, subject: e.target.value })} placeholder="예: [BUL:C] v1.2.0 업데이트 안내" />
        </div>

        <div className="amp-field">
          <span className="amp-field__label">본문 제목 <span className="amp-req">*</span></span>
          <input type="text" className="admin-modal-input" value={sendForm.title} onChange={e => setSendForm({ ...sendForm, title: e.target.value })} placeholder="메일 본문 상단의 큰 제목" />
        </div>

        <div className="amp-field">
          <span className="amp-field__label">본문 내용 <em className="amp-note">HTML 허용</em> <span className="amp-req">*</span></span>
          <textarea
            ref={contentRef}
            className="admin-modal-input"
            value={sendForm.contentHtml}
            onChange={e => setSendForm({ ...sendForm, contentHtml: e.target.value })}
            rows={10}
            placeholder="<p>{name}님 안녕하세요. ...</p>"
          />

          {/* 쓸 수 있는 항목을 표로 펼쳐 둔다. 문장으로 적으면 무엇이 있는지 한눈에
              안 들어오고, 넣을 때마다 중괄호를 손으로 쳐야 한다. */}
          <div className="amp-ph">
            <div className="amp-ph__head">
              개인화 항목 <em className="amp-note">누르면 커서 위치에 들어갑니다</em>
            </div>
            <table className="amp-ph__table">
              <tbody>
                {MAIL_PLACEHOLDERS.map(ph => (
                  <tr key={ph.token} className={ph.ready ? '' : 'amp-ph__row--pending'}>
                    <td>
                      <button
                        type="button"
                        className="amp-ph__btn"
                        onClick={() => insertPlaceholder(ph.token)}
                        disabled={!ph.ready}
                        title={ph.ready ? `${ph.token} 삽입` : '아직 치환되지 않습니다'}
                      >
                        {ph.token}
                      </button>
                    </td>
                    <td className="amp-ph__label">{ph.label}</td>
                    <td className="amp-ph__fallback">
                      {ph.ready ? <>없으면 <strong>{ph.fallback}</strong></> : <span className="amp-ph__badge">준비 중</span>}
                    </td>
                    <td className="amp-ph__note">{ph.note || ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="amp-note amp-note--block">
              예: <code>{'{name}'}님 안녕하세요</code> → <strong>강주원님 안녕하세요</strong> 로 수신자마다 각각 발송됩니다.
            </p>
          </div>

          {unreadyInBody.length > 0 && (
            <p className="amp-warn">
              본문에 아직 치환되지 않는 항목이 있습니다 —{' '}
              {unreadyInBody.map(p => <code key={p.token}>{p.token}</code>)}
              . 이대로 보내면 <strong>중괄호가 그대로 메일에 나갑니다.</strong>
            </p>
          )}
        </div>

        {sendResult && (
          <div className="amp-send-result">
            발송 결과 — 대상 <strong>{sendResult.targetCount}</strong> · 성공 <strong>{sendResult.sentCount}</strong> · 실패 <strong>{sendResult.failedCount}</strong>
          </div>
        )}

        <div className="amp-send-actions">
          <button className="cancel-btn" onClick={resetForm} disabled={isSending}>초기화</button>
          <button className="save-btn" onClick={handleSend} disabled={isSending}>{isSending ? '발송 중...' : '발송'}</button>
        </div>
      </div>
    </div>
  );
};

export default AdminMailingPanel;

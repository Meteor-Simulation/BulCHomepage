import React, { useState } from 'react';
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

  return (
    <div className="info-card admin-section-card wide">
      <div className="card-header">
        <h2 className="card-title">메일 발송</h2>
        <span className="amp-hint amp-hint--inline">홈페이지 회원과 직접 등록한 컨택에게 안내성·광고성 메일을 발송합니다. (광고성은 수신 동의자에게만 발송)</span>
      </div>

      <div className="amp-send-form">
        <div className="form-group">
          <label>메일 종류</label>
          <div className="admin-modal-radio-group">
            <label>
              <input type="radio" name="amp-mailtype" checked={sendForm.mailType === 'operational'} onChange={() => setSendForm({ ...sendForm, mailType: 'operational' })} /> 안내성 (수신동의 불필요)
            </label>
            <label>
              <input type="radio" name="amp-mailtype" checked={sendForm.mailType === 'promotional'} onChange={() => setSendForm({ ...sendForm, mailType: 'promotional' })} /> 광고성 (동의자 한정 · "(광고)" 표기)
            </label>
          </div>
        </div>
        <div className="form-group">
          <label>분류</label>
          <select className="admin-modal-input" value={sendForm.templateKey} onChange={e => setSendForm({ ...sendForm, templateKey: e.target.value })}>
            {SEND_TEMPLATE_KEYS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
          </select>
        </div>
        <div className="form-group">
          <label>발송 대상 <span>*</span></label>
          <div className="amp-checks">
            <label><input type="checkbox" checked={sendForm.includeMembers} onChange={e => setSendForm({ ...sendForm, includeMembers: e.target.checked })} /> 홈페이지 회원 {sendForm.mailType === 'promotional' ? '(광고 수신동의 회원)' : '(활성 회원 전체)'}</label>
            <label><input type="checkbox" checked={sendForm.includeContacts} onChange={e => setSendForm({ ...sendForm, includeContacts: e.target.checked })} /> 직접 등록 컨택 {sendForm.mailType === 'promotional' ? '(광고 동의 + 활성)' : '(안내성 동의 + 활성)'}</label>
          </div>
        </div>
        <div className="form-group">
          <label>메일 제목 <span>*</span></label>
          <input type="text" className="admin-modal-input" value={sendForm.subject} onChange={e => setSendForm({ ...sendForm, subject: e.target.value })} placeholder="예: [BUL:C] v1.2.0 업데이트 안내" />
        </div>
        <div className="form-group">
          <label>본문 제목 <span>*</span></label>
          <input type="text" className="admin-modal-input" value={sendForm.title} onChange={e => setSendForm({ ...sendForm, title: e.target.value })} placeholder="메일 본문 상단의 큰 제목" />
        </div>
        <div className="form-group vertical">
          <label>본문 내용 (HTML 허용) <span>*</span></label>
          <textarea className="admin-modal-input" value={sendForm.contentHtml} onChange={e => setSendForm({ ...sendForm, contentHtml: e.target.value })} rows={10} placeholder="<p>안녕하세요. ...</p>" />
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

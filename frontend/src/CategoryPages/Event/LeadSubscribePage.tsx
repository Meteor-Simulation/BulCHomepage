import React, { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { API_URL } from '../../utils/api';
import Seo from '../../components/Seo';
import PolicyModal from '../../components/PolicyModal';
import './LeadSubscribePage.css';

/**
 * MDP-707: 전시회·세미나 현장 무료 배포 코드 신청 페이지.
 *
 * 부스에 비치한 QR(`/subscribe?e=<행사명>`)로 접속한 방문자가 직접 정보를 입력한다.
 * 대부분 모바일에서 열리므로 단일 컬럼·큰 터치 영역 기준으로 구성한다.
 *
 * <p>동의는 개인정보 수집·이용(필수) 하나만 받는다. 코드 발송은 방문자 본인이 요청한
 * 것을 이행하는 안내성 발송이므로 광고성 정보 수신 동의를 별도로 받지 않는다.
 * 이후 제품 소식·이벤트 등 광고성 발송이 필요하면 코드 안내 메일에서 별도 동의를 받는다.
 */

interface FormState {
  email: string;
  contactName: string;
  companyName: string;
  department: string;
  role: string;
  mobilePhone: string;
  agreePrivacy: boolean;
  website: string; // honeypot
}

const INITIAL_FORM: FormState = {
  email: '',
  contactName: '',
  companyName: '',
  department: '',
  role: '',
  mobilePhone: '',
  agreePrivacy: false,
  website: '',
};

const LeadSubscribePage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const sourceEvent = searchParams.get('e') || '';

  const [form, setForm] = useState<FormState>(INITIAL_FORM);
  const [status, setStatus] = useState<'idle' | 'submitting' | 'done'>('idle');
  const [errorMsg, setErrorMsg] = useState('');
  const [policyOpen, setPolicyOpen] = useState(false);

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');

    if (!form.email.trim() || !form.contactName.trim()) {
      setErrorMsg('이름과 이메일을 입력해주세요.');
      return;
    }
    if (!form.agreePrivacy) {
      setErrorMsg('개인정보 수집·이용에 동의해주세요.');
      return;
    }

    setStatus('submitting');
    try {
      const res = await fetch(`${API_URL}/api/v1/lead-contacts/public`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, sourceEvent }),
      });

      if (res.ok) {
        setStatus('done');
        return;
      }

      const data = await res.json().catch(() => ({}));
      setErrorMsg(data.message || '등록에 실패했습니다. 잠시 후 다시 시도해주세요.');
      setStatus('idle');
    } catch {
      setErrorMsg('네트워크 상태를 확인한 뒤 다시 시도해주세요.');
      setStatus('idle');
    }
  };

  if (status === 'done') {
    return (
      <div className="lead-subscribe-page">
        <Seo title="신청 완료 | BUL:C" noindex />
        <div className="lead-subscribe-card lead-subscribe-done">
          <div className="lead-subscribe-check" aria-hidden="true">✓</div>
          <h1>신청이 완료되었습니다</h1>
          <p>
            찾아주셔서 감사합니다.
            <br />
            입력해주신 이메일로 무료 배포 코드를 보내드리겠습니다.
          </p>
          <a className="lead-subscribe-link" href="https://bulc.msimul.com">
            BUL:C 살펴보기
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="lead-subscribe-page">
      <Seo title="무료 배포 코드 신청 | BUL:C" noindex />

      <form className="lead-subscribe-card" onSubmit={handleSubmit} noValidate>
        <header className="lead-subscribe-header">
          <div className="lead-subscribe-brand">
            <img src="/logo_transparent.png" alt="" className="lead-subscribe-logo" />
            <span className="lead-subscribe-brand-text">BUL:C</span>
          </div>
          <p>찾아주셔서 감사합니다.</p>
        </header>

        <label className="lead-subscribe-field">
          <span>
            이름 <em>*</em>
          </span>
          <input
            type="text"
            value={form.contactName}
            onChange={(e) => setField('contactName', e.target.value)}
            autoComplete="name"
            maxLength={100}
            required
          />
        </label>

        <label className="lead-subscribe-field">
          <span>
            이메일 <em>*</em>
          </span>
          <input
            type="email"
            value={form.email}
            onChange={(e) => setField('email', e.target.value)}
            autoComplete="email"
            inputMode="email"
            maxLength={255}
            required
          />
        </label>

        <label className="lead-subscribe-field">
          <span>회사명</span>
          <input
            type="text"
            value={form.companyName}
            onChange={(e) => setField('companyName', e.target.value)}
            autoComplete="organization"
            maxLength={100}
          />
        </label>

        <div className="lead-subscribe-row">
          <label className="lead-subscribe-field">
            <span>부서</span>
            <input
              type="text"
              value={form.department}
              onChange={(e) => setField('department', e.target.value)}
              maxLength={100}
            />
          </label>
          <label className="lead-subscribe-field">
            <span>직책</span>
            <input
              type="text"
              value={form.role}
              onChange={(e) => setField('role', e.target.value)}
              autoComplete="organization-title"
              maxLength={100}
            />
          </label>
        </div>

        <label className="lead-subscribe-field">
          <span>연락처</span>
          <input
            type="tel"
            value={form.mobilePhone}
            onChange={(e) => setField('mobilePhone', e.target.value)}
            autoComplete="tel"
            inputMode="tel"
            maxLength={50}
          />
        </label>

        {/* honeypot — 사람에게는 보이지 않는다 */}
        <div className="lead-subscribe-hp" aria-hidden="true">
          <label htmlFor="website">Website</label>
          <input
            id="website"
            type="text"
            tabIndex={-1}
            autoComplete="off"
            value={form.website}
            onChange={(e) => setField('website', e.target.value)}
          />
        </div>

        <div className="lead-subscribe-consent">
          <label className="lead-subscribe-check-row">
            <input
              type="checkbox"
              checked={form.agreePrivacy}
              onChange={(e) => setField('agreePrivacy', e.target.checked)}
            />
            <span>
              <strong>[필수]</strong> 무료 배포 코드 발송을 위한 개인정보 수집·이용에 동의합니다.
              <button type="button" className="lead-subscribe-more" onClick={() => setPolicyOpen(true)}>
                자세히
              </button>
            </span>
          </label>
          <p className="lead-subscribe-consent-detail">
            수집 항목: 이름, 이메일, 회사명, 부서, 직책, 연락처
            <br />
            이용 목적: 무료 배포 코드 발송 및 관련 안내
            <br />
            보유 기간: 수신거부 시까지 (요청 시 즉시 파기)
          </p>
        </div>

        {errorMsg && <p className="lead-subscribe-error">{errorMsg}</p>}

        <button type="submit" className="lead-subscribe-submit" disabled={status === 'submitting'}>
          {status === 'submitting' ? '신청 중...' : '무료 배포 코드 신청'}
        </button>
      </form>

      <PolicyModal isOpen={policyOpen} type="privacy" onClose={() => setPolicyOpen(false)} />
    </div>
  );
};

export default LeadSubscribePage;

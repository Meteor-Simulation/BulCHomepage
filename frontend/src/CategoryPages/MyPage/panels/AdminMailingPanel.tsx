import React, { useCallback, useEffect, useState } from 'react';
import { getApiBaseUrl } from '../../../utils/api';
import { useAlert } from '../../../components/AlertProvider';
import './AdminMailingPanel.css';

const API = getApiBaseUrl();

interface LeadContact {
  id: number;
  email: string;
  contactName: string | null;
  companyName: string | null;
  department: string | null;
  role: string | null;
  address: string | null;
  workPhone: string | null;
  workFax: string | null;
  mobilePhone: string | null;
  sourceEvent: string | null;
  sourceDate: string | null;
  collectedBy: string | null;
  consentMethod: string | null;
  consentDate: string | null;
  consentEvidence: string | null;
  optInMarketing: boolean;
  optInTransactional: boolean;
  tags: string | null;
  notes: string | null;
  unsubscribeToken: string;
  unsubscribedAt: string | null;
  unsubscribeReason: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface FormState {
  email: string;
  contactName: string;
  companyName: string;
  department: string;
  role: string;
  address: string;
  workPhone: string;
  workFax: string;
  mobilePhone: string;
  sourceEvent: string;
  sourceDate: string;
  collectedBy: string;
  consentMethod: string;
  consentDate: string;
  consentEvidence: string;
  optInMarketing: boolean;
  optInTransactional: boolean;
  tags: string;
  notes: string;
}

const emptyForm = (): FormState => ({
  email: '',
  contactName: '',
  companyName: '',
  department: '',
  role: '',
  address: '',
  workPhone: '',
  workFax: '',
  mobilePhone: '',
  sourceEvent: '',
  sourceDate: '',
  collectedBy: '',
  consentMethod: 'business_card',
  consentDate: '',
  consentEvidence: '',
  optInMarketing: false,
  optInTransactional: true,
  tags: '',
  notes: '',
});

const CONSENT_METHODS = [
  { value: 'business_card', label: '명함 수거' },
  { value: 'booth_signup', label: '부스 사인업' },
  { value: 'verbal', label: '구두 동의' },
  { value: 'web_form', label: '웹 폼' },
  { value: 'import', label: '일괄 임포트' },
];

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

const ACTIVE_PAGE_SIZE = 10;
const INACTIVE_PAGE_SIZE = 20;

const formatDateOnly = (iso?: string | null) => {
  if (!iso) return '';
  return iso.length > 10 ? iso.slice(0, 10) : iso;
};

const AdminMailingPanel: React.FC = () => {
  const { showAlert } = useAlert();

  // 활성 섹션
  const [activeContacts, setActiveContacts] = useState<LeadContact[]>([]);
  const [activePage, setActivePage] = useState(0);
  const [activeTotalPages, setActiveTotalPages] = useState(0);
  const [activeTotalElements, setActiveTotalElements] = useState(0);
  const [isLoadingActive, setIsLoadingActive] = useState(false);
  const [activeSearchInput, setActiveSearchInput] = useState('');
  const [activeQ, setActiveQ] = useState('');

  // 비활성 섹션
  const [inactiveContacts, setInactiveContacts] = useState<LeadContact[]>([]);
  const [inactivePage, setInactivePage] = useState(0);
  const [inactiveTotalPages, setInactiveTotalPages] = useState(0);
  const [inactiveTotalElements, setInactiveTotalElements] = useState(0);
  const [isLoadingInactive, setIsLoadingInactive] = useState(false);
  const [inactiveSearchInput, setInactiveSearchInput] = useState('');
  const [inactiveQ, setInactiveQ] = useState('');

  // 등록/편집 모달
  const [isFormModalOpen, setIsFormModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [isSubmitting, setIsSubmitting] = useState(false);

  // CSV 모달
  const [isCsvModalOpen, setIsCsvModalOpen] = useState(false);
  const [csvFile, setCsvFile] = useState<File | null>(null);
  const [csvImporting, setCsvImporting] = useState(false);
  const [csvDragOver, setCsvDragOver] = useState(false);
  const [csvResult, setCsvResult] = useState<{ totalRows: number; registered: number; skipped: number; errors: { rowNumber: number; email: string; message: string }[] } | null>(null);

  // 수신 비활성 입력
  const [unsubReasonModal, setUnsubReasonModal] = useState<{ id: number; email: string } | null>(null);
  const [unsubReason, setUnsubReason] = useState('');

  // 메일 발송 모달
  const [isSendModalOpen, setIsSendModalOpen] = useState(false);
  const [sendForm, setSendForm] = useState<SendForm>(emptySendForm());
  const [isSending, setIsSending] = useState(false);
  const [sendResult, setSendResult] = useState<{ targetCount: number; sentCount: number; failedCount: number } | null>(null);

  const fetchSection = useCallback(async (isActiveSection: boolean, pageNum: number) => {
    const setLoading = isActiveSection ? setIsLoadingActive : setIsLoadingInactive;
    const setContacts = isActiveSection ? setActiveContacts : setInactiveContacts;
    const setTotalPagesFn = isActiveSection ? setActiveTotalPages : setInactiveTotalPages;
    const setTotalElementsFn = isActiveSection ? setActiveTotalElements : setInactiveTotalElements;
    const q = isActiveSection ? activeQ : inactiveQ;
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (q) params.set('q', q);
      if (isActiveSection) {
        params.set('activeOnly', 'true');
      } else {
        params.set('inactiveOnly', 'true');
      }
      params.set('page', String(pageNum));
      params.set('size', String(isActiveSection ? ACTIVE_PAGE_SIZE : INACTIVE_PAGE_SIZE));

      const res = await fetch(`${API}/api/v1/admin/lead-contacts?${params.toString()}`, { credentials: 'include' });
      if (!res.ok) {
        showAlert({ message: `${isActiveSection ? '활성' : '비활성'} 목록 조회 실패`, type: 'error' });
        return;
      }
      const data: PageResponse<LeadContact> = await res.json();
      setContacts(data.content);
      setTotalPagesFn(data.totalPages);
      setTotalElementsFn(data.totalElements);
    } catch {
      showAlert({ message: '목록 조회 중 오류', type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [activeQ, inactiveQ, showAlert]);

  // 활성 섹션 페이지/필터 변경 시
  useEffect(() => {
    fetchSection(true, activePage);
  }, [fetchSection, activePage]);

  // 비활성 섹션 페이지/필터 변경 시
  useEffect(() => {
    fetchSection(false, inactivePage);
  }, [fetchSection, inactivePage]);

  const refreshBoth = async () => {
    await Promise.all([fetchSection(true, activePage), fetchSection(false, inactivePage)]);
  };

  const applyActiveSearch = () => {
    setActiveQ(activeSearchInput.trim());
    setActivePage(0);
  };

  const clearActiveSearch = () => {
    setActiveSearchInput('');
    setActiveQ('');
    setActivePage(0);
  };

  const applyInactiveSearch = () => {
    setInactiveQ(inactiveSearchInput.trim());
    setInactivePage(0);
  };

  const clearInactiveSearch = () => {
    setInactiveSearchInput('');
    setInactiveQ('');
    setInactivePage(0);
  };

  // ---- Form CRUD ----

  const openCreateModal = () => {
    setEditingId(null);
    setForm(emptyForm());
    setIsFormModalOpen(true);
  };

  const openEditModal = (c: LeadContact) => {
    setEditingId(c.id);
    setForm({
      email: c.email,
      contactName: c.contactName || '',
      companyName: c.companyName || '',
      department: c.department || '',
      role: c.role || '',
      address: c.address || '',
      workPhone: c.workPhone || '',
      workFax: c.workFax || '',
      mobilePhone: c.mobilePhone || '',
      sourceEvent: c.sourceEvent || '',
      sourceDate: formatDateOnly(c.sourceDate),
      collectedBy: c.collectedBy || '',
      consentMethod: c.consentMethod || 'business_card',
      consentDate: formatDateOnly(c.consentDate),
      consentEvidence: c.consentEvidence || '',
      optInMarketing: c.optInMarketing,
      optInTransactional: c.optInTransactional,
      tags: c.tags || '',
      notes: c.notes || '',
    });
    setIsFormModalOpen(true);
  };

  const closeFormModal = () => {
    if (isSubmitting) return;
    setIsFormModalOpen(false);
    setEditingId(null);
  };

  const handleFormSubmit = async () => {
    if (!form.email.trim()) {
      showAlert({ message: '이메일은 필수입니다', type: 'error' });
      return;
    }
    setIsSubmitting(true);
    try {
      const payload: Record<string, unknown> = {
        email: form.email.trim(),
        contactName: form.contactName || null,
        companyName: form.companyName || null,
        department: form.department || null,
        role: form.role || null,
        address: form.address || null,
        workPhone: form.workPhone || null,
        workFax: form.workFax || null,
        mobilePhone: form.mobilePhone || null,
        sourceEvent: form.sourceEvent || null,
        sourceDate: form.sourceDate || null,
        collectedBy: form.collectedBy || null,
        consentMethod: form.consentMethod || null,
        consentDate: form.consentDate || null,
        consentEvidence: form.consentEvidence || null,
        optInMarketing: form.optInMarketing,
        optInTransactional: form.optInTransactional,
        tags: form.tags || null,
        notes: form.notes || null,
      };
      const url = editingId
        ? `${API}/api/v1/admin/lead-contacts/${editingId}`
        : `${API}/api/v1/admin/lead-contacts`;
      const method = editingId ? 'PUT' : 'POST';
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: '저장 실패' }));
        showAlert({ message: err.message || '저장 실패', type: 'error' });
        return;
      }
      showAlert({ message: editingId ? '수정되었습니다' : '등록되었습니다', type: 'success' });
      setIsFormModalOpen(false);
      setEditingId(null);
      await refreshBoth();
    } catch {
      showAlert({ message: '저장 중 오류', type: 'error' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (c: LeadContact) => {
    if (!window.confirm(`${c.email} 컨택을 삭제하시겠습니까? (영구 삭제)`)) return;
    const res = await fetch(`${API}/api/v1/admin/lead-contacts/${c.id}`, {
      method: 'DELETE',
      credentials: 'include',
    });
    if (res.ok || res.status === 204) {
      showAlert({ message: '삭제되었습니다', type: 'success' });
      await refreshBoth();
    } else {
      showAlert({ message: '삭제 실패', type: 'error' });
    }
  };

  const openUnsubscribeModal = (c: LeadContact) => {
    setUnsubReason('');
    setUnsubReasonModal({ id: c.id, email: c.email });
  };

  const handleUnsubscribe = async () => {
    if (!unsubReasonModal) return;
    const res = await fetch(`${API}/api/v1/admin/lead-contacts/${unsubReasonModal.id}/unsubscribe`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ reason: unsubReason || null }),
    });
    if (res.ok) {
      showAlert({ message: '수신 비활성으로 변경되었습니다', type: 'success' });
      setUnsubReasonModal(null);
      await refreshBoth();
    } else {
      showAlert({ message: '수신 비활성 처리 실패', type: 'error' });
    }
  };

  const handleReactivate = async (c: LeadContact) => {
    if (!window.confirm(`${c.email} 컨택을 수신 활성으로 되돌리시겠습니까?`)) return;
    const res = await fetch(`${API}/api/v1/admin/lead-contacts/${c.id}/reactivate`, {
      method: 'POST',
      credentials: 'include',
    });
    if (res.ok) {
      showAlert({ message: '수신 활성으로 복구되었습니다', type: 'success' });
      await refreshBoth();
    } else {
      showAlert({ message: '재활성화 실패', type: 'error' });
    }
  };

  // ---- 메일 발송 ----

  const openSendModal = () => {
    setSendForm(emptySendForm());
    setSendResult(null);
    setIsSendModalOpen(true);
  };

  const closeSendModal = () => {
    if (isSending) return;
    setIsSendModalOpen(false);
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

  // ---- CSV ----

  const openCsvModal = () => {
    setCsvFile(null);
    setCsvResult(null);
    setIsCsvModalOpen(true);
  };

  const closeCsvModal = () => {
    if (csvImporting) return;
    setIsCsvModalOpen(false);
    setCsvFile(null);
    setCsvResult(null);
  };

  const handleCsvImport = async () => {
    if (!csvFile) return;
    setCsvImporting(true);
    setCsvResult(null);
    try {
      const fd = new FormData();
      fd.append('file', csvFile);
      const res = await fetch(`${API}/api/v1/admin/lead-contacts/import`, {
        method: 'POST',
        credentials: 'include',
        body: fd,
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: '임포트 실패' }));
        showAlert({ message: err.message || '임포트 실패', type: 'error' });
        return;
      }
      const data = await res.json();
      setCsvResult(data);
      await refreshBoth();
    } catch {
      showAlert({ message: '임포트 중 오류', type: 'error' });
    } finally {
      setCsvImporting(false);
    }
  };

  const downloadCsvTemplate = () => {
    // 사용자 명함 양식 12개 컬럼 순서대로
    const header = '회사,이름,부서,직함,전자 메일 주소,근무지 주소 번지,근무처 전화,근무처 팩스,휴대폰,명함 등록일,명함첩 이름,메모';
    const example = '예시주식회사,홍길동,영업본부,차장,hong@example.com,서울특별시 강남구 테헤란로 123,02-555-1234,02-555-5678,010-1234-5678,2026-06-04,2026 소방안전 전시회,부스 방문';
    const blob = new Blob(['﻿' + header + '\n' + example + '\n'], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'lead_contacts_template.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  // ---- Render helpers ----

  const renderTable = (rows: LeadContact[], isActiveSection: boolean) => (
    <div className="admin-table-wrapper">
      <table className="admin-table">
        <thead>
          <tr>
            <th>이메일</th>
            <th>이름</th>
            <th>회사</th>
            <th>광고/안내</th>
            <th>액션</th>
          </tr>
        </thead>
        <tbody>
          {rows.length > 0 ? rows.map(c => (
            <tr key={c.id}>
              <td>{c.email}</td>
              <td>{c.contactName || '-'}</td>
              <td>{c.companyName || '-'}</td>
              <td>
                <span className={`status-toggle-btn ${c.optInMarketing ? 'active' : 'inactive'}`}>광고</span>{' '}
                <span className={`status-toggle-btn ${c.optInTransactional ? 'active' : 'inactive'}`}>안내</span>
              </td>
              <td>
                <div className="action-btn-group">
                  <button className="action-btn edit" onClick={() => openEditModal(c)}>편집</button>
                  {isActiveSection
                    ? <button className="action-btn warning" onClick={() => openUnsubscribeModal(c)}>비활성화</button>
                    : <button className="action-btn success" onClick={() => handleReactivate(c)}>재활성화</button>}
                  <button className="action-btn danger" onClick={() => handleDelete(c)}>삭제</button>
                </div>
              </td>
            </tr>
          )) : (
            <tr><td colSpan={5} className="empty-row">{isActiveSection ? '활성 컨택이 없습니다.' : '비활성 컨택이 없습니다.'}</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );

  const renderPagination = (currentPage: number, totalPgs: number, setPg: (p: number) => void) => (
    totalPgs > 1 ? (
      <div className="admin-pagination">
        <button disabled={currentPage === 0} onClick={() => setPg(currentPage - 1)}>&lsaquo;</button>
        <span>{currentPage + 1} / {totalPgs}</span>
        <button disabled={currentPage >= totalPgs - 1} onClick={() => setPg(currentPage + 1)}>&rsaquo;</button>
      </div>
    ) : null
  );

  return (
    <>
      {/* 등록/발송 */}
      <div className="info-card admin-section-card wide amp-actionbar">
        <div className="card-header">
          <h2 className="card-title">등록/발송</h2>
          <span className="amp-hint amp-hint--inline">홈페이지 회원과 직접 등록한 컨택에게 안내성·광고성 메일을 발송합니다. (광고성은 수신 동의자에게만 발송)</span>
          <div className="action-btn-group" style={{ marginLeft: 'auto' }}>
            <button className="action-btn edit" onClick={openCsvModal}>파일 등록</button>
            <button className="action-btn edit" onClick={openCreateModal}>수동 등록</button>
            <button className="action-btn edit" onClick={openSendModal}>발송</button>
          </div>
        </div>
      </div>

      {/* 수신 활성 목록 */}
      <div className="info-card admin-section-card wide">
        <div className="card-header">
          <h2 className="card-title">수신 활성 목록</h2>
          <span className="admin-count">{activeTotalElements}명</span>
        </div>
        <div className="admin-search-bar">
          <input
            type="text"
            placeholder="이메일, 이름, 회사로 검색"
            value={activeSearchInput}
            onChange={e => setActiveSearchInput(e.target.value)}
            onKeyPress={e => { if (e.key === 'Enter') applyActiveSearch(); }}
          />
          <button onClick={applyActiveSearch}>조회</button>
          {activeQ && <button className="clear" onClick={clearActiveSearch}>초기화</button>}
        </div>
        {isLoadingActive ? (
          <div className="admin-loading">데이터 로딩 중...</div>
        ) : (
          <>
            {renderTable(activeContacts, true)}
            {renderPagination(activePage, activeTotalPages, setActivePage)}
          </>
        )}
      </div>

      {/* 수신 비활성 목록 */}
      <div className="info-card admin-section-card wide">
        <div className="card-header">
          <h2 className="card-title">수신 비활성 목록</h2>
          <span className="admin-count">{inactiveTotalElements}명</span>
        </div>
        <div className="admin-search-bar">
          <input
            type="text"
            placeholder="이메일, 이름, 회사로 검색"
            value={inactiveSearchInput}
            onChange={e => setInactiveSearchInput(e.target.value)}
            onKeyPress={e => { if (e.key === 'Enter') applyInactiveSearch(); }}
          />
          <button onClick={applyInactiveSearch}>조회</button>
          {inactiveQ && <button className="clear" onClick={clearInactiveSearch}>초기화</button>}
        </div>
        {isLoadingInactive ? (
          <div className="admin-loading">데이터 로딩 중...</div>
        ) : (
          <>
            {renderTable(inactiveContacts, false)}
            {renderPagination(inactivePage, inactiveTotalPages, setInactivePage)}
          </>
        )}
      </div>

      {/* 메일 발송 모달 */}
      {isSendModalOpen && (
        <div className="admin-modal-overlay" onMouseDown={e => { if (e.target === e.currentTarget) closeSendModal(); }}>
          <div className="admin-modal">
            <button className="admin-modal-close" onClick={closeSendModal}>&times;</button>
            <div className="admin-modal-header">
              <h3>메일 발송</h3>
            </div>
            <div className="admin-modal-body">
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
                <textarea className="admin-modal-input" value={sendForm.contentHtml} onChange={e => setSendForm({ ...sendForm, contentHtml: e.target.value })} rows={8} placeholder="<p>안녕하세요. ...</p>" />
              </div>
              {sendResult && (
                <div className="amp-send-result">
                  발송 결과 — 대상 <strong>{sendResult.targetCount}</strong> · 성공 <strong>{sendResult.sentCount}</strong> · 실패 <strong>{sendResult.failedCount}</strong>
                </div>
              )}
            </div>
            <div className="admin-modal-footer">
              <button className="cancel-btn" onClick={closeSendModal} disabled={isSending}>닫기</button>
              <button className="save-btn" onClick={handleSend} disabled={isSending}>{isSending ? '발송 중...' : '발송'}</button>
            </div>
          </div>
        </div>
      )}

      {/* 등록/편집 모달 */}
      {isFormModalOpen && (
        <div className="admin-modal-overlay" onMouseDown={e => { if (e.target === e.currentTarget) closeFormModal(); }}>
          <div className="admin-modal">
            <button className="admin-modal-close" onClick={closeFormModal}>&times;</button>
            <div className="admin-modal-header">
              <h3>{editingId ? '컨택 수정' : '신규 컨택 등록'}</h3>
            </div>
            <div className="admin-modal-body">
              <div className="form-group">
                <label>이메일 <span>*</span></label>
                <input type="email" className="admin-modal-input" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} />
              </div>
              <div className="admin-modal-form-row">
                <div className="form-group">
                  <label>이름</label>
                  <input type="text" className="admin-modal-input" value={form.contactName} onChange={e => setForm({ ...form, contactName: e.target.value })} />
                </div>
                <div className="form-group">
                  <label>회사</label>
                  <input type="text" className="admin-modal-input" value={form.companyName} onChange={e => setForm({ ...form, companyName: e.target.value })} />
                </div>
              </div>
              <div className="admin-modal-form-row">
                <div className="form-group">
                  <label>부서</label>
                  <input type="text" className="admin-modal-input" value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} />
                </div>
                <div className="form-group">
                  <label>직함</label>
                  <input type="text" className="admin-modal-input" value={form.role} onChange={e => setForm({ ...form, role: e.target.value })} />
                </div>
              </div>
              <div className="admin-modal-form-row">
                <div className="form-group">
                  <label>휴대폰</label>
                  <input type="tel" className="admin-modal-input" value={form.mobilePhone} onChange={e => setForm({ ...form, mobilePhone: e.target.value })} placeholder="010-0000-0000" />
                </div>
                <div className="form-group">
                  <label>근무처 전화</label>
                  <input type="tel" className="admin-modal-input" value={form.workPhone} onChange={e => setForm({ ...form, workPhone: e.target.value })} placeholder="02-000-0000" />
                </div>
              </div>
              <div className="admin-modal-form-row">
                <div className="form-group">
                  <label>근무처 팩스</label>
                  <input type="tel" className="admin-modal-input" value={form.workFax} onChange={e => setForm({ ...form, workFax: e.target.value })} />
                </div>
                <div className="form-group">
                  <label>수집자</label>
                  <input type="text" className="admin-modal-input" value={form.collectedBy} onChange={e => setForm({ ...form, collectedBy: e.target.value })} />
                </div>
              </div>
              <div className="admin-modal-form-row">
                <div className="form-group">
                  <label>수집 행사</label>
                  <input type="text" className="admin-modal-input" value={form.sourceEvent} onChange={e => setForm({ ...form, sourceEvent: e.target.value })} placeholder="예: 2026 소방안전 전시회" />
                </div>
                <div className="form-group">
                  <label>수집 일자</label>
                  <input type="date" className="admin-modal-input" value={form.sourceDate} onChange={e => setForm({ ...form, sourceDate: e.target.value })} />
                </div>
              </div>
              <div className="admin-modal-form-row">
                <div className="form-group">
                  <label>동의 방식</label>
                  <select className="admin-modal-input" value={form.consentMethod} onChange={e => setForm({ ...form, consentMethod: e.target.value })}>
                    {CONSENT_METHODS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label>동의 일자</label>
                  <input type="date" className="admin-modal-input" value={form.consentDate} onChange={e => setForm({ ...form, consentDate: e.target.value })} />
                </div>
              </div>
              <div className="form-group">
                <label>근무지 주소</label>
                <input type="text" className="admin-modal-input" value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} placeholder="서울특별시 강남구 ..." />
              </div>
              <div className="form-group">
                <label>동의 증빙 (자유)</label>
                <input type="text" className="admin-modal-input" value={form.consentEvidence} onChange={e => setForm({ ...form, consentEvidence: e.target.value })} placeholder="명함 파일명, 부스 사인업 URL 등" />
              </div>
              <div className="form-group">
                <label>태그 (쉼표 구분)</label>
                <input type="text" className="admin-modal-input" value={form.tags} onChange={e => setForm({ ...form, tags: e.target.value })} placeholder="fire_safety, hospital, 2026_kfse" />
              </div>
              <div className="form-group vertical">
                <label>메모</label>
                <textarea className="admin-modal-input" value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} rows={3} />
              </div>
              <div className="amp-checks">
                <label><input type="checkbox" checked={form.optInMarketing} onChange={e => setForm({ ...form, optInMarketing: e.target.checked })} /> 광고성 메일 수신 동의 (정보통신망법 50조)</label>
                <label><input type="checkbox" checked={form.optInTransactional} onChange={e => setForm({ ...form, optInTransactional: e.target.checked })} /> 안내성 메일 수신 동의</label>
              </div>
            </div>
            <div className="admin-modal-footer">
              <button className="cancel-btn" onClick={closeFormModal} disabled={isSubmitting}>취소</button>
              <button className="save-btn" onClick={handleFormSubmit} disabled={isSubmitting}>
                {isSubmitting ? '저장 중...' : (editingId ? '수정' : '등록')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 비활성 사유 모달 */}
      {unsubReasonModal && (
        <div className="admin-modal-overlay" onMouseDown={e => { if (e.target === e.currentTarget) setUnsubReasonModal(null); }}>
          <div className="admin-modal">
            <button className="admin-modal-close" onClick={() => setUnsubReasonModal(null)}>&times;</button>
            <div className="admin-modal-header">
              <h3>수신 비활성화</h3>
            </div>
            <div className="admin-modal-body">
              <p className="amp-hint">{unsubReasonModal.email} 을(를) 수신 비활성으로 변경합니다. 사유를 입력해주세요. (선택)</p>
              <div className="form-group vertical">
                <label>비활성 사유</label>
                <textarea className="admin-modal-input" value={unsubReason} onChange={e => setUnsubReason(e.target.value)} rows={3} placeholder="비활성 사유" />
              </div>
            </div>
            <div className="admin-modal-footer">
              <button className="cancel-btn" onClick={() => setUnsubReasonModal(null)}>취소</button>
              <button className="save-btn" onClick={handleUnsubscribe}>비활성화</button>
            </div>
          </div>
        </div>
      )}

      {/* CSV 모달 */}
      {isCsvModalOpen && (
        <div className="admin-modal-overlay" onMouseDown={e => { if (e.target === e.currentTarget) closeCsvModal(); }}>
          <div className="admin-modal">
            <button className="admin-modal-close" onClick={closeCsvModal}>&times;</button>
            <div className="admin-modal-header">
              <h3>CSV 일괄 임포트</h3>
            </div>
            <div className="admin-modal-body">
              <ul className="amp-csv-help">
                <li><strong>CSV / Excel (.xlsx, .xls)</strong> 모두 지원. 헤더 행 필수, 이메일 컬럼 필수.</li>
                <li>한국어 명함 양식 헤더 자동 인식: <code>회사 · 이름 · 부서 · 직함 · 전자 메일 주소 · 근무지 주소 번지 · 근무처 전화 · 근무처 팩스 · 휴대폰 · 명함 등록일 · 명함첩 이름 · 메모</code></li>
                <li>날짜는 <code>yyyy-MM-dd</code> / <code>yyyy.MM.dd</code> / <code>yyyy/MM/dd</code> 자동 인식, Excel 시리얼 날짜도 처리</li>
                <li>이미 등록된 이메일은 건너뜁니다.</li>
                <li>명함 임포트는 기본값 <code>광고성 동의=false</code>, <code>안내성 동의=true</code>, <code>동의 방식=import</code>로 등록됨 (정보통신망법 안전 기본값).</li>
              </ul>
              <button className="action-btn edit" onClick={downloadCsvTemplate}>템플릿 다운로드</button>
              <label
                className={`amp-csv-drop${csvDragOver ? ' amp-csv-drop--over' : ''}`}
                onDragOver={e => { e.preventDefault(); e.stopPropagation(); setCsvDragOver(true); }}
                onDragEnter={e => { e.preventDefault(); e.stopPropagation(); setCsvDragOver(true); }}
                onDragLeave={e => { e.preventDefault(); e.stopPropagation(); setCsvDragOver(false); }}
                onDrop={e => {
                  e.preventDefault();
                  e.stopPropagation();
                  setCsvDragOver(false);
                  const f = e.dataTransfer.files?.[0];
                  if (f) setCsvFile(f);
                }}
              >
                <input
                  type="file"
                  accept=".csv,.xlsx,.xls,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
                  onChange={e => setCsvFile(e.target.files?.[0] ?? null)}
                />
                <div className="amp-csv-drop-text">
                  {csvFile
                    ? <>선택됨: <strong>{csvFile.name}</strong></>
                    : <>CSV / Excel 파일을 여기에 드래그하거나, <span className="amp-csv-drop-link">클릭하여 선택</span>하세요.</>}
                </div>
              </label>
              {csvResult && (
                <div className="amp-csv-result">
                  <p>총 {csvResult.totalRows}행 · 등록 {csvResult.registered} · 건너뜀 {csvResult.skipped}</p>
                  {csvResult.errors.length > 0 && (
                    <details>
                      <summary>에러 행 ({csvResult.errors.length})</summary>
                      <ul>
                        {csvResult.errors.map((e, i) => (
                          <li key={i}>행 {e.rowNumber} {e.email}: {e.message}</li>
                        ))}
                      </ul>
                    </details>
                  )}
                </div>
              )}
            </div>
            <div className="admin-modal-footer">
              <button className="cancel-btn" onClick={closeCsvModal} disabled={csvImporting}>닫기</button>
              <button className="save-btn" onClick={handleCsvImport} disabled={!csvFile || csvImporting}>
                {csvImporting ? '임포트 중...' : '임포트 실행'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default AdminMailingPanel;

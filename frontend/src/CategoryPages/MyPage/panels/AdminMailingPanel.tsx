import React, { useCallback, useEffect, useMemo, useState } from 'react';
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

const PAGE_SIZE = 20;

const formatDate = (iso?: string | null) => {
  if (!iso) return '-';
  const d = new Date(iso);
  if (!Number.isFinite(d.getTime())) return iso;
  return d.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
};

const formatDateOnly = (iso?: string | null) => {
  if (!iso) return '';
  return iso.length > 10 ? iso.slice(0, 10) : iso;
};

const AdminMailingPanel: React.FC = () => {
  const { showAlert } = useAlert();

  // 검색 폼
  const [filterEmail, setFilterEmail] = useState('');
  const [filterName, setFilterName] = useState('');
  const [filterCompany, setFilterCompany] = useState('');
  const [filterTag, setFilterTag] = useState('');
  const [filterSourceEvent, setFilterSourceEvent] = useState('');
  const [activeOnly, setActiveOnly] = useState(true);

  // 적용된 검색 조건 (실제 fetch에 쓰임)
  const [appliedFilters, setAppliedFilters] = useState({
    email: '', name: '', company: '', tag: '', sourceEvent: '', activeOnly: true,
  });

  // 페이징
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [contacts, setContacts] = useState<LeadContact[]>([]);
  const [isLoading, setIsLoading] = useState(false);

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

  // 해지 입력
  const [unsubReasonModal, setUnsubReasonModal] = useState<{ id: number; email: string } | null>(null);
  const [unsubReason, setUnsubReason] = useState('');

  const fetchContacts = useCallback(async () => {
    setIsLoading(true);
    try {
      const params = new URLSearchParams();
      if (appliedFilters.email) params.set('email', appliedFilters.email);
      if (appliedFilters.name) params.set('name', appliedFilters.name);
      if (appliedFilters.company) params.set('company', appliedFilters.company);
      if (appliedFilters.tag) params.set('tag', appliedFilters.tag);
      if (appliedFilters.sourceEvent) params.set('sourceEvent', appliedFilters.sourceEvent);
      params.set('activeOnly', String(appliedFilters.activeOnly));
      params.set('page', String(page));
      params.set('size', String(PAGE_SIZE));

      const res = await fetch(`${API}/api/v1/admin/lead-contacts?${params.toString()}`, { credentials: 'include' });
      if (!res.ok) {
        showAlert({ message: '컨택 목록 조회 실패', type: 'error' });
        return;
      }
      const data: PageResponse<LeadContact> = await res.json();
      setContacts(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch {
      showAlert({ message: '컨택 목록 조회 중 오류', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [appliedFilters, page, showAlert]);

  useEffect(() => {
    fetchContacts();
  }, [fetchContacts]);

  const handleApplySearch = () => {
    setAppliedFilters({
      email: filterEmail.trim(),
      name: filterName.trim(),
      company: filterCompany.trim(),
      tag: filterTag.trim(),
      sourceEvent: filterSourceEvent.trim(),
      activeOnly,
    });
    setPage(0);
  };

  const handleResetSearch = () => {
    setFilterEmail(''); setFilterName(''); setFilterCompany('');
    setFilterTag(''); setFilterSourceEvent(''); setActiveOnly(true);
    setAppliedFilters({ email: '', name: '', company: '', tag: '', sourceEvent: '', activeOnly: true });
    setPage(0);
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
      await fetchContacts();
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
      await fetchContacts();
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
      showAlert({ message: '해지 처리되었습니다', type: 'success' });
      setUnsubReasonModal(null);
      await fetchContacts();
    } else {
      showAlert({ message: '해지 처리 실패', type: 'error' });
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
      await fetchContacts();
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

  const pages = useMemo(() => {
    if (totalPages === 0) return [];
    const max = Math.min(totalPages, 10);
    const start = Math.max(0, Math.min(page - 4, totalPages - max));
    return Array.from({ length: max }, (_, i) => start + i);
  }, [totalPages, page]);

  return (
    <div className="admin-mailing-panel">
      <div className="amp-header">
        <h2>메일링 컨택 관리</h2>
        <p className="amp-desc">전시회·세미나 등에서 수집한 외부 컨택을 관리합니다.</p>
      </div>

      {/* 필터 */}
      <div className="amp-filter">
        <div className="amp-filter-row">
          <input type="text" placeholder="이메일" value={filterEmail} onChange={e => setFilterEmail(e.target.value)} />
          <input type="text" placeholder="이름" value={filterName} onChange={e => setFilterName(e.target.value)} />
          <input type="text" placeholder="회사" value={filterCompany} onChange={e => setFilterCompany(e.target.value)} />
          <input type="text" placeholder="태그" value={filterTag} onChange={e => setFilterTag(e.target.value)} />
          <input type="text" placeholder="수집 행사" value={filterSourceEvent} onChange={e => setFilterSourceEvent(e.target.value)} />
        </div>
        <div className="amp-filter-row amp-filter-actions">
          <label className="amp-checkbox">
            <input type="checkbox" checked={activeOnly} onChange={e => setActiveOnly(e.target.checked)} />
            <span>해지자 제외</span>
          </label>
          <div className="amp-filter-buttons">
            <button className="amp-btn amp-btn--secondary" onClick={handleResetSearch}>초기화</button>
            <button className="amp-btn amp-btn--primary" onClick={handleApplySearch}>검색</button>
          </div>
        </div>
      </div>

      {/* 액션 */}
      <div className="amp-actions">
        <div className="amp-actions-left">
          총 <strong>{totalElements}</strong> 명
        </div>
        <div className="amp-actions-right">
          <button className="amp-btn amp-btn--secondary" onClick={openCsvModal}>CSV 임포트</button>
          <button className="amp-btn amp-btn--primary" onClick={openCreateModal}>+ 신규 등록</button>
        </div>
      </div>

      {/* 목록 */}
      <div className="amp-table-wrap">
        {isLoading ? (
          <div className="amp-empty">불러오는 중...</div>
        ) : contacts.length === 0 ? (
          <div className="amp-empty">조회된 컨택이 없습니다.</div>
        ) : (
          <table className="amp-table">
            <thead>
              <tr>
                <th>이메일</th>
                <th>이름</th>
                <th>회사</th>
                <th>수집 행사</th>
                <th>태그</th>
                <th>광고/안내</th>
                <th>상태</th>
                <th>등록일</th>
                <th>액션</th>
              </tr>
            </thead>
            <tbody>
              {contacts.map(c => (
                <tr key={c.id} className={c.active ? '' : 'amp-row--unsub'}>
                  <td className="amp-email">{c.email}</td>
                  <td>{c.contactName || '-'}</td>
                  <td>{c.companyName || '-'}</td>
                  <td>{c.sourceEvent || '-'}</td>
                  <td className="amp-tags">{c.tags || '-'}</td>
                  <td className="amp-optin">
                    <span className={c.optInMarketing ? 'amp-badge amp-badge--on' : 'amp-badge amp-badge--off'}>광고{c.optInMarketing ? '✓' : '✗'}</span>
                    <span className={c.optInTransactional ? 'amp-badge amp-badge--on' : 'amp-badge amp-badge--off'}>안내{c.optInTransactional ? '✓' : '✗'}</span>
                  </td>
                  <td>
                    {c.active
                      ? <span className="amp-status amp-status--active">구독중</span>
                      : <span className="amp-status amp-status--unsub">해지</span>}
                  </td>
                  <td>{formatDate(c.createdAt)}</td>
                  <td className="amp-row-actions">
                    <button className="amp-btn amp-btn--small" onClick={() => openEditModal(c)}>편집</button>
                    {c.active && <button className="amp-btn amp-btn--small amp-btn--warn" onClick={() => openUnsubscribeModal(c)}>해지</button>}
                    <button className="amp-btn amp-btn--small amp-btn--danger" onClick={() => handleDelete(c)}>삭제</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className="amp-pagination">
          <button className="amp-btn amp-btn--small" disabled={page === 0} onClick={() => setPage(0)}>«</button>
          <button className="amp-btn amp-btn--small" disabled={page === 0} onClick={() => setPage(p => p - 1)}>‹</button>
          {pages.map(p => (
            <button key={p} className={`amp-btn amp-btn--small ${p === page ? 'amp-btn--page-active' : ''}`} onClick={() => setPage(p)}>{p + 1}</button>
          ))}
          <button className="amp-btn amp-btn--small" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>›</button>
          <button className="amp-btn amp-btn--small" disabled={page >= totalPages - 1} onClick={() => setPage(totalPages - 1)}>»</button>
        </div>
      )}

      {/* 등록/편집 모달 */}
      {isFormModalOpen && (
        <div className="amp-modal-backdrop" onClick={closeFormModal}>
          <div className="amp-modal" onClick={e => e.stopPropagation()}>
            <div className="amp-modal-header">
              <h3>{editingId ? '컨택 수정' : '신규 컨택 등록'}</h3>
              <button className="amp-modal-close" onClick={closeFormModal}>×</button>
            </div>
            <div className="amp-modal-body">
              <div className="amp-form-row">
                <label>이메일 *</label>
                <input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} />
              </div>
              <div className="amp-form-grid">
                <div className="amp-form-row">
                  <label>이름</label>
                  <input type="text" value={form.contactName} onChange={e => setForm({ ...form, contactName: e.target.value })} />
                </div>
                <div className="amp-form-row">
                  <label>회사</label>
                  <input type="text" value={form.companyName} onChange={e => setForm({ ...form, companyName: e.target.value })} />
                </div>
                <div className="amp-form-row">
                  <label>부서</label>
                  <input type="text" value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} />
                </div>
                <div className="amp-form-row">
                  <label>직함</label>
                  <input type="text" value={form.role} onChange={e => setForm({ ...form, role: e.target.value })} />
                </div>
                <div className="amp-form-row">
                  <label>휴대폰</label>
                  <input type="tel" value={form.mobilePhone} onChange={e => setForm({ ...form, mobilePhone: e.target.value })} placeholder="010-0000-0000" />
                </div>
                <div className="amp-form-row">
                  <label>근무처 전화</label>
                  <input type="tel" value={form.workPhone} onChange={e => setForm({ ...form, workPhone: e.target.value })} placeholder="02-000-0000" />
                </div>
                <div className="amp-form-row">
                  <label>근무처 팩스</label>
                  <input type="tel" value={form.workFax} onChange={e => setForm({ ...form, workFax: e.target.value })} />
                </div>
                <div className="amp-form-row">
                  <label>수집 행사</label>
                  <input type="text" value={form.sourceEvent} onChange={e => setForm({ ...form, sourceEvent: e.target.value })} placeholder="예: 2026 소방안전 전시회" />
                </div>
                <div className="amp-form-row">
                  <label>수집 일자</label>
                  <input type="date" value={form.sourceDate} onChange={e => setForm({ ...form, sourceDate: e.target.value })} />
                </div>
                <div className="amp-form-row">
                  <label>수집자</label>
                  <input type="text" value={form.collectedBy} onChange={e => setForm({ ...form, collectedBy: e.target.value })} />
                </div>
                <div className="amp-form-row">
                  <label>동의 방식</label>
                  <select value={form.consentMethod} onChange={e => setForm({ ...form, consentMethod: e.target.value })}>
                    {CONSENT_METHODS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </div>
                <div className="amp-form-row">
                  <label>동의 일자</label>
                  <input type="date" value={form.consentDate} onChange={e => setForm({ ...form, consentDate: e.target.value })} />
                </div>
              </div>
              <div className="amp-form-row">
                <label>근무지 주소</label>
                <input type="text" value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} placeholder="서울특별시 강남구 ..." />
              </div>
              <div className="amp-form-row">
                <label>동의 증빙 (자유)</label>
                <input type="text" value={form.consentEvidence} onChange={e => setForm({ ...form, consentEvidence: e.target.value })} placeholder="명함 파일명, 부스 사인업 URL 등" />
              </div>
              <div className="amp-form-row">
                <label>태그 (쉼표 구분)</label>
                <input type="text" value={form.tags} onChange={e => setForm({ ...form, tags: e.target.value })} placeholder="fire_safety, hospital, 2026_kfse" />
              </div>
              <div className="amp-form-row">
                <label>메모</label>
                <textarea value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} rows={3} />
              </div>
              <div className="amp-form-checks">
                <label className="amp-checkbox">
                  <input type="checkbox" checked={form.optInMarketing} onChange={e => setForm({ ...form, optInMarketing: e.target.checked })} />
                  <span>광고성 메일 수신 동의 (정보통신망법 50조)</span>
                </label>
                <label className="amp-checkbox">
                  <input type="checkbox" checked={form.optInTransactional} onChange={e => setForm({ ...form, optInTransactional: e.target.checked })} />
                  <span>안내성 메일 수신 동의</span>
                </label>
              </div>
            </div>
            <div className="amp-modal-footer">
              <button className="amp-btn amp-btn--secondary" onClick={closeFormModal} disabled={isSubmitting}>취소</button>
              <button className="amp-btn amp-btn--primary" onClick={handleFormSubmit} disabled={isSubmitting}>
                {isSubmitting ? '저장 중...' : (editingId ? '수정' : '등록')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 해지 사유 모달 */}
      {unsubReasonModal && (
        <div className="amp-modal-backdrop" onClick={() => setUnsubReasonModal(null)}>
          <div className="amp-modal amp-modal--small" onClick={e => e.stopPropagation()}>
            <div className="amp-modal-header">
              <h3>관리자 강제 해지</h3>
              <button className="amp-modal-close" onClick={() => setUnsubReasonModal(null)}>×</button>
            </div>
            <div className="amp-modal-body">
              <p>{unsubReasonModal.email} 을(를) 해지 처리합니다. 사유를 입력해주세요. (선택)</p>
              <textarea value={unsubReason} onChange={e => setUnsubReason(e.target.value)} rows={3} placeholder="해지 사유" />
            </div>
            <div className="amp-modal-footer">
              <button className="amp-btn amp-btn--secondary" onClick={() => setUnsubReasonModal(null)}>취소</button>
              <button className="amp-btn amp-btn--warn" onClick={handleUnsubscribe}>해지</button>
            </div>
          </div>
        </div>
      )}

      {/* CSV 모달 */}
      {isCsvModalOpen && (
        <div className="amp-modal-backdrop" onClick={closeCsvModal}>
          <div className="amp-modal amp-modal--medium" onClick={e => e.stopPropagation()}>
            <div className="amp-modal-header">
              <h3>CSV 일괄 임포트</h3>
              <button className="amp-modal-close" onClick={closeCsvModal}>×</button>
            </div>
            <div className="amp-modal-body">
              <ul className="amp-csv-help">
                <li><strong>CSV / Excel (.xlsx, .xls)</strong> 모두 지원. 헤더 행 필수, 이메일 컬럼 필수.</li>
                <li>한국어 명함 양식 헤더 자동 인식: <code>회사 · 이름 · 부서 · 직함 · 전자 메일 주소 · 근무지 주소 번지 · 근무처 전화 · 근무처 팩스 · 휴대폰 · 명함 등록일 · 명함첩 이름 · 메모</code></li>
                <li>날짜는 <code>yyyy-MM-dd</code> / <code>yyyy.MM.dd</code> / <code>yyyy/MM/dd</code> 자동 인식, Excel 시리얼 날짜도 처리</li>
                <li>이미 등록된 이메일은 건너뜁니다.</li>
                <li>명함 임포트는 기본값 <code>광고성 동의=false</code>, <code>안내성 동의=true</code>, <code>동의 방식=import</code>로 등록됨 (정보통신망법 안전 기본값).</li>
              </ul>
              <button className="amp-btn amp-btn--secondary amp-btn--small" onClick={downloadCsvTemplate}>템플릿 다운로드</button>
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
            <div className="amp-modal-footer">
              <button className="amp-btn amp-btn--secondary" onClick={closeCsvModal} disabled={csvImporting}>닫기</button>
              <button className="amp-btn amp-btn--primary" onClick={handleCsvImport} disabled={!csvFile || csvImporting}>
                {csvImporting ? '임포트 중...' : '임포트 실행'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminMailingPanel;

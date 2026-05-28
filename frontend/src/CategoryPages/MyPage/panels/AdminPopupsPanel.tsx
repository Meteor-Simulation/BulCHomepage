import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAlert } from '../../../components/AlertProvider';
import { getApiBaseUrl } from '../../../utils/api';
import {
  PopupItem,
  PopupType,
  PopupTrigger,
  PopupCloseOption,
} from '../../../components/PopupRenderer';
import './AdminPopupsPanel.css';

const API = getApiBaseUrl();
const ALL_TRIGGERS: PopupTrigger[] = ['HOME_ENTRY', 'POST_LOGIN'];
const ALL_CLOSE_OPTIONS: PopupCloseOption[] = ['HIDE_TODAY', 'HIDE_FOREVER'];

const TRIGGER_LABEL: Record<PopupTrigger, string> = {
  HOME_ENTRY: '홈 첫 진입',
  POST_LOGIN: '로그인 직후',
};

const CLOSE_LABEL: Record<PopupCloseOption, string> = {
  HIDE_TODAY: '오늘 하루 보지 않기',
  HIDE_FOREVER: '다시 보지 않기',
  CLOSE: 'X 닫기',
};

interface FormState {
  type: PopupType;
  title: string;
  content: string;
  imageUrl: string;
  triggers: PopupTrigger[];
  closeOptions: PopupCloseOption[];
  priority: number;
  isActive: boolean;
  startY: string;
  startM: string;
  startD: string;
  endY: string;
  endM: string;
  endD: string;
}

const emptyForm = (): FormState => ({
  type: 'TEXT_ONLY',
  title: '',
  content: '',
  imageUrl: '',
  triggers: ['HOME_ENTRY', 'POST_LOGIN'],
  closeOptions: ['HIDE_TODAY', 'HIDE_FOREVER'],
  priority: 0,
  isActive: true,
  startY: '',
  startM: '',
  startD: '',
  endY: '',
  endM: '',
  endD: '',
});

const formatDateTime = (iso?: string | null) => {
  if (!iso) return '-';
  const d = new Date(iso);
  if (!Number.isFinite(d.getTime())) return '-';
  return d.toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  });
};

const splitIsoToYMD = (iso?: string | null): { y: string; m: string; d: string } => {
  if (!iso) return { y: '', m: '', d: '' };
  const dt = new Date(iso);
  if (!Number.isFinite(dt.getTime())) return { y: '', m: '', d: '' };
  return {
    y: String(dt.getFullYear()),
    m: String(dt.getMonth() + 1),
    d: String(dt.getDate()),
  };
};

const ymdToIso = (y: string, m: string, d: string, endOfDay: boolean): string | null => {
  if (!y || !m || !d) return null;
  const dt = new Date(
    parseInt(y, 10),
    parseInt(m, 10) - 1,
    parseInt(d, 10),
    endOfDay ? 23 : 0,
    endOfDay ? 59 : 0,
    endOfDay ? 59 : 0
  );
  if (!Number.isFinite(dt.getTime())) return null;
  return dt.toISOString();
};

const YEAR_OPTIONS: number[] = (() => {
  const now = new Date().getFullYear();
  const arr: number[] = [];
  for (let y = now; y <= now + 50; y++) arr.push(y);
  return arr;
})();

const MONTH_OPTIONS: number[] = Array.from({ length: 12 }, (_, i) => i + 1);

const daysInMonth = (year: string, month: string): number => {
  const y = parseInt(year, 10);
  const m = parseInt(month, 10);
  if (!Number.isFinite(y) || !Number.isFinite(m)) return 31;
  return new Date(y, m, 0).getDate();
};

const AdminPopupsPanel: React.FC = () => {
  const { t } = useTranslation();
  const { showAlert } = useAlert();

  const [popups, setPopups] = useState<PopupItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);

  const loadPopups = useCallback(async () => {
    setIsLoading(true);
    try {
      const resp = await fetch(`${API}/api/admin/popups`, { credentials: 'include' });
      if (resp.ok) {
        const data: PopupItem[] = await resp.json();
        setPopups(data);
      } else {
        showAlert({ message: '팝업 목록 조회 실패', type: 'error' });
      }
    } catch {
      showAlert({ message: '네트워크 오류가 발생했습니다', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [showAlert]);

  useEffect(() => {
    loadPopups();
  }, [loadPopups]);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setIsModalOpen(true);
  };

  const openEdit = (p: PopupItem) => {
    setEditingId(p.id);
    const s = splitIsoToYMD(p.startAt);
    const e = splitIsoToYMD(p.endAt);
    setForm({
      type: p.type,
      title: p.title,
      content: p.content,
      imageUrl: p.imageUrl ?? '',
      triggers: p.triggers,
      closeOptions: p.closeOptions,
      priority: p.priority,
      isActive: p.isActive,
      startY: s.y, startM: s.m, startD: s.d,
      endY: e.y, endM: e.m, endD: e.d,
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingId(null);
    setForm(emptyForm());
  };

  const toggleTrigger = (tr: PopupTrigger) => {
    setForm((prev) => ({
      ...prev,
      triggers: prev.triggers.includes(tr)
        ? prev.triggers.filter((x) => x !== tr)
        : [...prev.triggers, tr],
    }));
  };

  const toggleClose = (c: PopupCloseOption) => {
    setForm((prev) => ({
      ...prev,
      closeOptions: prev.closeOptions.includes(c)
        ? prev.closeOptions.filter((x) => x !== c)
        : [...prev.closeOptions, c],
    }));
  };

  const handleImageUpload = async (file: File) => {
    if (file.size > 5 * 1024 * 1024) {
      showAlert({ message: '파일 크기는 5MB 이하여야 합니다', type: 'error' });
      return;
    }
    setIsUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const resp = await fetch(`${API}/api/admin/popups/images`, {
        method: 'POST',
        credentials: 'include',
        body: fd,
      });
      if (!resp.ok) {
        const body = await resp.json().catch(() => null);
        showAlert({ message: body?.error || '이미지 업로드 실패', type: 'error' });
        return;
      }
      const body = await resp.json();
      setForm((prev) => ({ ...prev, imageUrl: body.imageUrl }));
    } catch {
      showAlert({ message: '이미지 업로드 중 오류', type: 'error' });
    } finally {
      setIsUploading(false);
    }
  };

  const handleSubmit = async () => {
    if (!form.title.trim()) { showAlert({ message: '제목을 입력해주세요', type: 'error' }); return; }
    if (form.title.length > 100) { showAlert({ message: '제목은 100자 이하여야 합니다', type: 'error' }); return; }
    if (!form.content.trim()) { showAlert({ message: '본문을 입력해주세요', type: 'error' }); return; }
    if (form.content.length > 1000) { showAlert({ message: '본문은 1000자 이하여야 합니다', type: 'error' }); return; }
    if (form.type === 'IMAGE_TEXT' && !form.imageUrl) {
      showAlert({ message: '이미지를 업로드해주세요', type: 'error' });
      return;
    }
    if (form.triggers.length === 0) { showAlert({ message: '트리거를 1개 이상 선택해주세요', type: 'error' }); return; }
    if (form.closeOptions.length === 0) { showAlert({ message: '닫기 옵션을 1개 이상 선택해주세요', type: 'error' }); return; }

    setIsSaving(true);
    try {
      const payload = {
        type: form.type,
        title: form.title.trim(),
        content: form.content,
        imageUrl: form.type === 'IMAGE_TEXT' ? form.imageUrl : null,
        triggers: form.triggers,
        closeOptions: form.closeOptions,
        priority: form.priority,
        isActive: form.isActive,
        startAt: ymdToIso(form.startY, form.startM, form.startD, false),
        endAt: ymdToIso(form.endY, form.endM, form.endD, true),
      };
      const url = editingId
        ? `${API}/api/admin/popups/${editingId}`
        : `${API}/api/admin/popups`;
      const method = editingId ? 'PUT' : 'POST';
      const resp = await fetch(url, {
        method,
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!resp.ok) {
        const body = await resp.json().catch(() => null);
        showAlert({ message: body?.error || '저장 실패', type: 'error' });
        return;
      }
      showAlert({ message: editingId ? '수정되었습니다' : '등록되었습니다', type: 'success' });
      closeModal();
      await loadPopups();
    } catch {
      showAlert({ message: '저장 중 오류', type: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (p: PopupItem) => {
    if (!window.confirm(`팝업 "${p.title}" 을(를) 삭제하시겠습니까?`)) return;
    try {
      const resp = await fetch(`${API}/api/admin/popups/${p.id}`, {
        method: 'DELETE',
        credentials: 'include',
      });
      if (!resp.ok) {
        showAlert({ message: '삭제 실패', type: 'error' });
        return;
      }
      showAlert({ message: '삭제되었습니다', type: 'success' });
      await loadPopups();
    } catch {
      showAlert({ message: '네트워크 오류', type: 'error' });
    }
  };

  const sortedPopups = useMemo(
    () => [...popups].sort((a, b) => a.priority - b.priority || a.id - b.id),
    [popups]
  );

  return (
    <>
      <div className="info-card admin-section-card wide">
        <div className="card-header">
          <h2 className="card-title">{t('myPage.menu.adminPopups')}</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span className="admin-count">{popups.length}개</span>
            <button className="edit-btn" onClick={openCreate}>+ 팝업 추가</button>
          </div>
        </div>

        {isLoading ? (
          <div className="admin-loading">데이터 로딩 중...</div>
        ) : (
          <div className="admin-table-wrapper">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>제목</th>
                  <th>타입</th>
                  <th>트리거</th>
                  <th>일정</th>
                  <th>우선순위</th>
                  <th>상태</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {sortedPopups.length > 0 ? sortedPopups.map((p) => (
                  <tr key={p.id}>
                    <td>{p.id}</td>
                    <td>{p.title}</td>
                    <td>{p.type === 'IMAGE_TEXT' ? '이미지' : '문구'}</td>
                    <td>{p.triggers.map((tr) => TRIGGER_LABEL[tr]).join(', ')}</td>
                    <td>{formatDateTime(p.startAt)} ~ {formatDateTime(p.endAt)}</td>
                    <td>{p.priority}</td>
                    <td>
                      <span className={`status-badge ${p.isActive ? 'status-active' : 'status-inactive'}`}>
                        {p.isActive ? '활성' : '비활성'}
                      </span>
                    </td>
                    <td>
                      <div className="action-btn-group">
                        <button className="action-btn edit" onClick={() => openEdit(p)}>수정</button>
                        <button className="action-btn delete" onClick={() => handleDelete(p)}>삭제</button>
                      </div>
                    </td>
                  </tr>
                )) : (
                  <tr><td colSpan={8} className="empty-row">등록된 팝업이 없습니다.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 팝업 등록/수정 모달 */}
      {isModalOpen && (
        <div className="admin-modal-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) closeModal(); }}>
          <div className="admin-modal">
            <button className="admin-modal-close" onClick={closeModal}>&times;</button>
            <div className="admin-modal-header">
              <h3>{editingId ? '팝업 수정' : '팝업 추가'}</h3>
            </div>
            <div className="admin-modal-body">
              <div className="form-group">
                <label>타입 <span>*</span></label>
                <select
                  className="admin-modal-input"
                  value={form.type}
                  onChange={(e) => setForm({ ...form, type: e.target.value as PopupType })}
                >
                  <option value="TEXT_ONLY">문구 (제목 + 본문)</option>
                  <option value="IMAGE_TEXT">이미지 (제목 + 이미지 + 본문)</option>
                </select>
              </div>

              {form.type === 'IMAGE_TEXT' && (
                <div className="form-group">
                  <label>이미지 <span>*</span></label>
                  <div className="popup-input-stack">
                    <input
                      type="file"
                      className="admin-modal-input"
                      accept="image/jpeg,image/png,image/gif,image/webp"
                      onChange={(e) => {
                        const f = e.target.files?.[0];
                        if (f) handleImageUpload(f);
                      }}
                      disabled={isUploading}
                    />
                    <span className="admin-modal-input-hint">최대 5MB · jpg / png / gif / webp</span>
                    {isUploading && <div className="popup-upload-status">업로드 중...</div>}
                    {form.imageUrl && (
                      <div className="popup-image-preview">
                        <img src={`${API}${form.imageUrl}`} alt="미리보기" />
                        <div className="popup-image-url">{form.imageUrl}</div>
                      </div>
                    )}
                  </div>
                </div>
              )}

              <div className="form-group">
                <label>제목 <span>*</span></label>
                <input
                  type="text"
                  className="admin-modal-input"
                  maxLength={100}
                  value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  placeholder="팝업 제목"
                />
                <span className="admin-modal-input-hint">{form.title.length} / 100</span>
              </div>

              <div className="form-group">
                <label>본문 <span>*</span></label>
                <div className="popup-input-stack">
                  <textarea
                    className="admin-modal-input"
                    maxLength={1000}
                    rows={5}
                    value={form.content}
                    onChange={(e) => setForm({ ...form, content: e.target.value })}
                    placeholder="팝업 본문 (개행은 줄바꿈으로 표시됩니다)"
                  />
                  <span className="admin-modal-input-hint">{form.content.length} / 1000</span>
                </div>
              </div>

              <div className="form-group">
                <label>표시 트리거 <span>*</span></label>
                <div className="popup-check-group">
                  {ALL_TRIGGERS.map((tr) => (
                    <label key={tr} className="popup-check-label">
                      <input
                        type="checkbox"
                        checked={form.triggers.includes(tr)}
                        onChange={() => toggleTrigger(tr)}
                      />
                      {TRIGGER_LABEL[tr]}
                    </label>
                  ))}
                </div>
              </div>

              <div className="form-group">
                <label>닫기 옵션 <span>*</span></label>
                <div className="popup-check-group">
                  {ALL_CLOSE_OPTIONS.map((c) => (
                    <label key={c} className="popup-check-label">
                      <input
                        type="checkbox"
                        checked={form.closeOptions.includes(c)}
                        onChange={() => toggleClose(c)}
                      />
                      {CLOSE_LABEL[c]}
                    </label>
                  ))}
                </div>
              </div>

              <div className="admin-modal-form-row">
                <div className="form-group">
                  <label>우선순위</label>
                  <input
                    type="number"
                    className="admin-modal-input"
                    value={form.priority}
                    onChange={(e) => setForm({ ...form, priority: parseInt(e.target.value || '0', 10) })}
                  />
                  <span className="admin-modal-input-hint">낮을수록 먼저 표시</span>
                </div>
                <div className="form-group">
                  <label>활성 상태</label>
                  <label className="popup-check-label" style={{ marginTop: '6px' }}>
                    <input
                      type="checkbox"
                      checked={form.isActive}
                      onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                    />
                    활성화
                  </label>
                </div>
              </div>

              <div className="form-group">
                <label>공지 일정</label>
                <div className="popup-schedule-row">
                  <div className="popup-date-group">
                    <select
                      className="admin-modal-input popup-date-sel popup-date-sel-y"
                      value={form.startY}
                      onChange={(e) => setForm({ ...form, startY: e.target.value })}
                    >
                      <option value="">년</option>
                      {YEAR_OPTIONS.map((y) => (
                        <option key={y} value={y}>{y}</option>
                      ))}
                    </select>
                    <select
                      className="admin-modal-input popup-date-sel"
                      value={form.startM}
                      onChange={(e) => setForm({ ...form, startM: e.target.value })}
                    >
                      <option value="">월</option>
                      {MONTH_OPTIONS.map((m) => (
                        <option key={m} value={m}>{m}</option>
                      ))}
                    </select>
                    <select
                      className="admin-modal-input popup-date-sel"
                      value={form.startD}
                      onChange={(e) => setForm({ ...form, startD: e.target.value })}
                    >
                      <option value="">일</option>
                      {Array.from({ length: daysInMonth(form.startY, form.startM) }, (_, i) => i + 1).map((d) => (
                        <option key={d} value={d}>{d}</option>
                      ))}
                    </select>
                  </div>
                  <span className="popup-schedule-sep">~</span>
                  <div className="popup-date-group">
                    <select
                      className="admin-modal-input popup-date-sel popup-date-sel-y"
                      value={form.endY}
                      onChange={(e) => setForm({ ...form, endY: e.target.value })}
                    >
                      <option value="">년</option>
                      {YEAR_OPTIONS.map((y) => (
                        <option key={y} value={y}>{y}</option>
                      ))}
                    </select>
                    <select
                      className="admin-modal-input popup-date-sel"
                      value={form.endM}
                      onChange={(e) => setForm({ ...form, endM: e.target.value })}
                    >
                      <option value="">월</option>
                      {MONTH_OPTIONS.map((m) => (
                        <option key={m} value={m}>{m}</option>
                      ))}
                    </select>
                    <select
                      className="admin-modal-input popup-date-sel"
                      value={form.endD}
                      onChange={(e) => setForm({ ...form, endD: e.target.value })}
                    >
                      <option value="">일</option>
                      {Array.from({ length: daysInMonth(form.endY, form.endM) }, (_, i) => i + 1).map((d) => (
                        <option key={d} value={d}>{d}</option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>
            </div>
            <div className="admin-modal-footer">
              <button className="cancel-btn" onClick={closeModal} disabled={isSaving}>취소</button>
              <button className="save-btn" onClick={handleSubmit} disabled={isSaving}>
                {isSaving ? '저장 중...' : editingId ? '수정' : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default AdminPopupsPanel;

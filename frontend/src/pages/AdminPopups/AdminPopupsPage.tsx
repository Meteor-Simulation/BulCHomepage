import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/Header';
import { useAuth } from '../../context/AuthContext';
import { useAlert } from '../../components/AlertProvider';
import { getApiBaseUrl } from '../../utils/api';
import {
  PopupItem,
  PopupType,
  PopupTrigger,
  PopupCloseOption,
} from '../../components/PopupRenderer';
import './AdminPopupsPage.css';

const API = getApiBaseUrl();
const ALL_TRIGGERS: PopupTrigger[] = ['HOME_ENTRY', 'POST_LOGIN'];
const ALL_CLOSE_OPTIONS: PopupCloseOption[] = ['HIDE_TODAY', 'HIDE_FOREVER', 'CLOSE'];

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
  startAt: string;
  endAt: string;
}

const emptyForm = (): FormState => ({
  type: 'TEXT_ONLY',
  title: '',
  content: '',
  imageUrl: '',
  triggers: ['HOME_ENTRY'],
  closeOptions: ['CLOSE'],
  priority: 0,
  isActive: true,
  startAt: '',
  endAt: '',
});

const formatDateTime = (iso?: string | null) => {
  if (!iso) return '-';
  const d = new Date(iso);
  if (!Number.isFinite(d.getTime())) return '-';
  return d.toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  });
};

const toDatetimeLocal = (iso?: string | null): string => {
  if (!iso) return '';
  const d = new Date(iso);
  if (!Number.isFinite(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const fromDatetimeLocal = (s: string): string | null => {
  if (!s) return null;
  const d = new Date(s);
  if (!Number.isFinite(d.getTime())) return null;
  return d.toISOString();
};

const AdminPopupsPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn, isAuthReady, isAdmin } = useAuth();
  const { showAlert } = useAlert();

  const [popups, setPopups] = useState<PopupItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);

  useEffect(() => {
    if (!isAuthReady) return;
    if (!isLoggedIn || !isAdmin) {
      navigate('/', { replace: true });
    }
  }, [isAuthReady, isLoggedIn, isAdmin, navigate]);

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
    if (isAuthReady && isLoggedIn && isAdmin) {
      loadPopups();
    }
  }, [isAuthReady, isLoggedIn, isAdmin, loadPopups]);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setIsFormOpen(true);
  };

  const openEdit = (p: PopupItem) => {
    setEditingId(p.id);
    setForm({
      type: p.type,
      title: p.title,
      content: p.content,
      imageUrl: p.imageUrl ?? '',
      triggers: p.triggers,
      closeOptions: p.closeOptions,
      priority: p.priority,
      isActive: p.isActive,
      startAt: toDatetimeLocal(p.startAt),
      endAt: toDatetimeLocal(p.endAt),
    });
    setIsFormOpen(true);
  };

  const cancelForm = () => {
    setIsFormOpen(false);
    setEditingId(null);
    setForm(emptyForm());
  };

  const toggleTrigger = (t: PopupTrigger) => {
    setForm((prev) => ({
      ...prev,
      triggers: prev.triggers.includes(t)
        ? prev.triggers.filter((x) => x !== t)
        : [...prev.triggers, t],
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
        startAt: fromDatetimeLocal(form.startAt),
        endAt: fromDatetimeLocal(form.endAt),
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
      showAlert({ message: editingId ? '수정되었습니다' : '생성되었습니다', type: 'success' });
      cancelForm();
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

  if (!isAuthReady) return null;
  if (!isLoggedIn || !isAdmin) return null;

  return (
    <div className="admin-popups-page">
      <Header />
      <div className="admin-popups-container">
        <div className="admin-popups-header">
          <h1>팝업 관리</h1>
          {!isFormOpen && (
            <button type="button" className="btn-primary" onClick={openCreate}>
              + 새 팝업
            </button>
          )}
        </div>

        {isFormOpen && (
          <div className="admin-popups-form-card">
            <h2>{editingId ? '팝업 수정' : '새 팝업'}</h2>

            <div className="form-row">
              <label>타입</label>
              <div className="radio-group">
                <label>
                  <input
                    type="radio"
                    name="type"
                    checked={form.type === 'TEXT_ONLY'}
                    onChange={() => setForm({ ...form, type: 'TEXT_ONLY' })}
                  />
                  텍스트만 (제목 + 본문)
                </label>
                <label>
                  <input
                    type="radio"
                    name="type"
                    checked={form.type === 'IMAGE_TEXT'}
                    onChange={() => setForm({ ...form, type: 'IMAGE_TEXT' })}
                  />
                  이미지 + 텍스트 (제목 + 이미지 + 본문)
                </label>
              </div>
            </div>

            <div className="form-row">
              <label>제목 (최대 100자)</label>
              <input
                type="text"
                maxLength={100}
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="팝업 제목"
              />
              <div className="char-count">{form.title.length} / 100</div>
            </div>

            <div className="form-row">
              <label>본문 (최대 1000자, 개행은 줄바꿈으로 표시됨)</label>
              <textarea
                maxLength={1000}
                rows={6}
                value={form.content}
                onChange={(e) => setForm({ ...form, content: e.target.value })}
                placeholder="팝업 본문"
              />
              <div className="char-count">{form.content.length} / 1000</div>
            </div>

            {form.type === 'IMAGE_TEXT' && (
              <div className="form-row">
                <label>이미지 (최대 5MB · jpg/png/gif/webp)</label>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/gif,image/webp"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) handleImageUpload(f);
                  }}
                  disabled={isUploading}
                />
                {isUploading && <div className="upload-status">업로드 중...</div>}
                {form.imageUrl && (
                  <div className="image-preview">
                    <img src={`${API}${form.imageUrl}`} alt="미리보기" />
                    <div className="image-url">{form.imageUrl}</div>
                  </div>
                )}
              </div>
            )}

            <div className="form-row">
              <label>표시 트리거 (1개 이상)</label>
              <div className="checkbox-group">
                {ALL_TRIGGERS.map((t) => (
                  <label key={t}>
                    <input
                      type="checkbox"
                      checked={form.triggers.includes(t)}
                      onChange={() => toggleTrigger(t)}
                    />
                    {TRIGGER_LABEL[t]}
                  </label>
                ))}
              </div>
            </div>

            <div className="form-row">
              <label>닫기 옵션 (1개 이상)</label>
              <div className="checkbox-group">
                {ALL_CLOSE_OPTIONS.map((c) => (
                  <label key={c}>
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

            <div className="form-row-inline">
              <div>
                <label>우선순위 (낮을수록 먼저 표시)</label>
                <input
                  type="number"
                  value={form.priority}
                  onChange={(e) => setForm({ ...form, priority: parseInt(e.target.value || '0', 10) })}
                />
              </div>
              <div>
                <label>활성</label>
                <input
                  type="checkbox"
                  checked={form.isActive}
                  onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                />
              </div>
            </div>

            <div className="form-row-inline">
              <div>
                <label>시작 일시 (옵션)</label>
                <input
                  type="datetime-local"
                  value={form.startAt}
                  onChange={(e) => setForm({ ...form, startAt: e.target.value })}
                />
              </div>
              <div>
                <label>종료 일시 (옵션)</label>
                <input
                  type="datetime-local"
                  value={form.endAt}
                  onChange={(e) => setForm({ ...form, endAt: e.target.value })}
                />
              </div>
            </div>

            <div className="form-actions">
              <button type="button" className="btn-secondary" onClick={cancelForm} disabled={isSaving}>
                취소
              </button>
              <button type="button" className="btn-primary" onClick={handleSubmit} disabled={isSaving}>
                {isSaving ? '저장 중...' : editingId ? '수정' : '생성'}
              </button>
            </div>
          </div>
        )}

        <div className="admin-popups-table-wrap">
          {isLoading ? (
            <div className="admin-loading">데이터 로딩 중...</div>
          ) : (
            <table className="admin-popups-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>제목</th>
                  <th>타입</th>
                  <th>트리거</th>
                  <th>일정</th>
                  <th>우선순위</th>
                  <th>활성</th>
                  <th>액션</th>
                </tr>
              </thead>
              <tbody>
                {sortedPopups.length > 0 ? sortedPopups.map((p) => (
                  <tr key={p.id}>
                    <td>{p.id}</td>
                    <td>{p.title}</td>
                    <td>{p.type === 'IMAGE_TEXT' ? '이미지+텍스트' : '텍스트'}</td>
                    <td>{p.triggers.map((t) => TRIGGER_LABEL[t]).join(', ')}</td>
                    <td>{formatDateTime(p.startAt)} ~ {formatDateTime(p.endAt)}</td>
                    <td>{p.priority}</td>
                    <td>{p.isActive ? '활성' : '비활성'}</td>
                    <td>
                      <button type="button" className="btn-link" onClick={() => openEdit(p)}>수정</button>
                      <button type="button" className="btn-link btn-link-danger" onClick={() => handleDelete(p)}>삭제</button>
                    </td>
                  </tr>
                )) : (
                  <tr><td colSpan={8} className="empty-row">등록된 팝업이 없습니다.</td></tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};

export default AdminPopupsPage;

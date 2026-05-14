import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import { TextStyle } from '@tiptap/extension-text-style';
import FontSize from './extensions/FontSize';
import MathNode from './extensions/MathNode';
import InfoPanel from './extensions/InfoPanel';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../context/AuthContext';
import { useAlert } from '../../components/AlertProvider';
import { API_URL } from '../../utils/api';
import Header from '../../components/Header';
import ImageAnnotator, { AnnotatedImage } from './components/ImageAnnotator';
import MathEditorPopup from './components/MathEditorPopup';
import './PostEditorPage.css';

const PostEditorPage: React.FC = () => {
  const { t } = useTranslation();
  const { showAlert } = useAlert();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { isAuthReady, isAdmin } = useAuth();
  const isEditMode = !!id;

  const [title, setTitle] = useState('');
  const [visibility, setVisibility] = useState('PUBLIC');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(isEditMode);
  const [annotatedImages, setAnnotatedImages] = useState<AnnotatedImage[]>([]);

  const [mathPopup, setMathPopup] = useState<{ isOpen: boolean; editLatex: string; editCallback: ((latex: string) => void) | null }>({
    isOpen: false, editLatex: '', editCallback: null,
  });

  const editor = useEditor({
    extensions: [
      StarterKit,
      Image.configure({ inline: false, allowBase64: false }),
      TextStyle,
      FontSize,
      MathNode,
      InfoPanel,
    ],
    content: '',
  });

  // 수식 노드 클릭 → 편집 팝업
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      if (detail) {
        setMathPopup({
          isOpen: true,
          editLatex: detail.latex,
          editCallback: (newLatex: string) => {
            detail.updateAttributes({ latex: newLatex });
          },
        });
      }
    };
    document.addEventListener('edit-math', handler);
    return () => document.removeEventListener('edit-math', handler);
  }, []);

  useEffect(() => {
    if (isAuthReady && !isAdmin) {
      showAlert({ message: t('alerts.staffPermissionRequired'), type: 'warning' });
      navigate('/board');
    }
  }, [isAuthReady, isAdmin, navigate]);

  // 수정 모드: 기존 글 로드
  useEffect(() => {
    if (!isEditMode || !editor) return;

    const fetchPost = async () => {
      try {
        const response = await fetch(`${API_URL}/api/v1/posts/${id}`, {
          credentials: 'include',
        });
        if (response.ok) {
          const result = await response.json();
          if (result.success && result.data && !result.data.restricted) {
            setTitle(result.data.title);
            setVisibility(result.data.visibility);
            editor.commands.setContent(result.data.contentHtml || '');
          } else {
            setError(t('board.editor.errors.noPermission'));
          }
        }
      } catch {
        setError(t('board.editor.errors.loadFailed'));
      } finally {
        setIsLoading(false);
      }
    };

    fetchPost();
  }, [id, isEditMode, editor]);

  // 이미지 추가 → 주석 컴포넌트 생성
  const handleImageUpload = useCallback(() => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/jpeg,image/png,image/gif,image/webp';
    input.onchange = (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;

      if (file.size > 5 * 1024 * 1024) {
        showAlert({ message: t('alerts.imageSizeLimit'), type: 'warning' });
        return;
      }

      const imageUrl = URL.createObjectURL(file);
      setAnnotatedImages(prev => [...prev, { imageUrl, file, markers: [] }]);
    };
    input.click();
  }, []);

  // 글 저장
  const handleSubmit = async () => {
    if (!title.trim()) {
      setError(t('board.editor.errors.emptyTitle'));
      return;
    }
    if (!editor || editor.isEmpty) {
      setError(t('board.editor.errors.emptyContent'));
      return;
    }

    setIsSubmitting(true);
    setError('');

    try {
      const url = isEditMode ? `${API_URL}/api/v1/posts/${id}` : `${API_URL}/api/v1/posts`;
      const method = isEditMode ? 'PUT' : 'POST';

      const response = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          title: title.trim(),
          contentHtml: editor.getHTML(),
          visibility,
          annotatedImagesJson: annotatedImages.length > 0
            ? JSON.stringify(annotatedImages.map(img => ({
                imageUrl: img.imageUrl,
                markers: img.markers,
              })))
            : null,
        }),
      });

      if (response.ok) {
        const result = await response.json();
        if (result.success && result.data) {
          navigate(`/board/${result.data.id}`);
        }
      } else {
        const result = await response.json().catch(() => null);
        setError(result?.message || t('board.editor.errors.saveFailed'));
      }
    } catch {
      setError(t('board.editor.errors.saveError'));
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="post-editor-page">
        <Header />
        <div className="post-editor-container">
          <div className="post-editor-loading">{t('board.loading')}</div>
        </div>
      </div>
    );
  }

  return (
    <div className="post-editor-page">
      <Header />
      <div className="post-editor-container">
        <div className="post-editor-form">
          <input
            type="text"
            className="post-title-input"
            placeholder={t('board.editor.titlePlaceholder')}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={200}
          />

          {/* 에디터 툴바 */}
          {editor && (
            <div className="editor-toolbar">
              {/* 폰트 크기 드롭박스 */}
              <select
                className="toolbar-select"
                value={editor.getAttributes('textStyle').fontSize || '14px'}
                onChange={(e) => {
                  const size = e.target.value;
                  if (size === '14px') {
                    editor.chain().focus().unsetFontSize().run();
                  } else {
                    editor.chain().focus().setFontSize(size).run();
                  }
                }}
              >
                <option value="12px">12px</option>
                <option value="14px">14px</option>
                <option value="16px">16px</option>
                <option value="18px">18px</option>
                <option value="20px">20px</option>
                <option value="24px">24px</option>
                <option value="28px">28px</option>
                <option value="32px">32px</option>
              </select>

              <select
                className="toolbar-select"
                value={
                  editor.isActive('heading', { level: 1 }) ? '1' :
                  editor.isActive('heading', { level: 2 }) ? '2' :
                  editor.isActive('heading', { level: 3 }) ? '3' : '0'
                }
                onChange={(e) => {
                  const level = parseInt(e.target.value);
                  if (level === 0) {
                    editor.chain().focus().setParagraph().run();
                  } else {
                    editor.chain().focus().toggleHeading({ level: level as 1 | 2 | 3 }).run();
                  }
                }}
              >
                <option value="0">{t('board.editor.heading.paragraph')}</option>
                <option value="1">{t('board.editor.heading.h1')}</option>
                <option value="2">{t('board.editor.heading.h2')}</option>
                <option value="3">{t('board.editor.heading.h3')}</option>
              </select>

              <span className="toolbar-divider" />

              {/* 텍스트 스타일 */}
              <button
                type="button"
                className={editor.isActive('bold') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleBold().run()}
                title={t('board.editor.toolbar.bold')}
              >
                <strong>B</strong>
              </button>
              <button
                type="button"
                className={editor.isActive('italic') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleItalic().run()}
                title={t('board.editor.toolbar.italic')}
              >
                <em>I</em>
              </button>
              <button
                type="button"
                className={editor.isActive('strike') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleStrike().run()}
                title={t('board.editor.toolbar.strike')}
              >
                <s>S</s>
              </button>

              <span className="toolbar-divider" />

              {/* 목록 */}
              <button
                type="button"
                className={editor.isActive('bulletList') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleBulletList().run()}
                title={t('board.editor.toolbar.bulletList')}
              >
                &#8226;
              </button>
              <button
                type="button"
                className={editor.isActive('orderedList') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleOrderedList().run()}
                title={t('board.editor.toolbar.orderedList')}
              >
                1.
              </button>

              <span className="toolbar-divider" />

              {/* 블록 */}
              <button
                type="button"
                className={editor.isActive('blockquote') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleBlockquote().run()}
                title={t('board.editor.toolbar.blockquote')}
              >
                &ldquo;
              </button>
              <button
                type="button"
                className={editor.isActive('codeBlock') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleCodeBlock().run()}
                title={t('board.editor.toolbar.codeBlock')}
              >
                {'</>'}
              </button>

              <button
                type="button"
                className={editor.isActive('infoPanel') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleInfoPanel().run()}
                title={t('board.editor.toolbar.infoPanel')}
              >
                !
              </button>

              <span className="toolbar-divider" />

              {/* 수식 */}
              <button
                type="button"
                onClick={() => setMathPopup({ isOpen: true, editLatex: '', editCallback: null })}
                title={t('board.editor.toolbar.math')}
              >
                Σ
              </button>

              {/* 공개 범위 토글 — 오른쪽 끝 */}
              <div className="toolbar-spacer" />
              <button
                type="button"
                className={`toolbar-lock-btn ${visibility !== 'PUBLIC' ? 'locked' : ''}`}
                onClick={() => setVisibility(visibility === 'PUBLIC' ? 'MEMBER' : 'PUBLIC')}
                title={visibility === 'PUBLIC' ? t('board.editor.visibility.publicToggle') : t('board.editor.visibility.memberToggle')}
              >
                {visibility === 'PUBLIC' ? (
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                    <path d="M7 11V7a5 5 0 0 1 9.9-1" />
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                  </svg>
                )}
              </button>
            </div>
          )}

          <EditorContent editor={editor} className="post-editor-content" />

          {/* 주석 이미지 목록 */}
          {annotatedImages.length > 0 && (
            <div className="annotated-images-list">
              {annotatedImages.map((img, i) => (
                <ImageAnnotator
                  key={i}
                  image={img}
                  index={i}
                  onChange={(updated) => {
                    setAnnotatedImages(prev => prev.map((item, idx) => idx === i ? updated : item));
                  }}
                  onDelete={() => {
                    setAnnotatedImages(prev => prev.filter((_, idx) => idx !== i));
                  }}
                />
              ))}
            </div>
          )}

          {/* 이미지 추가 버튼 */}
          <button type="button" className="post-image-add-btn" onClick={handleImageUpload}>
            {t('board.editor.addImage')}
          </button>


          {error && <p className="post-editor-error">{error}</p>}

          <div className="post-editor-actions">
            <button className="post-cancel-btn" onClick={() => navigate(-1)} disabled={isSubmitting}>{t('board.actions.cancel')}</button>
            <button
              className="post-submit-btn"
              onClick={handleSubmit}
              disabled={isSubmitting}
            >
              {isSubmitting ? t('board.editor.saving') : isEditMode ? t('board.actions.edit') : t('board.editor.publish')}
            </button>
          </div>

          {/* 저장 중 화면 잠금 오버레이 */}
          {isSubmitting && (
            <div className="post-saving-overlay">
              <div className="post-saving-spinner" />
              <p>{t('board.editor.saving')}</p>
            </div>
          )}
        </div>
      </div>

      {/* 수식 편집 팝업 */}
      <MathEditorPopup
        isOpen={mathPopup.isOpen}
        initialLatex={mathPopup.editLatex}
        onInsert={(latex) => {
          if (mathPopup.editCallback) {
            // 기존 수식 편집
            mathPopup.editCallback(latex);
          } else if (editor) {
            // 새 수식 삽입
            editor.chain().focus().insertMath(latex).run();
          }
        }}
        onClose={() => setMathPopup({ isOpen: false, editLatex: '', editCallback: null })}
      />
    </div>
  );
};

export default PostEditorPage;

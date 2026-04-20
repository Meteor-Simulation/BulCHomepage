import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import { useAuth } from '../../context/AuthContext';
import { API_URL } from '../../utils/api';
import Header from '../../components/Header';
import './PostEditorPage.css';

const PostEditorPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { isLoggedIn, isAuthReady, isAdmin } = useAuth();
  const isEditMode = !!id;

  const [title, setTitle] = useState('');
  const [visibility, setVisibility] = useState('PUBLIC');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(isEditMode);

  const editor = useEditor({
    extensions: [
      StarterKit,
      Image.configure({ inline: false, allowBase64: false }),
    ],
    content: '',
  });

  // 로그인 체크
  useEffect(() => {
    if (isAuthReady && !isLoggedIn) {
      alert('로그인이 필요합니다.');
      navigate('/board');
    }
  }, [isAuthReady, isLoggedIn, navigate]);

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
            setError('수정 권한이 없습니다.');
          }
        }
      } catch {
        setError('게시글을 불러올 수 없습니다.');
      } finally {
        setIsLoading(false);
      }
    };

    fetchPost();
  }, [id, isEditMode, editor]);

  // 이미지 업로드
  const handleImageUpload = useCallback(async () => {
    if (!editor) return;

    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/jpeg,image/png,image/gif,image/webp';
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;

      if (file.size > 5 * 1024 * 1024) {
        alert('이미지 크기는 5MB 이하여야 합니다.');
        return;
      }

      const formData = new FormData();
      formData.append('file', file);

      try {
        const response = await fetch(`${API_URL}/api/v1/posts/images`, {
          method: 'POST',
          credentials: 'include',
          body: formData,
        });

        if (response.ok) {
          const result = await response.json();
          if (result.success && result.data) {
            editor.chain().focus().setImage({ src: result.data.imageUrl }).run();
          }
        } else {
          alert('이미지 업로드에 실패했습니다.');
        }
      } catch {
        alert('이미지 업로드 중 오류가 발생했습니다.');
      }
    };
    input.click();
  }, [editor]);

  // 글 저장
  const handleSubmit = async () => {
    if (!title.trim()) {
      setError('제목을 입력해주세요.');
      return;
    }
    if (!editor || editor.isEmpty) {
      setError('내용을 입력해주세요.');
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
        }),
      });

      if (response.ok) {
        const result = await response.json();
        if (result.success && result.data) {
          navigate(`/board/${result.data.id}`);
        }
      } else {
        const result = await response.json().catch(() => null);
        setError(result?.message || '저장에 실패했습니다.');
      }
    } catch {
      setError('저장 중 오류가 발생했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="post-editor-page">
        <Header />
        <div className="post-editor-container">
          <div className="post-editor-loading">로딩 중...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="post-editor-page">
      <Header />
      <div className="post-editor-container">
        <h1 className="post-editor-title">{isEditMode ? '글 수정' : '글 작성'}</h1>

        <div className="post-editor-form">
          <input
            type="text"
            className="post-title-input"
            placeholder="제목을 입력하세요"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={200}
          />

          <div className="post-visibility-select">
            <label>공개 범위:</label>
            <select value={visibility} onChange={(e) => setVisibility(e.target.value)}>
              <option value="PUBLIC">전체 공개</option>
              <option value="MEMBER">회원 전용</option>
              {isAdmin && <option value="STAFF">스태프 전용</option>}
            </select>
          </div>

          {/* 에디터 툴바 */}
          {editor && (
            <div className="editor-toolbar">
              <button
                type="button"
                className={editor.isActive('bold') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleBold().run()}
                title="볼드"
              >
                <strong>B</strong>
              </button>
              <button
                type="button"
                className={editor.isActive('italic') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleItalic().run()}
                title="이탤릭"
              >
                <em>I</em>
              </button>
              <button
                type="button"
                className={editor.isActive('strike') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleStrike().run()}
                title="취소선"
              >
                <s>S</s>
              </button>
              <span className="toolbar-divider" />
              <button
                type="button"
                className={editor.isActive('heading', { level: 2 }) ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
                title="제목"
              >
                H
              </button>
              <button
                type="button"
                className={editor.isActive('bulletList') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleBulletList().run()}
                title="불릿 목록"
              >
                &#8226;
              </button>
              <button
                type="button"
                className={editor.isActive('orderedList') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleOrderedList().run()}
                title="번호 목록"
              >
                1.
              </button>
              <button
                type="button"
                className={editor.isActive('blockquote') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleBlockquote().run()}
                title="인용"
              >
                &ldquo;
              </button>
              <button
                type="button"
                className={editor.isActive('codeBlock') ? 'active' : ''}
                onClick={() => editor.chain().focus().toggleCodeBlock().run()}
                title="코드 블록"
              >
                {'</>'}
              </button>
              <span className="toolbar-divider" />
              <button type="button" onClick={handleImageUpload} title="이미지 삽입">
                IMG
              </button>
            </div>
          )}

          <EditorContent editor={editor} className="post-editor-content" />

          {error && <p className="post-editor-error">{error}</p>}

          <div className="post-editor-actions">
            <button className="post-cancel-btn" onClick={() => navigate(-1)}>취소</button>
            <button
              className="post-submit-btn"
              onClick={handleSubmit}
              disabled={isSubmitting}
            >
              {isSubmitting ? '저장 중...' : isEditMode ? '수정' : '게시'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PostEditorPage;

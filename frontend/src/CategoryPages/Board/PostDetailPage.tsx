import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { API_URL } from '../../utils/api';
import Header from '../../components/Header';
import ImageAnnotator, { AnnotatedImage } from './components/ImageAnnotator';
import { PostDetail } from './types';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import './PostDetailPage.css';

const renderMathInHtml = (html: string): string => {
  if (!html) return '';
  return html.replace(/<math-node[^>]*data-latex="([^"]*)"[^>]*><\/math-node>/g, (_, latex) => {
    try {
      return katex.renderToString(latex, { throwOnError: false, displayMode: false });
    } catch {
      return latex;
    }
  });
};

const PostDetailPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { isLoggedIn, user, isAdmin } = useAuth();

  const [post, setPost] = useState<PostDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const canModify = isAdmin;

  useEffect(() => {
    const fetchPost = async () => {
      setIsLoading(true);
      try {
        const response = await fetch(`${API_URL}/api/v1/posts/${id}`, {
          credentials: 'include',
        });
        if (response.ok) {
          const result = await response.json();
          if (result.success) {
            setPost(result.data);
          } else {
            setError(result.message || '게시글을 불러올 수 없습니다.');
          }
        } else {
          setError('게시글을 불러올 수 없습니다.');
        }
      } catch {
        setError('게시글을 불러오는 중 오류가 발생했습니다.');
      } finally {
        setIsLoading(false);
      }
    };

    if (id) fetchPost();
  }, [id]);

  const handleDelete = async () => {
    if (!window.confirm('정말 삭제하시겠습니까?')) return;

    try {
      const response = await fetch(`${API_URL}/api/v1/posts/${id}`, {
        method: 'DELETE',
        credentials: 'include',
      });
      if (response.ok) {
        navigate('/board');
      }
    } catch {
      alert('삭제 중 오류가 발생했습니다.');
    }
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleString('ko-KR', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  };

  if (isLoading) {
    return (
      <div className="post-detail-page">
        <Header />
        <div className="post-detail-container">
          <div className="post-loading">로딩 중...</div>
        </div>
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="post-detail-page">
        <Header />
        <div className="post-detail-container">
          <div className="post-error">{error || '게시글을 찾을 수 없습니다.'}</div>
          <button className="post-back-btn" onClick={() => navigate('/board')}>목록으로</button>
        </div>
      </div>
    );
  }

  return (
    <div className="post-detail-page">
      <Header />
      <div className="post-detail-container">
        <article className="post-article">
          <div className="post-detail-header">
            <h1 className="post-detail-title">{post.title}</h1>
            <div className="post-meta">
              <span className="post-author">{post.authorName}</span>
              <span className="post-date">{formatDate(post.createdAt)}</span>
              <span className="post-views">조회 {post.viewCount}</span>
              {post.visibility !== 'PUBLIC' && (
                <span className={`post-visibility ${post.visibility.toLowerCase()}`}>
                  {post.visibility === 'MEMBER' ? '회원' : '스태프'}
                </span>
              )}
            </div>
          </div>

          {post.restricted ? (
            <div className="post-restricted">
              <div className="restricted-icon">🔒</div>
              <p className="restricted-message">{post.restrictedMessage}</p>
              {!isLoggedIn && (
                <p className="restricted-hint">로그인하시면 더 많은 게시글을 열람할 수 있습니다.</p>
              )}
            </div>
          ) : (
            <>
              <div
                className="post-content"
                dangerouslySetInnerHTML={{ __html: renderMathInHtml(post.contentHtml || '') }}
              />
              {post.annotatedImagesJson && (() => {
                try {
                  const images: AnnotatedImage[] = JSON.parse(post.annotatedImagesJson);
                  return images.length > 0 && (
                    <div className="post-annotated-images">
                      {images.map((img, i) => (
                        <ImageAnnotator key={i} image={img} index={i} readOnly />
                      ))}
                    </div>
                  );
                } catch { return null; }
              })()}
            </>
          )}
        </article>

        <div className="post-actions">
          <button className="post-back-btn" onClick={() => navigate('/board')}>확인</button>
          {canModify && !post.restricted && (
            <div className="post-modify-actions">
              <button className="post-edit-btn" onClick={() => navigate(`/board/edit/${post.id}`)}>수정</button>
              <button className="post-delete-btn" onClick={handleDelete}>삭제</button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default PostDetailPage;

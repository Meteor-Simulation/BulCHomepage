import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../context/AuthContext';
import { useAlert } from '../../components/AlertProvider';
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
  const { t, i18n } = useTranslation();
  const { showAlert } = useAlert();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { isLoggedIn, isAdmin } = useAuth();
  const dateLocale = i18n.language && i18n.language.startsWith('en') ? 'en-US' : 'ko-KR';

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
            setError(result.message || t('board.detail.errors.loadFailed'));
          }
        } else {
          setError(t('board.detail.errors.loadFailed'));
        }
      } catch {
        setError(t('board.detail.errors.loadError'));
      } finally {
        setIsLoading(false);
      }
    };

    if (id) fetchPost();
  }, [id]);

  const handleDelete = async () => {
    if (!window.confirm(t('board.detail.confirmDelete'))) return;

    try {
      const response = await fetch(`${API_URL}/api/v1/posts/${id}`, {
        method: 'DELETE',
        credentials: 'include',
      });
      if (response.ok) {
        navigate('/board');
      }
    } catch {
      showAlert({ message: t('alerts.deleteFailed'), type: 'error' });
    }
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleString(dateLocale, {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  };

  if (isLoading) {
    return (
      <div className="post-detail-page">
        <Header />
        <div className="post-detail-container">
          <div className="post-loading">{t('board.loading')}</div>
        </div>
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="post-detail-page">
        <Header />
        <div className="post-detail-container">
          <div className="post-error">{error || t('board.detail.errors.notFound')}</div>
          <button className="post-back-btn" onClick={() => navigate('/board')}>{t('board.detail.backToList')}</button>
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
              <span className="post-views">{t('board.viewCount')} {post.viewCount}</span>
              {post.visibility !== 'PUBLIC' && (
                <span className={`post-visibility ${post.visibility.toLowerCase()}`}>
                  {post.visibility === 'MEMBER' ? t('board.visibility.member') : t('board.visibility.staff')}
                </span>
              )}
            </div>
          </div>

          {post.restricted ? (
            <div className="post-restricted">
              <div className="restricted-icon">🔒</div>
              <p className="restricted-message">{post.restrictedMessage}</p>
              {!isLoggedIn && (
                <p className="restricted-hint">{t('board.detail.restrictedLoginHint')}</p>
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
          <button className="post-back-btn" onClick={() => navigate('/board')}>{t('board.actions.confirm')}</button>
          {canModify && !post.restricted && (
            <div className="post-modify-actions">
              <button className="post-edit-btn" onClick={() => navigate(`/board/edit/${post.id}`)}>{t('board.actions.edit')}</button>
              <button className="post-delete-btn" onClick={handleDelete}>{t('board.actions.delete')}</button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default PostDetailPage;

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { API_URL } from '../../utils/api';
import Header from '../../components/Header';
import AlertModal from '../../components/AlertModal';
import { PostListItem, PostDetail, PostPage, TreeNode } from './types';
import ImageAnnotator, { AnnotatedImage } from './components/ImageAnnotator';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import './BoardPage.css';

// 본문 HTML 내 math-node 태그를 KaTeX로 렌더링
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

// 플랫 목록 → 트리 변환
const buildTree = (posts: PostListItem[], expandedIds: Set<number>): TreeNode[] => {
  const map = new Map<number, TreeNode>();
  const roots: TreeNode[] = [];

  posts.forEach(p => map.set(p.id, { ...p, children: [], expanded: expandedIds.has(p.id) }));

  posts.forEach(p => {
    const node = map.get(p.id)!;
    if (p.parentId && map.has(p.parentId)) {
      map.get(p.parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  });

  // 각 레벨별 sortOrder 정렬
  const sortChildren = (nodes: TreeNode[]) => {
    nodes.sort((a, b) => a.sortOrder - b.sortOrder);
    nodes.forEach(n => sortChildren(n.children));
  };
  sortChildren(roots);

  return roots;
};

const BoardPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn, user, isAdmin } = useAuth();

  const [posts, setPosts] = useState<PostListItem[]>([]);
  const [selectedPost, setSelectedPost] = useState<PostDetail | null>(null);
  const [isLoadingSidebar, setIsLoadingSidebar] = useState(true);
  const [isLoadingContent, setIsLoadingContent] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());

  // 삭제 확인 모달
  const [deleteConfirm, setDeleteConfirm] = useState<{ isOpen: boolean; postId: number | null }>({ isOpen: false, postId: null });
  const [alertModal, setAlertModal] = useState<{ isOpen: boolean; message: string; type: 'success' | 'error' }>({ isOpen: false, message: '', type: 'success' });

  // 드래그 상태
  const [dragId, setDragId] = useState<number | null>(null);
  const [dragOverId, setDragOverId] = useState<number | null>(null);
  const [dragPosition, setDragPosition] = useState<'above' | 'inside' | 'below' | null>(null);
  const longPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [dragGhost, setDragGhost] = useState<{ title: string; x: number; y: number } | null>(null);

  const userRoleLevel = !isLoggedIn ? 0 : isAdmin ? 2 : 1;

  const fetchPosts = async () => {
    setIsLoadingSidebar(true);
    try {
      const response = await fetch(`${API_URL}/api/v1/posts?size=100`, { credentials: 'include' });
      if (response.ok) {
        const data: PostPage = await response.json();
        setPosts(data.content);
      }
    } catch { /* fetch error */ }
    finally { setIsLoadingSidebar(false); }
  };

  useEffect(() => { fetchPosts(); }, []);

  const tree = buildTree(posts, expandedIds);

  const toggleExpand = (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setExpandedIds(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const handleSelectPost = async (postId: number) => {
    if (selectedPost?.id === postId) return;
    setIsLoadingContent(true);
    try {
      const response = await fetch(`${API_URL}/api/v1/posts/${postId}`, { credentials: 'include' });
      if (response.ok) {
        const result = await response.json();
        if (result.success) setSelectedPost(result.data);
      }
    } catch { /* fetch error */ }
    finally { setIsLoadingContent(false); }
  };

  const requestDelete = (postId: number) => {
    setDeleteConfirm({ isOpen: true, postId });
  };

  const confirmDelete = async () => {
    const postId = deleteConfirm.postId;
    setDeleteConfirm({ isOpen: false, postId: null });
    if (!postId) return;

    try {
      const response = await fetch(`${API_URL}/api/v1/posts/${postId}`, { method: 'DELETE', credentials: 'include' });
      if (response.ok) {
        if (selectedPost?.id === postId) setSelectedPost(null);
        fetchPosts();
        setAlertModal({ isOpen: true, message: '페이지가 삭제되었습니다.', type: 'success' });
      } else {
        setAlertModal({ isOpen: true, message: '삭제에 실패했습니다.', type: 'error' });
      }
    } catch {
      setAlertModal({ isOpen: true, message: '삭제 중 오류가 발생했습니다.', type: 'error' });
    }
  };

  // 드래그 앤 드롭
  const handleLongPressStart = (id: number) => {
    longPressTimer.current = setTimeout(() => {
      setDragId(id);
      setIsDragging(true);
      const post = posts.find(p => p.id === id);
      if (post) {
        setDragGhost({ title: post.title, x: 0, y: 0 });
      }
    }, 500);
  };

  const handleLongPressEnd = () => {
    if (longPressTimer.current) {
      clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
  };

  // 드래그 중 마우스 추적
  useEffect(() => {
    if (!isDragging) return;
    const handleMouseMove = (e: MouseEvent) => {
      setDragGhost(prev => prev ? { ...prev, x: e.clientX, y: e.clientY } : null);
    };
    window.addEventListener('mousemove', handleMouseMove);
    return () => window.removeEventListener('mousemove', handleMouseMove);
  }, [isDragging]);

  const handleMouseMoveOver = (e: React.MouseEvent, id: number) => {
    if (dragId === id) return;

    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const y = e.clientY - rect.top;
    const height = rect.height;

    let position: 'above' | 'inside' | 'below';
    if (y < height * 0.25) position = 'above';
    else if (y > height * 0.75) position = 'below';
    else position = 'inside';

    setDragOverId(id);
    setDragPosition(position);
  };

  const handleDropOnNode = async (targetId: number, e: React.MouseEvent) => {
    if (!dragId || dragId === targetId) {
      resetDrag();
      return;
    }

    // 드롭 위치 재계산
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const y = e.clientY - rect.top;
    const height = rect.height;
    let finalPosition: 'above' | 'inside' | 'below';
    if (y < height * 0.25) finalPosition = 'above';
    else if (y > height * 0.75) finalPosition = 'below';
    else finalPosition = 'inside';

    let parentId: number | null = null;
    let sortOrder = 0;

    if (finalPosition === 'inside') {
      parentId = targetId;
      const targetChildren = posts.filter(p => p.parentId === targetId);
      sortOrder = targetChildren.length;
      setExpandedIds(prev => new Set(prev).add(targetId));
    } else {
      const target = posts.find(p => p.id === targetId);
      parentId = target?.parentId || null;
      const siblings = posts.filter(p => p.parentId === parentId && p.id !== dragId);
      const targetIndex = siblings.findIndex(p => p.id === targetId);
      sortOrder = finalPosition === 'above' ? targetIndex : targetIndex + 1;
    }

    try {
      await fetch(`${API_URL}/api/v1/posts/${dragId}/move`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ parentId, sortOrder }),
      });
      fetchPosts();
    } catch { /* move error */ }

    resetDrag();
  };

  // 드래그 중 다른 곳에서 마우스 놓으면 취소
  useEffect(() => {
    if (!isDragging) return;
    const handleGlobalMouseUp = () => resetDrag();
    window.addEventListener('mouseup', handleGlobalMouseUp);
    return () => window.removeEventListener('mouseup', handleGlobalMouseUp);
  }, [isDragging]);

  const resetDrag = () => {
    setDragId(null);
    setDragOverId(null);
    setDragPosition(null);
    setIsDragging(false);
    setDragGhost(null);
  };

  const getVisibilityLevel = (v: string) => v === 'STAFF' ? 2 : v === 'MEMBER' ? 1 : 0;
  const isLocked = (v: string) => userRoleLevel < getVisibilityLevel(v);
  const getVisibilityLabel = (v: string) => v === 'STAFF' ? '스태프' : v === 'MEMBER' ? '회원' : '';
  const canModify = () => isAdmin;

  // 트리 노드 렌더링 (재귀)
  const renderTreeNode = (node: TreeNode, depth: number = 0): React.ReactNode => {
    const hasChildren = node.children.length > 0;
    const isActive = selectedPost?.id === node.id;
    const isDragTarget = dragOverId === node.id;

    return (
      <div key={node.id}>
        <div
          className={`sidebar-item ${isActive ? 'active' : ''} ${isLocked(node.visibility) ? 'locked' : ''} ${isDragTarget && dragPosition ? `drag-${dragPosition}` : ''} ${dragId === node.id ? 'dragging-source' : ''}`}
          style={{ paddingLeft: `${12 + depth * 20}px` }}
          onClick={() => handleSelectPost(node.id)}
          onMouseDown={() => handleLongPressStart(node.id)}
          onMouseUp={(e) => { handleLongPressEnd(); if (isDragging && dragId) { handleDropOnNode(node.id, e); } }}
          onMouseEnter={() => { if (isDragging && dragId && dragId !== node.id) setDragOverId(node.id); }}
          onMouseLeave={() => { handleLongPressEnd(); if (dragOverId === node.id) { setDragOverId(null); setDragPosition(null); } }}
          onMouseMove={(e) => { if (isDragging && dragId && dragId !== node.id) { handleMouseMoveOver(e, node.id); } }}
        >
          <div className="sidebar-item-title">
            {hasChildren ? (
              <span className="tree-toggle" onClick={(e) => toggleExpand(node.id, e)}>
                {node.expanded ? '▼' : '▶'}
              </span>
            ) : (
              <span className="tree-toggle-placeholder" />
            )}
            {isLocked(node.visibility) && <span className="sidebar-lock">🔒</span>}
            <span className="sidebar-item-text">{node.title}</span>
            {node.visibility !== 'PUBLIC' && (
              <span className={`sidebar-badge ${node.visibility.toLowerCase()}`}>
                {getVisibilityLabel(node.visibility)}
              </span>
            )}
            {isAdmin && (
              <button
                className="sidebar-delete-btn"
                onClick={(e) => { e.stopPropagation(); requestDelete(node.id); }}
                title="삭제"
              >
                &times;
              </button>
            )}
          </div>
        </div>
        {hasChildren && node.expanded && (
          <div className="tree-children">
            {node.children.map(child => renderTreeNode(child, depth + 1))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="board-page">
      <Header />
      <div className="board-layout">
        <aside className={`board-sidebar ${sidebarCollapsed ? 'collapsed' : ''}`}>
          <div className="sidebar-header">
            <span className="sidebar-title">페이지</span>
            <div className="sidebar-actions">
              <button className="sidebar-collapse-btn" onClick={() => setSidebarCollapsed(!sidebarCollapsed)} title={sidebarCollapsed ? '펼치기' : '접기'}>
                {sidebarCollapsed ? '▶' : '◀'}
              </button>
            </div>
          </div>

          {!sidebarCollapsed && (
            <div className="sidebar-list">
              {isAdmin && (
                <div className="sidebar-item sidebar-create-item" onClick={() => navigate('/board/write')}>
                  <div className="sidebar-item-title">
                    <span className="sidebar-create-icon">+</span>
                    <span className="sidebar-item-text">페이지 생성</span>
                  </div>
                </div>
              )}
              {isLoadingSidebar ? (
                <div className="sidebar-loading">로딩 중...</div>
              ) : posts.length === 0 ? (
                <div className="sidebar-empty">페이지가 없습니다.</div>
              ) : (
                tree.map(node => renderTreeNode(node))
              )}
            </div>
          )}
        </aside>

        <main className="board-content">
          {isLoadingContent ? (
            <div className="content-loading">로딩 중...</div>
          ) : selectedPost ? (
            <article className="content-article">
              <div className="content-header">
                <h1 className="content-title">{selectedPost.title}</h1>
                <div className="content-meta">
                  <span className="content-author">{selectedPost.authorName}</span>
                  <span className="content-date">{new Date(selectedPost.createdAt).toLocaleString('ko-KR')}</span>
                  <span className="content-views">조회 {selectedPost.viewCount}</span>
                  {selectedPost.visibility !== 'PUBLIC' && (
                    <span className={`content-visibility ${selectedPost.visibility.toLowerCase()}`}>
                      {getVisibilityLabel(selectedPost.visibility)}
                    </span>
                  )}
                </div>
                {canModify() && !selectedPost.restricted && (
                  <div className="content-actions">
                    <button className="content-edit-btn" onClick={() => navigate(`/board/edit/${selectedPost.id}`)}>수정</button>
                    <button className="content-delete-btn" onClick={() => requestDelete(selectedPost.id)}>삭제</button>
                  </div>
                )}
              </div>

              {selectedPost.restricted ? (
                <div className="content-restricted">
                  <div className="restricted-icon">🔒</div>
                  <p className="restricted-message">{selectedPost.restrictedMessage}</p>
                  {!isLoggedIn && <p className="restricted-hint">로그인하시면 더 많은 페이지를 열람할 수 있습니다.</p>}
                </div>
              ) : (
                <>
                  <div className="content-body" dangerouslySetInnerHTML={{ __html: renderMathInHtml(selectedPost.contentHtml || '') }} />
                  {selectedPost.annotatedImagesJson && (() => {
                    try {
                      const images: AnnotatedImage[] = JSON.parse(selectedPost.annotatedImagesJson);
                      return images.length > 0 && (
                        <div className="content-annotated-images">
                          {images.map((img, i) => (<ImageAnnotator key={i} image={img} index={i} readOnly />))}
                        </div>
                      );
                    } catch { return null; }
                  })()}
                </>
              )}
            </article>
          ) : (
            <div className="content-empty">
              <p>왼쪽에서 페이지를 선택하세요.</p>
              {isAdmin && (
                <button className="content-create-btn" onClick={() => navigate('/board/write')}>새 페이지 만들기</button>
              )}
            </div>
          )}
        </main>
      </div>

      {/* 드래그 고스트 */}
      {dragGhost && isDragging && (
        <div
          className="drag-ghost"
          style={{ left: dragGhost.x + 12, top: dragGhost.y - 14 }}
        >
          {dragGhost.title}
        </div>
      )}

      {/* 삭제 확인 모달 */}
      {deleteConfirm.isOpen && (
        <div className="alert-overlay" onClick={() => setDeleteConfirm({ isOpen: false, postId: null })}>
          <div className="alert-modal warning" onClick={(e) => e.stopPropagation()}>
            <div className="alert-header">
              <svg viewBox="0 0 24 24" fill="none" className="alert-icon warning">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M12 9v4M12 17h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
              </svg>
              <h3 className="alert-title">페이지 삭제</h3>
            </div>
            <p className="alert-message">이 페이지를 삭제하시겠습니까?</p>
            <div className="confirm-buttons">
              <button className="confirm-cancel-btn" onClick={() => setDeleteConfirm({ isOpen: false, postId: null })}>취소</button>
              <button className="confirm-delete-btn" onClick={confirmDelete}>삭제</button>
            </div>
          </div>
        </div>
      )}

      {/* 결과 알림 모달 */}
      <AlertModal
        isOpen={alertModal.isOpen}
        onClose={() => setAlertModal({ ...alertModal, isOpen: false })}
        message={alertModal.message}
        type={alertModal.type}
        autoClose={2000}
      />
    </div>
  );
};

export default BoardPage;

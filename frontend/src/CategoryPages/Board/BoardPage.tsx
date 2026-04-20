import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { API_URL } from '../../utils/api';
import Header from '../../components/Header';
import { PostListItem, PostPage } from './types';
import './BoardPage.css';

const BoardPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn, user, isAdmin } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [posts, setPosts] = useState<PostListItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  const currentPage = parseInt(searchParams.get('page') || '0');
  const searchQuery = searchParams.get('search') || '';
  const [searchInput, setSearchInput] = useState(searchQuery);

  const userRoleLevel = !isLoggedIn ? 0 : isAdmin ? 2 : 1;

  useEffect(() => {
    const fetchPosts = async () => {
      setIsLoading(true);
      try {
        const params = new URLSearchParams();
        params.set('page', currentPage.toString());
        params.set('size', '20');
        if (searchQuery) params.set('search', searchQuery);

        const response = await fetch(`${API_URL}/api/v1/posts?${params}`, {
          credentials: 'include',
        });
        if (response.ok) {
          const data: PostPage = await response.json();
          setPosts(data.content);
          setTotalPages(data.totalPages);
          setTotalElements(data.totalElements);
        }
      } catch (error) {
        // fetch error
      } finally {
        setIsLoading(false);
      }
    };

    fetchPosts();
  }, [currentPage, searchQuery]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const params = new URLSearchParams();
    if (searchInput.trim()) params.set('search', searchInput.trim());
    params.set('page', '0');
    setSearchParams(params);
  };

  const handlePageChange = (page: number) => {
    const params = new URLSearchParams(searchParams);
    params.set('page', page.toString());
    setSearchParams(params);
  };

  const getVisibilityLevel = (v: string) => {
    if (v === 'STAFF') return 2;
    if (v === 'MEMBER') return 1;
    return 0;
  };

  const isLocked = (visibility: string) => userRoleLevel < getVisibilityLevel(visibility);

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffHours = diffMs / (1000 * 60 * 60);

    if (diffHours < 24) {
      if (diffHours < 1) return `${Math.floor(diffMs / (1000 * 60))}분 전`;
      return `${Math.floor(diffHours)}시간 전`;
    }
    return date.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' });
  };

  const getVisibilityLabel = (v: string) => {
    if (v === 'STAFF') return '스태프';
    if (v === 'MEMBER') return '회원';
    return '';
  };

  return (
    <div className="board-page">
      <Header />
      <div className="board-container">
        <div className="board-header">
          <h1>게시판</h1>
          <div className="board-header-actions">
            <form className="board-search" onSubmit={handleSearch}>
              <input
                type="text"
                placeholder="제목 검색..."
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                className="board-search-input"
              />
              <button type="submit" className="board-search-btn">검색</button>
            </form>
            {isLoggedIn && (
              <button className="board-write-btn" onClick={() => navigate('/board/write')}>
                글쓰기
              </button>
            )}
          </div>
        </div>

        {isLoading ? (
          <div className="board-loading">로딩 중...</div>
        ) : posts.length === 0 ? (
          <div className="board-empty">
            {searchQuery ? '검색 결과가 없습니다.' : '게시글이 없습니다.'}
          </div>
        ) : (
          <>
            <table className="board-table">
              <thead>
                <tr>
                  <th className="col-id">번호</th>
                  <th className="col-title">제목</th>
                  <th className="col-author">작성자</th>
                  <th className="col-date">날짜</th>
                  <th className="col-views">조회</th>
                </tr>
              </thead>
              <tbody>
                {posts.map((post) => (
                  <tr
                    key={post.id}
                    className={`board-row ${isLocked(post.visibility) ? 'locked' : ''}`}
                    onClick={() => navigate(`/board/${post.id}`)}
                  >
                    <td className="col-id">{post.id}</td>
                    <td className="col-title">
                      {isLocked(post.visibility) && <span className="lock-icon">🔒</span>}
                      <span className="post-title">{post.title}</span>
                      {post.visibility !== 'PUBLIC' && (
                        <span className={`visibility-badge ${post.visibility.toLowerCase()}`}>
                          {getVisibilityLabel(post.visibility)}
                        </span>
                      )}
                    </td>
                    <td className="col-author">{post.authorName}</td>
                    <td className="col-date">{formatDate(post.createdAt)}</td>
                    <td className="col-views">{post.viewCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {totalPages > 1 && (
              <div className="board-pagination">
                <button
                  disabled={currentPage === 0}
                  onClick={() => handlePageChange(currentPage - 1)}
                >
                  이전
                </button>
                {Array.from({ length: totalPages }, (_, i) => (
                  <button
                    key={i}
                    className={currentPage === i ? 'active' : ''}
                    onClick={() => handlePageChange(i)}
                  >
                    {i + 1}
                  </button>
                ))}
                <button
                  disabled={currentPage === totalPages - 1}
                  onClick={() => handlePageChange(currentPage + 1)}
                >
                  다음
                </button>
              </div>
            )}
          </>
        )}

        <div className="board-footer-info">
          총 {totalElements}개의 게시글
        </div>
      </div>
    </div>
  );
};

export default BoardPage;

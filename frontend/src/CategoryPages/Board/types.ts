export interface PostListItem {
  id: number;
  title: string;
  authorName: string;
  visibility: 'PUBLIC' | 'MEMBER' | 'STAFF';
  viewCount: number;
  createdAt: string;
}

export interface PostDetail {
  id: number;
  title: string;
  contentHtml: string;
  authorId: string;
  authorName: string;
  visibility: 'PUBLIC' | 'MEMBER' | 'STAFF';
  viewCount: number;
  restricted: boolean;
  restrictedMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PostPage {
  content: PostListItem[];
  totalPages: number;
  totalElements: number;
  number: number;
}

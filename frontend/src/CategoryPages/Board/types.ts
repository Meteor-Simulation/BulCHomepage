export interface PostListItem {
  id: number;
  title: string;
  authorName: string;
  visibility: 'PUBLIC' | 'MEMBER' | 'STAFF';
  parentId: number | null;
  sortOrder: number;
  viewCount: number;
  createdAt: string;
}

export interface TreeNode extends PostListItem {
  children: TreeNode[];
  expanded: boolean;
}

export interface PostDetail {
  id: number;
  title: string;
  contentHtml: string;
  annotatedImagesJson?: string;
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

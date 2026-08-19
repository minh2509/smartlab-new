import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AlertTriangle, ClipboardCheck, FileText, Newspaper, Plus } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import { listProjects } from '../../projects/api'
import { listPosts, removePostReaction, setPostReaction } from '../api'
import { PostCommentsDialog } from '../components/PostCommentsDialog'
import { PostFeedCard } from '../components/PostFeedCard'
import type { PostFeedItem, ReactionState, ReactionType } from '../types'
import type { Project } from '../../projects/types'

const SKELETON_KEYS = ['feed-1', 'feed-2', 'feed-3']

export function PostFeedPage() {
  const { token, profile } = useAuth()
  const navigate = useNavigate()
  const [posts, setPosts] = useState<PostFeedItem[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [projectCatalogLoading, setProjectCatalogLoading] = useState(true)
  const [cursor, setCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reactionPending, setReactionPending] = useState<number | null>(null)
  const [commentsPostId, setCommentsPostId] = useState<number | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    setPosts([])
    setCursor(null)
    setProjects([])
    setProjectCatalogLoading(true)
    void listPosts(token)
      .then((page) => { setPosts(page.items); setCursor(page.nextCursor) })
      .catch((value: unknown) => setError(messageOf(value, 'Không thể tải bảng tin.')))
      .finally(() => setLoading(false))
  }, [token])

  const hasProjectPost = posts.some((post) => post.visibility === 'PROJECT' && post.projectId !== null)

  useEffect(() => {
    if (!token || !hasProjectPost) return

    let cancelled = false
    void listProjects(token)
      .then((catalog) => { if (!cancelled) { setProjects(catalog); setProjectCatalogLoading(false) } })
      .catch(() => { if (!cancelled) { setProjects([]); setProjectCatalogLoading(false) } })
    return () => { cancelled = true }
  }, [token, hasProjectPost])

  const projectsById = useMemo(() => new Map(projects.map((project) => [project.id, project])), [projects])

  const closeComments = useCallback(() => setCommentsPostId(null), [])

  async function loadMore() {
    if (!cursor || loadingMore) return
    setLoadingMore(true)
    try {
      const page = await listPosts(token, cursor)
      setPosts((current) => [...current, ...page.items])
      setCursor(page.nextCursor)
    } catch (value) {
      setError(messageOf(value, 'Không thể tải thêm bài viết.'))
    } finally {
      setLoadingMore(false)
    }
  }

  async function react(postId: number, reaction: ReactionType) {
    if (!token) throw new Error('Bạn cần đăng nhập.')
    setReactionPending(postId)
    try {
      const state = await setPostReaction(token, postId, reaction)
      applyReaction(state)
      return state
    } catch (value) {
      setError(messageOf(value, 'Không thể cập nhật cảm xúc.'))
      throw value
    } finally {
      setReactionPending(null)
    }
  }

  async function removeReaction(postId: number) {
    if (!token) throw new Error('Bạn cần đăng nhập.')
    setReactionPending(postId)
    try {
      const state = await removePostReaction(token, postId)
      applyReaction(state)
      return state
    } catch (value) {
      setError(messageOf(value, 'Không thể gỡ cảm xúc.'))
      throw value
    } finally {
      setReactionPending(null)
    }
  }

  function applyReaction(state: ReactionState) {
    setPosts((current) => current.map((post) => post.id === state.postId
      ? { ...post, ...state }
      : post))
  }

  const commentsPost = posts.find((post) => post.id === commentsPostId) ?? null
  const canReviewPosts = profile?.permissions.includes('posts.review') ?? false

  return (
    <section className="post-feed-page social-feed-page">
      <div className="post-feed-shell">
        <div className="social-feed-layout">
          <main className="social-feed-main">
            <header className="post-feed-head social-feed-head">
              <div>
                <h1>Bảng tin Smart Lab</h1>
                <p>{token
                  ? 'Chia sẻ mới nhất từ thành viên và các dự án bạn đang tham gia.'
                  : 'Các bài viết công khai mới nhất từ cộng đồng Smart Lab.'}</p>
              </div>
            </header>

            {error ? <div className="social-feed-error" role="alert"><AlertTriangle aria-hidden="true" /> {error}</div> : null}

            {loading ? <div className="post-feed" aria-busy="true" aria-label="Đang tải bảng tin">
              {SKELETON_KEYS.map((key) => <div className="social-post-card social-card-skeleton" key={key} aria-hidden="true">
                <span /><span /><span /><span />
              </div>)}
            </div> : null}

            {!loading && posts.length === 0 && !error ? <div className="post-feed-state">
              <span className="post-feed-state-icon" aria-hidden="true"><Newspaper /></span>
              <h2>Bảng tin chưa có bài viết</h2>
              <p>Các bài đã xuất bản và phù hợp với quyền truy cập của bạn sẽ xuất hiện tại đây.</p>
            </div> : null}

            {!loading && posts.length > 0 ? <div className="post-feed">
              {posts.map((post) => <PostFeedCard
                key={post.id}
                post={post}
                project={post.projectId !== null ? projectsById.get(post.projectId) : undefined}
                projectCatalogLoading={projectCatalogLoading}
                token={token}
                reactionPending={reactionPending === post.id}
                canInteract={Boolean(token)}
                onReaction={(reaction) => react(post.id, reaction)}
                onRemoveReaction={() => removeReaction(post.id)}
                onComments={() => setCommentsPostId(post.id)}
                onRequireLogin={() => navigate('/login')}
              />)}
              {cursor ? <button className="feed-load-more" type="button" disabled={loadingMore} onClick={() => void loadMore()}>
                {loadingMore ? 'Đang tải...' : 'Xem thêm bài viết'}
              </button> : null}
            </div> : null}
          </main>

          <aside className="social-feed-sidebar" aria-label="Thao tác bảng tin">
            {token ? <section className="social-sidebar-card">
              <h2>Thao tác nhanh</h2>
              <nav className="social-sidebar-actions" aria-label="Thao tác bài viết">
                <Link className="btn primary" to="/posts/new"><Plus aria-hidden="true" /> Tạo bài viết</Link>
                <Link className="btn" to="/my-posts"><FileText aria-hidden="true" /> Bài viết của tôi</Link>
                {canReviewPosts ? <Link className="btn" to="/posts/review-queue"><ClipboardCheck aria-hidden="true" /> Duyệt bài</Link> : null}
              </nav>
            </section> : null}

            <section className="social-sidebar-card social-sidebar-about">
              <h2>Về Bảng tin</h2>
              <p>{token
                ? 'Xem các bài đã xuất bản từ thành viên và dự án bạn tham gia.'
                : 'Khám phá nội dung công khai đã được Smart Lab xuất bản.'}</p>
            </section>
          </aside>
        </div>
      </div>

      {token && commentsPost ? <PostCommentsDialog
        post={commentsPost}
        onClose={closeComments}
        reactionPending={reactionPending === commentsPost.id}
        onReaction={(reaction) => react(commentsPost.id, reaction)}
        onRemoveReaction={() => removeReaction(commentsPost.id)}
        onCommentCountChange={(count) => setPosts((current) => current.map((post) => post.id === commentsPost.id
          ? { ...post, commentCount: count }
          : post))}
      /> : null}
    </section>
  )
}

function messageOf(value: unknown, fallback: string) {
  return value instanceof Error ? value.message : fallback
}

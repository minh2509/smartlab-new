import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, ArrowRight, CalendarDays, Globe, Lock, Newspaper, Users } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import { listAdminPostQueue, listReviewablePosts } from '../api'
import type { PostStatus, PostSummary, PostVisibility } from '../types'

const VISIBILITY: Record<PostVisibility, { label: string; Icon: typeof Globe }> = {
  PUBLIC: { label: 'Công khai', Icon: Globe },
  LAB: { label: 'Nội bộ Lab', Icon: Users },
  PROJECT: { label: 'Theo dự án', Icon: Lock },
}

const STATUS: Record<PostStatus, { label: string; tone: 'ok' | 'warn' | 'danger' | 'muted' }> = {
  DRAFT: { label: 'Bản nháp', tone: 'muted' },
  PENDING_REVIEW: { label: 'Chờ duyệt', tone: 'warn' },
  REVISION_REQUIRED: { label: 'Cần chỉnh sửa', tone: 'warn' },
  APPROVED: { label: 'Đã duyệt', tone: 'ok' },
  PUBLISHED: { label: 'Đã xuất bản', tone: 'ok' },
  REJECTED: { label: 'Bị từ chối', tone: 'danger' },
}

const SKELETON_KEYS = ['review-sk-1', 'review-sk-2', 'review-sk-3']

export function PostReviewQueuePage({ adminMode = false }: { adminMode?: boolean }) {
  const { token, profile } = useAuth()
  const [posts, setPosts] = useState<PostSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) {
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    void (adminMode ? listAdminPostQueue(token) : listReviewablePosts(token))
      .then(setPosts)
      .catch((value: unknown) => {
        setError(value instanceof Error ? value.message : 'Không thể tải hàng chờ duyệt bài.')
      })
      .finally(() => setLoading(false))
  }, [adminMode, token])

  const canPublish = profile?.permissions.includes('posts.publish') ?? false
  const visiblePosts = posts.filter((post) => post.status === 'PENDING_REVIEW' || (canPublish && post.status === 'APPROVED'))

  return (
    <section className="post-feed-page">
      <div className="post-feed-shell">
        <header className="post-feed-head">
          <span className="post-feed-kicker">{adminMode ? 'Quản trị nội dung' : 'Quy trình duyệt'}</span>
          <div className="post-feed-headline">
            <h1>{adminMode ? 'Quản lý bài viết' : 'Hàng chờ duyệt bài'}</h1>
            {!loading && !error && visiblePosts.length > 0 ? (
              <span className="post-feed-count">{visiblePosts.length}</span>
            ) : null}
          </div>
          <p className="post-feed-sub">
            {adminMode
              ? 'Duyệt các bài đang chờ và xuất bản những bài đã được phê duyệt.'
              : 'Các bài viết đang chờ quyết định theo quyền truy cập hiện tại của bạn.'}
          </p>
        </header>

        {loading ? (
          <div className="post-feed" aria-busy="true" aria-live="polite">
            {SKELETON_KEYS.map((key) => (
              <div className="post-card is-skeleton" key={key} aria-hidden="true">
                <span className="sk sk-top" />
                <span className="sk sk-title" />
                <span className="sk sk-line" />
                <span className="sk sk-line short" />
                <span className="sk sk-foot" />
              </div>
            ))}
          </div>
        ) : null}

        {!loading && error ? (
          <div className="post-feed-state is-error" role="alert">
            <span className="post-feed-state-icon" aria-hidden="true">
              <AlertTriangle />
            </span>
            <h2>Không thể tải hàng chờ</h2>
            <p>{error}</p>
          </div>
        ) : null}

        {!loading && !error && visiblePosts.length === 0 ? (
          <div className="post-feed-state">
            <span className="post-feed-state-icon" aria-hidden="true">
              <Newspaper />
            </span>
            <h2>Không có bài cần xử lý</h2>
            <p>Hiện không có bài viết nào đang chờ duyệt hoặc chờ xuất bản.</p>
          </div>
        ) : null}

        {!loading && !error && visiblePosts.length > 0 ? (
          <div className="post-feed">
            {visiblePosts.map((post) => {
              const audience = VISIBILITY[post.visibility]
              const AudienceIcon = audience.Icon
              const status = STATUS[post.status]
              const href = post.status === 'APPROVED'
                ? `/posts/${encodeURIComponent(post.slug)}`
                : `/posts/review-queue/${encodeURIComponent(String(post.id))}`

              return (
                <article className="post-card" key={post.id}>
                  <div className="post-card-top">
                    {post.category ? (
                      <span className="post-card-topic">{post.category.name}</span>
                    ) : null}
                    <span className="post-card-audience">
                      <AudienceIcon aria-hidden="true" />
                      {audience.label}
                    </span>
                  </div>

                  <h2 className="post-card-title">
                    <Link to={href}>{post.title}</Link>
                  </h2>

                  {post.excerpt ? <p className="post-card-excerpt">{post.excerpt}</p> : null}

                  <div className="post-card-foot">
                    <span className="post-card-meta">
                      <CalendarDays aria-hidden="true" />
                      <span className={`post-card-status is-${status.tone}`}>{status.label}</span>
                      <span className="post-card-byline">
                        <span>{post.author?.name ?? 'Tác giả không còn khả dụng'}</span>
                        <span className="post-card-dot" aria-hidden="true">·</span>
                        <span className="post-card-date">{formatDate(post.updatedAt)}</span>
                      </span>
                    </span>
                    <Link className="post-card-action" to={href}>
                      {post.status === 'APPROVED' ? 'Mở để xuất bản' : 'Mở để duyệt'}
                      <ArrowRight aria-hidden="true" />
                    </Link>
                  </div>
                </article>
              )
            })}
          </div>
        ) : null}
      </div>
    </section>
  )
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(value))
}

import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useAuth } from '../../auth/authContext'
import { getPostBySlug } from '../api'
import type { PostDetail } from '../types'

export function PostDetailPage() {
  const { token } = useAuth()
  const { slug } = useParams<{ slug: string }>()
  const [post, setPost] = useState<PostDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token || !slug) {
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    void getPostBySlug(token, slug)
      .then(setPost)
      .catch((value: unknown) => {
        setError(value instanceof Error ? value.message : 'Không thể tải bài viết.')
      })
      .finally(() => setLoading(false))
  }, [slug, token])

  if (!token) {
    return <Navigate to="/login" replace />
  }

  return (
    <section className="section">
      <div className="wrap">
        <Link className="more" to="/posts">
          ← Danh sách bài viết
        </Link>

        {loading ? <div className="empty">Đang tải bài viết...</div> : null}

        {!loading && (!slug || error) ? (
          <div className="empty">
            <h3>Không thể tải bài viết</h3>
            <p>{error ?? 'Đường dẫn bài viết không hợp lệ.'}</p>
          </div>
        ) : null}

        {!loading && !error && post ? (
          <article className="post-page-detail">
            <header className="post-page-header">
              <div className="post-page-eyebrow">
                {post.category ? <span className="post-page-category">{post.category.name}</span> : null}
              </div>

              <h1 className="post-page-title">{post.title}</h1>

              {post.excerpt ? <p className="post-page-excerpt">{post.excerpt}</p> : null}

              <div className="post-page-meta">
                <span className="post-page-dates">
                  {formatDate(post.createdAt)}
                  {post.updatedAt !== post.createdAt ? ` (Cập nhật: ${formatDate(post.updatedAt)})` : ''}
                  {post.publishedAt ? ` · Xuất bản: ${formatDate(post.publishedAt)}` : ''}
                </span>
                <span className="post-page-status-visibility">
                  {post.status} · {post.visibility}
                </span>
              </div>
            </header>

            <hr className="post-page-divider" />

            <div className="post-page-content-state">
              <p>Nội dung bài viết hiện được lưu dưới dạng cấu trúc và chưa có trình hiển thị tương ứng.</p>
            </div>

            <div className="post-page-data-disclosure">
              <details>
                <summary>Xem dữ liệu nội dung</summary>
                <pre>{JSON.stringify(post.contentJson ?? {}, null, 2)}</pre>
              </details>
            </div>
          </article>
        ) : null}
      </div>
    </section>
  )
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(new Date(value))
}

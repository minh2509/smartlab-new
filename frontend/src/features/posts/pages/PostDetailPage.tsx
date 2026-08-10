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
          <article className="card pad">
            <div className="pmeta">
              {post.category ? <span className="chip accent">{post.category.name}</span> : null}
              <span>{post.status}</span>
              <span>·</span>
              <span>{post.visibility}</span>
            </div>

            <h1>{post.title}</h1>

            {post.excerpt ? <p>{post.excerpt}</p> : null}

            <div className="muted small">
              Tạo {formatDate(post.createdAt)} · Cập nhật {formatDate(post.updatedAt)}
              {post.publishedAt ? ` · Xuất bản ${formatDate(post.publishedAt)}` : ''}
            </div>

            <hr />

            <h2>Nội dung JSON</h2>
            <pre>{JSON.stringify(post.contentJson ?? {}, null, 2)}</pre>
          </article>
        ) : null}
      </div>
    </section>
  )
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('vi-VN')
}

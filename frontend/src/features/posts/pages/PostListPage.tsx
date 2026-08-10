import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '../../auth/authContext'
import { listPosts } from '../api'
import type { PostSummary } from '../types'

export function PostListPage() {
  const { token } = useAuth()
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

    void listPosts(token)
      .then(setPosts)
      .catch((value: unknown) => {
        setError(value instanceof Error ? value.message : 'Không thể tải danh sách bài viết.')
      })
      .finally(() => setLoading(false))
  }, [token])

  if (!token) {
    return <Navigate to="/login" replace />
  }

  return (
    <section className="section">
      <div className="wrap">
        <div className="sec-head">
          <div className="kicker">Bài viết nội bộ</div>
          <h1>Bài viết</h1>
          <p>Danh sách bài viết bạn được phép xem theo quyền truy cập hiện tại.</p>
        </div>

        {loading ? <div className="empty">Đang tải bài viết...</div> : null}

        {!loading && error ? (
          <div className="empty">
            <h3>Không thể tải bài viết</h3>
            <p>{error}</p>
          </div>
        ) : null}

        {!loading && !error && posts.length === 0 ? (
          <div className="empty">
            <h3>Chưa có bài viết</h3>
            <p>Hiện chưa có bài viết nào bạn được phép xem.</p>
          </div>
        ) : null}

        {!loading && !error && posts.length > 0 ? (
          <div className="grid c3">
            {posts.map((post) => (
              <article className="card pad" key={post.id}>
                <div className="pmeta">
                  {post.category ? <span className="chip accent">{post.category.name}</span> : null}
                  <span>{post.status}</span>
                  <span>·</span>
                  <span>{post.visibility}</span>
                </div>

                <h2>{post.title}</h2>

                {post.excerpt ? <p>{post.excerpt}</p> : null}

                <div className="muted small">
                  Cập nhật {formatDate(post.updatedAt)}
                </div>

                <Link className="more" to={`/posts/${encodeURIComponent(post.slug)}`}>
                  Xem bài viết
                </Link>
              </article>
            ))}
          </div>
        ) : null}
      </div>
    </section>
  )
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('vi-VN')
}

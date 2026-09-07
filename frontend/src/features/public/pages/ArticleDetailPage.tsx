import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getArticle } from '../articleApi'
import type { LabArticleDetail } from '../articleTypes'
import { ArticleContent } from '../components/ArticleContent'
import { PublicPageHead } from '../components/PublicPageHead'

export function ArticleDetailPage() {
  const { slug } = useParams<{ slug: string }>()
  const [article, setArticle] = useState<LabArticleDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!slug) {
      setLoading(false)
      return
    }
    let active = true
    setLoading(true)
    setError(null)

    getArticle(slug)
      .then((data) => {
        if (active) setArticle(data)
      })
      .catch(() => {
        if (active) setError('Không thể tải bài viết lúc này. Bài viết có thể không tồn tại hoặc tạm thời không khả dụng.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [slug])

  function formatDate(value: string) {
    return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(value))
  }

  if (loading) {
    return (
      <section className="section">
        <div className="wrap">
          <div className="empty">Đang tải bài viết...</div>
        </div>
      </section>
    )
  }

  if (error || !article) {
    return (
      <section className="section">
        <div className="wrap">
          <Link className="more" style={{ display: 'inline-block', marginBottom: 24 }} to="/bai-viet">
            ← Quay lại danh sách bài viết
          </Link>
          <div className="empty">
            <h3>Không thể tải bài viết</h3>
            <p>{error ?? 'Đường dẫn không hợp lệ hoặc bài viết không tồn tại.'}</p>
          </div>
        </div>
      </section>
    )
  }

  return (
    <>
      <PublicPageHead title={article.title} description={article.excerpt ?? ''} />
      <section className="section">
        <div className="wrap" style={{ maxWidth: 760 }}>
          <Link className="more" style={{ display: 'inline-block', marginBottom: 32 }} to="/bai-viet">
            ← Quay lại danh sách bài viết
          </Link>

          <article className="article-page-detail">
            <header className="article-page-header" style={{ marginBottom: 40 }}>
              <h2 className="article-page-title" style={{ fontSize: '2.5rem', lineHeight: 1.2, marginBottom: 16 }}>
                {article.title}
              </h2>
              {article.excerpt ? (
                <p className="article-page-excerpt" style={{ fontSize: '1.25rem', color: 'var(--text-muted)', marginBottom: 24 }}>
                  {article.excerpt}
                </p>
              ) : null}
              <div className="article-page-meta" style={{ display: 'flex', gap: 16, color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                <span className="chip accent">Smart Lab</span>
                <span>{formatDate(article.publishedAt)}</span>
              </div>
            </header>

            <ArticleContent
              contentJson={article.content}
              className="article-page-content-body"
              bodyClassName="prose"
            />
          </article>
        </div>
      </section>
    </>
  )
}

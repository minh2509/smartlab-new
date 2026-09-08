import { useEffect, useState, useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Calendar, Clock, BookOpen, ChevronRight, AlertCircle, RotateCcw } from 'lucide-react'
import { getArticle } from '../articleApi'
import type { LabArticleDetail } from '../articleTypes'
import { ArticleContent } from '../components/ArticleContent'
import '../landing.css'

export function ArticleDetailPage() {
  const { slug } = useParams<{ slug: string }>()
  const [article, setArticle] = useState<LabArticleDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

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
  }, [slug, reloadKey])

  const readingTime = useMemo(() => {
    if (!article?.content || typeof article.content.body !== 'string') return 1
    const words = article.content.body.trim().split(/\s+/).filter(Boolean).length
    return Math.max(1, Math.ceil(words / 180))
  }, [article])

  function formatDate(value: string | null | undefined) {
    if (!value) return ''
    return new Intl.DateTimeFormat('vi-VN', {
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(new Date(value))
  }

  function handleScrollToTop() {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  if (loading) {
    return (
      <main className="article-editorial-stage">
        <div className="article-editorial-wrap">
          <nav className="article-editorial-crumb" aria-label="Đường dẫn">
            <Link to="/" className="article-crumb-link">Trang chủ</Link>
            <ChevronRight size={13} className="article-crumb-sep" aria-hidden="true" />
            <Link to="/bai-viet" className="article-crumb-link">Bài viết</Link>
            <ChevronRight size={13} className="article-crumb-sep" aria-hidden="true" />
            <span className="article-crumb-current">Đang tải...</span>
          </nav>

          <article className="article-editorial-article" aria-busy="true">
            <div className="article-editorial-skeleton-head">
              <div className="article-skeleton-badge" />
              <div className="article-skeleton-title" />
              <div className="article-skeleton-title short" />
              <div className="article-skeleton-meta" />
            </div>
            <div className="article-editorial-skeleton-body">
              <div className="article-skeleton-line" />
              <div className="article-skeleton-line" />
              <div className="article-skeleton-line" />
              <div className="article-skeleton-line short" />
            </div>
          </article>
        </div>
      </main>
    )
  }

  if (error || !article) {
    return (
      <main className="article-editorial-stage">
        <div className="article-editorial-wrap">
          <nav className="article-editorial-crumb" aria-label="Đường dẫn">
            <Link to="/" className="article-crumb-link">Trang chủ</Link>
            <ChevronRight size={13} className="article-crumb-sep" aria-hidden="true" />
            <Link to="/bai-viet" className="article-crumb-link">Bài viết</Link>
          </nav>

          <div className="article-editorial-error-card" role="alert">
            <AlertCircle size={32} className="article-error-icon" aria-hidden="true" />
            <h2 className="article-error-title">Không thể tải bài viết</h2>
            <p className="article-error-desc">{error ?? 'Đường dẫn không hợp lệ hoặc bài viết không tồn tại.'}</p>
            <div className="article-error-actions">
              <button
                type="button"
                className="article-footer-btn secondary"
                onClick={() => setReloadKey((k) => k + 1)}
              >
                <RotateCcw size={15} aria-hidden="true" />
                <span>Thử tải lại</span>
              </button>
              <Link to="/bai-viet" className="article-footer-btn tertiary">
                <ArrowLeft size={15} aria-hidden="true" />
                <span>Về danh sách bài viết</span>
              </Link>
            </div>
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className="article-editorial-stage">
      <div className="article-editorial-wrap">
        {/* Navigation & Context Masthead */}
        <div className="article-editorial-top-nav">
          <nav className="article-editorial-crumb" aria-label="Đường dẫn">
            <Link to="/" className="article-crumb-link">Trang chủ</Link>
            <ChevronRight size={13} className="article-crumb-sep" aria-hidden="true" />
            <Link to="/bai-viet" className="article-crumb-link">Bài viết</Link>
            <ChevronRight size={13} className="article-crumb-sep" aria-hidden="true" />
            <span className="article-crumb-current" aria-current="page">{article.title}</span>
          </nav>

          <Link to="/bai-viet" className="article-editorial-back-link">
            <ArrowLeft size={15} aria-hidden="true" />
            <span>Tất cả bài viết</span>
          </Link>
        </div>

        {/* The Publication Monograph Paper */}
        <article className="article-editorial-article">
          <header className="article-editorial-header">
            <div className="article-editorial-kicker">
              <span className="article-kicker-badge">BÀI VIẾT SMART LAB</span>
              <span className="article-kicker-sep">•</span>
              <span className="article-kicker-topic">CHUYÊN MÔN &amp; NGHIÊN CỨU</span>
            </div>

            <h1 className="article-editorial-title">{article.title}</h1>

            {article.excerpt ? (
              <div className="article-editorial-standfirst">
                <p>{article.excerpt}</p>
              </div>
            ) : null}

            <div className="article-editorial-meta-row">
              <div className="article-meta-author">
                <div className="article-meta-avatar" aria-hidden="true">SL</div>
                <div className="article-meta-author-text">
                  <span className="article-meta-name">Phòng thí nghiệm Smart Lab</span>
                  <span className="article-meta-org">Khoa CNTT • Đại học FPT</span>
                </div>
              </div>

              <div className="article-meta-facts">
                {article.publishedAt ? (
                  <div className="article-meta-fact">
                    <Calendar size={13} className="article-meta-icon" aria-hidden="true" />
                    <time dateTime={article.publishedAt}>{formatDate(article.publishedAt)}</time>
                  </div>
                ) : null}

                <div className="article-meta-fact">
                  <Clock size={13} className="article-meta-icon" aria-hidden="true" />
                  <span>{readingTime} phút đọc</span>
                </div>

                <div className="article-meta-fact">
                  <BookOpen size={13} className="article-meta-icon" aria-hidden="true" />
                  <span>Ấn phẩm chính thức</span>
                </div>
              </div>
            </div>
          </header>

          <div className="article-editorial-divider" aria-hidden="true" />

          {/* Reading Body */}
          <div className="article-editorial-body-wrap">
            <ArticleContent
              contentJson={article.content}
              className="article-editorial-prose"
              bodyClassName="article-editorial-text"
              fallbackClassName="article-editorial-fallback"
            />
          </div>

          {/* Colophon & Endplate */}
          <footer className="article-editorial-footer">
            <div className="article-editorial-colophon">
              <div className="article-colophon-mark" aria-hidden="true">
                <span>SL</span>
              </div>
              <div className="article-colophon-text">
                <p className="article-colophon-title">Phát hành bởi Smart Lab</p>
                <p className="article-colophon-desc">
                  Phòng thí nghiệm Nghiên cứu &amp; Phát triển các công nghệ Trí tuệ nhân tạo, Robot tự hành và Kỹ thuật phần mềm.
                </p>
              </div>
            </div>

            <div className="article-editorial-footer-actions">
              <Link to="/bai-viet" className="article-footer-btn secondary">
                <ArrowLeft size={15} aria-hidden="true" />
                <span>Quay lại danh sách</span>
              </Link>
              <button
                type="button"
                onClick={handleScrollToTop}
                className="article-footer-btn tertiary"
                aria-label="Cuộn về đầu trang"
              >
                <span>Về đầu trang ↑</span>
              </button>
            </div>
          </footer>
        </article>
      </div>
    </main>
  )
}

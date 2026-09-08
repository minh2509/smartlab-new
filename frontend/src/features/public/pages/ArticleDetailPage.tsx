import { useEffect, useState, useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Calendar, Clock, ChevronRight, AlertCircle, RotateCcw } from 'lucide-react'
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
      <main className="article-pub-stage">
        <div className="article-pub-wrap">
          <nav className="article-pub-nav" aria-label="Đường dẫn">
            <Link to="/bai-viet" className="article-pub-back">
              <ArrowLeft size={16} aria-hidden="true" />
              <span>Tất cả bài viết</span>
            </Link>
          </nav>
          <div className="article-pub-skeleton" aria-busy="true">
            <div className="article-skeleton-kicker" />
            <div className="article-skeleton-title" />
            <div className="article-skeleton-title short" />
            <div className="article-skeleton-byline" />
            <div className="article-skeleton-rule" />
            <div className="article-skeleton-line" />
            <div className="article-skeleton-line" />
            <div className="article-skeleton-line" />
            <div className="article-skeleton-line short" />
          </div>
        </div>
      </main>
    )
  }

  if (error || !article) {
    return (
      <main className="article-pub-stage">
        <div className="article-pub-wrap">
          <nav className="article-pub-nav" aria-label="Đường dẫn">
            <Link to="/bai-viet" className="article-pub-back">
              <ArrowLeft size={16} aria-hidden="true" />
              <span>Tất cả bài viết</span>
            </Link>
          </nav>
          <div className="article-pub-error" role="alert">
            <AlertCircle size={28} className="article-pub-error-icon" aria-hidden="true" />
            <h2 className="article-pub-error-title">Không thể tải bài viết</h2>
            <p className="article-pub-error-desc">{error ?? 'Đường dẫn không hợp lệ hoặc bài viết không tồn tại.'}</p>
            <div className="article-pub-error-actions">
              <button
                type="button"
                className="article-pub-btn-primary"
                onClick={() => setReloadKey((k) => k + 1)}
              >
                <RotateCcw size={15} aria-hidden="true" />
                <span>Thử tải lại</span>
              </button>
              <Link to="/bai-viet" className="article-pub-btn-subtle">
                <span>Về danh sách bài viết</span>
              </Link>
            </div>
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className="article-pub-stage">
      <div className="article-pub-wrap">
        {/* Editorial Top Navigation */}
        <nav className="article-pub-nav" aria-label="Điều hướng bài viết">
          <Link to="/bai-viet" className="article-pub-back">
            <ArrowLeft size={16} aria-hidden="true" />
            <span>Tất cả bài viết</span>
          </Link>

          <ol className="article-pub-breadcrumbs">
            <li>
              <Link to="/" className="article-pub-crumb-link">Trang chủ</Link>
            </li>
            <li aria-hidden="true" className="article-pub-crumb-sep">
              <ChevronRight size={12} />
            </li>
            <li>
              <Link to="/bai-viet" className="article-pub-crumb-link">Bài viết</Link>
            </li>
            <li aria-hidden="true" className="article-pub-crumb-sep">
              <ChevronRight size={12} />
            </li>
            <li className="article-pub-crumb-current" aria-current="page">
              {article.title}
            </li>
          </ol>
        </nav>

        {/* Publication Header */}
        <header className="article-pub-header">
          <div className="article-pub-kicker">
            <span>BÀI VIẾT SMART LAB</span>
          </div>

          <h1 className="article-pub-title">{article.title}</h1>

          {article.excerpt ? (
            <p className="article-pub-standfirst">{article.excerpt}</p>
          ) : null}

          <div className="article-pub-byline">
            <span className="article-pub-byline-author">Ban Biên tập SmartLab</span>
            {article.publishedAt ? (
              <>
                <span className="article-pub-dot" aria-hidden="true">•</span>
                <span className="article-pub-byline-item">
                  <Calendar size={13} aria-hidden="true" />
                  <time dateTime={article.publishedAt}>{formatDate(article.publishedAt)}</time>
                </span>
              </>
            ) : null}
            <span className="article-pub-dot" aria-hidden="true">•</span>
            <span className="article-pub-byline-item">
              <Clock size={13} aria-hidden="true" />
              <span>{readingTime} phút đọc</span>
            </span>
          </div>
        </header>

        {/* Editorial Rule */}
        <hr className="article-pub-divider" aria-hidden="true" />

        {/* Article Reading Body */}
        <div className="article-pub-body">
          <ArticleContent
            contentJson={article.content}
            className="article-pub-prose"
            bodyClassName="article-pub-text"
            fallbackClassName="article-pub-fallback"
          />
        </div>

        {/* Publication Sign-off & Footer Actions */}
        <footer className="article-pub-footer">
          <div className="article-pub-colophon">
            <span className="article-pub-colophon-mark" aria-hidden="true">◆</span>
            <p className="article-pub-colophon-note">
              Cổng thông tin &amp; Nghiên cứu Smart Lab
            </p>
          </div>

          <div className="article-pub-actions">
            <Link to="/bai-viet" className="article-pub-btn-primary">
              <ArrowLeft size={15} aria-hidden="true" />
              <span>Quay lại danh sách bài viết</span>
            </Link>

            <button
              type="button"
              onClick={handleScrollToTop}
              className="article-pub-btn-ghost"
              aria-label="Cuộn về đầu trang"
            >
              <span>Về đầu trang ↑</span>
            </button>
          </div>
        </footer>
      </div>
    </main>
  )
}

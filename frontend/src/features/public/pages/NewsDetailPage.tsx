import { ArrowLeft, Calendar, ChevronRight, AlertCircle, RotateCcw, ExternalLink } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { PublicPageHead } from '../components/PublicPageHead'
import { getNews } from '../newsApi'
import type { LabNewsArticle } from '../newsTypes'
import '../landing.css'
import './NewsDetailPage.css'

export function NewsDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [news, setNews] = useState<LabNewsArticle | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) { setLoading(false); return }
    let active = true
    setLoading(true)
    setError(null)

    getNews(Number(id))
      .then((data) => { if (active) setNews(data) })
      .catch(() => { if (active) setError('Không thể tải tin tức lúc này.') })
      .finally(() => { if (active) setLoading(false) })

    return () => { active = false }
  }, [id])

  function handleScrollToTop() { window.scrollTo({ top: 0, behavior: 'smooth' }) }

  if (loading) return <Skeleton />
  if (error || !news) return <ErrorState error={error} onBack={() => window.history.back()} />

  return (
    <main className="news-detail-stage">
      <div className="news-detail-wrap">
        {/* Navigation */}
        <nav className="news-detail-nav" aria-label="Điều hướng">
          <Link to="/tin-tuc" className="news-detail-back">
            <ArrowLeft size={16} />
            <span>Tất cả tin tức</span>
          </Link>
          <ol className="news-detail-breadcrumbs">
            <li><Link to="/" className="news-detail-crumb-link">Trang chủ</Link></li>
            <li className="news-detail-crumb-sep"><ChevronRight size={12} /></li>
            <li><Link to="/tin-tuc" className="news-detail-crumb-link">Tin tức</Link></li>
            <li className="news-detail-crumb-sep"><ChevronRight size={12} /></li>
            <li className="news-detail-crumb-current">{news.title}</li>
          </ol>
        </nav>

        {/* Header */}
        <header className="news-detail-header">
          <div className="news-detail-kicker">{news.sourceName}</div>
          <h1 className="news-detail-title">{news.title}</h1>
          {news.excerpt && <p className="news-detail-standfirst">{news.excerpt}</p>}

          <div className="news-detail-byline">
            <span className="news-detail-byline-item">
              <Calendar size={13} />
              <time dateTime={news.publishedAt}>{formatDate(news.publishedAt)}</time>
            </span>
          </div>
        </header>

        <hr className="news-detail-divider" />

        {/* Actions */}
        <footer className="news-detail-footer">
          <a href={news.sourceUrl} target="_blank" rel="noopener noreferrer" className="news-detail-external-link">
            <ExternalLink size={15} />
            <span>Đọc bài viết gốc tại {news.sourceName}</span>
          </a>
          <div className="news-detail-actions">
            <Link to="/tin-tuc" className="news-detail-btn-primary">
              <ArrowLeft size={15} />
              <span>Quay lại</span>
            </Link>
            <button type="button" onClick={handleScrollToTop} className="news-detail-btn-ghost">Về đầu trang</button>
          </div>
        </footer>
      </div>
    </main>
  )
}

function Skeleton() {
  return (
    <main className="news-detail-stage">
      <div className="news-detail-wrap">
        <nav className="news-detail-nav">
          <Link to="/tin-tuc" className="news-detail-back"><ArrowLeft size={16} /><span>Tất cả tin tức</span></Link>
        </nav>
        <div className="news-detail-skeleton">
          <div className="skeleton-kicker" />
          <div className="skeleton-title" />
          <div className="skeleton-title short" />
          <div className="skeleton-meta" />
        </div>
      </div>
    </main>
  )
}

function ErrorState({ error, onBack }: { error: string | null; onBack: () => void }) {
  return (
    <main className="news-detail-stage">
      <div className="news-detail-wrap">
        <nav className="news-detail-nav">
          <button onClick={onBack} className="news-detail-back"><ArrowLeft size={16} /><span>Quay lại</span></button>
        </nav>
        <div className="news-detail-error">
          <AlertCircle size={28} />
          <h2>Không thể tải tin tức</h2>
          <p>{error ?? 'Tin tức không tồn tại hoặc không công khai.'}</p>
        </div>
      </div>
    </main>
  )
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(value))
}

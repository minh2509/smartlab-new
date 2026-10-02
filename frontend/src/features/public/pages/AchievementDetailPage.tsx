import { ArrowLeft, Calendar, ChevronRight, AlertCircle, RotateCcw, ExternalLink } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { PublicPageHead } from '../components/PublicPageHead'
import { getAchievement } from '../achievementApi'
import { ACHIEVEMENT_TYPE_LABELS } from '../achievementTypes'
import type { AchievementDetail } from '../achievementTypes'
import '../landing.css'
import './AchievementDetailPage.css'

export function AchievementDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [achievement, setAchievement] = useState<AchievementDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) { setLoading(false); return }
    let active = true
    setLoading(true)
    setError(null)

    getAchievement(Number(id))
      .then((data) => { if (active) setAchievement(data) })
      .catch(() => { if (active) setError('Không thể tải thành tựu lúc này.') })
      .finally(() => { if (active) setLoading(false) })

    return () => { active = false }
  }, [id])

  function handleScrollToTop() { window.scrollTo({ top: 0, behavior: 'smooth' }) }

  if (loading) return <Skeleton />
  if (error || !achievement) return <ErrorState error={error} onBack={() => window.history.back()} />

  const hasImages = achievement.images?.length > 0

  return (
    <main className="achievement-detail-stage">
      <div className="achievement-detail-wrap">
        {/* Navigation */}
        <nav className="achievement-detail-nav" aria-label="Điều hướng">
          <Link to="/thanh-tuu" className="achievement-detail-back">
            <ArrowLeft size={16} />
            <span>Tất cả thành tựu</span>
          </Link>
          <ol className="achievement-detail-breadcrumbs">
            <li><Link to="/" className="achievement-detail-crumb-link">Trang chủ</Link></li>
            <li className="achievement-detail-crumb-sep"><ChevronRight size={12} /></li>
            <li><Link to="/thanh-tuu" className="achievement-detail-crumb-link">Thành tựu</Link></li>
            <li className="achievement-detail-crumb-sep"><ChevronRight size={12} /></li>
            <li className="achievement-detail-crumb-current">{achievement.title}</li>
          </ol>
        </nav>

        {/* Header */}
        <header className="achievement-detail-header">
          <div className="achievement-detail-kicker">{ACHIEVEMENT_TYPE_LABELS[achievement.achievementType]}</div>
          <h1 className="achievement-detail-title">{achievement.title}</h1>
          {achievement.summary && <p className="achievement-detail-standfirst">{achievement.summary}</p>}

          <div className="achievement-detail-byline">
            <span className="achievement-detail-byline-item">
              <Calendar size={13} />
              <time dateTime={achievement.achievementDate ?? undefined}>{achievement.achievementDate ? formatDate(achievement.achievementDate) : `Năm ${achievement.achievementYear}`}</time>
            </span>
            {achievement.recognizingOrganization && (
              <>
                <span className="achievement-detail-dot" />
                <span className="achievement-detail-byline-item">{achievement.recognizingOrganization}</span>
              </>
            )}
            {achievement.relatedProject && (
              <>
                <span className="achievement-detail-dot" />
                <Link to={`/du-an/${achievement.relatedProject.id}`} className="achievement-detail-project-link">
                  {achievement.relatedProject.code}
                </Link>
              </>
            )}
          </div>
        </header>

        <hr className="achievement-detail-divider" />

        {/* Images */}
        {hasImages && (
          <section className="achievement-detail-images">
            <div className="achievement-detail-images-grid">
              {achievement.images.map((image) => (
                <figure key={image.id} className="achievement-detail-image">
                  <img src={`/api/v1.0/achievements/${achievement.id}/files/${image.id}`} alt={image.label || achievement.title} loading="lazy" />
                  {image.label && <figcaption>{image.label}</figcaption>}
                </figure>
              ))}
            </div>
          </section>
        )}

        {/* Actions */}
        <footer className="achievement-detail-footer">
          {achievement.evidenceUrl && (
            <a href={achievement.evidenceUrl} target="_blank" rel="noopener noreferrer" className="achievement-detail-evidence">
              <ExternalLink size={15} />
              <span>Xem minh chứng</span>
            </a>
          )}
          <div className="achievement-detail-actions">
            <Link to="/thanh-tuu" className="achievement-detail-btn-primary">
              <ArrowLeft size={15} />
              <span>Quay lại</span>
            </Link>
            <button type="button" onClick={handleScrollToTop} className="achievement-detail-btn-ghost">Về đầu trang</button>
          </div>
        </footer>
      </div>
    </main>
  )
}

function Skeleton() {
  return (
    <main className="achievement-detail-stage">
      <div className="achievement-detail-wrap">
        <nav className="achievement-detail-nav">
          <Link to="/thanh-tuu" className="achievement-detail-back"><ArrowLeft size={16} /><span>Tất cả thành tựu</span></Link>
        </nav>
        <div className="achievement-detail-skeleton">
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
    <main className="achievement-detail-stage">
      <div className="achievement-detail-wrap">
        <nav className="achievement-detail-nav">
          <button onClick={onBack} className="achievement-detail-back"><ArrowLeft size={16} /><span>Quay lại</span></button>
        </nav>
        <div className="achievement-detail-error">
          <AlertCircle size={28} />
          <h2>Không thể tải thành tựu</h2>
          <p>{error ?? 'Thành tựu không tồn tại hoặc không công khai.'}</p>
        </div>
      </div>
    </main>
  )
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(value))
}

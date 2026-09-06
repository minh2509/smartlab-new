import {
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  Inbox,
  MapPin,
  UsersRound,
  Wifi,
  WifiOff,
} from 'lucide-react'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router-dom'
import type { ResearchField } from '../../../shared/types/api'
import '../../../assets/lab.css'
import '../landing.css'
import { listPublicEvents } from '../../events/api'
import type { LabEvent } from '../../events/types'
import { listLatestNews } from '../newsApi'
import type { LabNewsArticle } from '../newsTypes'
import { listLatestArticles } from '../articleApi'
import type { LabArticleSummary } from '../articleTypes'
import { getResearchFields } from '../../profile/api'
import { listPublicRecruitingProjects, type RecruitingPage } from '../../projects/api'
import { PROJECT_TYPE_LABELS } from '../../projects/types'
import { listAchievements, listAchievementYears } from '../achievementApi'
import type { Achievement, YearCount } from '../achievementTypes'
import { ACHIEVEMENT_TYPE_LABELS } from '../achievementTypes'

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
const publicFileUrl = (fileId: number) => `${baseUrl}/files/${fileId}`
const MEDIA_TABS = ['ARTICLE', 'NEWS'] as const
type MediaTab = typeof MEDIA_TABS[number]
const MEDIA_TAB_IDS: Record<MediaTab, string> = {
  ARTICLE: 'landing-article-tab',
  NEWS: 'landing-news-tab',
}

function initialsOf(name: string | null | undefined) {
  if (!name) return '?'
  return name.trim().split(/\s+/).filter(Boolean).slice(-2).map((p) => p.charAt(0)).join('').toLocaleUpperCase('vi')
}

function formatDate(value: string | null | undefined, opts?: Intl.DateTimeFormatOptions) {
  if (!value) return ''
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : new Intl.DateTimeFormat('vi-VN', opts ?? { dateStyle: 'short' }).format(d)
}

function shorten(value: string | null | undefined, max: number) {
  if (!value) return ''
  return value.length > max ? `${value.slice(0, max).trimEnd()}…` : value
}


// ─── sub-components ─────────────────────────────────────────────────────────────

function LandingState({ loading, error, empty, emptyText, dark = false }: {
  loading: boolean; error?: string; empty: boolean; emptyText: string; dark?: boolean
}) {
  if (loading && empty) return (
    <div className={`landing-empty-state${dark ? ' on-dark' : ''}`} role="status" aria-live="polite" aria-atomic="true">
      <span>Đang tải dữ liệu…</span>
    </div>
  )
  if (error && empty) return (
    <div className={`landing-empty-state${dark ? ' on-dark' : ''} is-error`} role="alert">
      <span>{error}</span>
    </div>
  )
  if (!loading && empty) return (
    <div className={`landing-empty-state${dark ? ' on-dark' : ''}`} role="status" aria-live="polite" aria-atomic="true">
      <Inbox size={24} className="landing-empty-state-icon" strokeWidth={1.5} />
      <span>{emptyText}</span>
    </div>
  )
  return null
}

// ─── main component ────────────────────────────────────────────────────────────

export function HomePage() {
  // ── Research fields
  const [fields, setFields] = useState<ResearchField[]>([])
  const [fieldsLoading, setFieldsLoading] = useState(true)
  const [fieldsError, setFieldsError] = useState<string | undefined>()

  // ── Recruiting projects
  const [recruitPage, setRecruitPage] = useState<RecruitingPage | null>(null)
  const [recruitLoading, setRecruitLoading] = useState(true)
  const [recruitError, setRecruitError] = useState<string | undefined>()

  // ── Events
  const [events, setEvents] = useState<LabEvent[]>([])
  const [eventsLoading, setEventsLoading] = useState(true)
  const [eventsError, setEventsError] = useState<string | undefined>()

  // ── Achievements
  const [years, setYears] = useState<YearCount[]>([])
  const [selectedYear, setSelectedYear] = useState<number | null>(null)
  const [achievementItems, setAchievementItems] = useState<Achievement[]>([])
  const [achievementTotal, setAchievementTotal] = useState(0)
  const [achievementCurrentPage, setAchievementCurrentPage] = useState(0)
  const [achievementLoading, setAchievementLoading] = useState(true)
  const [achievementError, setAchievementError] = useState<string | undefined>()

  // ── Unified Media Tab
  const [mediaTab, setMediaTab] = useState<MediaTab>('ARTICLE')
  const mediaTabRefs = useRef<Record<MediaTab, HTMLButtonElement | null>>({ ARTICLE: null, NEWS: null })

  // ── Latest updates (News)
  const [newsItems, setNewsItems] = useState<LabNewsArticle[]>([])
  const [newsLoading, setNewsLoading] = useState(true)
  const [newsError, setNewsError] = useState<string | undefined>()

  // ── Latest Articles
  const [articleItems, setArticleItems] = useState<LabArticleSummary[]>([])
  const [articleLoading, setArticleLoading] = useState(true)
  const [articleError, setArticleError] = useState<string | undefined>()

  const [failedImages, setFailedImages] = useState<Record<number, boolean>>({})

  // ── Initial data load
  useEffect(() => {
    let active = true

    // Fields
    getResearchFields()
      .then((r) => { if (active) { setFields(r); setFieldsLoading(false) } })
      .catch(() => { if (active) { setFieldsError('Không thể tải dữ liệu định hướng lúc này. Vui lòng thử lại sau.'); setFieldsLoading(false) } })

    // Events
    listPublicEvents({ upcoming: false, limit: 4, sort: 'LATEST' })
      .then((r) => { if (active) { setEvents(r); setEventsLoading(false) } })
      .catch(() => { if (active) { setEventsError('Không thể tải sự kiện lúc này. Vui lòng thử lại sau.'); setEventsLoading(false) } })

    // News
    listLatestNews(3)
      .then((r) => { if (active) { setNewsItems(r); setNewsLoading(false) } })
      .catch(() => { if (active) { setNewsError('Không thể tải tin tức lúc này. Vui lòng thử lại sau.'); setNewsLoading(false) } })

    // Articles
    listLatestArticles(3)
      .then((r) => { if (active) { setArticleItems(r); setArticleLoading(false) } })
      .catch(() => { if (active) { setArticleError('Không thể tải bài viết lúc này. Vui lòng thử lại sau.'); setArticleLoading(false) } })

    // Achievement years
    listAchievementYears()
      .then((r) => {
        if (!active) return
        setYears(r)
        if (r.length > 0) {
          setSelectedYear(r[0].year)
        } else {
          setAchievementLoading(false)
        }
      })
      .catch(() => { if (active) { setAchievementError('Không thể tải thành tựu lúc này. Vui lòng thử lại sau.'); setAchievementLoading(false) } })

    return () => { active = false }
  }, [])

  // ── Bounded recruiting preview
  useEffect(() => {
    let active = true
    setRecruitLoading(true)
    setRecruitError(undefined)
    setRecruitPage(null)
    listPublicRecruitingProjects(0, 6)
      .then((r) => { if (active) { setRecruitPage(r); setRecruitLoading(false) } })
      .catch(() => { if (active) { setRecruitError('Không thể tải dự án lúc này. Vui lòng thử lại sau.'); setRecruitLoading(false) } })
    return () => { active = false }
  }, [])

  // ── Achievement year / page change
  useEffect(() => {
    if (selectedYear === null) return
    let active = true

    setAchievementLoading(true)
    setAchievementError(undefined)

    listAchievements(selectedYear, achievementCurrentPage, 3)
      .then((r) => {
        if (active) {
          setAchievementItems(r.items)
          setAchievementTotal(r.totalElements)
          setAchievementLoading(false)
        }
      })
      .catch(() => {
        if (active) {
          setAchievementError('Không thể tải thành tựu lúc này. Vui lòng thử lại sau.')
          setAchievementLoading(false)
        }
      })
    return () => { active = false }
  }, [selectedYear, achievementCurrentPage])

  function handleMediaTabKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const currentIndex = MEDIA_TABS.indexOf(mediaTab)
    let nextIndex = currentIndex

    if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % MEDIA_TABS.length
    else if (event.key === 'ArrowLeft') nextIndex = (currentIndex + MEDIA_TABS.length - 1) % MEDIA_TABS.length
    else if (event.key === 'Home') nextIndex = 0
    else if (event.key === 'End') nextIndex = MEDIA_TABS.length - 1
    else return

    event.preventDefault()
    const nextTab = MEDIA_TABS[nextIndex]
    setMediaTab(nextTab)
    mediaTabRefs.current[nextTab]?.focus()
  }

  function handleYearChange(year: number) {
    if (year === selectedYear) return
    setSelectedYear(year)
    setAchievementCurrentPage(0)
    setAchievementTotal(0)
    setAchievementItems([])
  }

  function handleAchievementPrev() {
    setAchievementCurrentPage(prev => Math.max(0, prev - 1))
  }

  function handleAchievementNext() {
    setAchievementCurrentPage(prev => prev + 1)
  }

  const recruitItems = recruitPage?.items ?? []

  return (
    <div className="landing-page">

      {/* ═══════════════════════════════════════════════════════════
          1. HERO
          ═══════════════════════════════════════════════════════════ */}
      <section className="landing-hero" aria-label="Giới thiệu Smart Lab">
        <div className="landing-hero-in">

          {/* Left — copy */}
          <div className="landing-hero-copy">
            <h1 className="landing-hero-title">
              <span className="landing-hero-brand">SMART LAB</span>
              <span className="landing-hero-headline">
                Nghiên cứu thật.<br />
                Dự án thật.<br />
                Sản phẩm thật.
              </span>
            </h1>

            <p className="landing-hero-lead">
              Phòng nghiên cứu về AI, Robotics và Software Engineering,
              nơi sinh viên cùng mentor xây dựng các dự án và sản phẩm thực tế.
            </p>

            <div className="landing-hero-cta">
              <Link className="btn primary lg" to="/linh-vuc">
                Khám phá lĩnh vực <ArrowRight size={17} />
              </Link>
              <Link className="btn outline-light lg" to="/du-an?status=RECRUITING">
                Xem dự án đang tuyển
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          2. RESEARCH AREAS
          ═══════════════════════════════════════════════════════════ */}
      <section className="landing-section" aria-labelledby="research-heading">
        <div className="landing-wrap">
          <div className="landing-head">
            <div className="landing-head-eyebrow">Định hướng nghiên cứu</div>
            <h2 id="research-heading">Lĩnh vực nghiên cứu</h2>
            <p>
              Các hướng nghiên cứu định hình dự án và hoạt động chuyên môn tại Smart Lab.
            </p>
          </div>

          <LandingState
            loading={fieldsLoading}
            error={fieldsError}
            empty={fields.length === 0}
            emptyText="Chưa có lĩnh vực nghiên cứu công khai."
          />

          {fields.length > 0 && (
            <div className="landing-research-grid">
              {fields.map((field, i) => (
                <article
                  className="landing-field-card"
                  key={field.id}
                >
                  <div className="landing-field-cover-area">
                    {field.coverFileId && !failedImages[field.id] ? (
                      <img
                        src={publicFileUrl(field.coverFileId)}
                        alt={`Ảnh đại diện lĩnh vực ${field.name}`}
                        className="landing-field-image"
                        onError={() => setFailedImages(prev => ({ ...prev, [field.id]: true }))}
                      />
                    ) : (
                      <div className="landing-field-fallback" aria-hidden="true">
                        <span className="landing-field-geom" data-index={i}>
                          {i % 3 === 0 && (
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                              <path d="M4 20v-4a4 4 0 0 1 4-4h12" />
                            </svg>
                          )}
                          {i % 3 === 1 && (
                            <svg width="32" height="12" viewBox="0 0 32 12" fill="none" stroke="currentColor" strokeWidth="1.5">
                              <path d="M0 6h12" />
                              <rect x="12" y="2" width="8" height="8" />
                              <path d="M20 6h12" />
                            </svg>
                          )}
                          {i % 3 === 2 && (
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                              <path d="M20 4l-8 8" />
                              <path d="M0 12h12v12" />
                            </svg>
                          )}
                        </span>
                      </div>
                    )}
                  </div>
                  <div className="landing-field-head">
                    <span className="landing-field-index">{String(i + 1).padStart(2, '0')}</span>
                  </div>

                  <div className="landing-field-body">
                    <h3>{field.name}</h3>
                    <p className="landing-field-desc">{field.description ?? 'Thông tin chi tiết đang được cập nhật.'}</p>
                  </div>

                  <div className="landing-field-foot">
                    <span className="landing-field-code">{field.code}</span>
                    <Link className="landing-field-link" to="/linh-vuc">Xem thêm <ArrowRight size={13} /></Link>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          3. RECRUITING PROJECTS
          ═══════════════════════════════════════════════════════════ */}
      <section className="landing-section landing-section-alt" aria-labelledby="recruit-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
              <div className="landing-head-eyebrow">Tham gia Lab</div>
              <h2 id="recruit-heading">Dự án đang tuyển thành viên</h2>
              <p>Các dự án hiện đang mở để nhận thành viên mới. Tìm dự án phù hợp và liên hệ leader.</p>
            </div>
            <Link className="landing-section-cta" to="/du-an?status=RECRUITING">
              Xem dự án đang tuyển <ArrowRight size={16} />
            </Link>
          </div>

          <LandingState
            loading={recruitLoading}
            error={recruitError}
            empty={recruitItems.length === 0}
            emptyText="Hiện chưa có dự án nào đang tuyển thành viên."
          />

          {recruitItems.length > 0 && (
            <div className="landing-recruit-grid">
              {recruitItems.map((project) => {
                const shownLeaders = project.leaders.slice(0, 3)
                const rest = project.leaders.length - shownLeaders.length
                const desc = project.description ?? project.goal ?? 'Xem chi tiết để biết thêm thông tin về dự án này.'
                return (
                  <Link
                    className="landing-recruit-card"
                    to={`/du-an/${project.id}`}
                    key={project.id}
                    aria-label={`Dự án ${project.name}`}
                  >
                    <div className="landing-recruit-card-top">
                      <span className="landing-recruit-badge">
                        <span className="landing-recruit-badge-dot" aria-hidden="true" />
                        Đang tuyển
                      </span>
                      <span className="chip accent">{PROJECT_TYPE_LABELS[project.projectType]}</span>
                      {project.researchFields && project.researchFields.length > 0 && (
                        <span className="chip muted">{project.researchFields[0].name}</span>
                      )}
                    </div>

                    <div className="landing-recruit-card-body">
                      <span className="landing-recruit-code">{project.code}</span>
                      <h3>{project.name}</h3>
                      <p>{shorten(desc, 160)}</p>
                    </div>

                    <div className="landing-recruit-card-foot">
                      {shownLeaders.length > 0 ? (
                        <span className="ava-stack" aria-label={`${project.leaders.length} leader`}>
                          {shownLeaders.map((l, index) => (
                            <span className="ava xs" title={l.name} key={`${l.name}-${index}`}>{initialsOf(l.name)}</span>
                          ))}
                          {rest > 0 ? <span className="ava xs">+{rest}</span> : null}
                        </span>
                      ) : (
                        <span className="muted small"><UsersRound size={13} /> Chưa công bố leader</span>
                      )}
                      <span className="landing-recruit-link">
                        Xem &amp; đăng ký <ArrowRight size={13} />
                      </span>
                    </div>
                  </Link>
                )
              })}
            </div>
          )}

        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          4. LAB LIFE / ANNUAL ACTIVITIES
          ═══════════════════════════════════════════════════════════ */}
      <section className="landing-section landing-section-dark" aria-labelledby="lablife-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
              <div className="landing-head-eyebrow">Văn hoá &amp; Đời sống</div>
              <h2 id="lablife-heading">Kết nối cộng đồng</h2>
              <p>Sự cân bằng giữa sự tập trung cao độ trong nghiên cứu và một môi trường gắn kết, cởi mở. Những hoạt động giúp các thành viên phát triển bản thân và xây dựng văn hóa Smart Lab.</p>
            </div>
            <Link className="landing-section-cta" to="/su-kien">
              Khám phá hoạt động của Lab <ArrowRight size={16} />
            </Link>
          </div>

          <LandingState
            loading={eventsLoading}
            error={eventsError}
            empty={events.length === 0}
            emptyText="Chưa có sự kiện công khai nào được ghi nhận."
            dark
          />

          {events.length > 0 && (
            <div className="landing-events-grid">
              {events.map((event) => {
                const start = new Date(event.startAt)
                const day = Number.isNaN(start.getTime()) ? '—' : String(start.getDate())
                const month = Number.isNaN(start.getTime())
                  ? ''
                  : new Intl.DateTimeFormat('vi-VN', { month: 'short' }).format(start)
                return (
                  <div className="landing-event-card" key={event.id}>
                    <div className="landing-event-datebox" aria-label={`Ngày ${day} ${month}`}>
                      <span className="landing-event-datebox-day">{day}</span>
                      <span className="landing-event-datebox-month">{month}</span>
                    </div>
                    <div className="landing-event-info">
                      <h3>{event.title}</h3>
                      <div className="landing-event-meta">
                        {event.location ? (
                          <span><MapPin size={13} />{event.location}</span>
                        ) : null}
                        <span>
                          {event.mode === 'ONLINE'
                            ? <><Wifi size={13} />Trực tuyến</>
                            : <><WifiOff size={13} />Trực tiếp</>
                          }
                        </span>
                      </div>
                      {event.content ? (
                        <p className="landing-event-excerpt">{shorten(event.content, 120)}</p>
                      ) : null}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          5. RESEARCH ACHIEVEMENTS / PUBLICATIONS
          ═══════════════════════════════════════════════════════════ */}
      <section className="landing-section" aria-labelledby="achievement-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
              <div className="landing-head-eyebrow">THÀNH TỰU SMART LAB</div>
              <h2 id="achievement-heading">Kết quả &amp; Thành tựu</h2>
              <p>Những sản phẩm, kết quả nghiên cứu và dấu mốc được Smart Lab ghi nhận trong quá trình hoạt động.</p>
            </div>
            <Link className="landing-section-cta" to="/thanh-tuu">
              Xem toàn bộ thành tựu <ArrowRight size={16} />
            </Link>
          </div>

          <div className="landing-achievement-controls">
            {years.length > 0 && (
              <div className="landing-achievement-toolbar-left">
                <select
                  id="year-selector"
                  className="landing-year-select"
                  value={selectedYear ?? ''}
                  onChange={(e) => handleYearChange(Number(e.target.value))}
                  aria-label="Chọn năm"
                >
                  {years.map(({ year, count }) => (
                    <option key={year} value={year}>
                      {year} · {count} thành tựu
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="landing-achievement-toolbar-right">
              <div className="landing-achievement-toolbar-text">
                {achievementTotal > 0 ? `${achievementCurrentPage * 3 + 1}–${Math.min((achievementCurrentPage + 1) * 3, achievementTotal)} / ${achievementTotal}` : '0 / 0'}
              </div>
              <div className="landing-achievement-toolbar-actions">
                <button
                  type="button"
                  className="landing-achievement-toolbar-btn"
                  onClick={handleAchievementPrev}
                  disabled={achievementCurrentPage === 0 || achievementLoading}
                  aria-label="Trang trước"
                >
                  <ChevronLeft size={18} />
                </button>
                <button
                  type="button"
                  className="landing-achievement-toolbar-btn"
                  onClick={handleAchievementNext}
                  disabled={(achievementCurrentPage + 1) * 3 >= achievementTotal || achievementLoading}
                  aria-label="Trang sau"
                >
                  <ChevronRight size={18} />
                </button>
              </div>
            </div>
          </div>

          <LandingState
            loading={achievementLoading}
            error={achievementError}
            empty={achievementItems.length === 0}
            emptyText={selectedYear ? `Chưa có thành tựu nào công khai trong năm ${selectedYear}.` : 'Chưa có thành tựu nào công khai.'}
          />

          {achievementItems.length > 0 && (
            <div className="landing-achievement-list-wrap">
              <div className="landing-achievement-grid" aria-label="Danh sách thành tựu">
                {achievementItems.map((item: Achievement) => {
                  const typeLabel = ACHIEVEMENT_TYPE_LABELS[item.achievementType]
                  return (
                    <div className="landing-achievement-card" key={item.id}>
                      <div className="landing-achievement-cover" data-type={item.achievementType}>
                        <div className="landing-achievement-cover-grid" />
                      </div>
                      <div className="landing-achievement-card-body">
                        <div className="landing-achievement-card-meta">
                          <span className="landing-achievement-card-type">{typeLabel}</span>
                          {(item.achievementDate || item.achievementYear) ? (
                            <span className="landing-achievement-card-date">
                              • {item.achievementDate ? formatDate(item.achievementDate) : item.achievementYear}
                            </span>
                          ) : null}
                        </div>
                        <h3 className="landing-achievement-card-title">
                          {item.title}
                        </h3>
                        {item.summary ? (
                          <div className="landing-achievement-card-summary">{item.summary}</div>
                        ) : null}
                        {item.relatedProject ? (
                          <div className="landing-achievement-card-project">
                            Liên quan: {item.relatedProject.name}
                          </div>
                        ) : null}
                        <div className="landing-achievement-card-footer">
                          {item.evidenceUrl ? (
                            <a
                              className="landing-achievement-card-action"
                              href={item.evidenceUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              aria-label="Xem minh chứng"
                            >
                              Xem minh chứng ↗
                            </a>
                          ) : null}
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          6. UNIFIED ARTICLES & MEDIA
          ═══════════════════════════════════════════════════════════ */}
      <section className="landing-section landing-section-alt" aria-labelledby="media-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
              <div className="landing-head-eyebrow">BÀI VIẾT &amp; TRUYỀN THÔNG</div>
              <h2 id="media-heading">Cập nhật mới nhất</h2>
              <p>Bài viết từ Smart Lab và những thông tin truyền thông mới nhất liên quan đến phòng Lab.</p>
            </div>
          </div>

          <div className="landing-media-control-row">
            <div className="landing-media-tabs" role="tablist" aria-label="Lọc cập nhật" onKeyDown={handleMediaTabKeyDown}>
              <button
                type="button"
                id={MEDIA_TAB_IDS.ARTICLE}
                ref={(element) => { mediaTabRefs.current.ARTICLE = element }}
                role="tab"
                aria-selected={mediaTab === 'ARTICLE'}
                tabIndex={mediaTab === 'ARTICLE' ? 0 : -1}
                aria-controls="landing-article-panel"
                className={`landing-media-tab ${mediaTab === 'ARTICLE' ? 'active' : ''}`}
                onClick={() => setMediaTab('ARTICLE')}
              >
                Bài viết
              </button>
              <button
                type="button"
                id={MEDIA_TAB_IDS.NEWS}
                ref={(element) => { mediaTabRefs.current.NEWS = element }}
                role="tab"
                aria-selected={mediaTab === 'NEWS'}
                tabIndex={mediaTab === 'NEWS' ? 0 : -1}
                aria-controls="landing-news-panel"
                className={`landing-media-tab ${mediaTab === 'NEWS' ? 'active' : ''}`}
                onClick={() => setMediaTab('NEWS')}
              >
                Tin tức
              </button>
            </div>

            {mediaTab === 'ARTICLE' ? (
              <Link className="landing-media-archive-link" to="/bai-viet">
                Xem bài viết <ArrowRight size={16} />
              </Link>
            ) : (
              <Link className="landing-media-archive-link" to="/tin-tuc">
                Xem tin tức <ArrowRight size={16} />
              </Link>
            )}
          </div>

          <LandingState
            loading={mediaTab === 'ARTICLE' ? articleLoading : newsLoading}
            error={mediaTab === 'ARTICLE' ? articleError : newsError}
            empty={mediaTab === 'ARTICLE' ? articleItems.length === 0 : newsItems.length === 0}
            emptyText={mediaTab === 'ARTICLE' ? "Chưa có bài viết nào được công bố." : "Chưa có thông tin truyền thông nào."}
          />

          {mediaTab === 'ARTICLE' && articleItems.length > 0 && (
            <div className="landing-media-grid" id="landing-article-panel" role="tabpanel" aria-labelledby={MEDIA_TAB_IDS.ARTICLE}>
              {articleItems.map((article) => (
                <Link className="landing-media-card hover is-article" to={`/bai-viet/${encodeURIComponent(article.slug)}`} key={article.id}>
                  <div className="landing-media-cover is-article">
                    <div className="landing-media-cover-grid" />
                  </div>
                  <div className="landing-media-card-body">
                    <div className="landing-media-card-meta">
                      {article.publishedAt ? (
                        <span className="landing-media-card-date">
                          {formatDate(article.publishedAt, { dateStyle: 'short' })}
                        </span>
                      ) : null}
                    </div>
                    <h3 className="landing-media-card-title">
                      {article.title}
                    </h3>
                    {article.excerpt ? (
                      <div className="landing-media-card-excerpt">{shorten(article.excerpt, 150)}</div>
                    ) : null}
                    <div className="landing-media-card-foot">
                      <span className="landing-media-card-action">Đọc bài viết &rarr;</span>
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          )}

          {mediaTab === 'NEWS' && newsItems.length > 0 && (
            <div className="landing-media-grid" id="landing-news-panel" role="tabpanel" aria-labelledby={MEDIA_TAB_IDS.NEWS}>
              {newsItems.map((news) => (
                <div className="landing-media-card hover is-news" key={news.id}>
                  <div className="landing-media-cover is-news">
                    <div className="landing-media-cover-grid" />
                  </div>
                  <div className="landing-media-card-body">
                    <div className="landing-media-card-meta">
                      <span className="landing-media-card-source">{news.sourceName}</span>
                      {news.publishedAt ? (
                        <span className="landing-media-card-date">
                          &bull; {formatDate(news.publishedAt, { dateStyle: 'short' })}
                        </span>
                      ) : null}
                    </div>
                    <h3 className="landing-media-card-title">
                      {news.title}
                    </h3>
                    {news.excerpt ? (
                      <div className="landing-media-card-excerpt">{shorten(news.excerpt, 150)}</div>
                    ) : null}
                    <div className="landing-media-card-foot">
                      <a
                        className="landing-media-card-action"
                        href={news.sourceUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label={`Xem chi tiết bài viết trên ${news.sourceName}`}
                      >
                        Xem chi tiết &rarr;
                      </a>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="landing-final-cta" aria-label="Khám phá Smart Lab">
        <div className="landing-wrap landing-final-cta-inner">
          <div>
            <h2>Tìm hướng đi phù hợp với bạn</h2>
            <p>Khám phá lĩnh vực, dự án đang tuyển và những hoạt động tạo nên cộng đồng Smart Lab.</p>
          </div>
          <div className="landing-hero-cta">
            <Link className="btn primary lg" to="/du-an?status=RECRUITING">Xem dự án đang tuyển <ArrowRight size={17} /></Link>
            <Link className="btn outline-light lg" to="/linh-vuc">Khám phá lĩnh vực</Link>
          </div>
        </div>
      </section>



    </div>
  )
}

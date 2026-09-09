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
import { Link, useLocation } from 'react-router-dom'
import type { ResearchField } from '../../../shared/types/api'
import '../../../assets/lab.css'
import '../landing.css'
import { SmartLabHero } from '../components/SmartLabHero'
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
import { RESEARCH_FIELDS_HASH, RESEARCH_FIELDS_PATH, scrollToResearchFields } from '../researchFieldNavigation'

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
  const location = useLocation()
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

  useEffect(() => {
    if (location.hash === RESEARCH_FIELDS_HASH && !fieldsLoading) {
      scrollToResearchFields()
    }
  }, [fieldsLoading, location.hash])

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
      <SmartLabHero onExploreFields={scrollToResearchFields} />

      {/* ═══════════════════════════════════════════════════════════
          2. RESEARCH AREAS — Architectural Research Ledger
          ═══════════════════════════════════════════════════════════ */}
      <section id="research-fields" className="landing-section" aria-labelledby="research-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
              <div className="landing-head-eyebrow">Định hướng nghiên cứu</div>
              <h2 id="research-heading">Lĩnh vực nghiên cứu</h2>
              <p>
                Các hướng nghiên cứu định hình dự án và hoạt động chuyên môn tại Smart Lab.
              </p>
            </div>
          </div>

          <LandingState
            loading={fieldsLoading}
            error={fieldsError}
            empty={fields.length === 0}
            emptyText="Chưa có lĩnh vực nghiên cứu công khai."
          />

          {fields.length > 0 && (
            <div className="landing-research-ledger">
              {fields.map((field, i) => (
                <article
                  className="landing-research-row"
                  key={field.id}
                >
                  <div className="landing-research-index-col">
                    <span className="landing-research-num">{String(i + 1).padStart(2, '0')}</span>
                    <span className="landing-research-code">{field.code}</span>
                  </div>

                  <div className="landing-research-visual-col">
                    {field.coverFileId && !failedImages[field.id] ? (
                      <img
                        src={publicFileUrl(field.coverFileId)}
                        alt={`Ảnh đại diện lĩnh vực ${field.name}`}
                        className="landing-research-thumb"
                        onError={() => setFailedImages(prev => ({ ...prev, [field.id]: true }))}
                      />
                    ) : (
                      <div className="landing-research-fallback" aria-hidden="true">
                        <span className="landing-research-geom">
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

                  <div className="landing-research-main-col">
                    <h3 className="landing-research-title">{field.name}</h3>
                    <p className="landing-research-desc">{field.description ?? 'Thông tin chi tiết đang được cập nhật.'}</p>
                  </div>

                  <div className="landing-research-action-col">
                    <Link className="landing-research-link" to={`/linh-vuc/${encodeURIComponent(field.code)}`}>
                      <span>Khám phá</span>
                      <ArrowRight size={14} />
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          3. RECRUITING PROJECTS — Operational Recruitment Stream
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
            <div className="landing-recruit-stream">
              <div className="landing-recruit-stream-header" aria-hidden="true">
                <span className="col-proj">Dự án nghiên cứu</span>
                <span className="col-meta">Phân loại &amp; Lĩnh vực</span>
                <span className="col-leader">Chủ nhiệm</span>
                <span className="col-action">Thao tác</span>
              </div>
              {recruitItems.map((project) => {
                const shownLeaders = project.leaders.slice(0, 3)
                const rest = project.leaders.length - shownLeaders.length
                const desc = project.description ?? project.goal ?? 'Xem chi tiết để biết thêm thông tin về dự án này.'
                return (
                  <Link
                    className="landing-recruit-row"
                    to={`/du-an/${project.id}`}
                    key={project.id}
                    aria-label={`Dự án ${project.name}`}
                  >
                    <div className="landing-recruit-col-proj">
                      <div className="landing-recruit-row-tags">
                        <span className="landing-recruit-badge">
                          <span className="landing-recruit-badge-dot" aria-hidden="true" />
                          Đang tuyển
                        </span>
                        <span className="landing-recruit-code">{project.code}</span>
                      </div>
                      <h3 className="landing-recruit-row-title">{project.name}</h3>
                      <p className="landing-recruit-row-desc">{shorten(desc, 140)}</p>
                    </div>

                    <div className="landing-recruit-col-meta">
                      <span className="chip accent">{PROJECT_TYPE_LABELS[project.projectType]}</span>
                      {project.researchFields && project.researchFields.length > 0 && (
                        <span className="chip muted">{project.researchFields[0].name}</span>
                      )}
                    </div>

                    <div className="landing-recruit-col-leader">
                      {shownLeaders.length > 0 ? (
                        <div className="landing-recruit-leaders-wrap">
                          <span className="ava-stack" aria-label={`${project.leaders.length} leader`}>
                            {shownLeaders.map((l, index) => (
                              <span className="ava xs" title={l.name} key={`${l.name}-${index}`}>{initialsOf(l.name)}</span>
                            ))}
                            {rest > 0 ? <span className="ava xs">+{rest}</span> : null}
                          </span>
                          <span className="landing-recruit-leader-name">{shownLeaders[0].name}</span>
                        </div>
                      ) : (
                        <span className="muted small"><UsersRound size={13} /> Chưa công bố</span>
                      )}
                    </div>

                    <div className="landing-recruit-col-action">
                      <span className="landing-recruit-action-btn">
                        Ứng tuyển <ArrowRight size={13} />
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
          4. LAB LIFE / ANNUAL ACTIVITIES — Event Horizon Timeline Rail
          ═══════════════════════════════════════════════════════════ */}
      <section className="landing-section landing-section-dark" aria-labelledby="lablife-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
              <div className="landing-head-eyebrow">Văn hoá &amp; Đời sống</div>
              <h2 id="lablife-heading">Kết nối cộng đồng</h2>
              <p>Sự cân bằng giữa nghiên cứu chuyên sâu và môi trường gắn kết, cởi mở.</p>
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
            <div className="landing-events-rail">
              {events.map((event) => {
                const start = new Date(event.startAt)
                const day = Number.isNaN(start.getTime()) ? '—' : String(start.getDate())
                const month = Number.isNaN(start.getTime())
                  ? ''
                  : new Intl.DateTimeFormat('vi-VN', { month: 'short' }).format(start)
                const year = Number.isNaN(start.getTime()) ? '' : String(start.getFullYear())
                return (
                  <div className="landing-event-node" key={event.id}>
                    <div className="landing-event-date-stamp">
                      <span className="landing-event-day">{day}</span>
                      <span className="landing-event-month">{month}</span>
                      <span className="landing-event-year">{year}</span>
                    </div>
                    <div className="landing-event-node-body">
                      <div className="landing-event-node-tags">
                        <span className="landing-event-mode-tag">
                          {event.mode === 'ONLINE'
                            ? <><Wifi size={12} /> Trực tuyến</>
                            : <><WifiOff size={12} /> Trực tiếp</>
                          }
                        </span>
                        {event.location ? (
                          <span className="landing-event-loc-tag"><MapPin size={12} /> {event.location}</span>
                        ) : null}
                      </div>
                      <h3 className="landing-event-node-title">{event.title}</h3>
                      {event.content ? (
                        <p className="landing-event-node-desc">{shorten(event.content, 140)}</p>
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
          5. ACHIEVEMENTS — Architectural Milestone Registry
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

          <div className="landing-achievement-ribbon">
            {years.length > 0 && (
              <div className="landing-achievement-pills" role="tablist" aria-label="Chọn năm thành tựu">
                {years.map(({ year, count }) => (
                  <button
                    type="button"
                    key={year}
                    className={`landing-achievement-pill ${selectedYear === year ? 'is-active' : ''}`}
                    onClick={() => handleYearChange(year)}
                    role="tab"
                    aria-selected={selectedYear === year}
                  >
                    <span className="landing-pill-year">{year}</span>
                    <span className="landing-pill-count">{count}</span>
                  </button>
                ))}
              </div>
            )}

            <div className="landing-achievement-ribbon-right">
              <span className="landing-achievement-toolbar-text">
                {achievementTotal > 0 ? `${achievementCurrentPage * 3 + 1}–${Math.min((achievementCurrentPage + 1) * 3, achievementTotal)} / ${achievementTotal}` : '0 / 0'}
              </span>
              <div className="landing-achievement-toolbar-actions">
                <button
                  type="button"
                  className="landing-achievement-toolbar-btn"
                  onClick={handleAchievementPrev}
                  disabled={achievementCurrentPage === 0 || achievementLoading}
                  aria-label="Trang trước"
                >
                  <ChevronLeft size={16} />
                </button>
                <button
                  type="button"
                  className="landing-achievement-toolbar-btn"
                  onClick={handleAchievementNext}
                  disabled={(achievementCurrentPage + 1) * 3 >= achievementTotal || achievementLoading}
                  aria-label="Trang sau"
                >
                  <ChevronRight size={16} />
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
            <div className="landing-achievement-ledger" aria-label="Danh sách thành tựu">
              {achievementItems.map((item: Achievement) => {
                const typeLabel = ACHIEVEMENT_TYPE_LABELS[item.achievementType]
                const dateStr = item.achievementDate ? formatDate(item.achievementDate) : item.achievementYear
                return (
                  <div className="landing-achievement-row" key={item.id}>
                    <div className="landing-achievement-type-col">
                      <span className="landing-achievement-type-badge" data-type={item.achievementType}>
                        {typeLabel}
                      </span>
                      {dateStr ? (
                        <span className="landing-achievement-date-text">{dateStr}</span>
                      ) : null}
                    </div>

                    <div className="landing-achievement-body-col">
                      <h3 className="landing-achievement-title">{item.title}</h3>
                      {item.summary ? (
                        <p className="landing-achievement-summary">{item.summary}</p>
                      ) : null}
                      {item.relatedProject ? (
                        <span className="landing-achievement-project-tag">
                          Dự án: {item.relatedProject.name}
                        </span>
                      ) : null}
                    </div>

                    <div className="landing-achievement-action-col">
                      {item.evidenceUrl ? (
                        <a
                          className="landing-achievement-evidence-btn"
                          href={item.evidenceUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          aria-label="Xem minh chứng"
                        >
                          Minh chứng ↗
                        </a>
                      ) : (
                        <span className="landing-achievement-verified-tag">Đã ghi nhận</span>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          6. UNIFIED ARTICLES & MEDIA — Magazine Asymmetric Feature + Stack
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
                Xem tất cả bài viết <ArrowRight size={16} />
              </Link>
            ) : (
              <Link className="landing-media-archive-link" to="/tin-tuc">
                Xem tất cả tin tức <ArrowRight size={16} />
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
            <div className="landing-editorial-layout" id="landing-article-panel" role="tabpanel" aria-labelledby={MEDIA_TAB_IDS.ARTICLE}>
              {articleItems[0] && (
                <Link className="landing-editorial-lead" to={`/bai-viet/${encodeURIComponent(articleItems[0].slug)}`}>
                  <div className="landing-editorial-lead-header">
                    <span className="landing-editorial-badge">Bài viết tiêu điểm</span>
                    {articleItems[0].publishedAt ? (
                      <span className="landing-editorial-date">
                        {formatDate(articleItems[0].publishedAt, { dateStyle: 'medium' })}
                      </span>
                    ) : null}
                  </div>
                  <h3 className="landing-editorial-lead-title">{articleItems[0].title}</h3>
                  {articleItems[0].excerpt ? (
                    <p className="landing-editorial-lead-desc">{shorten(articleItems[0].excerpt, 220)}</p>
                  ) : null}
                  <div className="landing-editorial-lead-foot">
                    <span className="landing-editorial-action-btn">Đọc toàn bộ bài viết <ArrowRight size={14} /></span>
                  </div>
                </Link>
              )}

              {articleItems.length > 1 && (
                <div className="landing-editorial-stack">
                  {articleItems.slice(1).map((article) => (
                    <Link className="landing-editorial-stack-item" to={`/bai-viet/${encodeURIComponent(article.slug)}`} key={article.id}>
                      {article.publishedAt ? (
                        <span className="landing-editorial-date">
                          {formatDate(article.publishedAt, { dateStyle: 'short' })}
                        </span>
                      ) : null}
                      <h4 className="landing-editorial-stack-title">{article.title}</h4>
                      {article.excerpt ? (
                        <p className="landing-editorial-stack-desc">{shorten(article.excerpt, 110)}</p>
                      ) : null}
                      <span className="landing-editorial-stack-link">Chi tiết <ArrowRight size={12} /></span>
                    </Link>
                  ))}
                </div>
              )}
            </div>
          )}

          {mediaTab === 'NEWS' && newsItems.length > 0 && (
            <div className="landing-editorial-layout" id="landing-news-panel" role="tabpanel" aria-labelledby={MEDIA_TAB_IDS.NEWS}>
              {newsItems[0] && (
                <div className="landing-editorial-lead is-news">
                  <div className="landing-editorial-lead-header">
                    <span className="landing-editorial-badge news-badge">{newsItems[0].sourceName}</span>
                    {newsItems[0].publishedAt ? (
                      <span className="landing-editorial-date">
                        {formatDate(newsItems[0].publishedAt, { dateStyle: 'medium' })}
                      </span>
                    ) : null}
                  </div>
                  <h3 className="landing-editorial-lead-title">{newsItems[0].title}</h3>
                  {newsItems[0].excerpt ? (
                    <p className="landing-editorial-lead-desc">{shorten(newsItems[0].excerpt, 220)}</p>
                  ) : null}
                  <div className="landing-editorial-lead-foot">
                    <a
                      className="landing-editorial-action-btn"
                      href={newsItems[0].sourceUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      aria-label={`Xem chi tiết bài viết trên ${newsItems[0].sourceName}`}
                    >
                      Xem nguồn bài viết ↗
                    </a>
                  </div>
                </div>
              )}

              {newsItems.length > 1 && (
                <div className="landing-editorial-stack">
                  {newsItems.slice(1).map((news) => (
                    <div className="landing-editorial-stack-item" key={news.id}>
                      <div className="landing-editorial-stack-meta">
                        <span className="landing-editorial-source-name">{news.sourceName}</span>
                        {news.publishedAt ? (
                          <span className="landing-editorial-date">
                            • {formatDate(news.publishedAt, { dateStyle: 'short' })}
                          </span>
                        ) : null}
                      </div>
                      <h4 className="landing-editorial-stack-title">{news.title}</h4>
                      {news.excerpt ? (
                        <p className="landing-editorial-stack-desc">{shorten(news.excerpt, 110)}</p>
                      ) : null}
                      <a
                        className="landing-editorial-stack-link"
                        href={news.sourceUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label={`Xem bài viết trên ${news.sourceName}`}
                      >
                        Nguồn tin ↗
                      </a>
                    </div>
                  ))}
                </div>
              )}
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
            <Link className="btn outline-light lg" to={RESEARCH_FIELDS_PATH} onClick={scrollToResearchFields}>Khám phá lĩnh vực</Link>
          </div>
        </div>
      </section>



    </div>
  )
}

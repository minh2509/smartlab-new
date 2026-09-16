import {
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  Inbox,
  Sparkles,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import type { ResearchField } from '../../../shared/types/api'
import '../../../assets/lab.css'
import '../landing.css'
import { SmartLabHero } from '../components/SmartLabHero'
import { getResearchFields } from '../../profile/api'
import { listPublicRecruitingProjects, type RecruitingPage } from '../../projects/api'
import { PROJECT_TYPE_LABELS } from '../../projects/types'
import { listAchievements, listAchievementYears } from '../achievementApi'
import type { Achievement, YearCount } from '../achievementTypes'
import { ACHIEVEMENT_TYPE_LABELS } from '../achievementTypes'
import { listPublicGallery, publicGalleryFileUrl } from '../galleryApi'
import type { PublicGalleryItem } from '../galleryTypes'
import { RESEARCH_FIELDS_HASH, RESEARCH_FIELDS_PATH, scrollToResearchFields } from '../researchFieldNavigation'
import { fieldVisual, publicFileUrl } from '../researchFieldVisual'
import { getResearchFieldDomainContent } from '../researchFieldContent'

function formatDate(value: string | null | undefined, opts?: Intl.DateTimeFormatOptions) {
  if (!value) return ''
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : new Intl.DateTimeFormat('vi-VN', opts ?? { dateStyle: 'short' }).format(d)
}

function getFieldPresentation(field: ResearchField) {
  const domain = getResearchFieldDomainContent(field.code)
  if (field.code.toUpperCase() === 'AI') {
    return {
      displayName: 'Trí tuệ nhân tạo (AI)',
      tagline: domain?.tagline ?? 'Kỹ nghệ hệ thống thông minh, mô hình hóa dữ liệu và học máy tự thích ứng',
    }
  }
  if (field.code.toUpperCase() === 'ROBOTICS') {
    return {
      displayName: 'Hệ thống thông minh & Robotics',
      tagline: domain?.tagline ?? 'Điều khiển tự động, định vị thời gian thực và tương tác robot vật lý',
    }
  }
  if (field.code.toUpperCase() === 'SE' || field.code.toUpperCase().includes('SOFTWARE')) {
    return {
      displayName: 'Kỹ thuật phần mềm & Nền tảng số',
      tagline: domain?.tagline ?? 'Kiến trúc hệ thống tin cậy, quy trình kiểm thử và hạ tầng phân tán',
    }
  }
  return {
    displayName: domain ? `${domain.vietnameseName} (${field.code})` : field.name,
    tagline: domain?.tagline ?? field.description ?? 'Định hướng nghiên cứu trọng tâm tại Smart Lab.',
  }
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

  // 1. Research fields
  const [fields, setFields] = useState<ResearchField[]>([])
  const [fieldsLoading, setFieldsLoading] = useState(true)
  const [fieldsError, setFieldsError] = useState<string | undefined>()

  // 2. Achievements
  const [years, setYears] = useState<YearCount[]>([])
  const [selectedYear, setSelectedYear] = useState<number | null>(null)
  const [achievementItems, setAchievementItems] = useState<Achievement[]>([])
  const [achievementTotal, setAchievementTotal] = useState(0)
  const [achievementCurrentPage, setAchievementCurrentPage] = useState(0)
  const [achievementLoading, setAchievementLoading] = useState(true)
  const [achievementError, setAchievementError] = useState<string | undefined>()

  // 3. Recruiting projects (compact overview)
  const [recruitPage, setRecruitPage] = useState<RecruitingPage | null>(null)
  const [recruitCurrentPage, setRecruitCurrentPage] = useState(0)
  const [recruitLoading, setRecruitLoading] = useState(true)
  const [recruitError, setRecruitError] = useState<string | undefined>()

  // 4. Lab Life & Culture (cinematic atmosphere strip)
  const [galleryItems, setGalleryItems] = useState<PublicGalleryItem[]>([])
  const [galleryLoading, setGalleryLoading] = useState(true)
  const [galleryError, setGalleryError] = useState<string | undefined>()
  const [activeCultureIdx, setActiveCultureIdx] = useState(0)
  const [isCulturePaused, setIsCulturePaused] = useState(false)

  const [failedImages, setFailedImages] = useState<Record<string | number, boolean>>({})

  // ── Initial data load
  useEffect(() => {
    let active = true

    // Fields
    getResearchFields()
      .then((r) => { if (active) { setFields(r); setFieldsLoading(false) } })
      .catch(() => {
        if (active) {
          setFieldsError('Không thể tải dữ liệu định hướng lúc này. Vui lòng thử lại sau.')
          setFieldsLoading(false)
        }
      })

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
      .catch(() => {
        if (active) {
          setAchievementError('Không thể tải thành tựu lúc này. Vui lòng thử lại sau.')
          setAchievementLoading(false)
        }
      })

    // Lab Life / Gallery preview
    listPublicGallery({ size: 8 })
      .then((r) => { if (active) { setGalleryItems(r.items); setGalleryLoading(false) } })
      .catch(() => {
        if (active) {
          setGalleryError('Không thể tải hình ảnh hoạt động lúc này.')
          setGalleryLoading(false)
        }
      })

    return () => { active = false }
  }, [])

  useEffect(() => {
    if (location.hash === RESEARCH_FIELDS_HASH && !fieldsLoading) {
      scrollToResearchFields()
    }
  }, [fieldsLoading, location.hash])

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

  // ── Recruiting preview pagination (bounded to 3 compact cards per page)
  useEffect(() => {
    let active = true
    setRecruitLoading(true)
    setRecruitError(undefined)
    listPublicRecruitingProjects(recruitCurrentPage, 3)
      .then((r) => {
        if (active) {
          setRecruitPage(r)
          setRecruitLoading(false)
        }
      })
      .catch(() => {
        if (active) {
          setRecruitError('Không thể tải dự án tuyển dụng lúc này.')
          setRecruitLoading(false)
        }
      })
    return () => { active = false }
  }, [recruitCurrentPage])

  function handleRecruitPrev() {
    setRecruitCurrentPage((prev) => Math.max(0, prev - 1))
  }

  function handleRecruitNext() {
    setRecruitCurrentPage((prev) => prev + 1)
  }

  // ── Subtle auto-progression for culture carousel (respects prefers-reduced-motion)
  useEffect(() => {
    if (galleryItems.length <= 1 || isCulturePaused) return
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    const timer = setInterval(() => {
      setActiveCultureIdx((prev) => (prev + 1) % galleryItems.length)
    }, 5500)

    return () => clearInterval(timer)
  }, [galleryItems.length, isCulturePaused])

  function handlePrevCulture(e: React.MouseEvent) {
    e.preventDefault()
    e.stopPropagation()
    setActiveCultureIdx((prev) => (prev - 1 + galleryItems.length) % galleryItems.length)
  }

  function handleNextCulture(e: React.MouseEvent) {
    e.preventDefault()
    e.stopPropagation()
    setActiveCultureIdx((prev) => (prev + 1) % galleryItems.length)
  }

  function handleCultureKeyDown(e: React.KeyboardEvent) {
    if (galleryItems.length <= 1) return
    if (e.key === 'ArrowLeft') {
      e.preventDefault()
      setActiveCultureIdx((prev) => (prev - 1 + galleryItems.length) % galleryItems.length)
    } else if (e.key === 'ArrowRight') {
      e.preventDefault()
      setActiveCultureIdx((prev) => (prev + 1) % galleryItems.length)
    }
  }

  const recruitTotal = recruitPage?.totalElements ?? 0
  const recruitItems = recruitPage?.items ?? []

  return (
    <div className="landing-page">

      {/* ═══════════════════════════════════════════════════════════
          1. HERO
          ═══════════════════════════════════════════════════════════ */}
      <SmartLabHero onExploreFields={scrollToResearchFields} />

      {/* ═══════════════════════════════════════════════════════════
          2. RESEARCH FIELDS: Prominent Research Directions
          ═══════════════════════════════════════════════════════════ */}
      <section id="research-fields" className="landing-section" aria-labelledby="research-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
              <h2 id="research-heading">Định hướng nghiên cứu</h2>
              <p>
                Ba trọng tâm khoa học và công nghệ định hình các dự án và sản phẩm thực tế tại Smart Lab.
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
            <div className="landing-research-grid">
              {fields.map((field, i) => {
                const visual = fieldVisual(field)
                const presentation = getFieldPresentation(field)
                const imageSrc = field.coverFileId && !failedImages[field.id]
                  ? publicFileUrl(field.coverFileId)
                  : visual.image

                return (
                  <article className="landing-research-card" key={field.id}>
                    <div className="landing-research-card-cover">
                      <img
                        src={imageSrc}
                        alt={`Ảnh minh họa ${presentation.displayName}`}
                        className="landing-research-card-img"
                        onError={() => setFailedImages((prev) => ({ ...prev, [field.id]: true }))}
                      />
                      <div className="landing-research-card-num" aria-hidden="true">
                        {String(i + 1).padStart(2, '0')}
                      </div>
                    </div>

                    <div className="landing-research-card-body">
                      <div className="landing-research-card-header">
                        <span className="landing-research-card-code">{field.code}</span>
                      </div>

                      <h3 className="landing-research-card-title">{presentation.displayName}</h3>
                      <p className="landing-research-card-tagline">{presentation.tagline}</p>

                      <div className="landing-research-card-action">
                        <Link
                          className="landing-research-card-btn"
                          to={`/linh-vuc/${encodeURIComponent(field.code)}`}
                        >
                          <span>Tìm hiểu định hướng</span>
                          <ArrowRight size={14} className="landing-btn-icon" />
                        </Link>
                      </div>
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          3. ACHIEVEMENTS: Architectural Milestone Registry (Pushed Higher)
          ═══════════════════════════════════════════════════════════ */}
      <section id="thanh-tuu" className="landing-section landing-section-alt" aria-labelledby="achievement-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
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
          4. RECRUITING PROJECTS: Compact Overview Cards
          ═══════════════════════════════════════════════════════════ */}
      <section id="du-an-tuyen-dung" className="landing-section" aria-labelledby="recruit-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
              <h2 id="recruit-heading">Dự án đang mở tuyển</h2>
              <p>Các dự án nghiên cứu và phát triển sản phẩm mở cơ hội cho thành viên mới tham gia.</p>
            </div>
            <Link className="landing-section-cta" to="/du-an?status=RECRUITING">
              Xem tất cả dự án đang tuyển <ArrowRight size={16} />
            </Link>
          </div>

          {recruitTotal > 3 && (
            <div className="landing-recruit-ribbon">
              <div className="landing-achievement-ribbon-right">
                <span className="landing-achievement-toolbar-text">
                  {`${recruitCurrentPage * 3 + 1}\u2013${Math.min((recruitCurrentPage + 1) * 3, recruitTotal)} / ${recruitTotal}`}
                </span>
                <div className="landing-achievement-toolbar-actions">
                  <button
                    type="button"
                    className="landing-achievement-toolbar-btn"
                    onClick={handleRecruitPrev}
                    disabled={recruitCurrentPage === 0 || recruitLoading}
                    aria-label="Dự án trang trước"
                  >
                    <ChevronLeft size={16} />
                  </button>
                  <button
                    type="button"
                    className="landing-achievement-toolbar-btn"
                    onClick={handleRecruitNext}
                    disabled={(recruitCurrentPage + 1) * 3 >= recruitTotal || recruitLoading}
                    aria-label="Dự án trang sau"
                  >
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            </div>
          )}

          <LandingState
            loading={recruitLoading}
            error={recruitError}
            empty={recruitItems.length === 0}
            emptyText="Hiện chưa có dự án nào đang mở tuyển thành viên."
          />

          {recruitItems.length > 0 && (
            <div className="landing-project-compact-grid">
              {recruitItems.slice(0, 3).map((project) => (
                <Link
                  className="landing-proj-card"
                  to={`/du-an/${project.id}`}
                  key={project.id}
                  aria-label={`Dự án ${project.name}`}
                >
                  <div className="landing-proj-card-top">
                    <span className="landing-proj-status-badge">
                      <span className="landing-proj-status-dot" aria-hidden="true" />
                      Đang tuyển
                    </span>
                    <span className="landing-proj-code">{project.code}</span>
                  </div>

                  <h3 className="landing-proj-title">{project.name}</h3>

                  <div className="landing-proj-tags">
                    <span className="landing-proj-tag category">
                      {PROJECT_TYPE_LABELS[project.projectType]}
                    </span>
                    {project.researchFields && project.researchFields.length > 0 && (
                      <span className="landing-proj-tag field">
                        {project.researchFields[0].name}
                      </span>
                    )}
                  </div>

                  <div className="landing-proj-footer">
                    <span className="landing-proj-link">
                      <span>Xem chi tiết</span>
                      <ArrowRight size={14} className="landing-btn-icon" />
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          5. LAB LIFE & CULTURE: Cinematic Atmosphere Strip
          ═══════════════════════════════════════════════════════════ */}
      <section id="van-hoa-doi-song" className="landing-section landing-section-alt" aria-labelledby="culture-heading">
        <div className="landing-wrap">
          <div className="landing-head-row">
            <div className="landing-head">
              <h2 id="culture-heading">Văn hoá &amp; Đời sống Lab</h2>
              <p>Không gian học thuật cởi mở, kết nối qua các hoạt động ngoại khóa, hội thảo và nghiên cứu nhóm.</p>
            </div>
            <Link className="landing-section-cta" to="/thu-vien-anh">
              Xem toàn bộ thư viện ảnh <ArrowRight size={16} />
            </Link>
          </div>

          <LandingState
            loading={galleryLoading}
            error={galleryError}
            empty={galleryItems.length === 0}
            emptyText="Hình ảnh hoạt động đang được cập nhật."
          />

          {galleryItems.length > 0 && (
            <div
              className="landing-culture-cinematic"
              role="region"
              aria-label="Hình ảnh hoạt động văn hóa phòng Lab"
              tabIndex={0}
              onMouseEnter={() => setIsCulturePaused(true)}
              onMouseLeave={() => setIsCulturePaused(false)}
              onFocus={() => setIsCulturePaused(true)}
              onBlur={() => setIsCulturePaused(false)}
              onKeyDown={handleCultureKeyDown}
            >
              <div className="landing-culture-slides">
                {galleryItems.map((item, idx) => {
                  const isActive = idx === activeCultureIdx
                  const imageUrl = publicGalleryFileUrl(item.fileId)
                  return (
                    <div
                      key={item.id}
                      className={`landing-culture-slide ${isActive ? 'is-active' : ''}`}
                      aria-hidden={!isActive}
                    >
                      <Link
                        className="landing-culture-slide-link"
                        to="/thu-vien-anh"
                        title={`Xem hình ảnh: ${item.title}`}
                        tabIndex={isActive ? 0 : -1}
                      >
                        <img
                          src={imageUrl}
                          alt={item.title}
                          className="landing-culture-slide-img"
                          loading={idx === 0 ? 'eager' : 'lazy'}
                          onError={() => setFailedImages((prev) => ({ ...prev, [`gal-${item.id}`]: true }))}
                        />
                        <div className="landing-culture-cinematic-overlay">
                          <div className="landing-culture-cinematic-caption-box">
                            <span className="landing-culture-cinematic-badge">
                              <Sparkles size={12} />
                              Không gian Smart Lab
                            </span>
                            <h3 className="landing-culture-cinematic-title">{item.title}</h3>
                            {item.caption ? (
                              <p className="landing-culture-cinematic-sub">{item.caption}</p>
                            ) : null}
                          </div>
                        </div>
                      </Link>
                    </div>
                  )
                })}
              </div>

              {galleryItems.length > 1 && (
                <>
                  <button
                    type="button"
                    className="landing-culture-nav-btn prev"
                    onClick={handlePrevCulture}
                    aria-label="Hình ảnh trước"
                  >
                    <ChevronLeft size={20} />
                  </button>
                  <button
                    type="button"
                    className="landing-culture-nav-btn next"
                    onClick={handleNextCulture}
                    aria-label="Hình ảnh kế tiếp"
                  >
                    <ChevronRight size={20} />
                  </button>

                  <div className="landing-culture-dots" role="tablist" aria-label="Chọn hình ảnh hoạt động">
                    {galleryItems.map((item, idx) => (
                      <button
                        key={item.id}
                        type="button"
                        className={`landing-culture-dot ${idx === activeCultureIdx ? 'is-active' : ''}`}
                        onClick={() => setActiveCultureIdx(idx)}
                        role="tab"
                        aria-selected={idx === activeCultureIdx}
                        aria-label={`Hình ảnh ${idx + 1}: ${item.title}`}
                      />
                    ))}
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          6. FINAL CTA: Coherent Next Step
          ═══════════════════════════════════════════════════════════ */}
      <section className="landing-final-cta" aria-label="Bắt đầu cùng Smart Lab">
        <div className="landing-wrap landing-final-cta-inner">
          <div>
            <h2>Bắt đầu cùng Smart Lab</h2>
            <p>Khám phá các định hướng nghiên cứu hoặc tìm kiếm dự án phù hợp với mục tiêu của bạn.</p>
          </div>
          <div className="landing-hero-cta">
            <Link className="btn primary lg landing-hero-btn-primary" to="/du-an?status=RECRUITING">
              <span>Xem dự án đang tuyển</span>
              <ArrowRight size={17} className="landing-hero-btn-icon" />
            </Link>
            <Link
              className="btn outline-light lg landing-hero-btn-secondary"
              to={RESEARCH_FIELDS_PATH}
              onClick={scrollToResearchFields}
            >
              Khám phá định hướng
            </Link>
          </div>
        </div>
      </section>

    </div>
  )
}

import {
  ArrowRight,
  Inbox,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
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
import { getFieldVisual, publicFileUrl } from '../researchFieldVisual'
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
  const [achievementLoading, setAchievementLoading] = useState(true)
  const [achievementError, setAchievementError] = useState<string | undefined>()

  // 3. Recruiting projects (spotlight preview)
  const [recruitPage, setRecruitPage] = useState<RecruitingPage | null>(null)
  const [recruitLoading, setRecruitLoading] = useState(true)
  const [recruitError, setRecruitError] = useState<string | undefined>()

  // 4. Lab Life & Culture (living photo stream reel)
  const [galleryItems, setGalleryItems] = useState<PublicGalleryItem[]>([])
  const [galleryLoading, setGalleryLoading] = useState(true)
  const [galleryError, setGalleryError] = useState<string | undefined>()

  const marqueeItems = useMemo(() => {
    if (galleryItems.length === 0) return []
    const minCount = 6
    let items = [...galleryItems]
    while (items.length < minCount) {
      items = items.concat(galleryItems)
    }
    return items
  }, [galleryItems])

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

    // Recruiting projects preview (curated teaser, max 2 items)
    listPublicRecruitingProjects(0, 2)
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
  }, [])

  useEffect(() => {
    if (location.hash === RESEARCH_FIELDS_HASH && !fieldsLoading) {
      scrollToResearchFields()
    }
  }, [fieldsLoading, location.hash])

  // ── Achievement year change (load top 4 milestones for selected year)
  useEffect(() => {
    if (selectedYear === null) return
    let active = true

    setAchievementLoading(true)
    setAchievementError(undefined)
    listAchievements(selectedYear, 0, 4)
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
  }, [selectedYear])

  function handleYearChange(year: number) {
    if (year === selectedYear) return
    setSelectedYear(year)
    setAchievementTotal(0)
    setAchievementItems([])
  }



  const recruitItems = recruitPage?.items ?? []

  return (
    <div className="landing-page">

      {/* ═══════════════════════════════════════════════════════════
          1. HERO (Preserved Retro-Modern Workstation Anchor)
          ═══════════════════════════════════════════════════════════ */}
      <SmartLabHero onExploreFields={scrollToResearchFields} />

      {/* ═══════════════════════════════════════════════════════════
          2. ACHIEVEMENTS: Proof of Work & Research Milestones
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
                      <h3 className="landing-achievement-title">
                        <Link to={`/thanh-tuu/${item.id}`} className="landing-achievement-title-link">
                          {item.title}
                        </Link>
                      </h3>
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
                          aria-label={`Xem minh chứng cho ${item.title}`}
                        >
                          Minh chứng ↗
                        </a>
                      ) : null}
                      <Link
                        to={`/thanh-tuu/${item.id}`}
                        className="landing-achievement-detail-btn"
                        aria-label={`Xem chi tiết thành tựu: ${item.title}`}
                      >
                        <span>Chi tiết</span>
                        <ArrowRight size={13} />
                      </Link>
                    </div>
                  </div>
                )
              })}
            </div>
          )}

          {achievementTotal > 4 && selectedYear && (
            <div className="landing-achievement-more">
              <Link to="/thanh-tuu" className="landing-achievement-more-link">
                <span>Xem thêm các kết quả khác của năm {selectedYear} tại Kho lưu trữ</span>
                <ArrowRight size={14} />
              </Link>
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          3. LAB LIFE & CULTURE: Infinite Living Image Reel
          ═══════════════════════════════════════════════════════════ */}
      <section id="van-hoa-doi-song" className="landing-section" aria-labelledby="culture-heading">
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

          {marqueeItems.length > 0 && (
            <div
              className="landing-culture-reel"
              role="region"
              aria-label="Hình ảnh hoạt động văn hóa phòng Lab"
              tabIndex={0}
            >
              <div className="landing-culture-track">
                <div className="landing-culture-track-group">
                  {marqueeItems.map((item, idx) => {
                    const imageUrl = publicGalleryFileUrl(item.fileId)
                    return (
                      <Link
                        key={`orig-${item.id}-${idx}`}
                        to="/thu-vien-anh"
                        className="landing-culture-card"
                        title={`Xem hình ảnh: ${item.title}`}
                        aria-label={`Xem hình ảnh: ${item.title}`}
                      >
                        <img
                          src={imageUrl}
                          alt={item.title}
                          className="landing-culture-card-img"
                          loading={idx < 4 ? 'eager' : 'lazy'}
                          onError={() => setFailedImages((prev) => ({ ...prev, [`gal-${item.id}`]: true }))}
                        />
                        <div className="landing-culture-card-overlay">
                          <span className="landing-culture-card-title">{item.title}</span>
                        </div>
                      </Link>
                    )
                  })}
                </div>

                <div className="landing-culture-track-group" aria-hidden="true" data-duplicate="true">
                  {marqueeItems.map((item, idx) => {
                    const imageUrl = publicGalleryFileUrl(item.fileId)
                    return (
                      <Link
                        key={`dup-${item.id}-${idx}`}
                        to="/thu-vien-anh"
                        className="landing-culture-card"
                        tabIndex={-1}
                        title={`Xem hình ảnh: ${item.title}`}
                        aria-hidden="true"
                      >
                        <img
                          src={imageUrl}
                          alt={item.title}
                          className="landing-culture-card-img"
                          loading="lazy"
                          onError={() => setFailedImages((prev) => ({ ...prev, [`gal-dup-${item.id}-${idx}`]: true }))}
                        />
                        <div className="landing-culture-card-overlay">
                          <span className="landing-culture-card-title">{item.title}</span>
                        </div>
                      </Link>
                    )
                  })}
                </div>
              </div>
            </div>
          )}
        </div>
      </section>

      {/* ═══════════════════════════════════════════════════════════
          4. RESEARCH FIELDS: Extensible Domain Matrix
          ═══════════════════════════════════════════════════════════ */}
      <section id="research-fields" className="landing-section landing-section-alt" aria-labelledby="research-heading">
        <div className="landing-wrap">
          <div className="landing-head">
            <h2 id="research-heading">Định hướng nghiên cứu</h2>
            <p>
              Các trọng tâm khoa học và công nghệ định hình các dự án và sản phẩm thực tế tại Smart Lab.
            </p>
          </div>

          <LandingState
            loading={fieldsLoading}
            error={fieldsError}
            empty={fields.length === 0}
            emptyText="Chưa có lĩnh vực nghiên cứu công khai."
          />

          {fields.length > 0 && (
            <div className={`landing-research-matrix count-${fields.length}`}>
              {fields.map((field) => {
                const visual = getFieldVisual(field)
                const presentation = getFieldPresentation(field)
                const imageSrc = field.coverFileId && !failedImages[field.id]
                  ? publicFileUrl(field.coverFileId)
                  : visual?.image ?? null

                return (
                  <article className="landing-research-card" key={field.id}>
                    <Link
                      to={`/linh-vuc/${encodeURIComponent(field.code)}`}
                      className="landing-research-card-cover"
                      tabIndex={-1}
                      aria-hidden="true"
                    >
                      {imageSrc && !failedImages[field.id] ? (
                        <img
                          src={imageSrc}
                          alt={`Ảnh minh họa ${presentation.displayName}`}
                          className="landing-research-card-img"
                          loading="lazy"
                          onError={() => setFailedImages((prev) => ({ ...prev, [field.id]: true }))}
                        />
                      ) : (
                        <div className="landing-research-card-fallback">
                          <span className="landing-research-fallback-code">{field.code}</span>
                          <span className="landing-research-fallback-name">{field.name}</span>
                        </div>
                      )}
                    </Link>

                    <div className="landing-research-card-body">
                      <div className="landing-research-card-header">
                        <span className="landing-research-card-code">{field.code}</span>
                      </div>

                      <h3 className="landing-research-card-title">
                        <Link
                          to={`/linh-vuc/${encodeURIComponent(field.code)}`}
                          className="landing-research-title-link"
                        >
                          {presentation.displayName}
                        </Link>
                      </h3>
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
          5. OPPORTUNITIES & RECRUITING SPOTLIGHT
          ═══════════════════════════════════════════════════════════ */}
      <section id="du-an-tuyen-dung" className="landing-section" aria-labelledby="recruit-heading">
        <div className="landing-wrap">
          <div className="landing-recruit-spotlight">
            <div className="landing-recruit-spotlight-copy">
              <div className="landing-recruit-badge">
                <span className="landing-proj-status-dot" aria-hidden="true" />
                <span>Cơ hội tham gia nghiên cứu</span>
              </div>
              <h2 id="recruit-heading" className="landing-recruit-title">
                Gia nhập các nhóm nghiên cứu tại Smart Lab
              </h2>
              <p className="landing-recruit-lead">
                Smart Lab liên tục chào đón sinh viên và nghiên cứu viên tài năng tham gia các đề tài nghiên cứu ứng dụng, giải pháp phần mềm và phát triển robot thông minh.
              </p>
              <div className="landing-recruit-actions">
                <Link className="btn primary lg landing-hero-btn-primary" to="/du-an?status=RECRUITING">
                  <span>Khám phá dự án đang tuyển</span>
                  <ArrowRight size={16} className="landing-hero-btn-icon" />
                </Link>
                <Link className="landing-recruit-sublink" to="/du-an">
                  Xem tất cả đề tài dự án
                </Link>
              </div>
            </div>

            <div className="landing-recruit-spotlight-preview">
              <LandingState
                loading={recruitLoading}
                error={recruitError}
                empty={recruitItems.length === 0}
                emptyText="Hiện tại các nhóm nghiên cứu đang hoàn thiện đợt tuyển. Vui lòng quay lại sau."
              />

              {recruitItems.length > 0 && (
                <div className="landing-recruit-preview-list">
                  {recruitItems.slice(0, 2).map((project) => (
                    <Link
                      key={project.id}
                      to={`/du-an/${project.id}`}
                      className="landing-recruit-preview-card"
                      aria-label={`Dự án đang tuyển: ${project.name}`}
                    >
                      <div className="landing-recruit-preview-top">
                        <span className="landing-recruit-preview-code">{project.code}</span>
                        <span className="landing-proj-status-badge">
                          <span className="landing-proj-status-dot" aria-hidden="true" />
                          Đang tuyển
                        </span>
                      </div>
                      <h3 className="landing-recruit-preview-name">{project.name}</h3>
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
                      <span className="landing-recruit-preview-arrow">
                        <span>Chi tiết đề tài</span>
                        <ArrowRight size={14} />
                      </span>
                    </Link>
                  ))}
                </div>
              )}
            </div>
          </div>
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

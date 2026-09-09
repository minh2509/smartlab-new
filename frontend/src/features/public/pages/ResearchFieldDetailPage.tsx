import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Cpu,
  ExternalLink,
  FileText,
  FolderGit2,
  GitBranch,
  Layers,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'
import { useEffect, useLayoutEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiClientError } from '../../../lib/apiClient'
import type { ResearchField } from '../../../shared/types/api'
import { listPublicProjects } from '../../projects/api'
import type { PublicProjectSummary } from '../../projects/types'
import { getResearchFieldByCode } from '../../profile/api'
import { fieldVisual, publicFileUrl } from '../researchFieldVisual'
import { RESEARCH_FIELDS_PATH } from '../researchFieldNavigation'
import { getResearchFieldDomainContent } from '../researchFieldContent'
import './ResearchFieldDetailPage.css'

type DetailState =
  | { status: 'loading' }
  | { status: 'ready'; field: ResearchField }
  | { status: 'not-found' }
  | { status: 'error' }

export function ResearchFieldDetailPage() {
  const { code = '' } = useParams()
  const [state, setState] = useState<DetailState>({ status: 'loading' })
  const [coverFailed, setCoverFailed] = useState(false)
  const [projectsState, setProjectsState] = useState<{
    status: 'loading' | 'ready' | 'error'
    projects: PublicProjectSummary[]
  }>({ status: 'loading', projects: [] })

  useLayoutEffect(() => {
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual'
    }
    window.scrollTo({
      top: 0,
      left: 0,
      behavior: 'auto',
    })
  }, [code])

  useEffect(() => {
    let active = true
    setState({ status: 'loading' })
    setCoverFailed(false)

    void getResearchFieldByCode(code)
      .then((field) => { if (active) setState({ status: 'ready', field }) })
      .catch((reason: unknown) => {
        if (!active) return
        setState(reason instanceof ApiClientError && reason.status === 404
          ? { status: 'not-found' }
          : { status: 'error' })
      })

    return () => { active = false }
  }, [code])

  // Fetch real SmartLab projects associated with this research field
  useEffect(() => {
    if (state.status !== 'ready') return
    let active = true
    setProjectsState({ status: 'loading', projects: [] })

    void listPublicProjects(0, 12, { researchFieldId: state.field.id })
      .then((res) => {
        if (active) setProjectsState({ status: 'ready', projects: res.items })
      })
      .catch(() => {
        if (active) setProjectsState({ status: 'error', projects: [] })
      })

    return () => { active = false }
  }, [state])

  if (state.status === 'loading') {
    return (
      <main className="rf-detail-page">
        <div className="rf-wrap">
          <div className="research-field-detail-state" role="status" aria-live="polite">
            <h1 className="rf-section-title">Đang tải lĩnh vực nghiên cứu…</h1>
            <p className="rf-section-lead">Hệ thống đang nạp dữ liệu chuyên môn và hồ sơ dự án từ máy chủ.</p>
          </div>
        </div>
      </main>
    )
  }

  if (state.status === 'not-found') {
    return (
      <main className="rf-detail-page">
        <div className="rf-wrap">
          <div className="research-field-detail-state" role="status" aria-live="polite">
            <h1 className="rf-section-title">Không tìm thấy lĩnh vực nghiên cứu</h1>
            <p className="rf-section-lead">Lĩnh vực mang mã "{code}" không tồn tại hoặc hiện không được công khai.</p>
            <div style={{ marginTop: '24px' }}>
              <Link className="btn outline" to={RESEARCH_FIELDS_PATH}>
                <ArrowLeft size={16} /> Quay lại danh mục lĩnh vực
              </Link>
            </div>
          </div>
        </div>
      </main>
    )
  }

  if (state.status === 'error') {
    return (
      <main className="rf-detail-page">
        <div className="rf-wrap">
          <div className="research-field-detail-state" role="status" aria-live="polite">
            <h1 className="rf-section-title">Chưa thể tải lĩnh vực nghiên cứu</h1>
            <p className="rf-section-lead">Có sự cố kết nối tới máy chủ dữ liệu. Vui lòng kiểm tra lại kết nối mạng.</p>
            <div style={{ marginTop: '24px' }}>
              <Link className="btn outline" to={RESEARCH_FIELDS_PATH}>
                <ArrowLeft size={16} /> Quay lại danh mục lĩnh vực
              </Link>
            </div>
          </div>
        </div>
      </main>
    )
  }

  const { field } = state
  const visual = fieldVisual(field)
  // Preserve uploaded cover image from backend if present, fallback gracefully to visual illustration
  const image = field.coverFileId && !coverFailed ? publicFileUrl(field.coverFileId) : visual.image
  const domainContent = getResearchFieldDomainContent(field.code)
  const projectCount = projectsState.projects.length

  return (
    <main className="rf-detail-page">
      <div className="rf-wrap">
        {/* Navigation / Breadcrumbs */}
        <nav className="rf-topbar" aria-label="Điều hướng lĩnh vực nghiên cứu">
          <Link className="rf-back-link" to={RESEARCH_FIELDS_PATH}>
            <ArrowLeft size={16} aria-hidden="true" /> Quay lại các lĩnh vực
          </Link>
          <div className="rf-breadcrumbs">
            <Link to="/">Trang chủ</Link>
            <span className="rf-breadcrumbs-separator">/</span>
            <Link to={RESEARCH_FIELDS_PATH}>Lĩnh vực</Link>
            <span className="rf-breadcrumbs-separator">/</span>
            <span className="rf-breadcrumbs-current" aria-current="page">{field.name}</span>
          </div>
        </nav>

        {/* SECTION A: HERO */}
        <section className="rf-hero" aria-label="Tổng quan đầu trang">
          <div className="rf-hero-grid">
            <div className="rf-hero-content">
              <div className="rf-badge-cluster">
                <span className="rf-code-chip">
                  <Sparkles size={13} aria-hidden="true" /> {field.code}
                </span>
                <span className="rf-domain-category">
                  {domainContent?.vietnameseName ?? 'Lĩnh vực nghiên cứu chuyên sâu'}
                </span>
              </div>

              <h1 className="rf-hero-title">{field.name}</h1>

              {domainContent ? (
                <p className="rf-hero-tagline">{domainContent.tagline}</p>
              ) : null}

              {/* Database introductory description explicitly marked as SmartLab context */}
              <div className="rf-hero-db-intro">
                <span className="rf-hero-db-intro-label">Định hướng nghiên cứu tại Smart Lab</span>
                <p>{field.description || 'Thông tin chi tiết về hướng nghiên cứu này đang được cập nhật.'}</p>
              </div>

              {/* Quick Operational Metrics */}
              <div className="rf-hero-meta-rail">
                <span className="rf-meta-pill">
                  <Cpu size={15} aria-hidden="true" /> Trọng tâm: {field.name}
                </span>
                <span className="rf-meta-pill">
                  <FolderGit2 size={15} aria-hidden="true" /> {projectCount} dự án công khai
                </span>
                <span className="rf-meta-pill">
                  <ShieldCheck size={15} aria-hidden="true" /> Trạng thái: {field.isActive ? 'Đang hoạt động' : 'Tạm dừng'}
                </span>
              </div>
            </div>

            {/* Uploaded Cover Image Container - Designed professionally around arbitrary real uploaded images */}
            <div className="rf-hero-media">
              <div className="rf-hero-frame">
                <img
                  className="rf-hero-cover-img"
                  src={image}
                  alt={field.coverFileId && !coverFailed ? `Ảnh đại diện định danh lĩnh vực ${field.name}` : visual.imageAlt}
                  onError={() => {
                    if (field.coverFileId && !coverFailed) setCoverFailed(true)
                  }}
                />
                <div className="rf-hero-frame-footer">
                  <span>Ảnh định danh lĩnh vực</span>
                  <span>{field.code} · Smart Lab Archive</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* CURATED DOMAIN KNOWLEDGE (AI, ROBOTICS, SE) */}
        {domainContent ? (
          <>
            {/* SECTION B: DOMAIN OVERVIEW */}
            <section className="rf-section rf-section-overview" aria-labelledby="overview-heading">
              <div className="rf-section-header">
                <span className="rf-eyebrow">Khám phá chuyên môn</span>
                <h2 id="overview-heading" className="rf-section-title">Lĩnh vực này là gì?</h2>
                <p className="rf-section-lead">
                  Tổng quan khoa học và ranh giới chuyên môn theo các tiêu chuẩn kỹ nghệ quốc tế.
                </p>
              </div>

              <div className="rf-overview-grid">
                <div className="rf-definition-block">
                  <blockquote>"{domainContent.overview.definition}"</blockquote>
                  <div className="rf-definition-source">
                    <BookOpen size={15} aria-hidden="true" />
                    <span>{domainContent.overview.definitionSource}</span>
                  </div>
                </div>

                <div className="rf-prose-stack">
                  <div className="rf-prose-card">
                    <h3>
                      <Layers size={16} aria-hidden="true" /> Không gian bài toán giải quyết
                    </h3>
                    <p>{domainContent.overview.problemSpace}</p>
                  </div>

                  <div className="rf-prose-card">
                    <h3>
                      <GitBranch size={16} aria-hidden="true" /> Điểm khác biệt & Ranh giới ngành
                    </h3>
                    <p>{domainContent.overview.distinction}</p>
                  </div>
                </div>
              </div>
            </section>

            {/* SECTION C: CORE RESEARCH MAP */}
            <section className="rf-section rf-section-research-map" aria-labelledby="research-map-heading">
              <div className="rf-section-header">
                <span className="rf-eyebrow">Bản đồ tri thức</span>
                <h2 id="research-map-heading" className="rf-section-title">Các hướng nghiên cứu trọng tâm</h2>
                <p className="rf-section-lead">{domainContent.researchMap.sectionDescription}</p>
              </div>

              <div className="rf-subdomains-grid">
                {domainContent.researchMap.items.map((subdomain) => (
                  <article key={subdomain.title} className="rf-subdomain-card">
                    <span className="rf-subdomain-english">{subdomain.englishTitle}</span>
                    <h3>{subdomain.title}</h3>
                    <p>{subdomain.description}</p>
                    <div className="rf-focus-areas" aria-label="Chủ đề tiêu biểu">
                      {subdomain.focusAreas.map((tag) => (
                        <span key={tag} className="rf-focus-tag">{tag}</span>
                      ))}
                    </div>
                  </article>
                ))}
              </div>
            </section>

            {/* SECTION D: ENGINEERING & RESEARCH LIFECYCLE */}
            <section className="rf-section rf-section-lifecycle" aria-labelledby="lifecycle-heading">
              <div className="rf-section-header">
                <span className="rf-eyebrow">Phương pháp luận kỹ nghệ</span>
                <h2 id="lifecycle-heading" className="rf-section-title">Quy trình nghiên cứu & Vòng đời hệ thống điển hình</h2>
                <p className="rf-section-lead">{domainContent.lifecycle.sectionDescription}</p>
              </div>

              <div className="rf-disclaimer-banner" role="note">
                <FileText size={18} aria-hidden="true" />
                <span>{domainContent.lifecycle.disclaimer}</span>
              </div>

              <div className="rf-lifecycle-rail">
                {domainContent.lifecycle.phases.map((phase) => (
                  <div key={phase.step} className="rf-phase-card">
                    <div className="rf-phase-topline">
                      <span className="rf-phase-step">{phase.step}</span>
                      <span className="rf-phase-english">{phase.englishName}</span>
                    </div>
                    <h3>{phase.name}</h3>
                    <p className="rf-phase-objective">{phase.objective}</p>
                    <div className="rf-phase-detail">
                      <div>{phase.activities}</div>
                      <div className="rf-phase-deliverable">
                        <strong>Kết quả bàn giao:</strong> {phase.deliverables}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </section>

            {/* SECTION E: TECHNICAL FOUNDATIONS */}
            <section className="rf-section rf-section-foundations" aria-labelledby="foundations-heading">
              <div className="rf-section-header">
                <span className="rf-eyebrow">Nền tảng khoa học</span>
                <h2 id="foundations-heading" className="rf-section-title">Tri thức & Kỹ năng nền tảng cốt lõi</h2>
                <p className="rf-section-lead">{domainContent.foundations.sectionDescription}</p>
              </div>

              <div className="rf-foundations-grid">
                {domainContent.foundations.items.map((item) => (
                  <div key={item.index} className="rf-foundation-card">
                    <span className="rf-foundation-index">{item.index}</span>
                    <span className="rf-foundation-english">{item.englishTitle}</span>
                    <h3>{item.title}</h3>
                    <p>{item.description}</p>
                    <div className="rf-foundation-pills" aria-label="Nội dung cốt lõi">
                      {item.disciplines.map((pill) => (
                        <span key={pill} className="rf-foundation-pill">{pill}</span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </section>

            {/* SECTION F: RESPONSIBILITY & QUALITY STANDARDS */}
            <section className="rf-section rf-section-responsibility" aria-labelledby="responsibility-heading">
              <div className="rf-responsibility-panel">
                <span className="rf-eyebrow">Chuẩn mực chất lượng & Đạo đức</span>
                <h2 id="responsibility-heading" className="rf-responsibility-title">{domainContent.responsibility.title}</h2>
                <div className="rf-responsibility-framework">
                  <ShieldCheck size={14} aria-hidden="true" />
                  <span>{domainContent.responsibility.framework}</span>
                </div>
                <p className="rf-responsibility-lead">{domainContent.responsibility.description}</p>

                <div className="rf-pillars-grid">
                  {domainContent.responsibility.pillars.map((pillar) => (
                    <div key={pillar.title} className="rf-pillar-card">
                      <h3>{pillar.title}</h3>
                      <span className="rf-pillar-english">{pillar.englishTitle}</span>
                      <p>{pillar.description}</p>
                    </div>
                  ))}
                </div>
              </div>
            </section>
          </>
        ) : (
          /* Fallback for Unknown / Newly-created Admin Fields */
          <div className="rf-unknown-notice">
            <h3>Thông tin học thuật chuyên sâu đang được cập nhật</h3>
            <p>
              Nội dung biên soạn chuẩn mực theo tiêu chuẩn quốc tế đang được phòng lab hoàn thiện cho lĩnh vực này.
              Các dự án và hoạt động thực tế vẫn được triển khai theo quy chuẩn kỹ thuật của Smart Lab.
            </p>
          </div>
        )}

        {/* SECTION G: SMARTLAB PROJECTS IN THIS FIELD (REAL API DATA) */}
        <section className="rf-section rf-section-projects" aria-labelledby="projects-heading">
          <div className="rf-section-header">
            <span className="rf-eyebrow">Hoạt động thực tế</span>
            <h2 id="projects-heading" className="rf-section-title">
              Dự án thực tế tại Smart Lab ({projectCount})
            </h2>
            <p className="rf-section-lead">
              Các dự án nghiên cứu và phát triển gắn liền với lĩnh vực {field.name} đang được công khai với cộng đồng.
            </p>
          </div>

          {projectCount > 0 ? (
            <div className="rf-projects-grid">
              {projectsState.projects.map((project) => (
                <article key={project.id} className="rf-project-card">
                  <div className="rf-project-topline">
                    <span className="rf-project-code">{project.code}</span>
                    <span className={`rf-project-status is-${project.publicStatus.toLowerCase().replace('_', '-')}`}>
                      {project.publicStatus === 'RECRUITING'
                        ? 'Đang tuyển quân'
                        : project.publicStatus === 'ACTIVE'
                        ? 'Đang thực hiện'
                        : project.publicStatus === 'UPCOMING'
                        ? 'Sắp diễn ra'
                        : 'Đã hoàn thành'}
                    </span>
                  </div>
                  <h3>
                    <Link to={`/du-an/${project.id}`}>{project.name}</Link>
                  </h3>
                  {project.description ? (
                    <p className="rf-project-desc">{project.description}</p>
                  ) : null}
                  {project.goal ? (
                    <div className="rf-project-goal">
                      <strong>Mục tiêu:</strong> {project.goal}
                    </div>
                  ) : null}
                  <Link className="rf-project-action" to={`/du-an/${project.id}`}>
                    Xem chi tiết dự án <ArrowRight size={14} aria-hidden="true" />
                  </Link>
                </article>
              ))}
            </div>
          ) : (
            /* Elegant Domain-Specific Empty State */
            <div className="rf-projects-empty">
              <div className="rf-empty-icon-box">
                <FolderGit2 size={24} aria-hidden="true" />
              </div>
              <h3>Chưa có dự án công khai</h3>
              <p>
                Hiện chưa có dự án công khai nào được gắn trực tiếp với lĩnh vực {field.name} trong kỳ công bố hiện tại.
                Bạn có thể khám phá danh mục toàn bộ dự án nghiên cứu đang triển khai tại lab.
              </p>
              <Link className="btn outline" to="/du-an">
                Xem tất cả dự án Smart Lab <ArrowRight size={15} aria-hidden="true" />
              </Link>
            </div>
          )}
        </section>

        {/* SECTION H: AUTHORITATIVE REFERENCES */}
        {domainContent ? (
          <section className="rf-section rf-section-references" aria-labelledby="references-heading">
            <div className="rf-section-header">
              <span className="rf-eyebrow">Khung chuẩn mực</span>
              <h2 id="references-heading" className="rf-section-title">Tài liệu & Khung tham chiếu học thuật</h2>
              <p className="rf-section-lead">
                Các tổ chức tiêu chuẩn, viện nghiên cứu và khung lý thuyết chuẩn mực định hình tri thức chuyên môn trên trang này.
              </p>
            </div>

            <div className="rf-references-grid">
              {domainContent.references.map((ref) => (
                <a
                  key={ref.url}
                  className="rf-reference-card"
                  href={ref.url}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <div className="rf-reference-topline">
                    <span className="rf-reference-institution">{ref.institution}</span>
                    <span className="rf-reference-domain">{ref.domain}</span>
                  </div>
                  <div className="rf-reference-title">
                    <span>{ref.title}</span>
                    <ExternalLink size={15} aria-hidden="true" />
                  </div>
                  <p className="rf-reference-scope">{ref.scope}</p>
                </a>
              ))}
            </div>
          </section>
        ) : null}
      </div>
    </main>
  )
}

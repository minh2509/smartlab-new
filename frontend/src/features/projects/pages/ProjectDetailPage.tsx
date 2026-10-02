import {
  Archive,
  ArrowLeft,
  BookOpen,
  CalendarDays,
  Download,
  File,
  FileImage,
  FileSpreadsheet,
  FileText,
  FolderKanban,
  LogIn,
  Presentation,
  UserPlus,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { hasAllPermissions } from '../../../app/accessPolicy'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import { listPublicDocuments, publicDocumentFileUrl } from '../../public/documentApi'
import type { PublicDocumentSummary } from '../../public/documentTypes'
import { getPublicProject, listMyProjectMemberships } from '../api'
import { ProjectJoinRequestCard } from '../components/ProjectJoinRequestCard'
import {
  PUBLIC_PROJECT_STATUS_LABELS,
  PROJECT_TYPE_LABELS,
} from '../types'
import type { PublicProjectDetail } from '../types'
import './ProjectDetailPage.css'

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { token, profile } = useAuth()
  const [project, setProject] = useState<PublicProjectDetail | null>(null)
  const [documents, setDocuments] = useState<PublicDocumentSummary[]>([])
  const [totalDocs, setTotalDocs] = useState(0)
  const [loadingDocs, setLoadingDocs] = useState(true)
  const [hasActiveMembership, setHasActiveMembership] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let active = true
    const projectId = Number(id)

    setProject(null)
    setDocuments([])
    setTotalDocs(0)
    setError(null)

    if (!Number.isInteger(projectId) || projectId <= 0) {
      setLoading(false)
      setError('Mã dự án không hợp lệ.')
      return () => {
        active = false
      }
    }

    setLoading(true)
    getPublicProject(projectId)
      .then((result) => {
        if (active) setProject(result)
      })
      .catch((reason: unknown) => {
        if (active) {
          setError(reason instanceof Error ? reason.message : 'Không tải được thông tin dự án.')
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    setLoadingDocs(true)
    listPublicDocuments(0, 10, { projectId })
      .then((result) => {
        if (active) {
          setDocuments(result.items)
          setTotalDocs(result.totalElements)
        }
      })
      .catch(() => {
        if (active) {
          setDocuments([])
          setTotalDocs(0)
        }
      })
      .finally(() => {
        if (active) setLoadingDocs(false)
      })

    return () => {
      active = false
    }
  }, [id, reloadKey])

  const isAdminProjectManager = Boolean(
    profile?.roles.includes('ADMIN')
      && hasAllPermissions(profile.permissions, ['PROJECT_MANAGE']),
  )

  useEffect(() => {
    if (!token || !project || isAdminProjectManager) {
      setHasActiveMembership(false)
      return
    }

    let active = true
    setHasActiveMembership(false)
    listMyProjectMemberships(token)
      .then((memberships) => {
        if (active) {
          setHasActiveMembership(memberships.some(
            (membership) => membership.projectId === project.id && membership.status === 'ACTIVE',
          ))
        }
      })
      .catch(() => {
        if (active) setHasActiveMembership(false)
      })

    return () => {
      active = false
    }
  }, [isAdminProjectManager, project, token])

  if (loading) {
    return (
      <main className="project-detail-stage">
        <div className="wrap project-detail-wrap">
          <div className="public-empty empty tight">Đang tải thông tin dự án...</div>
        </div>
      </main>
    )
  }

  if (error || !project) {
    return (
      <main className="project-detail-stage">
        <div className="wrap project-detail-wrap">
          <div className="project-detail-nav">
            <Link className="project-detail-back" to="/du-an">
              <ArrowLeft size={16} aria-hidden="true" />
              <span>Quay lại danh sách dự án</span>
            </Link>
          </div>
          <Feedback error={error ?? 'Không tìm thấy dự án.'} />
          <div className="row gap-6 wrapf" style={{ marginTop: '16px' }}>
            <Link className="btn" to="/du-an">Quay lại danh sách</Link>
            {id && Number.isInteger(Number(id)) && Number(id) > 0 ? (
              <button className="btn ghost" type="button" onClick={() => setReloadKey((value) => value + 1)}>
                Thử tải lại
              </button>
            ) : null}
          </div>
        </div>
      </main>
    )
  }

  const primaryLeader = project.primaryLeader
  const publicStatus = project.publicStatus
  const leaders = project.leaders.map((leader) => ({
    ...leader,
    isPrimary: leader.name === primaryLeader?.name,
  }))

  const isMemberOrAdmin = isAdminProjectManager || hasActiveMembership
  const isRecruiting = publicStatus === 'RECRUITING'

  const handleScrollToJoin = () => {
    const el = document.getElementById('project-join-box')
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }

  return (
    <main className="project-detail-page">
      {/* Header với màu nền cũ (navy gradient) nhưng tinh gọn, hiện đại */}
      <header className="project-detail-header pagehead-theme">
        <div className="wrap project-detail-wrap">
          <div className="project-detail-topline">
            <Link className="project-detail-back on-dark" to="/du-an">
              <ArrowLeft size={16} aria-hidden="true" />
              <span>Quay lại danh sách dự án</span>
            </Link>

            {isRecruiting && !isMemberOrAdmin ? (
              <button
                type="button"
                className="btn primary small project-detail-header-cta"
                onClick={handleScrollToJoin}
              >
                Đăng ký tham gia
              </button>
            ) : null}
          </div>

          <h1 className="project-detail-title on-dark">{renderFormattedTitle(project.name)}</h1>
        </div>
      </header>

      <div className="wrap project-detail-wrap project-detail-body">
        <div className="project-detail-layout">
          {/* Cột chính bên trái: Mô tả -> Mục tiêu nghiên cứu -> Đăng ký tham gia -> Tài liệu */}
          <div className="project-detail-main">
            {/* Phần mô tả chuyển từ pageheader xuống đặt ngay trên mục tiêu dự án */}
            {project.description ? (
              <div className="project-detail-desc-block">
                <p className="project-detail-lead-desc">{project.description}</p>
              </div>
            ) : null}

            {/* 1. Mục tiêu nghiên cứu */}
            <section className="project-detail-section-block" aria-labelledby="project-goal-title">
              <h2 id="project-goal-title" className="project-detail-block-title">
                Mục tiêu nghiên cứu
              </h2>
              <p className={!project.goal ? 'is-muted project-detail-paragraph' : 'project-detail-paragraph'}>
                {project.goal || 'Dự án chưa cập nhật chi tiết mục tiêu nghiên cứu.'}
              </p>
            </section>

            {/* 2. Tham gia dự án - Box đăng ký */}
            <section
              id="project-join-box"
              className="project-detail-section-block"
              aria-label="Đăng ký tham gia dự án"
            >
              {token ? (
                <ProjectJoinRequestCard
                  project={project}
                  showWorkspaceActions={!isMemberOrAdmin}
                />
              ) : isRecruiting ? (
                <div className="project-detail-recruiting-banner">
                  <div className="project-detail-recruiting-icon" aria-hidden="true">
                    <UserPlus size={22} />
                  </div>
                  <div className="project-detail-recruiting-content">
                    <strong>Dự án đang mở đợt tuyển thành viên mới</strong>
                    <p>
                      Sinh viên và nhà nghiên cứu quan tâm có thể đăng nhập tài khoản Smart Lab để gửi hồ sơ và lời nhắn trực tiếp tới nhóm Leader.
                    </p>
                    <Link className="btn primary small" to={`/login?redirect=/du-an/${project.id}`}>
                      <LogIn size={15} aria-hidden="true" /> Đăng nhập để đăng ký tham gia
                    </Link>
                  </div>
                </div>
              ) : null}
            </section>

            {/* 3. Tài liệu công khai */}
            <section className="project-detail-section-block" aria-labelledby="project-docs-title">
              <div className="project-detail-block-header">
                <h2 id="project-docs-title" className="project-detail-block-title">
                  Tài liệu công khai
                </h2>
                {totalDocs > 0 ? (
                  <span className="chip subtle">{totalDocs} tài liệu</span>
                ) : null}
              </div>

              {loadingDocs ? (
                <p className="project-detail-empty">Đang kiểm tra tài liệu công khai...</p>
              ) : documents.length > 0 ? (
                <div className="project-detail-docs-list">
                  {documents.map((doc) => {
                    const Icon = fileIcon(doc.mimeType, doc.originalFileName)
                    const downloadUrl = publicDocumentFileUrl(doc.currentFileId)
                    return (
                      <div key={doc.id} className="project-detail-doc-item">
                        <span className="project-detail-doc-icon" aria-hidden="true">
                          <Icon size={20} />
                        </span>
                        <div className="project-detail-doc-info">
                          <h3 className="project-detail-doc-title">{doc.title}</h3>
                          {doc.description ? (
                            <p className="project-detail-doc-desc">{doc.description}</p>
                          ) : null}
                          <div className="project-detail-doc-meta">
                            <span className="project-detail-doc-file-name" title={doc.originalFileName}>
                              {doc.originalFileName}
                            </span>
                            <span className="project-detail-doc-bullet" aria-hidden="true">•</span>
                            <span>{formatBytes(doc.sizeBytes)}</span>
                            <span className="project-detail-doc-bullet" aria-hidden="true">•</span>
                            <time dateTime={doc.updatedAt}>Cập nhật {formatDate(doc.updatedAt)}</time>
                          </div>
                        </div>
                        <a
                          className="btn small project-detail-doc-download"
                          href={downloadUrl}
                          download={doc.originalFileName}
                          title={`Tải về: ${doc.title}`}
                          aria-label={`Tải về tài liệu ${doc.title}`}
                        >
                          <Download size={14} aria-hidden="true" />
                          <span>Tải về</span>
                        </a>
                      </div>
                    )
                  })}

                  {totalDocs > documents.length ? (
                    <div className="project-detail-docs-footer">
                      <Link to={`/tai-lieu?project=${project.id}`} className="project-detail-docs-more">
                        Xem toàn bộ {totalDocs} tài liệu của dự án trong Thư viện tài liệu →
                      </Link>
                    </div>
                  ) : null}
                </div>
              ) : (
                <div className="project-detail-docs-empty">
                  <BookOpen size={20} aria-hidden="true" />
                  <span>Chưa có tài liệu hoặc báo cáo công khai nào cho dự án này.</span>
                </div>
              )}
            </section>
          </div>

          {/* Cột phụ bên phải: Thông tin dự án, Người phụ trách, Khu vực nội bộ */}
          <aside className="project-detail-sidebar">
            {/* Card Thông tin dự án */}
            <section className="project-detail-sidebar-card" aria-labelledby="project-facts-title">
              <h3 id="project-facts-title" className="project-detail-sidebar-title">
                Thông tin dự án
              </h3>

              <dl className="project-detail-fact-grid">
                <div className="project-detail-fact-item">
                  <dt>Trạng thái</dt>
                  <dd>
                    <span className={`project-detail-status-pill project-detail-status-${publicStatus.toLowerCase()}`}>
                      {PUBLIC_PROJECT_STATUS_LABELS[publicStatus]}
                    </span>
                  </dd>
                </div>
                <div className="project-detail-fact-item">
                  <dt>Loại hình</dt>
                  <dd>{PROJECT_TYPE_LABELS[project.projectType]}</dd>
                </div>
                <div className="project-detail-fact-item">
                  <dt>Ngày bắt đầu</dt>
                  <dd>{formatDate(project.startDate)}</dd>
                </div>
                <div className="project-detail-fact-item">
                  <dt>Ngày kết thúc dự kiến</dt>
                  <dd>{formatDate(project.expectedEndDate)}</dd>
                </div>
              </dl>

              {project.researchFields && project.researchFields.length > 0 ? (
                <div className="project-detail-fact-fields">
                  <span className="project-detail-fact-label">Lĩnh vực nghiên cứu</span>
                  <div className="project-detail-field-list">
                    {project.researchFields.map((field) => (
                      <Link
                        key={field.id}
                        to={`/du-an?field=${field.id}`}
                        className="chip clickable"
                        title={`Xem các dự án thuộc ${field.name}`}
                      >
                        {field.name}
                      </Link>
                    ))}
                  </div>
                </div>
              ) : null}
            </section>

            {/* Card Người phụ trách */}
            <section className="project-detail-sidebar-card" aria-labelledby="project-leaders-title">
              <h3 id="project-leaders-title" className="project-detail-sidebar-title">
                Người phụ trách
              </h3>

              {leaders.length > 0 ? (
                <ul className="project-detail-leader-list">
                  {leaders.map((leader, index) => (
                    <li key={`${leader.name}-${index}`}>
                      <span className="project-detail-avatar" aria-hidden="true">
                        {initialsOf(leader.name)}
                      </span>
                      <div className="project-detail-leader-info">
                        <strong>{leader.name}</strong>
                        {leader.isPrimary ? <small className="project-detail-primary-tag">Leader chính</small> : null}
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="project-detail-empty">Chưa có thông tin phụ trách công khai.</p>
              )}
            </section>

            {/* Card Không gian nội bộ (chỉ hiển thị với Member/Leader/Admin) */}
            {isMemberOrAdmin ? (
              <section className="project-detail-sidebar-card project-detail-workspace-card" aria-labelledby="project-workspace-title">
                <h3 id="project-workspace-title" className="project-detail-sidebar-title">
                  Khu vực nội bộ
                </h3>
                <p className="project-detail-workspace-desc">
                  {isAdminProjectManager
                    ? 'Bạn có quyền quản trị dự án này trong hệ thống.'
                    : 'Bạn đang là thành viên tích cực của dự án này.'}
                </p>
                <div className="project-detail-workspace-buttons">
                  <Link className="btn primary small" to={`/admin/projects?projectId=${project.id}`}>
                    <FolderKanban size={14} aria-hidden="true" /> Mở quản lý dự án
                  </Link>
                  <Link className="btn ghost small" to={`/admin/events?projectId=${project.id}`}>
                    <CalendarDays size={14} aria-hidden="true" /> Sự kiện dự án
                  </Link>
                </div>
              </section>
            ) : null}
          </aside>
        </div>
      </div>
    </main>
  )
}

function formatDate(value: string | null) {
  if (!value) return 'Chưa cập nhật'
  const date = new Date(value.includes('T') ? value : `${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date)
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value < 0) return 'Không rõ kích thước'
  if (value < 1024) return `${value} B`
  const units = ['KB', 'MB', 'GB']
  let size = value / 1024
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} ${units[index]}`
}

function fileIcon(mimeType: string, name: string) {
  const mime = (mimeType || '').toLowerCase()
  const lowerName = (name || '').toLowerCase()
  if (mime === 'application/pdf' || lowerName.endsWith('.pdf')) return FileText
  if (mime.startsWith('image/')) return FileImage
  if (
    mime.includes('spreadsheet')
    || mime.includes('excel')
    || mime === 'text/csv'
    || lowerName.endsWith('.xlsx')
    || lowerName.endsWith('.xls')
    || lowerName.endsWith('.csv')
  ) {
    return FileSpreadsheet
  }
  if (
    mime.includes('presentation')
    || mime.includes('powerpoint')
    || lowerName.endsWith('.pptx')
    || lowerName.endsWith('.ppt')
  ) {
    return Presentation
  }
  if (
    mime.includes('zip')
    || mime.includes('rar')
    || mime.includes('7z')
    || /\.(zip|rar|7z)$/.test(lowerName)
  ) {
    return Archive
  }
  if (
    mime.startsWith('text/')
    || mime.includes('word')
    || mime.includes('document')
    || lowerName.endsWith('.docx')
    || lowerName.endsWith('.doc')
  ) {
    return FileText
  }
  return File
}

function renderFormattedTitle(title: string) {
  if (!title) return title
  const regex = /(\([^)]+\))/g
  const parts = title.split(regex)
  if (parts.length === 1) return title

  return parts.map((part, index) => {
    if (part.startsWith('(') && part.endsWith(')')) {
      return (
        <span key={index} className="title-cluster">
          {part}
        </span>
      )
    }
    return part
  })
}

function initialsOf(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part.charAt(0))
    .join('')
    .toLocaleUpperCase('vi')
}


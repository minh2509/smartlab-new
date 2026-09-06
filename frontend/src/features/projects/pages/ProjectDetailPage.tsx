import { CalendarDays, FolderKanban, UsersRound } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { hasAllPermissions } from '../../../app/accessPolicy'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import { PublicPageHead } from '../../public/components/PublicPageHead'
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
  const [hasActiveMembership, setHasActiveMembership] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let active = true
    const projectId = Number(id)

    setProject(null)
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
      <>
        <PublicPageHead title="Dự án" description="Đang tải thông tin dự án..." />
        <section className="section"><div className="wrap"><div className="public-empty empty tight">Đang tải thông tin dự án...</div></div></section>
      </>
    )
  }

  if (error || !project) {
    return (
      <>
        <PublicPageHead title="Không tìm thấy dự án" description="Dự án không tồn tại hoặc bạn không có quyền xem." />
        <section className="section">
          <div className="wrap">
            <Feedback error={error ?? 'Không tìm thấy dự án.'} />
            <div className="row gap-6 wrapf">
              <Link className="btn" to="/du-an">Quay lại danh sách</Link>
              {id && Number.isInteger(Number(id)) && Number(id) > 0 ? (
                <button className="btn ghost" type="button" onClick={() => setReloadKey((value) => value + 1)}>
                  Thử tải lại
                </button>
              ) : null}
            </div>
          </div>
        </section>
      </>
    )
  }

  const facts = [
    { label: 'Mã dự án', value: project.code },
    { label: 'Loại', value: PROJECT_TYPE_LABELS[project.projectType] },
    { label: 'Ngày bắt đầu', value: formatDate(project.startDate) },
    { label: 'Dự kiến kết thúc', value: formatDate(project.expectedEndDate) },
    { label: 'Kết thúc thực tế', value: formatDate(project.actualEndDate) },
  ]

  const primaryLeader = project.primaryLeader
  const publicStatus = project.publicStatus
  const leaders = project.leaders.map((leader) => ({
    ...leader,
    isPrimary: leader.name === primaryLeader?.name,
  }))

  return (
    <>
      <PublicPageHead title={project.name} description={project.description || 'Thông tin dự án tại Smart Lab.'} />

      <section className="section project-detail-section">
        <div className="wrap project-detail-wrap">
          <Link className="project-detail-back" to="/du-an">← Quay lại danh sách dự án</Link>

          <div className="project-detail-context" aria-label="Phân loại dự án">
            <span className={`project-detail-public-status project-detail-public-status-${publicStatus.toLowerCase()}`}>
              {PUBLIC_PROJECT_STATUS_LABELS[publicStatus]}
            </span>
            <span className="project-detail-type">{PROJECT_TYPE_LABELS[project.projectType]}</span>
            {project.isFeatured ? <span className="project-detail-meta">Nổi bật</span> : null}
          </div>

          <div className="project-detail-layout">
            <div className="project-detail-main">
              <section className="project-detail-copy" aria-labelledby="project-overview-title">
                <h2 id="project-overview-title">Tổng quan</h2>
                <p className={!project.description ? 'is-muted' : undefined}>{project.description || 'Dự án chưa có mô tả.'}</p>
              </section>

              <section className="project-detail-copy" aria-labelledby="project-goal-title">
                <h2 id="project-goal-title">Mục tiêu</h2>
                <p className={!project.goal ? 'is-muted' : undefined}>{project.goal || 'Dự án chưa cập nhật mục tiêu.'}</p>
              </section>

              <section className="project-detail-copy" aria-labelledby="project-fields-title">
                <h2 id="project-fields-title">Lĩnh vực nghiên cứu</h2>
                {project.researchFields && project.researchFields.length > 0 ? (
                  <div className="project-detail-field-list">
                    {project.researchFields.map((field) => <span className="chip" key={field.id}>{field.name}</span>)}
                  </div>
                ) : <p className="is-muted">Dự án chưa cập nhật lĩnh vực nghiên cứu.</p>}
              </section>
            </div>

            <aside className="project-detail-leaders" aria-labelledby="project-leaders-title">
              <h2 id="project-leaders-title"><UsersRound size={18} aria-hidden="true" /> Nhóm leader</h2>
              {leaders.length > 0 ? (
                <ul className="project-detail-leader-list">
                  {leaders.map((leader, index) => (
                    <li key={`${leader.name}-${index}`}>
                      <span className="project-detail-avatar" aria-hidden="true">{initialsOf(leader.name)}</span>
                      <span>
                        <strong>{leader.name}</strong>
                        {leader.isPrimary ? <small>Leader chính</small> : null}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : <p className="project-detail-empty">Chưa phân công leader.</p>}
            </aside>

            <section className="project-detail-facts-section" aria-labelledby="project-facts-title">
              <h2 id="project-facts-title"><CalendarDays size={19} aria-hidden="true" /> Thông tin dự án</h2>
              <dl className="project-detail-facts">
                {facts.map((fact) => (
                  <div key={fact.label} className="project-detail-fact">
                    <dt>{fact.label}</dt>
                    <dd>{fact.value}</dd>
                  </div>
                ))}
              </dl>
            </section>
          </div>
        </div>
      </section>

      {token ? (
        <section className="section project-detail-internal-section" aria-label="Công cụ nội bộ của dự án">
          <div className="wrap project-detail-wrap">
            {isAdminProjectManager || hasActiveMembership ? <ProjectWorkspaceSection project={project} /> : null}
            <ProjectJoinRequestCard project={project} showWorkspaceActions={!(isAdminProjectManager || hasActiveMembership)} />
          </div>
        </section>
      ) : null}
    </>
  )
}

function ProjectWorkspaceSection({ project }: { project: Pick<PublicProjectDetail, 'id'> }) {
  return (
    <section className="project-detail-workspace" aria-labelledby="project-workspace-title">
      <div className="project-detail-workspace-copy">
        <div className="kicker">Không gian nội bộ</div>
        <h2 id="project-workspace-title">Tiếp tục làm việc với dự án</h2>
        <p>Quản lý thành viên, tài liệu và hoạt động nội bộ của dự án trong workspace.</p>
      </div>
      <div className="project-detail-workspace-actions">
        <Link className="btn primary" to="/admin/projects">
          <FolderKanban aria-hidden="true" /> Mở quản lý dự án
        </Link>
        <Link className="btn" to={`/admin/events?projectId=${project.id}`}>
          <CalendarDays aria-hidden="true" /> Xem sự kiện dự án
        </Link>
      </div>
    </section>
  )
}

function formatDate(value: string | null) {
  if (!value) return 'Chưa cập nhật'
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(date)
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

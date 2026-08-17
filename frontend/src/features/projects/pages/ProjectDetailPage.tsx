import { CalendarDays, FolderKanban, UsersRound } from 'lucide-react'
import { Fragment, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import { PublicPageHead } from '../../public/components/PublicPageHead'
import { getProject } from '../api'
import { ProjectJoinRequestCard } from '../components/ProjectJoinRequestCard'
import {
  PROJECT_STATUS_BADGES,
  PROJECT_STATUS_LABELS,
  PROJECT_TYPE_LABELS,
} from '../types'
import type { Project } from '../types'

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { token } = useAuth()
  const [project, setProject] = useState<Project | null>(null)
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
    getProject(projectId, token)
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
  }, [id, reloadKey, token])

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
    { label: 'Phạm vi', value: project.isPublic ? 'Công khai' : 'Nội bộ' },
    { label: 'Nổi bật', value: project.isFeatured ? 'Có' : 'Không' },
  ]

  return (
    <>
      <PublicPageHead title={project.name} description={project.description || 'Thông tin dự án tại Smart Lab.'} />

      <section className="section">
        <div className="wrap">
          <Link className="muted-link" to="/du-an">← Quay lại danh sách dự án</Link>

          <div className="layout-side" style={{ marginTop: 24 }}>
            <div className="prose">
              <div className="row gap-6 wrapf">
                <span className={`badge ${PROJECT_STATUS_BADGES[project.status]}`}>
                  <span className="dot" />
                  {PROJECT_STATUS_LABELS[project.status]}
                </span>
                <span className="chip accent">{PROJECT_TYPE_LABELS[project.projectType]}</span>
                {project.isFeatured ? <span className="chip">Dự án nổi bật</span> : null}
              </div>

              <h2>Tổng quan</h2>
              <p>{project.description || 'Dự án chưa có mô tả.'}</p>

              <h3>Mục tiêu</h3>
              <p>{project.goal || 'Dự án chưa cập nhật mục tiêu.'}</p>

              <div className="card pad">
                <h3><CalendarDays size={19} /> Thông tin dự án</h3>
                <dl className="deflist">
                  {facts.map((fact) => (
                    <Fragment key={fact.label}>
                      <dt>{fact.label}</dt>
                      <dd>{fact.value}</dd>
                    </Fragment>
                  ))}
                </dl>
              </div>
            </div>

            <aside>
              <div className="side-box sticky">
                <h4><UsersRound size={18} /> Nhóm leader</h4>
                {project.primaryLeader ? (
                  <p>
                    <span className="muted small">Leader chính</span><br />
                    <strong>{project.primaryLeader.name}</strong>
                  </p>
                ) : <p className="muted">Chưa chọn leader chính.</p>}

                <div className="form-stack mt-20">
                  {project.leaders.map((leader) => (
                    <div className="row gap-6" key={leader.userId}>
                      <span className="ava xs">{initialsOf(leader.name)}</span>
                      <span>{leader.name}</span>
                    </div>
                  ))}
                  {project.leaders.length === 0 ? <span className="muted small">Chưa phân công leader.</span> : null}
                </div>
              </div>
            </aside>
          </div>
        </div>
      </section>

      {token ? <ProjectWorkspaceSection project={project} /> : null}
    </>
  )
}

function ProjectWorkspaceSection({ project }: { project: Project }) {
  return (
    <section className="section alt" aria-label="Công cụ nội bộ của dự án">
      <div className="wrap">
        <div className="sec-head">
          <div className="kicker">Không gian nội bộ</div>
          <h2>Tiếp tục làm việc với dự án</h2>
          <p>Thành viên, lĩnh vực nghiên cứu, tài liệu, phiên bản và sự kiện đã được quản lý trong khu vực đăng nhập.</p>
        </div>
        <div className="row gap-6 wrapf">
          <Link className="btn primary" to="/admin/projects">
            <FolderKanban aria-hidden="true" /> Mở quản lý dự án
          </Link>
          <Link className="btn" to={`/admin/events?projectId=${project.id}`}>
            <CalendarDays aria-hidden="true" /> Xem sự kiện dự án
          </Link>
        </div>
        <ProjectJoinRequestCard project={project} />
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

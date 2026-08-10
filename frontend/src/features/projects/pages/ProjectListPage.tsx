import { Search, UsersRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import { PublicPageHead } from '../../public/components/PublicPageHead'
import { listProjects } from '../api'
import {
  PROJECT_STATUSES,
  PROJECT_STATUS_BADGES,
  PROJECT_STATUS_LABELS,
  PROJECT_TYPES,
  PROJECT_TYPE_LABELS,
} from '../types'
import type { Project, ProjectStatus, ProjectType } from '../types'

type TypeFilter = ProjectType | 'ALL'
type StatusFilter = ProjectStatus | 'ALL'

export function ProjectListPage() {
  const { token } = useAuth()
  const [projects, setProjects] = useState<Project[]>([])
  const [query, setQuery] = useState('')
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)

    listProjects(token)
      .then((result) => {
        if (active) setProjects(result)
      })
      .catch((reason: unknown) => {
        if (active) {
          setError(reason instanceof Error ? reason.message : 'Không tải được danh sách dự án.')
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [reloadKey, token])

  const visibleProjects = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('vi')

    return projects.filter((project) => {
      if (typeFilter !== 'ALL' && project.projectType !== typeFilter) return false
      if (statusFilter !== 'ALL' && project.status !== statusFilter) return false
      if (!normalizedQuery) return true

      const searchableText = [
        project.code,
        project.name,
        project.description ?? '',
        project.goal ?? '',
        ...project.leaders.map((leader) => leader.name),
      ]
        .join(' ')
        .toLocaleLowerCase('vi')

      return searchableText.includes(normalizedQuery)
    })
  }, [projects, query, statusFilter, typeFilter])

  const hasActiveFilters = Boolean(query.trim()) || typeFilter !== 'ALL' || statusFilter !== 'ALL'

  return (
    <>
      <PublicPageHead
        title="Dự án"
        description="Các dự án nghiên cứu và sản phẩm đang được phát triển tại Smart Lab."
      />

      <section className="section">
        <div className="wrap">
          <div className="toolbar">
            <div className="searchbar" style={{ flex: 1, minWidth: 240 }}>
              <Search aria-hidden="true" />
              <input
                className="input"
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Tìm theo mã, tên, mô tả hoặc leader..."
                aria-label="Tìm kiếm dự án"
              />
            </div>

            <select
              className="select"
              value={typeFilter}
              onChange={(event) => setTypeFilter(event.target.value as TypeFilter)}
              aria-label="Lọc theo loại dự án"
            >
              <option value="ALL">Tất cả loại dự án</option>
              {PROJECT_TYPES.map((type) => (
                <option value={type} key={type}>{PROJECT_TYPE_LABELS[type]}</option>
              ))}
            </select>

            <select
              className="select"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
              aria-label="Lọc theo trạng thái dự án"
            >
              <option value="ALL">Tất cả trạng thái</option>
              {PROJECT_STATUSES.map((status) => (
                <option value={status} key={status}>{PROJECT_STATUS_LABELS[status]}</option>
              ))}
            </select>

            {hasActiveFilters ? (
              <button
                className="btn ghost"
                type="button"
                onClick={() => {
                  setQuery('')
                  setTypeFilter('ALL')
                  setStatusFilter('ALL')
                }}
              >
                Xóa bộ lọc
              </button>
            ) : null}
          </div>

          {error ? (
            <>
              <Feedback error={error} />
              <button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}>
                Thử tải lại
              </button>
            </>
          ) : null}

          {loading ? (
            <div className="public-empty empty tight">Đang tải danh sách dự án...</div>
          ) : null}

          {!loading && !error && visibleProjects.length > 0 ? (
            <>
              <p className="muted small">Hiển thị {visibleProjects.length} / {projects.length} dự án.</p>
              <div className="grid c3">
                {visibleProjects.map((project) => <ProjectCard project={project} key={project.id} />)}
              </div>
            </>
          ) : null}

          {!loading && !error && visibleProjects.length === 0 ? (
            <EmptyState
              title={projects.length === 0 ? 'Chưa có dự án bạn có thể xem' : 'Không tìm thấy dự án'}
              description={
                projects.length === 0
                  ? 'Dự án công khai và dự án nội bộ bạn được phép xem sẽ xuất hiện tại đây.'
                  : 'Hãy thay đổi từ khóa hoặc bộ lọc để xem kết quả khác.'
              }
            />
          ) : null}
        </div>
      </section>
    </>
  )
}

function ProjectCard({ project }: { project: Project }) {
  const shownLeaders = project.leaders.slice(0, 3)
  const remainingLeaderCount = project.leaders.length - shownLeaders.length

  return (
    <Link className="card hover projcard" to={`/du-an/${project.id}`}>
      <div className="body">
        <div className="row gap-6 wrapf">
          <span className={`lbl badge ${PROJECT_STATUS_BADGES[project.status]}`}>
            <span className="dot" />
            {PROJECT_STATUS_LABELS[project.status]}
          </span>
          <span className="chip accent">{PROJECT_TYPE_LABELS[project.projectType]}</span>
          {!project.isPublic ? <span className="chip">Nội bộ</span> : null}
          {project.isFeatured ? <span className="chip">Nổi bật</span> : null}
        </div>

        <div>
          <span className="muted small">{project.code}</span>
          <h3>{project.name}</h3>
        </div>
        <p>{project.description || 'Dự án chưa có mô tả.'}</p>

        <div className="foot">
          {shownLeaders.length > 0 ? (
            <span className="ava-stack" aria-label={`${project.leaders.length} leader`}>
              {shownLeaders.map((leader) => (
                <span className="ava xs" title={leader.name} key={leader.userId}>
                  {initialsOf(leader.name)}
                </span>
              ))}
              {remainingLeaderCount > 0 ? <span className="ava xs">+{remainingLeaderCount}</span> : null}
            </span>
          ) : (
            <span className="muted small"><UsersRound size={14} /> Chưa phân công leader</span>
          )}
          <span className="muted-link">Xem chi tiết</span>
        </div>
      </div>
    </Link>
  )
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

import { CalendarDays } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import { listProjects } from '../../projects/api'
import type { Project } from '../../projects/types'
import { EventManagementPanel } from '../components/EventManagementPanel'

export function EventManagementPage() {
  const { token } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const [projects, setProjects] = useState<Project[]>([])
  const [loadingProjects, setLoadingProjects] = useState(true)
  const [projectsError, setProjectsError] = useState('')

  const rawProjectId = searchParams.get('projectId')
  const requestedProjectId = parseProjectId(rawProjectId)
  const selectedProject = useMemo(
    () => projects.find((project) => project.id === requestedProjectId) ?? null,
    [projects, requestedProjectId],
  )

  useEffect(() => {
    let active = true
    setLoadingProjects(true)
    setProjectsError('')
    void listProjects(token)
      .then((result) => {
        if (active) setProjects(result)
      })
      .catch((reason: unknown) => {
        if (active) {
          setProjectsError(reason instanceof Error ? reason.message : 'Không tải được danh sách dự án.')
        }
      })
      .finally(() => {
        if (active) setLoadingProjects(false)
      })
    return () => {
      active = false
    }
  }, [token])

  function selectScope(value: string) {
    const next = new URLSearchParams(searchParams)
    if (value === 'LAB') next.delete('projectId')
    else next.set('projectId', value)
    setSearchParams(next, { replace: true })
  }

  const invalidRequestedProject = rawProjectId !== null && requestedProjectId === null
  const missingRequestedProject = !loadingProjects && (
    invalidRequestedProject
    || (requestedProjectId !== null && selectedProject === null)
  )

  return (
    <>
      <div className="page-title">
        <div>
          <span className="eyebrow">Lịch hoạt động</span>
          <h1>Quản lý sự kiện</h1>
          <p>Quản lý tập trung sự kiện toàn Lab và sự kiện thuộc từng dự án.</p>
        </div>
        <CalendarDays size={28} />
      </div>

      <section className="panel page-section">
        <div className="panel-head">
          <div>
            <h2>Phạm vi làm việc</h2>
            <p>Chọn toàn Lab hoặc một dự án bạn có quyền xem.</p>
          </div>
        </div>

        <Feedback
          error={projectsError || (missingRequestedProject
            ? 'Dự án được yêu cầu không hợp lệ, không tồn tại hoặc bạn không có quyền xem.'
            : '')}
        />

        <label className="field" style={{ maxWidth: 560 }}>
          <span>Phạm vi sự kiện</span>
          <select
            className="select"
            value={missingRequestedProject ? '' : selectedProject ? String(selectedProject.id) : 'LAB'}
            disabled={loadingProjects}
            onChange={(event) => selectScope(event.target.value)}
          >
            {missingRequestedProject ? <option value="" disabled>Chọn lại phạm vi</option> : null}
            <option value="LAB">Sự kiện cấp Lab</option>
            {projects.map((project) => (
              <option value={project.id} key={project.id}>
                {project.code} · {project.name}
              </option>
            ))}
          </select>
          <small>
            {loadingProjects
              ? 'Đang tải dự án...'
              : `${projects.length} dự án bạn có thể truy cập.`}
          </small>
        </label>
      </section>

      {!loadingProjects && !missingRequestedProject
        ? <EventManagementPanel project={selectedProject} />
        : null}
    </>
  )
}

function parseProjectId(value: string | null) {
  if (!value) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

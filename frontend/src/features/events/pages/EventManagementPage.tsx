import { CalendarDays } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Feedback } from '../../../shared/components/Feedback'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
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
      <div className="page-title event-page-title">
        <div>
          <span className="eyebrow">Lịch hoạt động</span>
          <h1>Quản lý sự kiện</h1>
          <p>Quản lý tập trung sự kiện toàn Lab và sự kiện thuộc từng dự án.</p>
        </div>
        <CalendarDays className="event-page-title-icon" size={24} aria-hidden="true" />
      </div>

      <section className="panel page-section event-scope-panel" aria-labelledby="event-scope-heading">
        <div className="event-scope-copy">
          <h2 id="event-scope-heading">Phạm vi sự kiện</h2>
          <p>Chọn toàn Lab hoặc một dự án bạn có quyền xem.</p>
        </div>

        <Feedback
          error={projectsError || (missingRequestedProject
            ? 'Dự án được yêu cầu không hợp lệ, không tồn tại hoặc bạn không có quyền xem.'
            : '')}
        />

        <div className="field event-scope-select">
          <PopupSelect
            value={missingRequestedProject ? '' : selectedProject ? String(selectedProject.id) : 'LAB'}
            disabled={loadingProjects}
            ariaLabel="Phạm vi sự kiện"
            onChange={selectScope}
            options={[
              ...(missingRequestedProject ? [{ value: '', label: 'Chọn lại phạm vi' }] : []),
              { value: 'LAB', label: 'Sự kiện cấp Lab' },
              ...projects.map((project) => ({ value: String(project.id), label: `${project.code} · ${project.name}` })),
            ]}
          />
          <small>
            {loadingProjects
              ? 'Đang tải dự án...'
              : `${projects.length} dự án bạn có thể truy cập.`}
          </small>
        </div>
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

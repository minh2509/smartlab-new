import {
  FolderKanban,
  Plus,
  RefreshCw,
  Save,
  Trash2,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '../../auth/authContext'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import {
  createProject,
  deleteProject,
  listProjects,
  updateProject,
} from '../api'
import { ProjectLeaderPicker } from '../components/ProjectLeaderPicker'
import { ProjectLeadershipEditor } from '../components/ProjectLeadershipEditor'
import {
  PROJECT_STATUSES,
  PROJECT_STATUS_BADGES,
  PROJECT_STATUS_LABELS,
  PROJECT_TYPES,
  PROJECT_TYPE_LABELS,
} from '../types'
import type {
  CreateProjectPayload,
  Project,
  ProjectLeaderCandidate,
  ProjectStatus,
  ProjectType,
  UpdateProjectPayload,
} from '../types'

type ProjectCoreForm = {
  code: string
  name: string
  description: string
  goal: string
  projectType: ProjectType
  status: ProjectStatus
  startDate: string
  expectedEndDate: string
  actualEndDate: string
  isPublic: boolean
  isFeatured: boolean
}

type CreateProjectForm = ProjectCoreForm

type BusyAction = 'create' | 'update' | 'leadership' | 'delete' | null

const emptyCreateForm: CreateProjectForm = {
  code: '',
  name: '',
  description: '',
  goal: '',
  projectType: 'RESEARCH',
  status: 'PROPOSED',
  startDate: '',
  expectedEndDate: '',
  actualEndDate: '',
  isPublic: false,
  isFeatured: false,
}

export function ProjectManagementPage() {
  const { token, profile } = useAuth()
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [createForm, setCreateForm] = useState<CreateProjectForm>(emptyCreateForm)
  const [createLeaders, setCreateLeaders] = useState<ProjectLeaderCandidate[]>([])
  const [createPrimaryLeaderUserId, setCreatePrimaryLeaderUserId] = useState<string | null>(null)
  const [createPickerVersion, setCreatePickerVersion] = useState(0)
  const [editForm, setEditForm] = useState<ProjectCoreForm | null>(null)
  const [loading, setLoading] = useState(true)
  const [busyAction, setBusyAction] = useState<BusyAction>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const isAdmin = Boolean(profile?.roles.includes('ADMIN'))
  const canManageProject = isAdmin && Boolean(profile?.permissions.includes('PROJECT_MANAGE'))
  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedId) ?? null,
    [projects, selectedId],
  )
  const canEditSelected = Boolean(
    selectedProject
      && profile
      && (isAdmin || selectedProject.leaders.some((leader) => leader.userId === profile.userId)),
  )
  const isBusy = busyAction !== null

  const loadProjects = useCallback(async () => {
    if (!token) return
    const result = await listProjects(token)
    setProjects(result)
    setSelectedId((current) => {
      if (current !== null && result.some((project) => project.id === current)) return current
      return result[0]?.id ?? null
    })
  }, [token])

  useEffect(() => {
    setLoading(true)
    setError('')
    void loadProjects()
      .catch((reason: unknown) => setError(errorMessage(reason, 'Không tải được danh sách dự án.')))
      .finally(() => setLoading(false))
  }, [loadProjects])

  useEffect(() => {
    if (!selectedProject) {
      setEditForm(null)
      return
    }
    setEditForm(toCoreForm(selectedProject))
  }, [selectedProject])

  function selectProject(projectId: number) {
    setSelectedId(projectId)
    clearFeedback()
  }

  function clearFeedback() {
    setMessage('')
    setError('')
  }

  function mergeProject(project: Project) {
    setProjects((current) => {
      const exists = current.some((item) => item.id === project.id)
      return exists
        ? current.map((item) => (item.id === project.id ? project : item))
        : [project, ...current]
    })
    setSelectedId(project.id)
  }

  async function refreshProjects() {
    setLoading(true)
    clearFeedback()
    try {
      await loadProjects()
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không tải lại được danh sách dự án.'))
    } finally {
      setLoading(false)
    }
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !canManageProject || isBusy) return

    const validationError = validateCoreForm(createForm)
    if (validationError) {
      setError(validationError)
      return
    }
    const selectedLeaderUserIds = createLeaders.map((leader) => leader.userId)
    if (createPrimaryLeaderUserId && !selectedLeaderUserIds.includes(createPrimaryLeaderUserId)) {
      setError('Leader chính phải nằm trong nhóm leader đã chọn.')
      return
    }
    const additionalLeaderUserIds = selectedLeaderUserIds.filter(
      (userId) => userId !== createPrimaryLeaderUserId,
    )

    const payload: CreateProjectPayload = {
      ...toCorePayload(createForm),
      code: createForm.code.trim(),
      name: createForm.name.trim(),
      leaderUserId: createPrimaryLeaderUserId ?? undefined,
      additionalLeaderUserIds: additionalLeaderUserIds.length ? additionalLeaderUserIds : undefined,
    }

    setBusyAction('create')
    clearFeedback()
    try {
      const created = await createProject(token, payload)
      mergeProject(created)
      setCreateForm(emptyCreateForm)
      setCreateLeaders([])
      setCreatePrimaryLeaderUserId(null)
      setCreatePickerVersion((current) => current + 1)
      setMessage(`Đã tạo dự án ${created.code}.`)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể tạo dự án.'))
    } finally {
      setBusyAction(null)
    }
  }

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !selectedProject || !editForm || !canEditSelected || isBusy) return

    const validationError = validateCoreForm(editForm, selectedProject)
    if (validationError) {
      setError(validationError)
      return
    }

    const payload: UpdateProjectPayload = toCorePayload(editForm)
    setBusyAction('update')
    clearFeedback()
    try {
      const updated = await updateProject(token, selectedProject.id, payload)
      mergeProject(updated)
      setMessage(`Đã cập nhật dự án ${updated.code}.`)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể cập nhật dự án.'))
    } finally {
      setBusyAction(null)
    }
  }

  async function handleDelete() {
    if (!token || !selectedProject || !canManageProject || isBusy) return
    if (!window.confirm(`Xóa mềm dự án “${selectedProject.name}” (${selectedProject.code})? Dự án sẽ không còn xuất hiện trong danh sách.`)) return

    setBusyAction('delete')
    clearFeedback()
    try {
      const deletedCode = selectedProject.code
      await deleteProject(token, selectedProject.id)
      await loadProjects()
      setMessage(`Đã xóa mềm dự án ${deletedCode}.`)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể xóa dự án.'))
    } finally {
      setBusyAction(null)
    }
  }

  return (
    <>
      <div className="page-title">
        <div>
          <span className="eyebrow">D3 · Project core</span>
          <h1>Quản lý dự án</h1>
          <p>CRUD thông tin dự án và phân công nhóm leader theo tên hoặc email.</p>
        </div>
        <button className="btn" type="button" onClick={() => void refreshProjects()} disabled={loading || isBusy}>
          <RefreshCw />
          {loading ? 'Đang tải...' : 'Tải lại'}
        </button>
      </div>

      <Feedback message={message} error={error} />

      {loading ? (
        <div className="empty">Đang tải dự án...</div>
      ) : (
        <div className="panel-grid">
          <section className="panel">
            <div className="panel-head">
              <div>
                <h2>Danh sách dự án</h2>
                <p>{projects.length} dự án bạn có quyền xem.</p>
              </div>
              <FolderKanban size={20} />
            </div>
            {projects.length ? (
              <div className="field-admin-list">
                {projects.map((project) => (
                  <button
                    className={`member-select-row ${selectedId === project.id ? 'selected' : ''}`}
                    type="button"
                    key={project.id}
                    onClick={() => selectProject(project.id)}
                  >
                    <span className="member-avatar">{project.code.slice(0, 2).toUpperCase()}</span>
                    <span>
                      <strong>{project.name}</strong>
                      <small>{project.code} · {PROJECT_TYPE_LABELS[project.projectType]}</small>
                    </span>
                    <span className={`badge ${PROJECT_STATUS_BADGES[project.status]}`}>
                      {PROJECT_STATUS_LABELS[project.status]}
                    </span>
                  </button>
                ))}
              </div>
            ) : (
              <EmptyState title="Chưa có dự án" description="Admin có thể tạo dự án đầu tiên bằng biểu mẫu bên cạnh." />
            )}
          </section>

          {canManageProject && token ? (
            <form className="panel" onSubmit={handleCreate} noValidate>
              <div className="panel-head">
                <div>
                  <h2>Tạo dự án</h2>
                  <p>Chỉ code và tên là bắt buộc; có thể tìm và phân công leader ngay bên dưới.</p>
                </div>
                <Plus size={20} />
              </div>
              <ProjectCoreFields form={createForm} onChange={setCreateForm} />
              <div style={{ marginTop: 18 }}>
                <h3 style={{ marginBottom: 6 }}>Nhóm leader ban đầu</h3>
                <p className="muted small" style={{ marginBottom: 16 }}>
                  Bước này không bắt buộc và có thể thay đổi sau khi tạo dự án.
                </p>
                <ProjectLeaderPicker
                  idPrefix={`create-project-leadership-${createPickerVersion}`}
                  token={token}
                  leaders={createLeaders}
                  primaryLeaderUserId={createPrimaryLeaderUserId}
                  disabled={isBusy}
                  onLeadersChange={setCreateLeaders}
                  onPrimaryLeaderChange={setCreatePrimaryLeaderUserId}
                />
              </div>
              <div className="form-actions" style={{ marginTop: 16 }}>
                <button className="btn primary" type="submit" disabled={isBusy}>
                  <Plus />
                  {busyAction === 'create' ? 'Đang tạo...' : 'Tạo dự án'}
                </button>
              </div>
            </form>
          ) : (
            <section className="panel">
              <EmptyState
                title="Không có quyền tạo dự án"
                description="Tạo, xóa và phân công leader cần role ADMIN cùng permission PROJECT_MANAGE."
              />
            </section>
          )}
        </div>
      )}

      {!loading && selectedProject ? (
        <>
          <section className="panel page-section">
            <div className="panel-head">
              <div>
                <h2>{selectedProject.name}</h2>
                <p>
                  {selectedProject.code} · {PROJECT_TYPE_LABELS[selectedProject.projectType]} ·{' '}
                  {PROJECT_STATUS_LABELS[selectedProject.status]}
                </p>
              </div>
              <span className={`badge ${selectedProject.isPublic ? 'success' : 'info'}`}>
                {selectedProject.isPublic ? 'Công khai' : 'Nội bộ'}
              </span>
            </div>
            <div className="form-stack">
              <p>{selectedProject.description || 'Chưa có mô tả.'}</p>
              <p><strong>Mục tiêu:</strong> {selectedProject.goal || 'Chưa cập nhật.'}</p>
              <p><strong>Thời gian:</strong> {formatProjectDates(selectedProject)}</p>
              <p>
                <strong>Leader chính:</strong>{' '}
                {selectedProject.primaryLeader
                  ? selectedProject.primaryLeader.name
                  : 'Chưa chọn'}
              </p>
              <div>
                <strong>Toàn bộ leader ({selectedProject.leaders.length}):</strong>
                <div className="inline-badges">
                  {selectedProject.leaders.map((leader) => (
                    <span className="badge info" key={leader.userId}>{leader.name}</span>
                  ))}
                  {!selectedProject.leaders.length ? <span> Chưa có leader.</span> : null}
                </div>
              </div>
            </div>
          </section>

          {canEditSelected && editForm ? (
            <form className="panel page-section" onSubmit={handleUpdate} noValidate>
              <div className="panel-head">
                <div>
                  <h2>Cập nhật thông tin cốt lõi</h2>
                  <p>Admin hoặc leader đang hoạt động của dự án có thể chỉnh sửa.</p>
                </div>
                <Save size={20} />
              </div>
              <ProjectCoreFields form={editForm} onChange={setEditForm} />
              <button className="btn primary" type="submit" disabled={isBusy}>
                <Save />
                {busyAction === 'update' ? 'Đang lưu...' : 'Lưu thông tin dự án'}
              </button>
            </form>
          ) : (
            <section className="page-section">
              <EmptyState
                title="Chỉ xem thông tin"
                description="Chỉ Admin hoặc leader được liệt kê trong dự án mới có thể sửa phần cốt lõi."
              />
            </section>
          )}

          {canManageProject && token ? (
            <ProjectLeadershipEditor
              project={selectedProject}
              token={token}
              disabled={isBusy}
              onClearFeedback={clearFeedback}
              onError={setError}
              onSavingChange={(saving) => setBusyAction(saving ? 'leadership' : null)}
              onSaved={(updated) => {
                mergeProject(updated)
                setMessage(`Đã cập nhật nhóm leader của ${updated.code}.`)
              }}
            />
          ) : null}

          {canManageProject ? (
            <section className="panel page-section">
              <div className="panel-head">
                <div>
                  <h2>Xóa mềm dự án</h2>
                  <p>Không xóa dữ liệu vật lý; dự án sẽ bị ẩn khỏi các API đang hoạt động.</p>
                </div>
                <Trash2 size={20} />
              </div>
              <button className="btn danger-text" type="button" disabled={isBusy} onClick={() => void handleDelete()}>
                <Trash2 />
                {busyAction === 'delete' ? 'Đang xóa...' : `Xóa mềm ${selectedProject.code}`}
              </button>
            </section>
          ) : null}
        </>
      ) : null}

      <section className="panel page-section">
        <div className="panel-head">
          <div>
            <h2>Phạm vi đợt 1</h2>
            <p>Trang này chỉ gọi Project core API. Thành viên, lĩnh vực nghiên cứu, tài liệu/phiên bản và sự kiện được giữ thành phân hệ riêng cho các đợt sau.</p>
          </div>
        </div>
      </section>
    </>
  )
}

function ProjectCoreFields<T extends ProjectCoreForm>({
  form,
  onChange,
}: {
  form: T
  onChange: (next: T) => void
}) {
  function patch(next: Partial<ProjectCoreForm>) {
    onChange({ ...form, ...next })
  }

  return (
    <div className="form-stack">
      <label className="field">
        <span>Code</span>
        <input className="input" required maxLength={60} value={form.code} onChange={(event) => patch({ code: event.target.value })} placeholder="SL-AI-2026" />
      </label>
      <label className="field">
        <span>Tên dự án</span>
        <input className="input" required maxLength={200} value={form.name} onChange={(event) => patch({ name: event.target.value })} />
      </label>
      <label className="field">
        <span>Mô tả</span>
        <textarea className="textarea" rows={4} value={form.description} onChange={(event) => patch({ description: event.target.value })} />
      </label>
      <label className="field">
        <span>Mục tiêu</span>
        <textarea className="textarea" rows={3} value={form.goal} onChange={(event) => patch({ goal: event.target.value })} />
      </label>
      <label className="field">
        <span>Loại dự án</span>
        <select className="select" value={form.projectType} onChange={(event) => patch({ projectType: event.target.value as ProjectType })}>
          {PROJECT_TYPES.map((type) => <option key={type} value={type}>{PROJECT_TYPE_LABELS[type]}</option>)}
        </select>
      </label>
      <label className="field">
        <span>Trạng thái</span>
        <select className="select" value={form.status} onChange={(event) => patch({ status: event.target.value as ProjectStatus })}>
          {PROJECT_STATUSES.map((status) => <option key={status} value={status}>{PROJECT_STATUS_LABELS[status]}</option>)}
        </select>
      </label>
      <label className="field">
        <span>Ngày bắt đầu</span>
        <input className="input" type="date" value={form.startDate} onChange={(event) => patch({ startDate: event.target.value })} />
      </label>
      <label className="field">
        <span>Ngày kết thúc dự kiến</span>
        <input className="input" type="date" value={form.expectedEndDate} onChange={(event) => patch({ expectedEndDate: event.target.value })} />
      </label>
      <label className="field">
        <span>Ngày kết thúc thực tế</span>
        <input className="input" type="date" value={form.actualEndDate} onChange={(event) => patch({ actualEndDate: event.target.value })} />
      </label>
      <label className={`field-option ${form.isPublic ? 'selected' : ''}`}>
        <input type="checkbox" checked={form.isPublic} onChange={(event) => patch({ isPublic: event.target.checked })} />
        <span><strong>Công khai</strong><small>Khách không đăng nhập có thể xem dự án.</small></span>
      </label>
      <label className={`field-option ${form.isFeatured ? 'selected' : ''}`}>
        <input type="checkbox" checked={form.isFeatured} onChange={(event) => patch({ isFeatured: event.target.checked })} />
        <span><strong>Nổi bật</strong><small>Đánh dấu dự án để giao diện công khai ưu tiên hiển thị sau này.</small></span>
      </label>
    </div>
  )
}

function toCoreForm(project: Project): ProjectCoreForm {
  return {
    code: project.code,
    name: project.name,
    description: project.description ?? '',
    goal: project.goal ?? '',
    projectType: project.projectType,
    status: project.status,
    startDate: project.startDate ?? '',
    expectedEndDate: project.expectedEndDate ?? '',
    actualEndDate: project.actualEndDate ?? '',
    isPublic: project.isPublic,
    isFeatured: project.isFeatured,
  }
}

function toCorePayload(form: ProjectCoreForm): UpdateProjectPayload {
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description,
    goal: form.goal,
    projectType: form.projectType,
    status: form.status,
    startDate: form.startDate || undefined,
    expectedEndDate: form.expectedEndDate || undefined,
    actualEndDate: form.actualEndDate || undefined,
    isPublic: form.isPublic,
    isFeatured: form.isFeatured,
  }
}

function validateCoreForm(form: ProjectCoreForm, original?: Project): string | null {
  const code = form.code.trim()
  const name = form.name.trim()
  if (!code) return 'Code dự án là bắt buộc.'
  if (code.length > 60) return 'Code dự án không được vượt quá 60 ký tự.'
  if (!name) return 'Tên dự án là bắt buộc.'
  if (name.length > 200) return 'Tên dự án không được vượt quá 200 ký tự.'

  const dates: Array<[string, string]> = [
    ['Ngày bắt đầu', form.startDate],
    ['Ngày kết thúc dự kiến', form.expectedEndDate],
    ['Ngày kết thúc thực tế', form.actualEndDate],
  ]
  for (const [label, value] of dates) {
    if (value && !isValidIsoDate(value)) return `${label} không hợp lệ.`
  }
  if (form.startDate && form.expectedEndDate && form.expectedEndDate < form.startDate) {
    return 'Ngày kết thúc dự kiến không được trước ngày bắt đầu.'
  }
  if (form.startDate && form.actualEndDate && form.actualEndDate < form.startDate) {
    return 'Ngày kết thúc thực tế không được trước ngày bắt đầu.'
  }
  if (original) {
    if (original.startDate && !form.startDate) return 'Backend hiện chưa hỗ trợ xóa ngày bắt đầu; hãy giữ ngày cũ.'
    if (original.expectedEndDate && !form.expectedEndDate) return 'Backend hiện chưa hỗ trợ xóa ngày kết thúc dự kiến; hãy giữ ngày cũ.'
    if (original.actualEndDate && !form.actualEndDate) return 'Backend hiện chưa hỗ trợ xóa ngày kết thúc thực tế; hãy giữ ngày cũ.'
  }
  return null
}

function isValidIsoDate(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

function formatProjectDates(project: Project) {
  if (!project.startDate && !project.expectedEndDate && !project.actualEndDate) return 'Chưa cập nhật.'
  return [
    project.startDate ? `bắt đầu ${project.startDate}` : null,
    project.expectedEndDate ? `dự kiến ${project.expectedEndDate}` : null,
    project.actualEndDate ? `thực tế ${project.actualEndDate}` : null,
  ].filter(Boolean).join(' · ')
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}

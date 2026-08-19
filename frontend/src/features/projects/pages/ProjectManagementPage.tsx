import {
  CalendarRange,
  ChevronLeft,
  ChevronRight,
  Eye,
  FileText,
  FlaskConical,
  FolderKanban,
  History,
  LayoutDashboard,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Search,
  Trash2,
  UsersRound,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/authContext'
import { ProjectDocumentsPanel } from '../../documents/components/ProjectDocumentsPanel'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'
import { OverlayPortalHost } from '../../../shared/ui/OverlayPortalHost'
import {
  createProject,
  deleteProject,
  listMyProjectMemberships,
  listProjects,
  updateProject,
} from '../api'
import { ProjectLeadershipEditor } from '../components/ProjectLeadershipEditor'
import { ProjectMembersPanel } from '../components/ProjectMembersPanel'
import { ProjectResearchFieldsPanel } from '../components/ProjectResearchFieldsPanel'
import './ProjectManagementPage.css'
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
  ProjectMembershipHistory,
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
type ProjectTab = 'overview' | 'leaders' | 'members' | 'research-fields' | 'documents'
type ProjectTypeFilter = ProjectType | 'ALL'
type ProjectStatusFilter = ProjectStatus | 'ALL'
type ProjectSort = 'NEWEST' | 'NAME_ASC' | 'CODE_ASC'

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

const PROJECT_TABS: ProjectTab[] = ['overview', 'leaders', 'members', 'research-fields', 'documents']
const PROJECT_PAGE_SIZE = 10

export function ProjectManagementPage() {
  const { token, profile } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [projects, setProjects] = useState<Project[]>([])
  const [membershipHistory, setMembershipHistory] = useState<ProjectMembershipHistory[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [projectQuery, setProjectQuery] = useState('')
  const [projectTypeFilter, setProjectTypeFilter] = useState<ProjectTypeFilter>('ALL')
  const [projectStatusFilter, setProjectStatusFilter] = useState<ProjectStatusFilter>('ALL')
  const [projectSort, setProjectSort] = useState<ProjectSort>('NEWEST')
  const [projectPage, setProjectPage] = useState(0)
  const [createForm, setCreateForm] = useState<CreateProjectForm>(emptyCreateForm)
  const [editForm, setEditForm] = useState<ProjectCoreForm | null>(null)
  const [activeTab, setActiveTab] = useState<ProjectTab>('overview')
  const [isCreating, setIsCreating] = useState(false)
  const [isEditingCore, setIsEditingCore] = useState(false)
  const [leadershipDirty, setLeadershipDirty] = useState(false)
  const [researchFieldsDirty, setResearchFieldsDirty] = useState(false)
  const [documentsDirty, setDocumentsDirty] = useState(false)
  const [documentsBusy, setDocumentsBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busyAction, setBusyAction] = useState<BusyAction>(null)
  const [error, setError] = useState('')
  const createDialogRef = useRef<HTMLDialogElement>(null)
  const createTriggerRef = useRef<HTMLButtonElement>(null)
  const projectDialogRef = useRef<HTMLDialogElement>(null)
  const projectDialogTriggerRef = useRef<HTMLButtonElement | null>(null)

  const isAdmin = Boolean(profile?.roles.includes('ADMIN'))
  const hasProjectManage = Boolean(profile?.permissions.includes('PROJECT_MANAGE'))
  const canAdminManageProject = isAdmin && hasProjectManage
  const canCreateProject = hasProjectManage && Boolean(
    isAdmin || profile?.roles.includes('LEADER'),
  )
  const requestedProjectId = positiveInteger(searchParams.get('projectId'))
  const requestedProjectTab = projectTab(searchParams.get('tab'))
  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedId) ?? null,
    [projects, selectedId],
  )
  const filteredProjects = useMemo(() => {
    const query = projectQuery.trim().toLocaleLowerCase('vi')
    const result = projects.filter((project) => {
      if (projectTypeFilter !== 'ALL' && project.projectType !== projectTypeFilter) return false
      if (projectStatusFilter !== 'ALL' && project.status !== projectStatusFilter) return false
      if (!query) return true
      return project.name.toLocaleLowerCase('vi').includes(query)
        || project.code.toLocaleLowerCase('vi').includes(query)
    })

    return [...result].sort((left, right) => {
      if (projectSort === 'NAME_ASC') {
        return left.name.localeCompare(right.name, 'vi')
          || left.code.localeCompare(right.code, 'vi')
          || left.id - right.id
      }
      if (projectSort === 'CODE_ASC') {
        return left.code.localeCompare(right.code, 'vi') || left.id - right.id
      }
      return projectCreatedAt(right) - projectCreatedAt(left) || right.id - left.id
    })
  }, [projectQuery, projectSort, projectStatusFilter, projectTypeFilter, projects])
  const projectPageCount = Math.max(1, Math.ceil(filteredProjects.length / PROJECT_PAGE_SIZE))
  const pagedProjects = useMemo(
    () => filteredProjects.slice(
      projectPage * PROJECT_PAGE_SIZE,
      (projectPage + 1) * PROJECT_PAGE_SIZE,
    ),
    [filteredProjects, projectPage],
  )
  const hasProjectFilters = Boolean(
    projectQuery.trim()
      || projectTypeFilter !== 'ALL'
      || projectStatusFilter !== 'ALL'
      || projectSort !== 'NEWEST',
  )
  const canEditProject = (project: Project) => Boolean(
    profile && (isAdmin || project.leaders.some((leader) => leader.userId === profile.userId)),
  )
  const membershipByProjectId = useMemo(
    () => new Map(
      membershipHistory
        .filter((membership) => membership.status === 'ACTIVE')
        .map((membership) => [membership.projectId, membership]),
    ),
    [membershipHistory],
  )
  const removedMemberships = useMemo(
    () => membershipHistory
      .filter((membership) => membership.status === 'REMOVED')
      .sort((left, right) => membershipTimestamp(right) - membershipTimestamp(left)),
    [membershipHistory],
  )
  const canEditSelected = Boolean(
    selectedProject && canEditProject(selectedProject),
  )
  const createDirty = useMemo(
    () => !sameCoreForm(createForm, emptyCreateForm),
    [createForm],
  )
  const editDirty = useMemo(
    () => Boolean(selectedProject && editForm && !sameCoreForm(editForm, toCoreForm(selectedProject))),
    [editForm, selectedProject],
  )
  const selectedProjectDirty = editDirty || leadershipDirty || researchFieldsDirty || documentsDirty
  const isBusy = busyAction !== null || documentsBusy

  const loadProjects = useCallback(async () => {
    if (!token) return
    const [visibleProjects, ownMemberships] = await Promise.all([
      listProjects(token),
      isAdmin ? Promise.resolve<ProjectMembershipHistory[]>([]) : listMyProjectMemberships(token),
    ])
    const result = isAdmin
      ? visibleProjects
      : visibleProjects.filter((project) => ownMemberships.some(
        (membership) => membership.projectId === project.id && membership.status === 'ACTIVE',
      ))
    setProjects(result)
    setMembershipHistory(ownMemberships)
    setSelectedId((current) => (
      current !== null && result.some((project) => project.id === current) ? current : null
    ))
  }, [isAdmin, token])

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
    if (!isEditingCore) setEditForm(toCoreForm(selectedProject))
  }, [isEditingCore, selectedProject])

  useEffect(() => {
    const dialog = createDialogRef.current
    if (isCreating && dialog && !dialog.open) dialog.showModal()
  }, [isCreating])

  useEffect(() => {
    const dialog = projectDialogRef.current
    if (!selectedProject || !dialog || dialog.open) return
    dialog.showModal()
    window.requestAnimationFrame(() => document.getElementById(`project-tab-${activeTab}`)?.focus())
  }, [activeTab, selectedProject])

  useEffect(() => {
    if (loading || selectedId !== null || requestedProjectId === null) return
    const requestedProject = projects.find((project) => project.id === requestedProjectId)
    if (!requestedProject) return
    projectDialogTriggerRef.current = null
    setSelectedId(requestedProject.id)
    setEditForm(toCoreForm(requestedProject))
    setActiveTab(requestedProjectTab ?? 'overview')
    setIsEditingCore(false)
    setLeadershipDirty(false)
    setResearchFieldsDirty(false)
    setDocumentsDirty(false)
    setDocumentsBusy(false)
  }, [loading, projects, requestedProjectId, requestedProjectTab, selectedId])

  useEffect(() => {
    setProjectPage(0)
  }, [projectQuery, projectSort, projectStatusFilter, projectTypeFilter])

  useEffect(() => {
    setProjectPage((current) => Math.min(current, projectPageCount - 1))
  }, [projectPageCount])

  function resetProjectDialogState() {
    setActiveTab('overview')
    setIsEditingCore(false)
    setLeadershipDirty(false)
    setResearchFieldsDirty(false)
    setDocumentsDirty(false)
    setDocumentsBusy(false)
  }

  function restoreProjectDialogFocus() {
    const trigger = projectDialogTriggerRef.current
    projectDialogTriggerRef.current = null
    window.requestAnimationFrame(() => {
      if (trigger?.isConnected) trigger.focus()
      else createTriggerRef.current?.focus()
    })
  }

  function finishClosingProjectDialog() {
    if (projectDialogRef.current?.open) projectDialogRef.current.close()
    setSelectedId(null)
    if (searchParams.has('projectId') || searchParams.has('tab')) {
      setSearchParams((current) => {
        const next = new URLSearchParams(current)
        next.delete('projectId')
        next.delete('tab')
        return next
      }, { replace: true })
    }
    resetProjectDialogState()
    restoreProjectDialogFocus()
  }

  async function requestCloseProjectDialog() {
    if (isBusy) return
    if (selectedProjectDirty && !await confirmDialog({ title: 'Bỏ thay đổi chưa lưu?', description: 'Dự án có thay đổi chưa lưu. Tiếp tục sẽ loại bỏ các thay đổi này.', confirmLabel: 'Bỏ thay đổi' })) return
    finishClosingProjectDialog()
    clearFeedback()
  }

  function openProjectDialog(project: Project, trigger: HTMLButtonElement) {
    if (isBusy) return
    projectDialogTriggerRef.current = trigger
    setSelectedId(project.id)
    setEditForm(toCoreForm(project))
    setActiveTab('overview')
    setIsEditingCore(false)
    setLeadershipDirty(false)
    setResearchFieldsDirty(false)
    setDocumentsDirty(false)
    setDocumentsBusy(false)
    clearFeedback()
  }

  async function startCreating() {
    if (selectedProjectDirty && !await confirmDialog({ title: 'Bỏ thay đổi chưa lưu?', description: 'Mở biểu mẫu tạo dự án sẽ loại bỏ các thay đổi trong dự án hiện tại.', confirmLabel: 'Tiếp tục' })) return
    if (selectedProjectDirty) {
      setIsEditingCore(false)
      setLeadershipDirty(false)
      setResearchFieldsDirty(false)
      setDocumentsDirty(false)
    }
    setIsCreating(true)
    clearFeedback()
  }

  async function cancelCreating() {
    if (isBusy) return
    if (createDirty && !await confirmDialog({ title: 'Hủy tạo dự án?', description: 'Thông tin bạn đã nhập sẽ không được lưu.', confirmLabel: 'Bỏ thông tin' })) return
    setCreateForm(emptyCreateForm)
    setIsCreating(false)
    clearFeedback()
    window.requestAnimationFrame(() => createTriggerRef.current?.focus())
  }

  async function cancelCoreEditing() {
    if (editDirty && !await confirmDialog({ title: 'Bỏ thay đổi chưa lưu?', description: 'Các chỉnh sửa trong phần tổng quan sẽ không được lưu.', confirmLabel: 'Bỏ thay đổi' })) return
    if (selectedProject) setEditForm(toCoreForm(selectedProject))
    setIsEditingCore(false)
    clearFeedback()
  }

  function clearFeedback() {
    setError('')
  }

  function resetProjectFilters() {
    setProjectQuery('')
    setProjectTypeFilter('ALL')
    setProjectStatusFilter('ALL')
    setProjectSort('NEWEST')
    setProjectPage(0)
  }

  function mergeProject(project: Project, select = false) {
    setProjects((current) => {
      const exists = current.some((item) => item.id === project.id)
      return exists
        ? current.map((item) => (item.id === project.id ? project : item))
        : [project, ...current]
    })
    if (select) setSelectedId(project.id)
  }

  async function refreshProjects() {
    if (selectedProjectDirty && !await confirmDialog({ title: 'Tải lại danh sách?', description: 'Các thay đổi chưa lưu trong dự án đang mở sẽ bị bỏ.', confirmLabel: 'Tải lại' })) return
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
    if (!token || !canCreateProject || isBusy) return

    const validationError = validateCoreForm(createForm)
    if (validationError) {
      setError(validationError)
      return
    }
    const payload: CreateProjectPayload = {
      code: createForm.code.trim(),
      name: createForm.name.trim(),
      projectType: createForm.projectType,
    }

    setBusyAction('create')
    clearFeedback()
    try {
      const created = await createProject(token, payload)
      projectDialogTriggerRef.current = createTriggerRef.current
      mergeProject(created, true)
      setEditForm(toCoreForm(created))
      setCreateForm(emptyCreateForm)
      setIsCreating(false)
      resetProjectFilters()
      setActiveTab('overview')
      setIsEditingCore(true)
      toast.success('Đã tạo dự án', `“${created.name}” đã được tạo.`)
      window.requestAnimationFrame(() => document.getElementById('project-tab-overview')?.focus())
    } catch (reason: unknown) {
      const message = errorMessage(reason, 'Không thể tạo dự án.')
      setError(message)
      toast.error('Không thể tạo dự án', message)
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
      setIsEditingCore(false)
      toast.success('Đã lưu thay đổi', 'Thông tin dự án đã được cập nhật.')
    } catch (reason: unknown) {
      const message = errorMessage(reason, 'Không thể cập nhật dự án.')
      setError(message)
      toast.error('Không thể lưu thay đổi', message)
    } finally {
      setBusyAction(null)
    }
  }

  async function handleDelete() {
    if (!token || !selectedProject || !canAdminManageProject || isBusy) return
    const draftWarning = selectedProjectDirty ? ' Các thay đổi chưa lưu cũng sẽ bị bỏ.' : ''
    if (!await confirmDialog({ title: 'Xóa dự án?', description: `Dự án sẽ không còn xuất hiện trong danh sách đang hoạt động. Dữ liệu lịch sử vẫn được giữ lại.${draftWarning}`, confirmLabel: 'Xóa dự án', destructive: true })) return

    setBusyAction('delete')
    clearFeedback()
    try {
      const deletedCode = selectedProject.code
      await deleteProject(token, selectedProject.id)
      await loadProjects()
      finishClosingProjectDialog()
      toast.success('Đã xóa mềm dự án', deletedCode)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể xóa dự án.'))
    } finally {
      setBusyAction(null)
    }
  }

  function handleTabKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const currentIndex = PROJECT_TABS.indexOf(activeTab)
    let nextIndex = currentIndex
    if (event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + PROJECT_TABS.length) % PROJECT_TABS.length
    if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % PROJECT_TABS.length
    if (event.key === 'Home') nextIndex = 0
    if (event.key === 'End') nextIndex = PROJECT_TABS.length - 1
    const nextTab = PROJECT_TABS[nextIndex]
    setActiveTab(nextTab)
    window.requestAnimationFrame(() => document.getElementById(`project-tab-${nextTab}`)?.focus())
  }

  return (
    <div className="project-management-page">
      <div className="page-title">
        <div>
          <span className="eyebrow">{isAdmin ? 'Không gian quản trị' : 'Không gian thành viên'}</span>
          <h1>{isAdmin ? 'Quản lý dự án' : 'Dự án của tôi'}</h1>
          <p>{isAdmin
            ? 'Tìm dự án và mở cửa sổ quản lý để xem hoặc cập nhật từng nhóm thông tin.'
            : 'Theo dõi các dự án bạn đang tham gia và xem lịch sử tham gia của mình.'}</p>
        </div>
        <div className="project-page-actions">
          {canCreateProject && token ? (
            <button
              ref={createTriggerRef}
              className="btn primary"
              type="button"
              aria-expanded={isCreating}
                  onClick={() => void startCreating()}
              disabled={isCreating || isBusy}
            >
              <Plus /> Tạo dự án
            </button>
          ) : null}
          <button className="btn" type="button" onClick={() => void refreshProjects()} disabled={loading || isBusy}>
            <RefreshCw />
            {loading ? 'Đang tải...' : 'Tải lại'}
          </button>
        </div>
      </div>

      <Feedback error={error} />

      {loading ? (
        <div className="empty">Đang tải dự án...</div>
      ) : (
        <section className="panel project-list-panel">
          <div className="panel-head">
            <div>
              <h2>{isAdmin ? 'Danh sách dự án' : 'Dự án đang tham gia'}</h2>
              <p>{projects.length} {isAdmin ? 'dự án bạn có quyền xem' : 'dự án có membership ACTIVE'}. Mỗi dự án được mở trong một cửa sổ riêng.</p>
            </div>
            <FolderKanban size={20} aria-hidden="true" />
          </div>
          {projects.length ? (
            <>
              <div className="project-list-toolbar">
                <label className="project-list-search">
                  <Search aria-hidden="true" />
                  <input
                    className="input"
                    type="search"
                    value={projectQuery}
                    onChange={(event) => setProjectQuery(event.target.value)}
                    placeholder="Tìm theo tên hoặc mã dự án..."
                    aria-label="Tìm dự án theo tên hoặc mã"
                  />
                </label>
                <PopupSelect className="project-list-filter" value={projectTypeFilter} onChange={(value) => setProjectTypeFilter(value as ProjectTypeFilter)} ariaLabel="Lọc theo loại dự án" options={[{ value: 'ALL', label: 'Tất cả loại' }, ...PROJECT_TYPES.map((value) => ({ value, label: PROJECT_TYPE_LABELS[value] }))]} />
                <PopupSelect className="project-list-filter" value={projectStatusFilter} onChange={(value) => setProjectStatusFilter(value as ProjectStatusFilter)} ariaLabel="Lọc theo trạng thái dự án" options={[{ value: 'ALL', label: 'Tất cả trạng thái' }, ...PROJECT_STATUSES.map((value) => ({ value, label: PROJECT_STATUS_LABELS[value] }))]} />
                <PopupSelect className="project-list-filter" value={projectSort} onChange={(value) => setProjectSort(value as ProjectSort)} ariaLabel="Sắp xếp dự án" options={[{ value: 'NEWEST', label: 'Mới nhất' }, { value: 'NAME_ASC', label: 'Tên A–Z' }, { value: 'CODE_ASC', label: 'Mã A–Z' }]} />
                {hasProjectFilters ? (
                  <button className="btn ghost table-btn" type="button" onClick={resetProjectFilters}>
                    <RotateCcw aria-hidden="true" /> Đặt lại
                  </button>
                ) : null}
              </div>

              <div className="project-list-summary" aria-live="polite">
                <span>
                  {filteredProjects.length
                    ? `Hiển thị ${projectPage * PROJECT_PAGE_SIZE + 1}–${Math.min((projectPage + 1) * PROJECT_PAGE_SIZE, filteredProjects.length)} / ${filteredProjects.length} dự án phù hợp`
                    : 'Không có dự án phù hợp'}
                  {filteredProjects.length !== projects.length ? ` · ${projects.length} dự án tổng cộng` : ''}
                </span>
              </div>

              {pagedProjects.length ? (
                <div className="field-admin-list project-management-list">
                  {pagedProjects.map((project) => {
                    const membership = membershipByProjectId.get(project.id)
                    return (
                    <article className="member-select-row project-management-row" key={project.id}>
                      <span className="member-avatar">{project.code.slice(0, 2).toUpperCase()}</span>
                      <span className="project-management-row-main">
                        <strong>{project.name}</strong>
                        <small>{project.code} · {PROJECT_TYPE_LABELS[project.projectType]}</small>
                      </span>
                      <span className="project-management-row-actions">
                        <span className={`badge ${PROJECT_STATUS_BADGES[project.status]}`}>
                          {PROJECT_STATUS_LABELS[project.status]}
                        </span>
                        {membership ? (
                          <span className={`badge ${membership.projectRole === 'LEADER' ? 'info' : 'success'}`}>
                            {membership.projectRole === 'LEADER' ? 'Leader' : 'Thành viên'}
                          </span>
                        ) : null}
                        <button
                          className="btn ghost table-btn"
                          type="button"
                          disabled={isBusy}
                          aria-label={`Chi tiết dự án ${project.name}`}
                          onClick={(event) => openProjectDialog(project, event.currentTarget)}
                        >
                          <Eye aria-hidden="true" />
                          Chi tiết
                        </button>
                      </span>
                    </article>
                    )
                  })}
                </div>
              ) : (
                <div className="project-filter-empty">
                  <EmptyState
                    title="Không tìm thấy dự án"
                    description="Thử từ khóa khác hoặc đặt lại bộ lọc để xem toàn bộ dự án."
                  />
                  <button className="btn ghost" type="button" onClick={resetProjectFilters}>
                    <RotateCcw aria-hidden="true" /> Đặt lại bộ lọc
                  </button>
                </div>
              )}

              {filteredProjects.length > PROJECT_PAGE_SIZE ? (
                <nav className="project-pagination" aria-label="Phân trang danh sách dự án">
                  <button
                    className="btn ghost table-btn"
                    type="button"
                    disabled={projectPage === 0}
                    onClick={() => setProjectPage((current) => Math.max(0, current - 1))}
                  >
                    <ChevronLeft aria-hidden="true" /> Trước
                  </button>
                  <span>Trang {projectPage + 1} / {projectPageCount}</span>
                  <button
                    className="btn ghost table-btn"
                    type="button"
                    disabled={projectPage + 1 >= projectPageCount}
                    onClick={() => setProjectPage((current) => Math.min(projectPageCount - 1, current + 1))}
                  >
                    Sau <ChevronRight aria-hidden="true" />
                  </button>
                </nav>
              ) : null}
            </>
          ) : (
            <EmptyState
              title="Chưa có dự án"
              description={canCreateProject
                ? 'Bấm “Tạo dự án” để thêm dự án đầu tiên.'
                : isAdmin
                  ? 'Hiện chưa có dự án nào bạn có quyền xem.'
                  : 'Bạn chưa tham gia dự án nào. Hãy xem danh sách công khai để gửi yêu cầu tham gia.'}
            />
          )}
        </section>
      )}

      {!loading && !isAdmin ? (
        <MembershipHistoryPanel memberships={removedMemberships} />
      ) : null}

      {isCreating && canCreateProject && token ? (
        <dialog
          ref={createDialogRef}
          className="project-create-dialog"
          aria-labelledby="project-create-title"
          aria-describedby="project-create-description"
          onCancel={(event) => {
            event.preventDefault()
            void cancelCreating()
          }}
          onClick={(event) => {
            if (event.target === event.currentTarget) void cancelCreating()
          }}
        >
          <form className="project-create-dialog-form" onSubmit={handleCreate} noValidate>
            <header className="project-create-dialog-head">
              <span className="project-create-dialog-icon" aria-hidden="true">
                <FolderKanban />
              </span>
              <div>
                <h2 id="project-create-title">Tạo dự án mới</h2>
                <p id="project-create-description">Nhập thông tin cơ bản để bắt đầu.</p>
              </div>
              <button
                className="project-close-button"
                type="button"
                aria-label="Đóng cửa sổ tạo dự án"
                onClick={() => void cancelCreating()}
                disabled={isBusy}
              >
                <X aria-hidden="true" />
              </button>
            </header>

            <div className="project-create-dialog-body">
              <div role="alert" aria-live="assertive">
                <Feedback message="" error={error} />
              </div>
              <label className="field">
                <span>Tên dự án <span aria-hidden="true">*</span></span>
                <input
                  className="input"
                  autoFocus
                  required
                  maxLength={200}
                  value={createForm.name}
                  onChange={(event) => setCreateForm({ ...createForm, name: event.target.value })}
                  placeholder="Ví dụ: Nghiên cứu Robot tự hành"
                />
              </label>
              <label className="field">
                <span>Mã dự án <span aria-hidden="true">*</span></span>
                <input
                  className="input"
                  required
                  maxLength={60}
                  value={createForm.code}
                  onChange={(event) => setCreateForm({ ...createForm, code: event.target.value })}
                  placeholder="Ví dụ: SL-ROBOT-2026"
                />
                <small>Mã sẽ được chuẩn hóa thành chữ in hoa và phải là duy nhất.</small>
              </label>
              <label className="field">
                <span>Loại dự án</span>
                <PopupSelect value={createForm.projectType} onChange={(value) => setCreateForm({ ...createForm, projectType: value as ProjectType })} ariaLabel="Loại dự án" options={PROJECT_TYPES.map((value) => ({ value, label: PROJECT_TYPE_LABELS[value] }))} />
              </label>
              <div className="project-create-dialog-note">
                {isAdmin
                  ? 'Mô tả, thời gian, leader, thành viên và lĩnh vực có thể bổ sung sau khi tạo.'
                  : 'Bạn sẽ được gán làm leader chính. Mô tả, thời gian, thành viên và lĩnh vực có thể bổ sung sau.'}
              </div>
            </div>

            <footer className="project-create-dialog-actions">
              {createDirty ? <span className="muted small">Có thông tin chưa lưu.</span> : <span />}
              <button className="btn ghost" type="button" disabled={isBusy} onClick={() => void cancelCreating()}>
                Hủy
              </button>
              <button className="btn primary" type="submit" disabled={isBusy}>
                <Plus />
                {busyAction === 'create' ? 'Đang tạo...' : 'Tạo dự án'}
              </button>
            </footer>
          </form>
        </dialog>
      ) : null}

      {!loading && selectedProject ? (
        <dialog
          ref={projectDialogRef}
          className="project-editor-dialog"
          aria-labelledby="project-editor-title"
          aria-describedby="project-editor-description"
          onCancel={(event) => {
            event.preventDefault()
            void requestCloseProjectDialog()
          }}
          onClick={(event) => {
            if (event.target === event.currentTarget) void requestCloseProjectDialog()
          }}
        >
          <OverlayPortalHost />
          <div className="project-editor-dialog-shell">
            <header className="project-editor-dialog-head">
              <div className="project-editor-dialog-heading">
                <span className="project-create-dialog-icon" aria-hidden="true">
                  <FolderKanban />
                </span>
                <div>
                  <span className="eyebrow">{canEditSelected ? 'Chỉnh sửa dự án' : 'Chi tiết dự án'}</span>
                  <h2 id="project-editor-title">{selectedProject.name}</h2>
                  <p id="project-editor-description">
                    {selectedProject.code} · {PROJECT_TYPE_LABELS[selectedProject.projectType]} ·{' '}
                    {PROJECT_STATUS_LABELS[selectedProject.status]}
                  </p>
                </div>
              </div>
              <div className="project-editor-dialog-actions">
                <span className={`badge ${selectedProject.isPublic ? 'success' : 'info'}`}>
                  {selectedProject.isPublic ? 'Công khai' : 'Nội bộ'}
                </span>
                <Link
                  className="btn ghost table-btn"
                  to={`/admin/events?projectId=${selectedProject.id}`}
                  aria-disabled={isBusy}
                  onClick={(event) => {
                    if (isBusy) {
                      event.preventDefault()
                      return
                    }
                    if (selectedProjectDirty) {
                      event.preventDefault()
                      void confirmDialog({ title: 'Mở trang Sự kiện?', description: 'Các thay đổi chưa lưu trong dự án sẽ bị bỏ.', confirmLabel: 'Mở Sự kiện' }).then((confirmed) => { if (confirmed) navigate(`/admin/events?projectId=${selectedProject.id}`) })
                    }
                  }}
                >
                  <CalendarRange aria-hidden="true" /> Sự kiện
                </Link>
                <button
                  className="project-close-button"
                  type="button"
                  aria-label="Đóng cửa sổ dự án"
                  onClick={requestCloseProjectDialog}
                  disabled={isBusy}
                >
                  <X aria-hidden="true" />
                </button>
              </div>
            </header>

            <div className="project-tabs project-editor-dialog-tabs" role="tablist" aria-label={`Quản lý dự án ${selectedProject.name}`} onKeyDown={handleTabKeyDown}>
              <button
                id="project-tab-overview"
                className="project-tab"
                type="button"
                role="tab"
                aria-selected={activeTab === 'overview'}
                aria-controls="project-panel-overview"
                tabIndex={activeTab === 'overview' ? 0 : -1}
                onClick={() => setActiveTab('overview')}
              >
                <LayoutDashboard aria-hidden="true" /> Tổng quan
              </button>
              <button
                id="project-tab-leaders"
                className="project-tab"
                type="button"
                role="tab"
                aria-selected={activeTab === 'leaders'}
                aria-controls="project-panel-leaders"
                tabIndex={activeTab === 'leaders' ? 0 : -1}
                onClick={() => setActiveTab('leaders')}
              >
                <UsersRound aria-hidden="true" /> Nhóm leader
              </button>
              <button
                id="project-tab-members"
                className="project-tab"
                type="button"
                role="tab"
                aria-selected={activeTab === 'members'}
                aria-controls="project-panel-members"
                tabIndex={activeTab === 'members' ? 0 : -1}
                onClick={() => setActiveTab('members')}
              >
                <UsersRound aria-hidden="true" /> Thành viên
              </button>
              <button
                id="project-tab-research-fields"
                className="project-tab"
                type="button"
                role="tab"
                aria-selected={activeTab === 'research-fields'}
                aria-controls="project-panel-research-fields"
                tabIndex={activeTab === 'research-fields' ? 0 : -1}
                onClick={() => setActiveTab('research-fields')}
              >
                <FlaskConical aria-hidden="true" /> Lĩnh vực nghiên cứu
              </button>
              <button
                id="project-tab-documents"
                className="project-tab"
                type="button"
                role="tab"
                aria-selected={activeTab === 'documents'}
                aria-controls="project-panel-documents"
                tabIndex={activeTab === 'documents' ? 0 : -1}
                onClick={() => setActiveTab('documents')}
              >
                <FileText aria-hidden="true" /> Tài liệu
              </button>
            </div>

            <div className="project-editor-dialog-body">
              <div className="project-editor-dialog-feedback" role="status" aria-live="polite">
                <Feedback error={error} />
              </div>

              <section
                id="project-panel-overview"
                className="panel project-tab-panel"
                role="tabpanel"
                aria-labelledby="project-tab-overview"
                tabIndex={0}
                hidden={activeTab !== 'overview'}
              >
              <div className="panel-head">
                <div>
                  <h2>Tổng quan dự án</h2>
                  <p>Thông tin cốt lõi và trạng thái hiện tại.</p>
                </div>
                {canEditSelected && !isEditingCore ? (
                  <button className="btn ghost table-btn" type="button" disabled={isBusy} onClick={() => setIsEditingCore(true)}>
                    <Pencil aria-hidden="true" /> Chỉnh sửa
                  </button>
                ) : null}
              </div>

              {isEditingCore && canEditSelected && editForm ? (
                <form className="project-overview-editor" onSubmit={handleUpdate} noValidate>
                  <ProjectCoreFields form={editForm} onChange={setEditForm} />
                  <div className="project-overview-action-footer">
                    <span className="muted small" aria-live="polite">
                      {editDirty ? 'Có thay đổi chưa lưu.' : 'Chưa có thay đổi.'}
                    </span>
                    <div className="form-actions">
                      <button className="btn ghost" type="button" disabled={isBusy} onClick={() => void cancelCoreEditing()}>Hủy</button>
                      <button className="btn primary" type="submit" disabled={isBusy || !editDirty}><Save aria-hidden="true" />{busyAction === 'update' ? 'Đang lưu...' : 'Lưu thay đổi'}</button>
                    </div>
                  </div>
                </form>
              ) : (
                <div className="project-overview-read">
                  <section className="project-overview-read-section project-overview-wide">
                    <h3>Thông tin cơ bản</h3>
                    <div className="project-overview-item">
                      <span>Mô tả</span>
                      <strong>{selectedProject.description || 'Chưa có mô tả.'}</strong>
                    </div>
                    <div className="project-overview-item">
                      <span>Mục tiêu</span>
                      <strong>{selectedProject.goal || 'Chưa cập nhật.'}</strong>
                    </div>
                  </section>
                  <section className="project-overview-read-section project-overview-wide">
                    <h3>Thông tin dự án</h3>
                    <div className="project-overview-metadata-grid">
                      <div className="project-overview-item"><span>Loại dự án</span><strong>{PROJECT_TYPE_LABELS[selectedProject.projectType]}</strong></div>
                      <div className="project-overview-item"><span>Trạng thái</span><strong>{PROJECT_STATUS_LABELS[selectedProject.status]}</strong></div>
                      <div className="project-overview-item"><span>Leader chính</span><strong>{selectedProject.primaryLeader?.name ?? 'Chưa chọn'}</strong></div>
                      <div className="project-overview-item"><span>Mã dự án</span><strong>{selectedProject.code}</strong></div>
                      <div className="project-overview-item"><span>Nhóm leader</span><strong>{selectedProject.leaders.length} người</strong></div>
                      <div className="project-overview-item"><span>Hiển thị</span><strong>{selectedProject.isFeatured ? 'Nổi bật' : 'Thông thường'} · {selectedProject.isPublic ? 'Công khai' : 'Nội bộ'}</strong></div>
                    </div>
                  </section>
                  <section className="project-overview-read-section project-overview-wide">
                    <h3>Tiến độ thời gian</h3>
                    <div className="project-overview-metadata-grid project-overview-timeline-read">
                      <div className="project-overview-item"><span>Ngày bắt đầu</span><strong>{selectedProject.startDate || 'Chưa cập nhật'}</strong></div>
                      <div className="project-overview-item"><span>Kết thúc dự kiến</span><strong>{selectedProject.expectedEndDate || 'Chưa cập nhật'}</strong></div>
                      <div className="project-overview-item"><span>Kết thúc thực tế</span><strong>{selectedProject.actualEndDate || 'Chưa cập nhật'}</strong></div>
                    </div>
                  </section>
                </div>
              )}

              {!isEditingCore && canAdminManageProject ? (
                <div className="project-danger-zone">
                  <div>
                    <strong>Xóa mềm dự án</strong>
                    <p>Dự án sẽ không còn xuất hiện trên các màn hình đang hoạt động; dữ liệu vẫn được giữ lại.</p>
                  </div>
                  <button className="btn danger-text" type="button" disabled={isBusy} onClick={() => void handleDelete()}>
                    <Trash2 aria-hidden="true" />
                    {busyAction === 'delete' ? 'Đang xóa...' : `Xóa mềm ${selectedProject.code}`}
                  </button>
                </div>
              ) : null}
              </section>

              <div
                id="project-panel-leaders"
                role="tabpanel"
                aria-labelledby="project-tab-leaders"
                tabIndex={0}
                hidden={activeTab !== 'leaders'}
              >
              {canAdminManageProject && token ? (
                <ProjectLeadershipEditor
                  key={selectedProject.id}
                  project={selectedProject}
                  token={token}
                  disabled={isBusy}
                  onClearFeedback={clearFeedback}
                  onError={(message) => { setError(message); toast.error('Không thể cập nhật nhóm leader', message) }}
                  onSavingChange={(saving) => setBusyAction(saving ? 'leadership' : null)}
                  onDirtyChange={setLeadershipDirty}
                  onSaved={(updated) => {
                    mergeProject(updated)
                    toast.success('Đã cập nhật nhóm leader', 'Thay đổi leader đã được lưu.')
                  }}
                />
              ) : (
                <section className="panel page-section">
                  <div className="panel-head">
                    <div>
                      <h2>Nhóm leader</h2>
                      <p>Bạn có thể xem nhóm hiện tại nhưng không có quyền thay đổi.</p>
                    </div>
                    <UsersRound size={20} aria-hidden="true" />
                  </div>
                  <div className="inline-badges">
                    {selectedProject.leaders.map((leader) => (
                      <span className="badge info" key={leader.userId}>
                        {leader.name}{leader.userId === selectedProject.primaryLeader?.userId ? ' · Chính' : ''}
                      </span>
                    ))}
                    {!selectedProject.leaders.length ? <span className="muted small">Dự án chưa có leader.</span> : null}
                  </div>
                </section>
              )}
              </div>

              <div
                id="project-panel-members"
                role="tabpanel"
                aria-labelledby="project-tab-members"
                tabIndex={0}
                hidden={activeTab !== 'members'}
              >
                <ProjectMembersPanel project={selectedProject} />
              </div>

              <div
                id="project-panel-research-fields"
                role="tabpanel"
                aria-labelledby="project-tab-research-fields"
                tabIndex={0}
                hidden={activeTab !== 'research-fields'}
              >
                <ProjectResearchFieldsPanel
                  project={selectedProject}
                  onDirtyChange={setResearchFieldsDirty}
                />
              </div>

              <div
                id="project-panel-documents"
                role="tabpanel"
                aria-labelledby="project-tab-documents"
                tabIndex={0}
                hidden={activeTab !== 'documents'}
              >
                <ProjectDocumentsPanel
                  project={selectedProject}
                  onDirtyChange={setDocumentsDirty}
                  onBusyChange={setDocumentsBusy}
                />
              </div>
            </div>

            <footer className="project-editor-dialog-footer">
              <span className="muted small" aria-live="polite">
                {selectedProjectDirty ? 'Có thay đổi chưa lưu trong cửa sổ này.' : 'Mọi thay đổi đã được đồng bộ.'}
              </span>
              <button className="btn ghost" type="button" disabled={isBusy} onClick={() => void requestCloseProjectDialog()}>
                Đóng
              </button>
            </footer>
          </div>
        </dialog>
      ) : null}
    </div>
  )
}

function projectTab(value: string | null): ProjectTab | null {
  return value !== null && PROJECT_TABS.includes(value as ProjectTab) ? value as ProjectTab : null
}

function MembershipHistoryPanel({ memberships }: { memberships: ProjectMembershipHistory[] }) {
  return (
    <section className="panel project-membership-history" aria-labelledby="project-membership-history-title">
      <div className="panel-head">
        <div>
          <h2 id="project-membership-history-title">Lịch sử tham gia</h2>
          <p>Các membership đã chuyển sang REMOVED vẫn được giữ lại cho riêng bạn.</p>
        </div>
        <History size={20} aria-hidden="true" />
      </div>

      {memberships.length ? (
        <div className="field-admin-list project-membership-history-list">
          {memberships.map((membership) => (
            <article
              className="member-select-row project-management-row project-membership-history-row"
              key={`${membership.projectId}-${membership.joinedAt}`}
            >
              <span className="member-avatar">{membership.projectCode.slice(0, 2).toUpperCase()}</span>
              <span className="project-management-row-main">
                <strong>{membership.projectName}</strong>
                <small>{membership.projectCode} · {PROJECT_STATUS_LABELS[membership.projectStatus]}</small>
                <small>
                  Tham gia {formatDateTime(membership.joinedAt)}
                  {membership.removedAt ? ` · Rời dự án ${formatDateTime(membership.removedAt)}` : ''}
                </small>
              </span>
              <span className="project-management-row-actions">
                <span className={`badge ${membership.projectRole === 'LEADER' ? 'info' : 'success'}`}>
                  {membership.projectRole === 'LEADER' ? 'Leader' : 'Thành viên'}
                </span>
                <span className="badge danger">Đã rời dự án</span>
              </span>
            </article>
          ))}
        </div>
      ) : (
        <EmptyState
          title="Chưa có lịch sử đã rời dự án"
          description="Khi membership của bạn chuyển sang REMOVED, thông tin tham gia sẽ xuất hiện tại đây."
        />
      )}
    </section>
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
    <div className="project-core-fields">
      <section className="project-overview-section project-basic-information"><h3>Thông tin cơ bản</h3><div className="project-basic-grid"><label className="field">
        <span>Code</span>
        <input className="input" required maxLength={60} value={form.code} onChange={(event) => patch({ code: event.target.value })} placeholder="SL-AI-2026" />
      </label><label className="field">
        <span>Tên dự án</span>
        <input className="input" required maxLength={200} value={form.name} onChange={(event) => patch({ name: event.target.value })} />
      </label><label className="field project-core-field-wide">
        <span>Mô tả</span>
        <textarea className="textarea" rows={4} value={form.description} onChange={(event) => patch({ description: event.target.value })} />
      </label><label className="field project-core-field-wide">
        <span>Mục tiêu</span>
        <textarea className="textarea" rows={3} value={form.goal} onChange={(event) => patch({ goal: event.target.value })} />
      </label></div></section>
      <section className="project-overview-section"><h3>Phân loại</h3><div className="project-basic-grid"><label className="field">
        <span>Loại dự án</span>
        <PopupSelect value={form.projectType} onChange={(value) => patch({ projectType: value as ProjectType })} ariaLabel="Loại dự án" options={PROJECT_TYPES.map((value) => ({ value, label: PROJECT_TYPE_LABELS[value] }))} />
      </label><label className="field">
        <span>Trạng thái</span>
        <PopupSelect value={form.status} onChange={(value) => patch({ status: value as ProjectStatus })} ariaLabel="Trạng thái dự án" options={PROJECT_STATUSES.map((value) => ({ value, label: PROJECT_STATUS_LABELS[value] }))} />
      </label></div></section>
      <section className="project-overview-section"><h3>Tiến độ thời gian</h3><div className="project-timeline-grid"><label className="field">
        <span>Ngày bắt đầu</span>
        <input className="input" type="date" value={form.startDate} onChange={(event) => patch({ startDate: event.target.value })} />
      </label><label className="field">
        <span>Ngày kết thúc dự kiến</span>
        <input className="input" type="date" value={form.expectedEndDate} onChange={(event) => patch({ expectedEndDate: event.target.value })} />
      </label><label className="field">
        <span>Ngày kết thúc thực tế</span>
        <input className="input" type="date" value={form.actualEndDate} onChange={(event) => patch({ actualEndDate: event.target.value })} />
      </label></div></section>
      <section className="project-overview-section"><h3>Hiển thị</h3><div className="project-visibility-settings"><label className="project-visibility-row">
        <input type="checkbox" checked={form.isPublic} onChange={(event) => patch({ isPublic: event.target.checked })} />
        <span><strong>Công khai</strong><small>Khách không đăng nhập có thể xem dự án.</small></span>
      </label><label className="project-visibility-row">
        <input type="checkbox" checked={form.isFeatured} onChange={(event) => patch({ isFeatured: event.target.checked })} />
        <span><strong>Nổi bật</strong><small>Ưu tiên dự án trên giao diện công khai.</small></span>
      </label></div></section>
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

function sameCoreForm(left: ProjectCoreForm, right: ProjectCoreForm) {
  return (
    left.code === right.code
    && left.name === right.name
    && left.description === right.description
    && left.goal === right.goal
    && left.projectType === right.projectType
    && left.status === right.status
    && left.startDate === right.startDate
    && left.expectedEndDate === right.expectedEndDate
    && left.actualEndDate === right.actualEndDate
    && left.isPublic === right.isPublic
    && left.isFeatured === right.isFeatured
  )
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

function projectCreatedAt(project: Project) {
  if (!project.createdAt) return 0
  const value = Date.parse(project.createdAt)
  return Number.isNaN(value) ? 0 : value
}

function positiveInteger(value: string | null) {
  if (value === null || !/^\d+$/.test(value)) return null
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
}

function membershipTimestamp(membership: ProjectMembershipHistory) {
  const value = Date.parse(membership.removedAt ?? membership.joinedAt)
  return Number.isNaN(value) ? 0 : value
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date)
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}

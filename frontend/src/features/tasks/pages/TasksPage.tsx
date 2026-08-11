import {
  AlertCircle,
  Award,
  CheckCircle2,
  CheckSquare,
  ChevronRight,
  Clock,
  Download,
  Edit3,
  FilePlus,
  Filter,
  FolderKanban,
  LayoutGrid,
  List as ListIcon,
  Plus,
  Send,
  Sliders,
  Trash2,
  Upload,
  UserPlus,
  Users,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../auth/authContext'
import { uploadFile } from '../../files/api'
import { listProjects } from '../../projects/api'
import type { Project } from '../../projects/types'
import {
  createEvaluation,
  createEvaluationCriterion,
  listEvaluationCriteria,
  updateEvaluationCriterion,
} from '../../evaluations/api'
import type { EvaluationCriterion } from '../../evaluations/types'
import {
  addTaskAssignees,
  addTaskAttachment,
  createTask,
  deleteTask,
  getTaskDetail,
  listTasks,
  removeTaskAssignee,
  submitTask,
  updateTask,
} from '../api'
import {
  TASK_PRIORITY_BADGES,
  TASK_PRIORITY_LABELS,
  TASK_STATUS_BADGES,
  TASK_STATUS_LABELS,
} from '../types'
import type {
  AttachmentType,
  TaskDetail,
  TaskPriority,
  TaskStatus,
  TaskSummary,
} from '../types'

type ActiveTab = 'tasks' | 'criteria' | 'evaluations'
type ViewMode = 'kanban' | 'list'

export function TasksPage() {
  const { token, profile } = useAuth()
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null)
  const [activeTab, setActiveTab] = useState<ActiveTab>('tasks')
  const [viewMode, setViewMode] = useState<ViewMode>('kanban')

  // Task list state
  const [tasks, setTasks] = useState<TaskSummary[]>([])
  const [filterStatus, setFilterStatus] = useState<TaskStatus | ''>('')
  const [filterPriority, setFilterPriority] = useState<TaskPriority | ''>('')
  const [loadingTasks, setLoadingTasks] = useState(false)

  // Selected task detail modal
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null)
  const [taskDetail, setTaskDetail] = useState<TaskDetail | null>(null)
  const [loadingDetail, setLoadingDetail] = useState(false)

  // Create/Edit Task modal state
  const [showTaskModal, setShowTaskModal] = useState(false)
  const [editingTask, setEditingTask] = useState<TaskSummary | null>(null)
  const [taskFormTitle, setTaskFormTitle] = useState('')
  const [taskFormDesc, setTaskFormDesc] = useState('')
  const [taskFormPriority, setTaskFormPriority] = useState<TaskPriority>('MEDIUM')
  const [taskFormStatus, setTaskFormStatus] = useState<TaskStatus>('TODO')
  const [taskFormStartAt, setTaskFormStartAt] = useState('')
  const [taskFormDueAt, setTaskFormDueAt] = useState('')
  const [savingTask, setSavingTask] = useState(false)

  // Assignee modal
  const [showAssigneeModal, setShowAssigneeModal] = useState(false)
  const [assigneeUserIdsInput, setAssigneeUserIdsInput] = useState('')
  const [savingAssignee, setSavingAssignee] = useState(false)

  // Attachment modal
  const [showAttachmentModal, setShowAttachmentModal] = useState(false)
  const [attachFile, setAttachFile] = useState<File | null>(null)
  const [attachType, setAttachType] = useState<AttachmentType>('INPUT')
  const [attachDesc, setAttachDesc] = useState('')
  const [uploadingAttach, setUploadingAttach] = useState(false)

  // Submit task modal
  const [showSubmitModal, setShowSubmitModal] = useState(false)
  const [submitFile, setSubmitFile] = useState<File | null>(null)
  const [submitNote, setSubmitNote] = useState('')
  const [submittingResult, setSubmittingResult] = useState(false)

  // Evaluation criteria state
  const [criteria, setCriteria] = useState<EvaluationCriterion[]>([])
  const [loadingCriteria, setLoadingCriteria] = useState(false)
  const [showCritModal, setShowCritModal] = useState(false)
  const [critName, setCritName] = useState('')
  const [critDesc, setCritDesc] = useState('')
  const [critMaxScore, setCritMaxScore] = useState('10')
  const [critDisplayOrder, setCritDisplayOrder] = useState('0')
  const [savingCrit, setSavingCrit] = useState(false)

  // Member Evaluation form state
  const [evalUserSearch, setEvalUserSearch] = useState('')
  const [evalSelectedUserId, setEvalSelectedUserId] = useState<number | null>(null)
  const [evalNote, setEvalNote] = useState('')
  const [evalScoresMap, setEvalScoresMap] = useState<Record<number, string>>({})
  const [submittingEval, setSubmittingEval] = useState(false)

  // Messages
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  // Load Projects on mount
  useEffect(() => {
    if (!token) return
    listProjects(token)
      .then((res) => {
        setProjects(res)
        if (res.length > 0 && !selectedProjectId) {
          setSelectedProjectId(res[0].id)
        }
      })
      .catch((err: Error) => setError(err.message))
  }, [token])

  // Load Tasks when project or filters change
  const loadTaskList = useCallback(async () => {
    if (!token || !selectedProjectId) return
    setLoadingTasks(true)
    try {
      const res = await listTasks(token, selectedProjectId, {
        status: filterStatus || undefined,
        priority: filterPriority || undefined,
        size: 100,
      })
      setTasks(res.content)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể tải danh sách nhiệm vụ')
    } finally {
      setLoadingTasks(false)
    }
  }, [token, selectedProjectId, filterStatus, filterPriority])

  useEffect(() => {
    if (activeTab === 'tasks' && selectedProjectId) {
      loadTaskList()
    }
  }, [activeTab, selectedProjectId, loadTaskList])

  // Load Criteria when Criteria tab or Evaluation tab opened
  const loadCriteriaList = useCallback(async () => {
    if (!token || !selectedProjectId) return
    setLoadingCriteria(true)
    try {
      const res = await listEvaluationCriteria(token, selectedProjectId)
      setCriteria(res)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể tải tiêu chí đánh giá')
    } finally {
      setLoadingCriteria(false)
    }
  }, [token, selectedProjectId])

  useEffect(() => {
    if ((activeTab === 'criteria' || activeTab === 'evaluations') && selectedProjectId) {
      loadCriteriaList()
    }
  }, [activeTab, selectedProjectId, loadCriteriaList])

  // Load Task Detail
  const fetchTaskDetail = async (taskId: number) => {
    if (!token) return
    setLoadingDetail(true)
    try {
      const detail = await getTaskDetail(token, taskId)
      setTaskDetail(detail)
      setSelectedTaskId(taskId)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể xem chi tiết nhiệm vụ')
    } finally {
      setLoadingDetail(false)
    }
  }

  // Handle Save Task (Create/Edit)
  const handleSaveTask = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token || !selectedProjectId) return
    setSavingTask(true)
    setError('')
    try {
      if (editingTask) {
        await updateTask(token, editingTask.id, {
          title: taskFormTitle,
          description: taskFormDesc,
          priority: taskFormPriority,
          status: taskFormStatus,
          startAt: taskFormStartAt ? new Date(taskFormStartAt).toISOString() : undefined,
          dueAt: taskFormDueAt ? new Date(taskFormDueAt).toISOString() : undefined,
        })
        setSuccess('Cập nhật nhiệm vụ thành công!')
      } else {
        await createTask(token, selectedProjectId, {
          title: taskFormTitle,
          description: taskFormDesc,
          priority: taskFormPriority,
          status: taskFormStatus,
          startAt: taskFormStartAt ? new Date(taskFormStartAt).toISOString() : undefined,
          dueAt: taskFormDueAt ? new Date(taskFormDueAt).toISOString() : undefined,
        })
        setSuccess('Tạo nhiệm vụ mới thành công!')
      }
      setShowTaskModal(false)
      loadTaskList()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi lưu nhiệm vụ')
    } finally {
      setSavingTask(false)
    }
  }

  // Handle Delete Task
  const handleDeleteTask = async (taskId: number) => {
    if (!token || !window.confirm('Bạn có chắc chắn muốn xóa nhiệm vụ này?')) return
    try {
      await deleteTask(token, taskId)
      setSuccess('Đã xóa nhiệm vụ!')
      if (selectedTaskId === taskId) {
        setSelectedTaskId(null)
        setTaskDetail(null)
      }
      loadTaskList()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi xóa nhiệm vụ')
    }
  }

  // Handle Quick Status Change
  const handleStatusChange = async (taskId: number, newStatus: TaskStatus) => {
    if (!token) return
    try {
      await updateTask(token, taskId, { status: newStatus })
      loadTaskList()
      if (selectedTaskId === taskId) {
        fetchTaskDetail(taskId)
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi đổi trạng thái')
    }
  }

  // Handle Add Assignees
  const handleAddAssignees = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token || !selectedTaskId) return
    const ids = assigneeUserIdsInput
      .split(',')
      .map((s) => parseInt(s.trim()))
      .filter((n) => !isNaN(n))

    if (ids.length === 0) {
      setError('Vui lòng nhập ít nhất 1 User ID số hợp lệ')
      return
    }

    setSavingAssignee(true)
    try {
      await addTaskAssignees(token, selectedTaskId, { userIds: ids })
      setSuccess('Đã gán người phụ trách!')
      setShowAssigneeModal(false)
      setAssigneeUserIdsInput('')
      fetchTaskDetail(selectedTaskId)
      loadTaskList()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi gán người phụ trách')
    } finally {
      setSavingAssignee(false)
    }
  }

  // Handle Remove Assignee
  const handleRemoveAssignee = async (userId: number) => {
    if (!token || !selectedTaskId) return
    try {
      await removeTaskAssignee(token, selectedTaskId, userId)
      setSuccess('Đã gỡ phân công!')
      fetchTaskDetail(selectedTaskId)
      loadTaskList()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi gỡ người phụ trách')
    }
  }

  // Handle Attach File
  const handleAttachFile = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token || !selectedTaskId || !attachFile) {
      setError('Vui lòng chọn tệp')
      return
    }
    setUploadingAttach(true)
    setError('')
    try {
      const uploaded = await uploadFile(token, attachFile, 'PROJECT', attachDesc || 'Task Attachment')
      await addTaskAttachment(token, selectedTaskId, {
        fileId: uploaded.id,
        attachmentType: attachType,
        description: attachDesc,
      })
      setSuccess('Đính kèm tệp thành công!')
      setShowAttachmentModal(false)
      setAttachFile(null)
      setAttachDesc('')
      fetchTaskDetail(selectedTaskId)
      loadTaskList()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi đính kèm tệp')
    } finally {
      setUploadingAttach(false)
    }
  }

  // Handle Submit Task Result
  const handleSubmitTask = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token || !selectedTaskId || !submitFile) {
      setError('Vui lòng chọn tệp báo cáo kết quả')
      return
    }
    setSubmittingResult(true)
    setError('')
    try {
      const uploaded = await uploadFile(token, submitFile, 'PROJECT', 'Task Submission Result')
      await submitTask(token, selectedTaskId, {
        fileId: uploaded.id,
        note: submitNote,
      })
      setSuccess('Nộp kết quả nhiệm vụ thành công! Trạng thái đã chuyển sang Chờ duyệt.')
      setShowSubmitModal(false)
      setSubmitFile(null)
      setSubmitNote('')
      fetchTaskDetail(selectedTaskId)
      loadTaskList()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi nộp bài')
    } finally {
      setSubmittingResult(false)
    }
  }

  // Handle Create Criteria
  const handleCreateCriterion = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token || !selectedProjectId) return
    setSavingCrit(true)
    try {
      await createEvaluationCriterion(token, selectedProjectId, {
        name: critName,
        description: critDesc,
        maxScore: parseFloat(critMaxScore) || 10,
        displayOrder: parseInt(critDisplayOrder) || 0,
      })
      setSuccess('Tạo tiêu chí đánh giá thành công!')
      setShowCritModal(false)
      setCritName('')
      setCritDesc('')
      loadCriteriaList()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi tạo tiêu chí')
    } finally {
      setSavingCrit(false)
    }
  }

  // Handle Toggle Active Criterion
  const handleToggleCritActive = async (criterion: EvaluationCriterion) => {
    if (!token || !selectedProjectId) return
    try {
      await updateEvaluationCriterion(token, selectedProjectId, criterion.id, {
        isActive: !criterion.isActive,
      })
      loadCriteriaList()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi đổi trạng thái tiêu chí')
    }
  }

  // Handle Submit Member Evaluation
  const handleSubmitEvaluation = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token || !selectedProjectId || !evalSelectedUserId) {
      setError('Vui lòng chọn ID thành viên cần đánh giá')
      return
    }
    const scoresEntries = Object.entries(evalScoresMap).map(([critIdStr, scoreStr]) => ({
      criterionId: parseInt(critIdStr),
      score: parseFloat(scoreStr) || 0,
    }))
    if (scoresEntries.length === 0) {
      setError('Vui lòng nhập điểm cho ít nhất một tiêu chí')
      return
    }
    setSubmittingEval(true)
    setError('')
    try {
      await createEvaluation(token, selectedProjectId, {
        evaluatedUserId: evalSelectedUserId,
        note: evalNote,
        scores: scoresEntries,
      })
      setSuccess('Gửi phiếu đánh giá thành công!')
      setEvalNote('')
      setEvalScoresMap({})
      setEvalSelectedUserId(null)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi khi gửi đánh giá')
    } finally {
      setSubmittingEval(false)
    }
  }

  const selectedProject = projects.find((p) => p.id === selectedProjectId)

  return (
    <div className="admin-page-wrap">
      <header className="page-head">
        <div>
          <h1>Quản lý Nhiệm vụ & Đánh giá (D4)</h1>
          <p>Phân công công việc, đính kèm kết quả, thiết lập tiêu chí và chấm điểm thành viên dự án.</p>
        </div>
      </header>

      {error && (
        <div className="feedback error" style={{ margin: '16px 0' }}>
          <AlertCircle size={18} />
          <span>{error}</span>
          <button type="button" onClick={() => setError('')} className="btn ghost sm">
            <X size={14} />
          </button>
        </div>
      )}
      {success && (
        <div className="feedback success" style={{ margin: '16px 0' }}>
          <CheckCircle2 size={18} />
          <span>{success}</span>
          <button type="button" onClick={() => setSuccess('')} className="btn ghost sm">
            <X size={14} />
          </button>
        </div>
      )}

      {/* Project Selector & Tab Navigation */}
      <div className="panel" style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <FolderKanban size={18} color="var(--primary)" />
            <strong>Chọn dự án:</strong>
          </div>
          <select
            className="select"
            style={{ maxWidth: 320 }}
            value={selectedProjectId ?? ''}
            onChange={(e) => setSelectedProjectId(Number(e.target.value))}
          >
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                [{p.code}] {p.name}
              </option>
            ))}
          </select>

          <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
            <button
              type="button"
              className={`btn ${activeTab === 'tasks' ? 'primary' : 'secondary'}`}
              onClick={() => setActiveTab('tasks')}
            >
              <CheckSquare size={16} />
              Nhiệm vụ
            </button>
            <button
              type="button"
              className={`btn ${activeTab === 'criteria' ? 'primary' : 'secondary'}`}
              onClick={() => setActiveTab('criteria')}
            >
              <Sliders size={16} />
              Tiêu chí đánh giá
            </button>
            <button
              type="button"
              className={`btn ${activeTab === 'evaluations' ? 'primary' : 'secondary'}`}
              onClick={() => setActiveTab('evaluations')}
            >
              <Award size={16} />
              Đánh giá thành viên
            </button>
          </div>
        </div>
      </div>

      {/* TAB 1: TASKS */}
      {activeTab === 'tasks' && (
        <div className="admin-content-grid">
          <div className="panel" style={{ flex: 1 }}>
            {/* Filter & Toolbar */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                <Filter size={16} />
                <select
                  className="select sm"
                  value={filterStatus}
                  onChange={(e) => setFilterStatus(e.target.value as TaskStatus | '')}
                >
                  <option value="">-- Tất cả trạng thái --</option>
                  {(Object.keys(TASK_STATUS_LABELS) as TaskStatus[]).map((st) => (
                    <option key={st} value={st}>
                      {TASK_STATUS_LABELS[st]}
                    </option>
                  ))}
                </select>

                <select
                  className="select sm"
                  value={filterPriority}
                  onChange={(e) => setFilterPriority(e.target.value as TaskPriority | '')}
                >
                  <option value="">-- Tất cả độ ưu tiên --</option>
                  {(Object.keys(TASK_PRIORITY_LABELS) as TaskPriority[]).map((pr) => (
                    <option key={pr} value={pr}>
                      {TASK_PRIORITY_LABELS[pr]}
                    </option>
                  ))}
                </select>

                <div className="btn-group sm" style={{ marginLeft: 8 }}>
                  <button
                    type="button"
                    className={`btn sm ${viewMode === 'kanban' ? 'primary' : 'ghost'}`}
                    onClick={() => setViewMode('kanban')}
                  >
                    <LayoutGrid size={14} /> Kanban
                  </button>
                  <button
                    type="button"
                    className={`btn sm ${viewMode === 'list' ? 'primary' : 'ghost'}`}
                    onClick={() => setViewMode('list')}
                  >
                    <ListIcon size={14} /> Bảng
                  </button>
                </div>
              </div>

              <button
                type="button"
                className="btn primary"
                onClick={() => {
                  setEditingTask(null)
                  setTaskFormTitle('')
                  setTaskFormDesc('')
                  setTaskFormPriority('MEDIUM')
                  setTaskFormStatus('TODO')
                  setTaskFormStartAt('')
                  setTaskFormDueAt('')
                  setShowTaskModal(true)
                }}
              >
                <Plus size={16} /> Tạo nhiệm vụ mới
              </button>
            </div>

            {loadingTasks ? (
              <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-3)' }}>Đang tải danh sách nhiệm vụ...</div>
            ) : tasks.length === 0 ? (
              <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-3)' }}>Chưa có nhiệm vụ nào trong dự án này.</div>
            ) : viewMode === 'kanban' ? (
              /* KANBAN BOARD */
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
                {(['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE', 'CANCELLED'] as TaskStatus[]).map((st) => {
                  const colTasks = tasks.filter((t) => t.status === st)
                  return (
                    <div key={st} style={{ background: 'var(--bg-2)', borderRadius: 10, padding: 12 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                        <span className={`badge ${TASK_STATUS_BADGES[st]}`} style={{ fontWeight: 700 }}>
                          {TASK_STATUS_LABELS[st]} ({colTasks.length})
                        </span>
                      </div>
                      <div style={{ display: 'grid', gap: 10 }}>
                        {colTasks.map((t) => (
                          <div
                            key={t.id}
                            style={{
                              background: 'var(--panel)',
                              border: selectedTaskId === t.id ? '2px solid var(--primary)' : '1px solid var(--border)',
                              borderRadius: 8,
                              padding: 12,
                              cursor: 'pointer',
                              boxShadow: '0 2px 4px rgba(0,0,0,0.04)',
                            }}
                            onClick={() => fetchTaskDetail(t.id)}
                          >
                            <div style={{ fontWeight: 600, marginBottom: 6, fontSize: 14 }}>{t.title}</div>
                            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 8 }}>
                              <span className={`badge sm ${TASK_PRIORITY_BADGES[t.priority]}`}>
                                {TASK_PRIORITY_LABELS[t.priority]}
                              </span>
                              {t.attachmentCount > 0 && (
                                <span className="badge sm secondary">📎 {t.attachmentCount}</span>
                              )}
                            </div>
                            {t.assignees && t.assignees.length > 0 && (
                              <div style={{ fontSize: 12, color: 'var(--text-2)', display: 'flex', alignItems: 'center', gap: 4 }}>
                                <Users size={12} /> {t.assignees.map((a) => a.name).join(', ')}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : (
              /* LIST TABLE */
              <div className="table-responsive">
                <table className="table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Tiêu đề</th>
                      <th>Trạng thái</th>
                      <th>Độ ưu tiên</th>
                      <th>Người phụ trách</th>
                      <th>Tệp đính kèm</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tasks.map((t) => (
                      <tr key={t.id}>
                        <td>#{t.id}</td>
                        <td style={{ fontWeight: 600 }}>{t.title}</td>
                        <td>
                          <span className={`badge ${TASK_STATUS_BADGES[t.status]}`}>
                            {TASK_STATUS_LABELS[t.status]}
                          </span>
                        </td>
                        <td>
                          <span className={`badge ${TASK_PRIORITY_BADGES[t.priority]}`}>
                            {TASK_PRIORITY_LABELS[t.priority]}
                          </span>
                        </td>
                        <td>
                          {t.assignees.length > 0
                            ? t.assignees.map((a) => a.name).join(', ')
                            : <span style={{ color: 'var(--text-3)' }}>Chưa gán</span>}
                        </td>
                        <td>{t.attachmentCount} tệp</td>
                        <td>
                          <div style={{ display: 'flex', gap: 4 }}>
                            <button
                              type="button"
                              className="btn ghost sm"
                              onClick={() => fetchTaskDetail(t.id)}
                            >
                              Chi tiết
                            </button>
                            <button
                              type="button"
                              className="btn ghost sm danger"
                              onClick={() => handleDeleteTask(t.id)}
                            >
                              <Trash2 size={14} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* TASK DETAIL SIDE PANEL */}
          {selectedTaskId && taskDetail && (
            <div className="panel" style={{ width: 380, flexShrink: 0 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h3 style={{ margin: 0 }}>Chi tiết Nhiệm vụ #{taskDetail.id}</h3>
                <button type="button" className="btn ghost sm" onClick={() => setSelectedTaskId(null)}>
                  <X size={16} />
                </button>
              </div>

              <div style={{ display: 'grid', gap: 16 }}>
                <div>
                  <h4 style={{ margin: '0 0 6px 0', fontSize: 16 }}>{taskDetail.title}</h4>
                  <p style={{ margin: 0, color: 'var(--text-2)', fontSize: 13 }}>
                    {taskDetail.description || 'Không có mô tả'}
                  </p>
                </div>

                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <select
                    className={`select sm ${TASK_STATUS_BADGES[taskDetail.status]}`}
                    value={taskDetail.status}
                    onChange={(e) => handleStatusChange(taskDetail.id, e.target.value as TaskStatus)}
                  >
                    {(Object.keys(TASK_STATUS_LABELS) as TaskStatus[]).map((st) => (
                      <option key={st} value={st}>
                        {TASK_STATUS_LABELS[st]}
                      </option>
                    ))}
                  </select>

                  <span className={`badge ${TASK_PRIORITY_BADGES[taskDetail.priority]}`}>
                    {TASK_PRIORITY_LABELS[taskDetail.priority]}
                  </span>
                </div>

                {/* Assignees Section */}
                <div style={{ borderTop: '1px solid var(--border)', paddingTop: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <strong style={{ fontSize: 13 }}>👥 Người phụ trách ({taskDetail.assignees.length}):</strong>
                    <button
                      type="button"
                      className="btn secondary sm"
                      onClick={() => setShowAssigneeModal(true)}
                    >
                      <UserPlus size={14} /> Gán
                    </button>
                  </div>
                  {taskDetail.assignees.length === 0 ? (
                    <span style={{ color: 'var(--text-3)', fontSize: 13 }}>Chưa có ai phụ trách</span>
                  ) : (
                    <div style={{ display: 'grid', gap: 6 }}>
                      {taskDetail.assignees.map((a) => (
                        <div
                          key={a.userId}
                          style={{
                            display: 'flex',
                            justify: 'space-between',
                            alignItems: 'center',
                            background: 'var(--bg-2)',
                            padding: '4px 8px',
                            borderRadius: 6,
                            fontSize: 13,
                          }}
                        >
                          <span>{a.name} (ID: {a.userId})</span>
                          <button
                            type="button"
                            className="btn ghost sm danger"
                            onClick={() => handleRemoveAssignee(a.userId)}
                            title="Gỡ người này"
                          >
                            <X size={12} />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Attachments Section */}
                <div style={{ borderTop: '1px solid var(--border)', paddingTop: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <strong style={{ fontSize: 13 }}>📎 Tệp đính kèm ({taskDetail.attachments.length}):</strong>
                    <button
                      type="button"
                      className="btn secondary sm"
                      onClick={() => setShowAttachmentModal(true)}
                    >
                      <Upload size={14} /> Đính kèm
                    </button>
                  </div>
                  {taskDetail.attachments.length === 0 ? (
                    <span style={{ color: 'var(--text-3)', fontSize: 13 }}>Chưa có tệp đính kèm</span>
                  ) : (
                    <div style={{ display: 'grid', gap: 6 }}>
                      {taskDetail.attachments.map((att) => (
                        <div
                          key={att.id}
                          style={{
                            background: 'var(--bg-2)',
                            padding: '6px 10px',
                            borderRadius: 6,
                            fontSize: 12,
                          }}
                        >
                          <div style={{ fontWeight: 600, display: 'flex', justifyContent: 'space-between' }}>
                            <span>{att.originalName}</span>
                            <span className="badge sm secondary">{att.attachmentType}</span>
                          </div>
                          {att.description && <div style={{ color: 'var(--text-2)', marginTop: 2 }}>{att.description}</div>}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Submit Task Action */}
                <div style={{ borderTop: '1px solid var(--border)', paddingTop: 12 }}>
                  <button
                    type="button"
                    className="btn primary"
                    style={{ width: '100%' }}
                    onClick={() => setShowSubmitModal(true)}
                  >
                    <Send size={16} /> Nộp kết quả nhiệm vụ (Submit)
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: CRITERIA */}
      {activeTab === 'criteria' && (
        <div className="panel">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <h2>Tiêu chí đánh giá động cho dự án</h2>
            <button type="button" className="btn primary" onClick={() => setShowCritModal(true)}>
              <Plus size={16} /> Thêm tiêu chí mới
            </button>
          </div>

          {loadingCriteria ? (
            <div>Đang tải tiêu chí...</div>
          ) : criteria.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 30, color: 'var(--text-3)' }}>Chưa có tiêu chí nào.</div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Thứ tự</th>
                  <th>Tên tiêu chí</th>
                  <th>Mô tả</th>
                  <th>Điểm tối đa</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {criteria.map((c) => (
                  <tr key={c.id}>
                    <td>{c.displayOrder}</td>
                    <td style={{ fontWeight: 600 }}>{c.name}</td>
                    <td>{c.description || '-'}</td>
                    <td><strong style={{ color: 'var(--primary)' }}>{c.maxScore}</strong></td>
                    <td>
                      <span className={`badge ${c.isActive ? 'badge-success' : 'badge-secondary'}`}>
                        {c.isActive ? 'Đang bật' : 'Tắt'}
                      </span>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="btn ghost sm"
                        onClick={() => handleToggleCritActive(c)}
                      >
                        {c.isActive ? 'Tắt' : 'Bật'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* TAB 3: EVALUATIONS */}
      {activeTab === 'evaluations' && (
        <div className="panel">
          <h2>Đánh giá thành viên trong dự án</h2>
          <p style={{ color: 'var(--text-2)', marginBottom: 20 }}>
            Chấm điểm đóng góp của từng thành viên dựa trên các tiêu chí đã kích hoạt.
          </p>

          <form onSubmit={handleSubmitEvaluation} style={{ display: 'grid', gap: 16, maxWidth: 600 }}>
            <div>
              <label className="field-label">User ID thành viên cần đánh giá *</label>
              <input
                type="number"
                className="input"
                placeholder="Nhập ID thành viên (ví dụ: 2)"
                value={evalSelectedUserId ?? ''}
                onChange={(e) => setEvalSelectedUserId(e.target.value ? Number(e.target.value) : null)}
                required
              />
            </div>

            <div>
              <label className="field-label">Điểm số theo tiêu chí:</label>
              {criteria.filter((c) => c.isActive).length === 0 ? (
                <div style={{ color: 'var(--danger)', fontSize: 13 }}>
                  Chưa có tiêu chí đánh giá nào đang mở. Vui lòng sang tab Tiêu chí để tạo thêm.
                </div>
              ) : (
                <div style={{ display: 'grid', gap: 12 }}>
                  {criteria
                    .filter((c) => c.isActive)
                    .map((crit) => (
                      <div
                        key={crit.id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justify: 'space-between',
                          background: 'var(--bg-2)',
                          padding: '8px 12px',
                          borderRadius: 8,
                        }}
                      >
                        <div>
                          <strong>{crit.name}</strong>
                          <div style={{ fontSize: 12, color: 'var(--text-2)' }}>
                            Tối đa: {crit.maxScore} điểm
                          </div>
                        </div>
                        <input
                          type="number"
                          step="0.5"
                          min="0"
                          max={crit.maxScore}
                          className="input sm"
                          style={{ width: 90 }}
                          placeholder={`0 - ${crit.maxScore}`}
                          value={evalScoresMap[crit.id] ?? ''}
                          onChange={(e) =>
                            setEvalScoresMap((prev) => ({ ...prev, [crit.id]: e.target.value }))
                          }
                          required
                        />
                      </div>
                    ))}
                </div>
              )}
            </div>

            <div>
              <label className="field-label">Ghi chú / Nhận xét chung</label>
              <textarea
                className="textarea"
                rows={3}
                placeholder="Nhận xét ưu điểm, điểm cần cải thiện..."
                value={evalNote}
                onChange={(e) => setEvalNote(e.target.value)}
              />
            </div>

            <button
              type="submit"
              className="btn primary"
              disabled={submittingEval || criteria.filter((c) => c.isActive).length === 0}
            >
              {submittingEval ? 'Đang lưu...' : 'Gửi phiếu đánh giá'}
            </button>
          </form>
        </div>
      )}

      {/* CREATE/EDIT TASK MODAL */}
      {showTaskModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3>{editingTask ? 'Chỉnh sửa nhiệm vụ' : 'Tạo nhiệm vụ mới'}</h3>
            <form onSubmit={handleSaveTask} style={{ display: 'grid', gap: 12, marginTop: 12 }}>
              <div>
                <label className="field-label">Tiêu đề nhiệm vụ *</label>
                <input
                  type="text"
                  className="input"
                  value={taskFormTitle}
                  onChange={(e) => setTaskFormTitle(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="field-label">Mô tả chi tiết</label>
                <textarea
                  className="textarea"
                  rows={3}
                  value={taskFormDesc}
                  onChange={(e) => setTaskFormDesc(e.target.value)}
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label className="field-label">Độ ưu tiên</label>
                  <select
                    className="select"
                    value={taskFormPriority}
                    onChange={(e) => setTaskFormPriority(e.target.value as TaskPriority)}
                  >
                    {(Object.keys(TASK_PRIORITY_LABELS) as TaskPriority[]).map((pr) => (
                      <option key={pr} value={pr}>
                        {TASK_PRIORITY_LABELS[pr]}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="field-label">Trạng thái</label>
                  <select
                    className="select"
                    value={taskFormStatus}
                    onChange={(e) => setTaskFormStatus(e.target.value as TaskStatus)}
                  >
                    {(Object.keys(TASK_STATUS_LABELS) as TaskStatus[]).map((st) => (
                      <option key={st} value={st}>
                        {TASK_STATUS_LABELS[st]}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label className="field-label">Ngày bắt đầu</label>
                  <input
                    type="datetime-local"
                    className="input"
                    value={taskFormStartAt}
                    onChange={(e) => setTaskFormStartAt(e.target.value)}
                  />
                </div>
                <div>
                  <label className="field-label">Hạn nộp (Due date)</label>
                  <input
                    type="datetime-local"
                    className="input"
                    value={taskFormDueAt}
                    onChange={(e) => setTaskFormDueAt(e.target.value)}
                  />
                </div>
              </div>
              <div style={{ display: 'flex', justifyRight: 'flex-end', gap: 8, marginTop: 12 }}>
                <button type="button" className="btn ghost" onClick={() => setShowTaskModal(false)}>
                  Hủy
                </button>
                <button type="submit" className="btn primary" disabled={savingTask}>
                  {savingTask ? 'Đang lưu...' : 'Lưu nhiệm vụ'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ADD ASSIGNEES MODAL */}
      {showAssigneeModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3>Phân công người phụ trách</h3>
            <form onSubmit={handleAddAssignees} style={{ display: 'grid', gap: 12, marginTop: 12 }}>
              <div>
                <label className="field-label">User ID (phân cách bằng dấu phẩy)</label>
                <input
                  type="text"
                  className="input"
                  placeholder="Ví dụ: 2, 3, 5"
                  value={assigneeUserIdsInput}
                  onChange={(e) => setAssigneeUserIdsInput(e.target.value)}
                  required
                />
              </div>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button type="button" className="btn ghost" onClick={() => setShowAssigneeModal(false)}>
                  Hủy
                </button>
                <button type="submit" className="btn primary" disabled={savingAssignee}>
                  {savingAssignee ? 'Đang gán...' : 'Gán người phụ trách'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ATTACHMENT MODAL */}
      {showAttachmentModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3>Đính kèm tệp vào nhiệm vụ</h3>
            <form onSubmit={handleAttachFile} style={{ display: 'grid', gap: 12, marginTop: 12 }}>
              <div>
                <label className="field-label">Chọn tệp *</label>
                <input
                  type="file"
                  className="input"
                  onChange={(e) => setAttachFile(e.target.files?.[0] || null)}
                  required
                />
              </div>
              <div>
                <label className="field-label">Loại tệp</label>
                <select
                  className="select"
                  value={attachType}
                  onChange={(e) => setAttachType(e.target.value as AttachmentType)}
                >
                  <option value="INPUT">INPUT - Tài liệu đầu vào</option>
                  <option value="REFERENCE">REFERENCE - Tài liệu tham khảo</option>
                  <option value="RESULT">RESULT - Kết quả báo cáo</option>
                </select>
              </div>
              <div>
                <label className="field-label">Mô tả tệp</label>
                <input
                  type="text"
                  className="input"
                  placeholder="Ghi chú về tệp..."
                  value={attachDesc}
                  onChange={(e) => setAttachDesc(e.target.value)}
                />
              </div>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button type="button" className="btn ghost" onClick={() => setShowAttachmentModal(false)}>
                  Hủy
                </button>
                <button type="submit" className="btn primary" disabled={uploadingAttach}>
                  {uploadingAttach ? 'Đang tải lên...' : 'Tải lên & Đính kèm'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* SUBMIT TASK RESULT MODAL */}
      {showSubmitModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3>Nộp kết quả nhiệm vụ (Submit)</h3>
            <form onSubmit={handleSubmitTask} style={{ display: 'grid', gap: 12, marginTop: 12 }}>
              <div>
                <label className="field-label">Chọn tệp báo cáo kết quả *</label>
                <input
                  type="file"
                  className="input"
                  onChange={(e) => setSubmitFile(e.target.files?.[0] || null)}
                  required
                />
              </div>
              <div>
                <label className="field-label">Ghi chú nộp bài</label>
                <textarea
                  className="textarea"
                  rows={3}
                  placeholder="Ghi chú kết quả hoàn thành..."
                  value={submitNote}
                  onChange={(e) => setSubmitNote(e.target.value)}
                />
              </div>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button type="button" className="btn ghost" onClick={() => setShowSubmitModal(false)}>
                  Hủy
                </button>
                <button type="submit" className="btn primary" disabled={submittingResult}>
                  {submittingResult ? 'Đang nộp...' : 'Nộp báo cáo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CREATE CRITERION MODAL */}
      {showCritModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3>Tạo tiêu chí đánh giá mới</h3>
            <form onSubmit={handleCreateCriterion} style={{ display: 'grid', gap: 12, marginTop: 12 }}>
              <div>
                <label className="field-label">Tên tiêu chí *</label>
                <input
                  type="text"
                  className="input"
                  placeholder="Ví dụ: Tiến độ hoàn thành"
                  value={critName}
                  onChange={(e) => setCritName(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="field-label">Mô tả tiêu chí</label>
                <textarea
                  className="textarea"
                  rows={2}
                  value={critDesc}
                  onChange={(e) => setCritDesc(e.target.value)}
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label className="field-label">Điểm tối đa</label>
                  <input
                    type="number"
                    step="0.5"
                    className="input"
                    value={critMaxScore}
                    onChange={(e) => setCritMaxScore(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label className="field-label">Thứ tự hiển thị</label>
                  <input
                    type="number"
                    className="input"
                    value={critDisplayOrder}
                    onChange={(e) => setCritDisplayOrder(e.target.value)}
                    required
                  />
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button type="button" className="btn ghost" onClick={() => setShowCritModal(false)}>
                  Hủy
                </button>
                <button type="submit" className="btn primary" disabled={savingCrit}>
                  {savingCrit ? 'Đang lưu...' : 'Lưu tiêu chí'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

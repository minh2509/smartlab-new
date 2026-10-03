import {
  ChevronDown,
  ChevronUp,
  Download,
  FileCheck,
  FilePlus2,
  FileText,
  History,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Trash2,
  Undo2,
  Upload,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { useAuth } from '../../auth/authContext'
import { downloadFile } from '../../files/api'
import type { Project } from '../../projects/types'
import {
  createDocumentVersion,
  createProjectDocument,
  deleteDocument,
  downloadDocument,
  listDocumentVersions,
  listProjectDocuments,
  updateProjectDocument,
} from '../api'
import type { DocumentAccessScope, DocumentVersion, ProjectDocument } from '../types'
import './ProjectDocumentsPanel.css'

const MAX_FILE_SIZE = 25 * 1024 * 1024
const ACCEPTED_FILE_TYPES = [
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'application/pdf',
  'text/plain',
  'text/csv',
  'application/zip',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
].join(',')

const ACCESS_SCOPE_OPTIONS: Array<{
  value: DocumentAccessScope
  label: string
  description: string
}> = [
  { value: 'PROJECT', label: 'Trong dự án', description: 'Thành viên có quyền truy cập dự án.' },
  { value: 'LAB', label: 'Nội bộ Lab', description: 'Mọi thành viên đã đăng nhập.' },
  { value: 'PUBLIC', label: 'Công khai', description: 'Có thể chia sẻ ngoài Lab.' },
  { value: 'PRIVATE', label: 'Riêng tư', description: 'Chỉ người được backend cho phép.' },
]

type ProjectDocumentsPanelProps = {
  project: Project | null
  onDirtyChange?: (dirty: boolean) => void
  onBusyChange?: (busy: boolean) => void
}

type CreateDocumentForm = {
  title: string
  description: string
  accessScope: DocumentAccessScope
  note: string
  file: File | null
}

type VersionForm = {
  accessScope: DocumentAccessScope
  note: string
  file: File | null
}

type DocumentEditForm = {
  title: string
  description: string
}

const EMPTY_CREATE_FORM: CreateDocumentForm = {
  title: '',
  description: '',
  accessScope: 'PROJECT',
  note: '',
  file: null,
}

export function ProjectDocumentsPanel({ project, onDirtyChange, onBusyChange }: ProjectDocumentsPanelProps) {
  const { token, profile } = useAuth()
  const toast = useToast()
  const projectId = project?.id ?? null
  const createFileInputRef = useRef<HTMLInputElement>(null)
  const versionFileInputRef = useRef<HTMLInputElement>(null)
  const versionsRequestIdRef = useRef(0)
  const initialAutoOpenRef = useRef(false)

  const [documents, setDocuments] = useState<ProjectDocument[]>([])
  const [versions, setVersions] = useState<DocumentVersion[]>([])
  const [expandedDocumentId, setExpandedDocumentId] = useState<number | null>(null)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState<CreateDocumentForm>(EMPTY_CREATE_FORM)
  const [versionForm, setVersionForm] = useState<VersionForm>({
    accessScope: 'PROJECT',
    note: '',
    file: null,
  })
  const [editingDocumentId, setEditingDocumentId] = useState<number | null>(null)
  const [editForm, setEditForm] = useState<DocumentEditForm>({ title: '', description: '' })
  const [loading, setLoading] = useState(false)
  const [loadingVersions, setLoadingVersions] = useState(false)
  const [busyAction, setBusyAction] = useState<string | null>(null)
  const [error, setError] = useState('')

  const isAdminManager = Boolean(
    profile?.roles.includes('ADMIN') && profile.permissions.includes('PROJECT_MANAGE'),
  )
  const isProjectLeader = Boolean(
    profile && project?.leaders.some((leader) => leader.userId === profile.userId),
  )
  const canManage = isAdminManager || isProjectLeader

  const createDirty = Boolean(
    createForm.file
    || createForm.title.trim()
    || createForm.description.trim()
    || createForm.note.trim()
    || createForm.accessScope !== 'PROJECT',
  )
  const versionDirty = Boolean(
    versionForm.file
    || versionForm.note.trim()
    || (
      expandedDocumentId
      && versionForm.accessScope !== scopeOf(documents.find((item) => item.id === expandedDocumentId))
    ),
  )
  const editingDocument = documents.find((item) => item.id === editingDocumentId)
  const editDirty = Boolean(
    editingDocument
    && (editForm.title !== editingDocument.title
      || editForm.description !== (editingDocument.description ?? '')),
  )

  useEffect(() => {
    onDirtyChange?.(createDirty || versionDirty || editDirty)
  }, [createDirty, editDirty, onDirtyChange, versionDirty])

  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange])

  useEffect(() => {
    onBusyChange?.(Boolean(busyAction))
  }, [busyAction, onBusyChange])

  useEffect(() => () => onBusyChange?.(false), [onBusyChange])

  const loadDocuments = useCallback(async () => {
    if (!token || !projectId) return
    const result = await listProjectDocuments(token, projectId)
    setDocuments(result)
  }, [projectId, token])

  useEffect(() => {
    versionsRequestIdRef.current += 1
    initialAutoOpenRef.current = false
    setDocuments([])
    setVersions([])
    setExpandedDocumentId(null)
    setEditingDocumentId(null)
    setIsCreateOpen(false)
    setEditForm({ title: '', description: '' })
    setCreateForm(EMPTY_CREATE_FORM)
    setVersionForm({ accessScope: 'PROJECT', note: '', file: null })
    setError('')
    setLoadingVersions(false)
    if (createFileInputRef.current) createFileInputRef.current.value = ''
    if (versionFileInputRef.current) versionFileInputRef.current.value = ''
    if (!token || !projectId) return

    let active = true
    setLoading(true)
    void listProjectDocuments(token, projectId)
      .then((result) => {
        if (active) {
          setDocuments(result)
          if (result.length === 0 && canManage && !initialAutoOpenRef.current) {
            initialAutoOpenRef.current = true
            setIsCreateOpen(true)
          }
        }
      })
      .catch((reason: unknown) => {
        if (active) setError(errorMessage(reason, 'Không tải được tài liệu của dự án.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
      versionsRequestIdRef.current += 1
    }
  }, [canManage, projectId, token])

  const sortedDocuments = useMemo(
    () => [...documents].sort((left, right) => dateValue(right.updatedAt) - dateValue(left.updatedAt)),
    [documents],
  )

  async function handleToggleCreate() {
    if (isCreateOpen) {
      if (createDirty) {
        const discard = await confirmDialog({
          title: 'Bỏ thông tin tài liệu đang tạo?',
          description: 'Thông tin và file bạn đã nhập chưa được lưu sẽ bị hủy.',
          confirmLabel: 'Bỏ thay đổi',
          destructive: true,
        })
        if (!discard) return
      }
      setCreateForm(EMPTY_CREATE_FORM)
      if (createFileInputRef.current) createFileInputRef.current.value = ''
      setIsCreateOpen(false)
    } else {
      clearFeedback()
      setIsCreateOpen(true)
    }
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !project || !canManage || busyAction) return

    const validationError = validateDocumentForm(createForm)
    if (validationError) {
      setError(validationError)
      return
    }

    setBusyAction('create')
    clearFeedback()
    try {
      const created = await createProjectDocument(token, project.id, {
        file: createForm.file!,
        title: createForm.title,
        description: createForm.description,
        accessScope: createForm.accessScope,
        note: createForm.note,
      })
      setDocuments((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setCreateForm(EMPTY_CREATE_FORM)
      setIsCreateOpen(false)
      if (createFileInputRef.current) createFileInputRef.current.value = ''
      toast.success('Đã tạo tài liệu mới', `“${created.title}” đã được tạo với phiên bản v1.`)
    } catch (reason: unknown) {
      const message = errorMessage(reason, 'Không thể tạo tài liệu.')
      setError(message)
      toast.error('Không thể tạo tài liệu', message)
    } finally {
      setBusyAction(null)
    }
  }

  async function toggleVersions(document: ProjectDocument) {
    if (!token || busyAction) return
    if (
      expandedDocumentId !== null
      && versionDirty
      && !await confirmDialog({ title: 'Bỏ thay đổi phiên bản?', description: 'Thông tin và file phiên bản mới chưa được lưu sẽ bị bỏ.', confirmLabel: 'Bỏ thay đổi' })
    ) return

    versionsRequestIdRef.current += 1
    if (expandedDocumentId === document.id) {
      setExpandedDocumentId(null)
      setVersions([])
      setLoadingVersions(false)
      resetVersionForm()
      return
    }

    const requestId = versionsRequestIdRef.current
    clearFeedback()
    setExpandedDocumentId(document.id)
    setVersions([])
    setVersionForm({ accessScope: scopeOf(document), note: '', file: null })
    if (versionFileInputRef.current) versionFileInputRef.current.value = ''
    setLoadingVersions(true)
    try {
      const result = await listDocumentVersions(token, document.id)
      if (versionsRequestIdRef.current === requestId) setVersions(sortVersions(result))
    } catch (reason: unknown) {
      if (versionsRequestIdRef.current === requestId) {
        setError(errorMessage(reason, 'Không tải được lịch sử phiên bản.'))
      }
    } finally {
      if (versionsRequestIdRef.current === requestId) setLoadingVersions(false)
    }
  }

  async function handleCreateVersion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !expandedDocumentId || !canManage || busyAction) return

    const fileError = validateFile(versionForm.file)
    if (fileError) {
      setError(fileError)
      return
    }

    const document = documents.find((item) => item.id === expandedDocumentId)
    if (!document) return

    setBusyAction(`version:${document.id}`)
    clearFeedback()
    try {
      const created = await createDocumentVersion(token, document.id, {
        file: versionForm.file!,
        accessScope: versionForm.accessScope,
        note: versionForm.note,
      })
      setVersions((current) => sortVersions([created, ...current.filter((item) => item.id !== created.id)]))
      setDocuments((current) => current.map((item) => item.id === document.id
        ? {
            ...item,
            currentFile: created.file,
            currentVersionNo: created.versionNo,
            updatedAt: created.createdAt,
          }
        : item))
      setVersionForm({ accessScope: created.file.accessScope as DocumentAccessScope, note: '', file: null })
      if (versionFileInputRef.current) versionFileInputRef.current.value = ''
      toast.success('Đã tải lên phiên bản mới', `Phiên bản ${created.versionNo} của “${document.title}” đã được lưu.`)
    } catch (reason: unknown) {
      const message = errorMessage(reason, 'Không thể tạo phiên bản mới.')
      setError(message)
      toast.error('Không thể tạo phiên bản mới', message)
    } finally {
      setBusyAction(null)
    }
  }

  function startEditing(document: ProjectDocument) {
    if (!canManage || busyAction) return
    clearFeedback()
    setEditingDocumentId(document.id)
    setEditForm({ title: document.title, description: document.description ?? '' })
  }

  function cancelEditing() {
    clearFeedback()
    setEditingDocumentId(null)
    setEditForm({ title: '', description: '' })
  }

  async function handleUpdateMetadata(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !editingDocument || !canManage || busyAction) return

    const validationError = validateMetadataForm(editForm)
    if (validationError) {
      setError(validationError)
      return
    }

    setBusyAction(`edit:${editingDocument.id}`)
    clearFeedback()
    try {
      const updated = await updateProjectDocument(token, editingDocument.id, editForm)
      setDocuments((current) => current.map((item) => item.id === updated.id ? updated : item))
      cancelEditing()
      toast.success('Đã cập nhật tài liệu', `“${updated.title}” đã được đồng bộ.`)
    } catch (reason: unknown) {
      const message = errorMessage(reason, 'Không thể cập nhật thông tin tài liệu.')
      setError(message)
      toast.error('Không thể cập nhật tài liệu', message)
    } finally {
      setBusyAction(null)
    }
  }

  async function handleDownload(document: ProjectDocument) {
    if (!token || busyAction) return
    setBusyAction(`download:${document.id}`)
    clearFeedback()
    try {
      const downloaded = await downloadDocument(token, document.id)
      saveBlob(downloaded.blob, downloaded.filename || document.currentFile.originalName)
    } catch (reason: unknown) {
      const message = errorMessage(reason, 'Không thể tải tài liệu xuống.')
      setError(message)
      toast.error('Không thể tải tài liệu xuống', message)
    } finally {
      setBusyAction(null)
    }
  }

  async function handleDownloadVersion(version: DocumentVersion) {
    if (!token || busyAction) return
    setBusyAction(`download-version:${version.id}`)
    clearFeedback()
    try {
      const blob = await downloadFile(token, version.file.id)
      saveBlob(blob, version.file.originalName)
    } catch (reason: unknown) {
      const message = errorMessage(reason, `Không thể tải phiên bản ${version.versionNo}.`)
      setError(message)
      toast.error('Không thể tải phiên bản', message)
    } finally {
      setBusyAction(null)
    }
  }

  async function handleDelete(document: ProjectDocument) {
    if (
      !token
      || !canManage
      || busyAction
    ) return
    if (!await confirmDialog({ title: 'Xóa tài liệu?', description: `Tài liệu “${document.title}” sẽ được xóa mềm. Lịch sử trong cơ sở dữ liệu vẫn được giữ lại.`, confirmLabel: 'Xóa tài liệu', destructive: true })) return

    setBusyAction(`delete:${document.id}`)
    clearFeedback()
    try {
      await deleteDocument(token, document.id)
      setDocuments((current) => current.filter((item) => item.id !== document.id))
      if (expandedDocumentId === document.id) {
        versionsRequestIdRef.current += 1
        setExpandedDocumentId(null)
        setVersions([])
        setLoadingVersions(false)
        resetVersionForm()
      }
      toast.success('Đã xóa mềm tài liệu', `“${document.title}” vẫn được giữ trong lịch sử.`)
    } catch (reason: unknown) {
      const message = errorMessage(reason, 'Không thể xóa tài liệu.')
      setError(message)
      toast.error('Không thể xóa tài liệu', message)
    } finally {
      setBusyAction(null)
    }
  }

  function resetVersionForm() {
    setVersionForm({ accessScope: 'PROJECT', note: '', file: null })
    if (versionFileInputRef.current) versionFileInputRef.current.value = ''
  }

  function clearFeedback() {
    setError('')
  }

  if (!project) {
    return (
      <section className="panel page-section project-documents-panel">
        <EmptyState title="Chưa chọn dự án" description="Chọn một dự án để xem tài liệu và lịch sử phiên bản." />
      </section>
    )
  }

  return (
    <section className="panel page-section project-documents-panel">
      {/* Panel Header */}
      <div className="panel-head project-documents-panel-head">
        <div className="project-documents-head-intro">
          <h2>Tài liệu dự án</h2>
          <p>Quản lý các tài liệu kỹ thuật, nghiên cứu và theo dõi đầy đủ lịch sử phiên bản.</p>
        </div>
        <div className="project-documents-head-actions">
          <span className="badge info">{documents.length} tài liệu</span>
          <button
            className="btn ghost table-btn"
            type="button"
            disabled={loading || Boolean(busyAction) || editingDocumentId !== null}
            title="Tải lại danh sách tài liệu"
            onClick={() => {
              setLoading(true)
              clearFeedback()
              void loadDocuments()
                .catch((reason: unknown) => setError(errorMessage(reason, 'Không tải lại được tài liệu.')))
                .finally(() => setLoading(false))
            }}
          >
            <RefreshCw size={14} aria-hidden="true" /> Tải lại
          </button>
          {canManage ? (
            <button
              className={`btn ${isCreateOpen ? 'ghost' : 'primary'} table-btn project-documents-create-toggle`}
              type="button"
              disabled={loading || Boolean(busyAction) || editingDocumentId !== null}
              aria-expanded={isCreateOpen}
              onClick={() => void handleToggleCreate()}
            >
              {isCreateOpen ? (
                <>
                  <X size={14} aria-hidden="true" /> Đóng tạo mới
                </>
              ) : (
                <>
                  <Plus size={14} aria-hidden="true" /> Thêm tài liệu
                </>
              )}
            </button>
          ) : null}
        </div>
      </div>

      <Feedback error={error} />

      {/* Collapsible Create Document Card */}
      {canManage && isCreateOpen ? (
        <form className="project-document-create-card" onSubmit={handleCreate}>
          <div className="project-document-create-head">
            <div className="project-document-create-title-wrap">
              <span className="project-document-create-icon"><FilePlus2 size={20} aria-hidden="true" /></span>
              <div>
                <h3>Tạo tài liệu mới</h3>
                <p>File được tải lên sẽ đồng thời tạo phiên bản đầu tiên (v1). Thông tin metadata có thể chỉnh sửa riêng sau này.</p>
              </div>
            </div>
            <button
              className="btn ghost icon-btn project-document-create-close"
              type="button"
              title="Đóng biểu mẫu tạo tài liệu"
              aria-label="Đóng biểu mẫu tạo tài liệu"
              disabled={Boolean(busyAction)}
              onClick={() => void handleToggleCreate()}
            >
              <X size={18} aria-hidden="true" />
            </button>
          </div>

          <div className="project-document-create-grid">
            <div className="project-document-create-col">
              <label className="field">
                <span>Tiêu đề tài liệu <strong className="required-mark">*</strong></span>
                <input
                  className="input"
                  required
                  maxLength={255}
                  value={createForm.title}
                  disabled={Boolean(busyAction) || editingDocumentId !== null}
                  placeholder="Ví dụ: Báo cáo kiến trúc hệ thống Q3"
                  onChange={(event) => setCreateForm((current) => ({ ...current, title: event.target.value }))}
                />
              </label>

              <label className="field">
                <span>Mô tả</span>
                <textarea
                  className="textarea"
                  rows={3}
                  maxLength={20000}
                  value={createForm.description}
                  disabled={Boolean(busyAction) || editingDocumentId !== null}
                  placeholder="Mô tả tóm tắt nội dung, phạm vi hoặc mục đích của tài liệu..."
                  onChange={(event) => setCreateForm((current) => ({ ...current, description: event.target.value }))}
                />
              </label>
            </div>

            <div className="project-document-create-col">
              <label className="field">
                <span>File đính kèm (phiên bản v1) <strong className="required-mark">*</strong></span>
                <input
                  ref={createFileInputRef}
                  className="project-document-file-input"
                  type="file"
                  accept={ACCEPTED_FILE_TYPES}
                  required
                  disabled={Boolean(busyAction) || editingDocumentId !== null}
                  onChange={(event) => {
                    clearFeedback()
                    setCreateForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))
                  }}
                />
                <small className="project-document-help-text">Hỗ trợ PDF, Office, ảnh, văn bản, ZIP. Tối đa 25 MB.</small>
              </label>

              <label className="field">
                <span>Phạm vi truy cập</span>
                <PopupSelect
                  value={createForm.accessScope}
                  disabled={Boolean(busyAction) || editingDocumentId !== null}
                  ariaLabel="Phạm vi truy cập tài liệu"
                  options={ACCESS_SCOPE_OPTIONS}
                  onChange={(value) => setCreateForm((current) => ({ ...current, accessScope: value as DocumentAccessScope }))}
                />
                <small className="project-document-scope-help">{scopeDescription(createForm.accessScope)}</small>
              </label>

              <label className="field">
                <span>Ghi chú phiên bản đầu tiên</span>
                <input
                  className="input"
                  maxLength={5000}
                  value={createForm.note}
                  disabled={Boolean(busyAction) || editingDocumentId !== null}
                  placeholder="Ví dụ: Khởi tạo tài liệu dự án..."
                  onChange={(event) => setCreateForm((current) => ({ ...current, note: event.target.value }))}
                />
              </label>
            </div>
          </div>

          <div className="project-document-create-actions">
            <button
              className="btn primary"
              type="submit"
              disabled={!createForm.file || !createForm.title.trim() || Boolean(busyAction) || editingDocumentId !== null}
            >
              <FilePlus2 size={16} aria-hidden="true" />
              {busyAction === 'create' ? 'Đang tạo...' : 'Tạo tài liệu'}
            </button>
            <button
              className="btn ghost"
              type="button"
              disabled={Boolean(busyAction)}
              onClick={() => void handleToggleCreate()}
            >
              Hủy
            </button>
          </div>
        </form>
      ) : null}

      {/* Document List */}
      <div className="project-document-list">
        {loading ? <div className="empty tight">Đang tải tài liệu...</div> : null}
        {!loading && !sortedDocuments.length && !isCreateOpen ? (
          <EmptyState
            title="Chưa có tài liệu"
            description={canManage
              ? 'Dự án chưa có tài liệu nào. Hãy nhấn “Thêm tài liệu” ở trên để bắt đầu.'
              : 'Dự án chưa có tài liệu mà bạn có quyền xem.'}
          />
        ) : null}

        {!loading ? sortedDocuments.map((document) => {
          const isEditing = editingDocumentId === document.id
          const isExpanded = expandedDocumentId === document.id

          return (
            <article
              className={`project-document-card ${isEditing ? 'is-editing' : ''} ${isExpanded ? 'is-expanded' : ''}`}
              key={document.id}
            >
              <div className="project-document-card-main">
                {/* Header: File type badge + Title + Badges */}
                <div className="project-document-header-row">
                  <div className="project-document-title-cluster">
                    <span className="project-document-type-badge">
                      {getFileExtension(document.currentFile.originalName)}
                    </span>
                    <div className="project-document-title-group">
                      {isEditing ? (
                        <div className="project-document-editing-banner">
                          <Pencil size={14} aria-hidden="true" />
                          <span>Chỉnh sửa thông tin tài liệu (file đính kèm được giữ nguyên)</span>
                        </div>
                      ) : (
                        <div className="project-document-title-line">
                          <h3 className="project-document-title">{document.title}</h3>
                          <div className="project-document-badges">
                            <span className="badge info" title={`Phiên bản hiện tại: v${document.currentVersionNo}`}>
                              v{document.currentVersionNo}
                            </span>
                            <span className={`badge scope-badge scope-${document.currentFile.accessScope.toLowerCase()}`}>
                              {scopeLabel(document.currentFile.accessScope)}
                            </span>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Edit Form OR Document Content */}
                {isEditing ? (
                  <form className="project-document-edit-form" onSubmit={handleUpdateMetadata}>
                    <label className="field">
                      <span>Tiêu đề tài liệu <strong className="required-mark">*</strong></span>
                      <input
                        className="input"
                        required
                        maxLength={255}
                        value={editForm.title}
                        disabled={Boolean(busyAction)}
                        onChange={(event) => setEditForm((current) => ({ ...current, title: event.target.value }))}
                      />
                    </label>
                    <label className="field">
                      <span>Mô tả</span>
                      <textarea
                        className="textarea"
                        rows={3}
                        maxLength={20000}
                        value={editForm.description}
                        disabled={Boolean(busyAction)}
                        placeholder="Nội dung hoặc mục đích của tài liệu..."
                        onChange={(event) => setEditForm((current) => ({ ...current, description: event.target.value }))}
                      />
                    </label>
                    <div className="form-actions">
                      <button className="btn primary" type="submit" disabled={!editDirty || Boolean(busyAction)}>
                        <Save size={15} aria-hidden="true" />
                        {busyAction === `edit:${document.id}` ? 'Đang lưu...' : 'Lưu thông tin'}
                      </button>
                      <button className="btn ghost" type="button" disabled={Boolean(busyAction)} onClick={cancelEditing}>
                        <Undo2 size={15} aria-hidden="true" /> Hủy
                      </button>
                    </div>
                  </form>
                ) : (
                  <>
                    {document.description ? (
                      <p className="project-document-description">{document.description}</p>
                    ) : null}

                    {/* Current File Asset Row */}
                    <div className="project-document-file-bar">
                      <div className="project-document-file-info">
                        <div className="project-document-file-chip">
                          <FileText size={16} className="file-chip-icon" aria-hidden="true" />
                          <span className="project-document-file-name" title={document.currentFile.originalName}>
                            {document.currentFile.originalName}
                          </span>
                        </div>
                        <div className="project-document-meta-chips">
                          <span>{formatBytes(document.currentFile.sizeBytes)}</span>
                          <span className="meta-sep">·</span>
                          <span>Cập nhật {formatDateTime(document.updatedAt)}</span>
                          <span className="meta-sep">·</span>
                          <span>{document.createdBy?.name ?? 'Tài khoản không còn tồn tại'}</span>
                        </div>
                      </div>

                      <button
                        className="btn primary table-btn project-document-download-btn"
                        type="button"
                        disabled={Boolean(busyAction) || editingDocumentId !== null}
                        title={`Tải file hiện tại: ${document.currentFile.originalName}`}
                        onClick={() => void handleDownload(document)}
                      >
                        <Download size={15} aria-hidden="true" />
                        <span>{busyAction === `download:${document.id}` ? 'Đang tải...' : 'Tải xuống'}</span>
                      </button>
                    </div>
                  </>
                )}

                {/* Footer Actions Bar */}
                {!isEditing ? (
                  <div className="project-document-footer-bar">
                    <div className="project-document-footer-left">
                      <button
                        className={`btn ghost table-btn project-document-history-toggle ${isExpanded ? 'is-active' : ''}`}
                        type="button"
                        disabled={Boolean(busyAction) || editingDocumentId !== null}
                        aria-expanded={isExpanded}
                        aria-controls={`project-document-history-${document.id}`}
                        onClick={() => void toggleVersions(document)}
                      >
                        <History size={15} aria-hidden="true" />
                        <span>Lịch sử phiên bản</span>
                        <span className="history-count-pill">{document.currentVersionNo}</span>
                        {isExpanded ? (
                          <ChevronUp size={14} aria-hidden="true" />
                        ) : (
                          <ChevronDown size={14} aria-hidden="true" />
                        )}
                      </button>
                    </div>

                    {canManage ? (
                      <div className="project-document-footer-right">
                        <button
                          className="btn ghost table-btn"
                          type="button"
                          disabled={Boolean(busyAction) || (editingDocumentId !== null && editingDocumentId !== document.id)}
                          title="Chỉnh sửa tiêu đề và mô tả tài liệu"
                          onClick={() => startEditing(document)}
                        >
                          <Pencil size={14} aria-hidden="true" /> Sửa
                        </button>
                        <button
                          className="btn ghost table-btn danger-text"
                          type="button"
                          disabled={Boolean(busyAction) || editingDocumentId !== null}
                          title="Xóa mềm tài liệu khỏi dự án"
                          onClick={() => void handleDelete(document)}
                        >
                          <Trash2 size={14} aria-hidden="true" /> Xóa
                        </button>
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </div>

              {/* Version History Drawer */}
              {isExpanded ? (
                <div className="project-document-history" id={`project-document-history-${document.id}`}>
                  <div className="project-document-history-head">
                    <div>
                      <h4>Lịch sử phiên bản</h4>
                      <p>Mỗi lần tải file mới sẽ sinh ra một phiên bản mới. File hiện tại (v{document.currentVersionNo}) là bản mặc định khi tải xuống.</p>
                    </div>
                    <span className="badge info">{versions.length} bản lưu</span>
                  </div>

                  {loadingVersions ? <div className="empty tight">Đang tải lịch sử phiên bản...</div> : null}

                  {!loadingVersions && versions.length ? (
                    <div className="project-document-versions">
                      {versions.map((version) => {
                        const isCurrent = version.versionNo === document.currentVersionNo
                        return (
                          <div className={`project-document-version-row ${isCurrent ? 'is-current' : ''}`} key={version.id}>
                            <div className="project-document-version-col-number">
                              <span className="project-document-version-number">v{version.versionNo}</span>
                              {isCurrent ? (
                                <span className="current-version-pill" title="File đang được sử dụng làm bản hiện tại">
                                  <FileCheck size={12} aria-hidden="true" /> Hiện tại
                                </span>
                              ) : null}
                            </div>

                            <div className="project-document-version-info">
                              <strong className="project-document-version-filename">{version.file.originalName}</strong>
                              <div className="project-document-version-meta">
                                <span>{formatBytes(version.file.sizeBytes)}</span>
                                <span>·</span>
                                <span className={`scope-text scope-${version.file.accessScope.toLowerCase()}`}>{scopeLabel(version.file.accessScope)}</span>
                                <span>·</span>
                                <span>{formatDateTime(version.createdAt)}</span>
                                <span>·</span>
                                <span>{version.uploadedBy?.name ?? 'Tài khoản không còn tồn tại'}</span>
                              </div>
                              {version.note ? (
                                <div className="project-document-version-note">
                                  <p>{version.note}</p>
                                </div>
                              ) : null}
                            </div>

                            <button
                              className="btn ghost table-btn project-document-version-download"
                              type="button"
                              disabled={Boolean(busyAction) || editingDocumentId !== null}
                              aria-label={`Tải phiên bản ${version.versionNo}: ${version.file.originalName}`}
                              onClick={() => void handleDownloadVersion(version)}
                            >
                              <Download size={14} aria-hidden="true" />
                              <span>{busyAction === `download-version:${version.id}` ? 'Đang tải...' : 'Tải bản này'}</span>
                            </button>
                          </div>
                        )
                      })}
                    </div>
                  ) : null}

                  {!loadingVersions && !versions.length ? (
                    <EmptyState title="Chưa có phiên bản" description="Không tìm thấy lịch sử phiên bản của tài liệu." />
                  ) : null}

                  {/* Upload New Version Section (for managers) */}
                  {canManage ? (
                    <form className="project-document-version-form" onSubmit={handleCreateVersion}>
                      <div className="project-document-version-form-head">
                        <h5>Tải lên phiên bản mới (v{document.currentVersionNo + 1})</h5>
                        <p>File mới sẽ trở thành phiên bản hiện tại để tải xuống. Tiêu đề và mô tả của tài liệu vẫn được giữ nguyên.</p>
                      </div>

                      <div className="project-document-version-form-fields">
                        <label className="field">
                          <span>File phiên bản mới <strong className="required-mark">*</strong></span>
                          <input
                            ref={versionFileInputRef}
                            className="project-document-file-input"
                            type="file"
                            accept={ACCEPTED_FILE_TYPES}
                            disabled={Boolean(busyAction) || editingDocumentId !== null}
                            onChange={(event) => {
                              clearFeedback()
                              setVersionForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))
                            }}
                          />
                        </label>

                        <label className="field">
                          <span>Phạm vi truy cập</span>
                          <PopupSelect
                            value={versionForm.accessScope}
                            disabled={Boolean(busyAction) || editingDocumentId !== null}
                            ariaLabel="Phạm vi truy cập phiên bản"
                            options={ACCESS_SCOPE_OPTIONS}
                            onChange={(value) => setVersionForm((current) => ({ ...current, accessScope: value as DocumentAccessScope }))}
                          />
                          <small className="project-document-scope-help">{scopeDescription(versionForm.accessScope)}</small>
                        </label>

                        <label className="field">
                          <span>Ghi chú phiên bản</span>
                          <textarea
                            className="textarea"
                            rows={2}
                            maxLength={5000}
                            value={versionForm.note}
                            disabled={Boolean(busyAction) || editingDocumentId !== null}
                            placeholder="Ví dụ: Cập nhật kết quả thử nghiệm tháng 9, chỉnh sửa bảng số liệu..."
                            onChange={(event) => setVersionForm((current) => ({ ...current, note: event.target.value }))}
                          />
                        </label>
                      </div>

                      <div className="form-actions">
                        <button
                          className="btn primary"
                          type="submit"
                          disabled={!versionForm.file || Boolean(busyAction) || editingDocumentId !== null}
                        >
                          <Upload size={15} aria-hidden="true" />
                          {busyAction === `version:${document.id}` ? 'Đang tải lên...' : 'Tải lên phiên bản mới'}
                        </button>
                        <button
                          className="btn ghost"
                          type="button"
                          disabled={!versionDirty || Boolean(busyAction) || editingDocumentId !== null}
                          onClick={() => {
                            setVersionForm({ accessScope: scopeOf(document), note: '', file: null })
                            if (versionFileInputRef.current) versionFileInputRef.current.value = ''
                          }}
                        >
                          Hủy thay đổi
                        </button>
                      </div>
                    </form>
                  ) : null}
                </div>
              ) : null}
            </article>
          )
        }) : null}
      </div>

      {!canManage ? (
        <div className="project-documents-readonly-notice">
          <span>Chế độ chỉ đọc: Chỉ Admin hoặc Leader của dự án mới có quyền thêm tài liệu, sửa thông tin hoặc tải phiên bản mới.</span>
        </div>
      ) : null}
    </section>
  )
}

function validateDocumentForm(form: CreateDocumentForm) {
  if (!form.title.trim()) return 'Tiêu đề tài liệu là bắt buộc.'
  if (form.title.trim().length > 255) return 'Tiêu đề tài liệu không được vượt quá 255 ký tự.'
  return validateFile(form.file)
}

function validateMetadataForm(form: DocumentEditForm) {
  if (!form.title.trim()) return 'Tiêu đề tài liệu là bắt buộc.'
  if (form.title.trim().length > 255) return 'Tiêu đề tài liệu không được vượt quá 255 ký tự.'
  if (form.description.trim().length > 20000) return 'Mô tả tài liệu không được vượt quá 20000 ký tự.'
  return null
}

function validateFile(file: File | null) {
  if (!file) return 'Vui lòng chọn file cần tải lên.'
  if (file.size === 0) return 'File không được để trống.'
  if (file.size > MAX_FILE_SIZE) return 'File vượt quá giới hạn 25 MB.'
  return null
}

function scopeOf(document?: ProjectDocument) {
  const scope = document?.currentFile.accessScope
  return ACCESS_SCOPE_OPTIONS.some((option) => option.value === scope)
    ? scope as DocumentAccessScope
    : 'PROJECT'
}

function scopeLabel(scope: string) {
  return ACCESS_SCOPE_OPTIONS.find((option) => option.value === scope)?.label ?? scope
}

function scopeDescription(scope: DocumentAccessScope) {
  return ACCESS_SCOPE_OPTIONS.find((option) => option.value === scope)?.description ?? ''
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function getFileExtension(filename: string): string {
  const parts = filename.split('.')
  if (parts.length > 1) {
    const ext = parts.pop()?.toUpperCase() ?? ''
    return ext.length <= 5 ? ext : 'TỆP'
  }
  return 'TỆP'
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date)
}

function dateValue(value: string) {
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

function sortVersions(versions: DocumentVersion[]) {
  return [...versions].sort((left, right) => right.versionNo - left.versionNo)
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = window.document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}

import {
  ChevronDown,
  ChevronUp,
  Download,
  FilePlus2,
  FileText,
  History,
  RefreshCw,
  Trash2,
  Upload,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
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

const EMPTY_CREATE_FORM: CreateDocumentForm = {
  title: '',
  description: '',
  accessScope: 'PROJECT',
  note: '',
  file: null,
}

export function ProjectDocumentsPanel({ project, onDirtyChange, onBusyChange }: ProjectDocumentsPanelProps) {
  const { token, profile } = useAuth()
  const projectId = project?.id ?? null
  const createFileInputRef = useRef<HTMLInputElement>(null)
  const versionFileInputRef = useRef<HTMLInputElement>(null)
  const versionsRequestIdRef = useRef(0)
  const [documents, setDocuments] = useState<ProjectDocument[]>([])
  const [versions, setVersions] = useState<DocumentVersion[]>([])
  const [expandedDocumentId, setExpandedDocumentId] = useState<number | null>(null)
  const [createForm, setCreateForm] = useState<CreateDocumentForm>(EMPTY_CREATE_FORM)
  const [versionForm, setVersionForm] = useState<VersionForm>({
    accessScope: 'PROJECT',
    note: '',
    file: null,
  })
  const [loading, setLoading] = useState(false)
  const [loadingVersions, setLoadingVersions] = useState(false)
  const [busyAction, setBusyAction] = useState<string | null>(null)
  const [message, setMessage] = useState('')
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
    || createForm.title
    || createForm.description
    || createForm.note
    || createForm.accessScope !== 'PROJECT',
  )
  const versionDirty = Boolean(
    versionForm.file
    || versionForm.note
    || (
      expandedDocumentId
      && versionForm.accessScope !== scopeOf(documents.find((item) => item.id === expandedDocumentId))
    ),
  )

  useEffect(() => {
    onDirtyChange?.(createDirty || versionDirty)
  }, [createDirty, onDirtyChange, versionDirty])

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
    setDocuments([])
    setVersions([])
    setExpandedDocumentId(null)
    setCreateForm(EMPTY_CREATE_FORM)
    setVersionForm({ accessScope: 'PROJECT', note: '', file: null })
    setMessage('')
    setError('')
    setLoadingVersions(false)
    if (createFileInputRef.current) createFileInputRef.current.value = ''
    if (versionFileInputRef.current) versionFileInputRef.current.value = ''
    if (!token || !projectId) return

    let active = true
    setLoading(true)
    void listProjectDocuments(token, projectId)
      .then((result) => {
        if (active) setDocuments(result)
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
  }, [projectId, token])

  const sortedDocuments = useMemo(
    () => [...documents].sort((left, right) => dateValue(right.updatedAt) - dateValue(left.updatedAt)),
    [documents],
  )

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !project || !canManage || busyAction) return

    const validationError = validateDocumentForm(createForm)
    if (validationError) {
      setError(validationError)
      setMessage('')
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
      if (createFileInputRef.current) createFileInputRef.current.value = ''
      setMessage(`Đã tạo tài liệu “${created.title}” với phiên bản đầu tiên.`)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể tạo tài liệu.'))
    } finally {
      setBusyAction(null)
    }
  }

  async function toggleVersions(document: ProjectDocument) {
    if (!token || busyAction) return
    if (
      expandedDocumentId !== null
      && versionDirty
      && !window.confirm('Bỏ các thay đổi phiên bản chưa lưu?')
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
      setMessage('')
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
      setMessage(`Đã tải lên phiên bản ${created.versionNo} của “${document.title}”.`)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể tạo phiên bản mới.'))
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
      setError(errorMessage(reason, 'Không thể tải tài liệu xuống.'))
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
      setError(errorMessage(reason, `Không thể tải phiên bản ${version.versionNo}.`))
    } finally {
      setBusyAction(null)
    }
  }

  async function handleDelete(document: ProjectDocument) {
    if (
      !token
      || !canManage
      || busyAction
      || !window.confirm(`Xóa tài liệu “${document.title}”? Tài liệu sẽ được xóa mềm và giữ lại lịch sử trong database.`)
    ) return

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
      setMessage(`Đã xóa mềm tài liệu “${document.title}”.`)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể xóa tài liệu.'))
    } finally {
      setBusyAction(null)
    }
  }

  function resetVersionForm() {
    setVersionForm({ accessScope: 'PROJECT', note: '', file: null })
    if (versionFileInputRef.current) versionFileInputRef.current.value = ''
  }

  function clearFeedback() {
    setMessage('')
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
      <div className="panel-head">
        <div>
          <h2>Tài liệu dự án</h2>
          <p>Quản lý file hiện tại và giữ đầy đủ lịch sử phiên bản của từng tài liệu.</p>
        </div>
        <div className="project-documents-head-actions">
          <span className="badge info">{documents.length} tài liệu</span>
          <button
            className="btn ghost table-btn"
            type="button"
            disabled={loading || Boolean(busyAction)}
            onClick={() => {
              setLoading(true)
              clearFeedback()
              void loadDocuments()
                .catch((reason: unknown) => setError(errorMessage(reason, 'Không tải lại được tài liệu.')))
                .finally(() => setLoading(false))
            }}
          >
            <RefreshCw aria-hidden="true" /> Tải lại
          </button>
        </div>
      </div>

      <Feedback message={message} error={error} />

      <div className={`project-documents-layout ${canManage ? '' : 'read-only'}`}>
        <div className="project-document-list">
          {loading ? <div className="empty tight">Đang tải tài liệu...</div> : null}
          {!loading && !sortedDocuments.length ? (
            <EmptyState
              title="Chưa có tài liệu"
              description={canManage
                ? 'Tạo tài liệu đầu tiên bằng biểu mẫu bên cạnh.'
                : 'Dự án chưa có tài liệu mà bạn có quyền xem.'}
            />
          ) : null}
          {!loading ? sortedDocuments.map((document) => (
            <article className="project-document-card" key={document.id}>
              <div className="project-document-card-main">
                <span className="project-document-icon"><FileText aria-hidden="true" /></span>
                <div className="project-document-summary">
                  <div className="project-document-title-row">
                    <strong>{document.title}</strong>
                    <span className="badge info">Bản {document.currentVersionNo}</span>
                    <span className="badge success">{scopeLabel(document.currentFile.accessScope)}</span>
                  </div>
                  {document.description ? <p className="project-document-description">{document.description}</p> : null}
                  <span className="project-document-file-name">{document.currentFile.originalName}</span>
                  <div className="project-document-meta">
                    <span>{formatBytes(document.currentFile.sizeBytes)}</span>
                    <span>·</span>
                    <span>Cập nhật {formatDateTime(document.updatedAt)}</span>
                    <span>·</span>
                    <span>{document.createdBy?.name ?? 'Tài khoản không còn tồn tại'}</span>
                  </div>
                </div>
                <div className="project-document-actions">
                  <button
                    className="btn ghost table-btn"
                    type="button"
                    disabled={Boolean(busyAction)}
                    title="Tải file hiện tại"
                    onClick={() => void handleDownload(document)}
                  >
                    <Download aria-hidden="true" /> Tải xuống
                  </button>
                  <button
                    className="btn ghost table-btn"
                    type="button"
                    disabled={Boolean(busyAction)}
                    aria-expanded={expandedDocumentId === document.id}
                    aria-controls={`project-document-history-${document.id}`}
                    onClick={() => void toggleVersions(document)}
                  >
                    <History aria-hidden="true" /> Lịch sử
                    {expandedDocumentId === document.id
                      ? <ChevronUp aria-hidden="true" />
                      : <ChevronDown aria-hidden="true" />}
                  </button>
                  {canManage ? (
                    <button
                      className="btn ghost table-btn danger-text"
                      type="button"
                      disabled={Boolean(busyAction)}
                      title="Xóa mềm tài liệu"
                      onClick={() => void handleDelete(document)}
                    >
                      <Trash2 aria-hidden="true" /> Xóa
                    </button>
                  ) : null}
                </div>
              </div>

              {expandedDocumentId === document.id ? (
                <div className="project-document-history" id={`project-document-history-${document.id}`}>
                  <div className="project-document-history-head">
                    <h3>Lịch sử phiên bản</h3>
                    <span>File mới nhất luôn là file tải xuống mặc định.</span>
                  </div>
                  {loadingVersions ? <div className="empty tight">Đang tải lịch sử...</div> : null}
                  {!loadingVersions && versions.length ? (
                    <div className="project-document-versions">
                      {versions.map((version) => (
                        <div className="project-document-version-row" key={version.id}>
                          <span className="project-document-version-number">v{version.versionNo}</span>
                          <div className="project-document-version-info">
                            <strong>{version.file.originalName}</strong>
                            <div className="project-document-version-meta">
                              <span>{formatBytes(version.file.sizeBytes)}</span>
                              <span>·</span>
                              <span>{scopeLabel(version.file.accessScope)}</span>
                              <span>·</span>
                              <span>{formatDateTime(version.createdAt)}</span>
                              <span>·</span>
                              <span>{version.uploadedBy?.name ?? 'Tài khoản không còn tồn tại'}</span>
                            </div>
                            {version.note ? <p>{version.note}</p> : null}
                          </div>
                          <button
                            className="btn ghost table-btn project-document-version-download"
                            type="button"
                            disabled={Boolean(busyAction)}
                            aria-label={`Tải phiên bản ${version.versionNo}: ${version.file.originalName}`}
                            onClick={() => void handleDownloadVersion(version)}
                          >
                            <Download aria-hidden="true" />
                            {busyAction === `download-version:${version.id}` ? 'Đang tải...' : 'Tải bản này'}
                          </button>
                        </div>
                      ))}
                    </div>
                  ) : null}
                  {!loadingVersions && !versions.length ? (
                    <EmptyState title="Chưa có phiên bản" description="Không tìm thấy lịch sử phiên bản của tài liệu." />
                  ) : null}

                  {canManage ? (
                    <form className="project-document-version-form" onSubmit={handleCreateVersion}>
                      <h4>Tải phiên bản mới</h4>
                      <p>File mới sẽ trở thành file hiện tại; các phiên bản cũ vẫn được giữ lại.</p>
                      <label className="field">
                        <span>File mới</span>
                        <input
                          ref={versionFileInputRef}
                          className="project-document-file-input"
                          type="file"
                          accept={ACCEPTED_FILE_TYPES}
                          disabled={Boolean(busyAction)}
                          onChange={(event) => {
                            clearFeedback()
                            setVersionForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))
                          }}
                        />
                      </label>
                      <label className="field">
                        <span>Phạm vi truy cập</span>
                        <select
                          className="select"
                          value={versionForm.accessScope}
                          disabled={Boolean(busyAction)}
                          onChange={(event) => setVersionForm((current) => ({
                            ...current,
                            accessScope: event.target.value as DocumentAccessScope,
                          }))}
                        >
                          {ACCESS_SCOPE_OPTIONS.map((option) => (
                            <option value={option.value} key={option.value}>{option.label}</option>
                          ))}
                        </select>
                        <small className="project-document-scope-help">{scopeDescription(versionForm.accessScope)}</small>
                      </label>
                      <label className="field">
                        <span>Ghi chú phiên bản</span>
                        <textarea
                          className="textarea"
                          rows={2}
                          maxLength={5000}
                          value={versionForm.note}
                          disabled={Boolean(busyAction)}
                          placeholder="Ví dụ: Cập nhật kết quả thử nghiệm tháng 8..."
                          onChange={(event) => setVersionForm((current) => ({ ...current, note: event.target.value }))}
                        />
                      </label>
                      <div className="form-actions">
                        <button className="btn primary" type="submit" disabled={!versionForm.file || Boolean(busyAction)}>
                          <Upload aria-hidden="true" />
                          {busyAction === `version:${document.id}` ? 'Đang tải lên...' : 'Tạo phiên bản'}
                        </button>
                        <button
                          className="btn ghost"
                          type="button"
                          disabled={!versionDirty || Boolean(busyAction)}
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
          )) : null}
        </div>

        {canManage ? (
          <form className="project-document-create" onSubmit={handleCreate}>
            <h3><FilePlus2 size={18} aria-hidden="true" /> Tạo tài liệu</h3>
            <p>File được tải lên sẽ đồng thời tạo phiên bản đầu tiên.</p>
            <label className="field">
              <span>Tiêu đề</span>
              <input
                className="input"
                required
                maxLength={255}
                value={createForm.title}
                disabled={Boolean(busyAction)}
                placeholder="Tên tài liệu dễ nhận biết"
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
                disabled={Boolean(busyAction)}
                placeholder="Nội dung hoặc mục đích của tài liệu..."
                onChange={(event) => setCreateForm((current) => ({ ...current, description: event.target.value }))}
              />
            </label>
            <label className="field">
              <span>File phiên bản đầu tiên</span>
              <input
                ref={createFileInputRef}
                className="project-document-file-input"
                type="file"
                accept={ACCEPTED_FILE_TYPES}
                required
                disabled={Boolean(busyAction)}
                onChange={(event) => {
                  clearFeedback()
                  setCreateForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))
                }}
              />
            </label>
            <label className="field">
              <span>Phạm vi truy cập</span>
              <select
                className="select"
                value={createForm.accessScope}
                disabled={Boolean(busyAction)}
                onChange={(event) => setCreateForm((current) => ({
                  ...current,
                  accessScope: event.target.value as DocumentAccessScope,
                }))}
              >
                {ACCESS_SCOPE_OPTIONS.map((option) => (
                  <option value={option.value} key={option.value}>{option.label}</option>
                ))}
              </select>
              <small className="project-document-scope-help">{scopeDescription(createForm.accessScope)}</small>
            </label>
            <label className="field">
              <span>Ghi chú phiên bản</span>
              <textarea
                className="textarea"
                rows={2}
                maxLength={5000}
                value={createForm.note}
                disabled={Boolean(busyAction)}
                placeholder="Có thể để trống"
                onChange={(event) => setCreateForm((current) => ({ ...current, note: event.target.value }))}
              />
            </label>
            <button className="btn primary" type="submit" disabled={!createForm.file || !createForm.title.trim() || Boolean(busyAction)}>
              <FilePlus2 aria-hidden="true" />
              {busyAction === 'create' ? 'Đang tạo...' : 'Tạo tài liệu'}
            </button>
          </form>
        ) : (
          <p className="muted small">
            Bạn đang ở chế độ chỉ đọc. Chỉ Admin có quyền PROJECT_MANAGE hoặc leader đang hoạt động của dự án mới có thể thay đổi tài liệu.
          </p>
        )}
      </div>
    </section>
  )
}

function validateDocumentForm(form: CreateDocumentForm) {
  if (!form.title.trim()) return 'Tiêu đề tài liệu là bắt buộc.'
  if (form.title.trim().length > 255) return 'Tiêu đề tài liệu không được vượt quá 255 ký tự.'
  return validateFile(form.file)
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
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}

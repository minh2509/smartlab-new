import { AlertCircle, Check, ChevronDown, Download, FileText, FolderOpen, Globe2, LockKeyhole, Trash2, Upload, UsersRound } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { useAuth } from '../../auth/authContext'
import type { FileResponse } from '../../../shared/types/api'
import { D2_UPLOAD_ACCEPT, deleteFile, downloadFile, listOwnFiles, uploadFile } from '../api'
import type { FileAccessScope } from '../api'

const MAX_FILE_SIZE = 25 * 1024 * 1024
const scopeOptions: Array<{ value: FileAccessScope; label: string; description: string }> = [
  { value: 'PRIVATE', label: 'Riêng tư', description: 'Chỉ bạn và quản trị viên có thể tải xuống.' },
  { value: 'LAB', label: 'Nội bộ Lab', description: 'Mọi thành viên đã đăng nhập có thể tải xuống.' },
  { value: 'PUBLIC', label: 'Công khai', description: 'Có thể tải xuống mà không cần đăng nhập.' },
]

export function FilesPage() {
  const { token, profile } = useAuth()
  const toast = useToast()
  const inputRef = useRef<HTMLInputElement>(null)
  const scopeSelectorRef = useRef<HTMLDivElement>(null)
  const scopeTriggerRef = useRef<HTMLButtonElement>(null)
  const scopeListboxRef = useRef<HTMLUListElement>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [accessScope, setAccessScope] = useState<FileAccessScope>('PRIVATE')
  const [description, setDescription] = useState('')
  const [uploadedFiles, setUploadedFiles] = useState<FileResponse[]>([])
  const [loadingFiles, setLoadingFiles] = useState(true)
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [busyFileId, setBusyFileId] = useState<number | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [scopeSelectorOpen, setScopeSelectorOpen] = useState(false)
  const [focusedScopeIndex, setFocusedScopeIndex] = useState(0)

  const canDelete = profile?.permissions.includes('FILE_DELETE') ?? false
  const selectedScope = scopeOptions.find((option) => option.value === accessScope)
  const selectedScopeIndex = scopeOptions.findIndex((option) => option.value === accessScope)

  useEffect(() => {
    let cancelled = false
    if (!token) {
      setUploadedFiles([])
      setLoadingFiles(false)
      return
    }

    setLoadingFiles(true)
    void listOwnFiles(token)
      .then((files) => {
        if (!cancelled) setUploadedFiles(files)
      })
      .catch((reason: unknown) => {
        if (!cancelled) setLoadError(reason instanceof Error ? reason.message : 'Không thể tải danh sách file.')
      })
      .finally(() => {
        if (!cancelled) setLoadingFiles(false)
      })

    return () => { cancelled = true }
  }, [token])

  useEffect(() => {
    if (!scopeSelectorOpen) return

    const handlePointerDown = (event: MouseEvent) => {
      if (!scopeSelectorRef.current?.contains(event.target as Node)) setScopeSelectorOpen(false)
    }

    document.addEventListener('mousedown', handlePointerDown)
    scopeListboxRef.current?.focus()
    return () => document.removeEventListener('mousedown', handlePointerDown)
  }, [scopeSelectorOpen])

  const openScopeSelector = (index = selectedScopeIndex) => {
    setFocusedScopeIndex(index)
    setScopeSelectorOpen(true)
  }

  const selectScope = (option: typeof scopeOptions[number]) => {
    setAccessScope(option.value)
    setScopeSelectorOpen(false)
    requestAnimationFrame(() => scopeTriggerRef.current?.focus())
  }

  const handleScopeListKeyDown = (event: React.KeyboardEvent<HTMLUListElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      setScopeSelectorOpen(false)
      requestAnimationFrame(() => scopeTriggerRef.current?.focus())
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setFocusedScopeIndex((index) => (index + 1) % scopeOptions.length)
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setFocusedScopeIndex((index) => (index - 1 + scopeOptions.length) % scopeOptions.length)
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      selectScope(scopeOptions[focusedScopeIndex])
    }
  }

  const chooseFile = (file?: File) => {
    setValidationError(null)
    if (!file) {
      setSelectedFile(null)
      return
    }
    if (file.size === 0) {
      setValidationError('File không được để trống.')
      setSelectedFile(null)
      return
    }
    if (file.size > MAX_FILE_SIZE) {
      setValidationError('File vượt quá giới hạn 25 MB.')
      setSelectedFile(null)
      return
    }
    setSelectedFile(file)
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !selectedFile) {
      setValidationError('Vui lòng chọn file cần tải lên.')
      return
    }

    setUploading(true)
    setValidationError(null)
    try {
      const uploaded = await uploadFile(token, selectedFile, accessScope, description)
      setUploadedFiles((current) => [uploaded, ...current.filter((file) => file.id !== uploaded.id)])
      setSelectedFile(null)
      setDescription('')
      if (inputRef.current) inputRef.current.value = ''
      toast.success('Đã tải file lên', `“${uploaded.originalName}” đã được thêm vào kho lưu trữ.`)
    } catch (reason: unknown) {
      toast.error('Không thể tải file lên', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setUploading(false)
    }
  }

  const handleDownload = async (file: FileResponse) => {
    if (!token) return
    setBusyFileId(file.id)
    try {
      const blob = await downloadFile(token, file.id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = file.originalName
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (reason: unknown) {
      toast.error('Không thể tải file xuống', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setBusyFileId(null)
    }
  }

  const handleDelete = async (file: FileResponse) => {
    if (!token || !canDelete || !window.confirm(`Chuyển “${file.originalName}” vào thùng rác?`)) return
    setBusyFileId(file.id)
    try {
      await deleteFile(token, file.id)
      setUploadedFiles((current) => current.filter((item) => item.id !== file.id))
      toast.success('Đã chuyển file vào thùng rác', `“${file.originalName}” đã được gỡ khỏi danh sách.`)
    } catch (reason: unknown) {
      toast.error('Không thể xóa file', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setBusyFileId(null)
    }
  }

  return (
    <div className="files-page">
      <div className="files-page-intro">
        <div>
          <span className="files-product-label">Google Drive Storage</span>
          <h1>Tệp của tôi</h1>
          <p>Lưu trữ và quản lý các file cá nhân trong không gian làm việc SmartLab.</p>
        </div>
      </div>

      <Feedback error={loadError ?? undefined} />

      <form className="files-upload-surface" onSubmit={handleSubmit}>
        <div className="files-surface-head">
          <div>
            <h2>Tải file lên</h2>
            <p>Hình ảnh, PDF, tài liệu Office và ZIP.</p>
          </div>
          <span>Max 25 MB</span>
        </div>

        <div
          className={`file-dropzone ${dragging ? 'dragging' : ''} ${selectedFile ? 'has-file' : ''}`}
          role="button"
          tabIndex={0}
          aria-describedby={validationError ? 'file-selection-error' : 'file-selection-help'}
          onClick={() => inputRef.current?.click()}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault()
              inputRef.current?.click()
            }
          }}
          onDragEnter={(event) => { event.preventDefault(); setDragging(true) }}
          onDragOver={(event) => event.preventDefault()}
          onDragLeave={(event) => { event.preventDefault(); setDragging(false) }}
          onDrop={(event) => {
            event.preventDefault()
            setDragging(false)
            chooseFile(event.dataTransfer.files[0])
          }}
        >
          <input
            ref={inputRef}
            type="file"
            accept={D2_UPLOAD_ACCEPT}
            onChange={(event) => chooseFile(event.target.files?.[0])}
          />
          <span className="file-dropzone-icon" aria-hidden="true">{selectedFile ? <FileText /> : <Upload />}</span>
          <span className="file-dropzone-copy">
            <strong title={selectedFile?.name}>{selectedFile?.name ?? 'Kéo thả file vào đây hoặc chọn từ máy tính'}</strong>
            <small id="file-selection-help">{selectedFile ? `${formatBytes(selectedFile.size)} - Nhấn để chọn file khác` : 'Hỗ trợ ảnh, PDF, Office và ZIP. Tối đa 25 MB.'}</small>
          </span>
        </div>
        {validationError && <p className="file-selection-error" id="file-selection-error" role="alert"><AlertCircle aria-hidden="true" />{validationError}</p>}

        <div className="files-upload-fields">
          <div className="field files-scope-field" ref={scopeSelectorRef}>
            <span id="files-scope-label">Phạm vi truy cập</span>
            <button
              className={`scope-selector-trigger ${scopeSelectorOpen ? 'is-open' : ''}`}
              ref={scopeTriggerRef}
              type="button"
              aria-labelledby="files-scope-label files-scope-current"
              aria-haspopup="listbox"
              aria-expanded={scopeSelectorOpen}
              onClick={() => scopeSelectorOpen ? setScopeSelectorOpen(false) : openScopeSelector()}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault()
                  if (scopeSelectorOpen) setScopeSelectorOpen(false)
                  else openScopeSelector()
                } else if (event.key === 'ArrowDown') {
                  event.preventDefault()
                  openScopeSelector((selectedScopeIndex + 1) % scopeOptions.length)
                } else if (event.key === 'ArrowUp') {
                  event.preventDefault()
                  openScopeSelector((selectedScopeIndex - 1 + scopeOptions.length) % scopeOptions.length)
                } else if (event.key === 'Escape' && scopeSelectorOpen) {
                  event.preventDefault()
                  setScopeSelectorOpen(false)
                }
              }}
            >
              <ScopeIcon scope={accessScope} />
              <span id="files-scope-current">{selectedScope?.label}</span>
              <ChevronDown aria-hidden="true" />
            </button>
            {scopeSelectorOpen && <ul
              className="scope-selector-listbox"
              ref={scopeListboxRef}
              role="listbox"
              tabIndex={-1}
              aria-labelledby="files-scope-label"
              aria-activedescendant={`files-scope-option-${focusedScopeIndex}`}
              onKeyDown={handleScopeListKeyDown}
            >
              {scopeOptions.map((option, index) => (
                <li
                  className={`scope-selector-option ${index === focusedScopeIndex ? 'is-focused' : ''}`}
                  id={`files-scope-option-${index}`}
                  key={option.value}
                  role="option"
                  aria-selected={option.value === accessScope}
                  onMouseMove={() => setFocusedScopeIndex(index)}
                  onClick={() => selectScope(option)}
                >
                  <span className="scope-selector-option-check" aria-hidden="true">{option.value === accessScope && <Check />}</span>
                  <span><strong>{option.label}</strong><small>{option.description}</small></span>
                </li>
              ))}
            </ul>}
            <small className="muted">{selectedScope?.description}</small>
          </div>

          <label className="field files-description-field">
            <span>Mô tả <small>(không bắt buộc)</small></span>
            <textarea className="textarea" rows={2} maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Nội dung hoặc mục đích sử dụng của file..." />
          </label>
        </div>

        <div className="files-upload-actions">
          <button className="btn primary" type="submit" disabled={uploading || !selectedFile}>
            <Upload size={16} aria-hidden="true" />{uploading ? 'Đang tải lên...' : 'Tải file lên'}
          </button>
        </div>
      </form>

      <section className="files-list-surface" aria-labelledby="files-list-heading">
        <div className="files-list-head">
          <div>
            <h2 id="files-list-heading">File của bạn</h2>
            <p>Tất cả file bạn đã tải lên Google Drive của SmartLab.</p>
          </div>
          <span>{uploadedFiles.length} {uploadedFiles.length === 1 ? 'file' : 'files'}</span>
        </div>

        {loadingFiles ? <div className="files-list-loading" role="status"><FolderOpen aria-hidden="true" /><span>Đang tải danh sách file...</span></div> : uploadedFiles.length ? <div className="uploaded-file-list">
          {uploadedFiles.map((file) => (
            <article className="uploaded-file-row" key={file.id}>
              <span className="uploaded-file-icon" aria-hidden="true"><FileText /></span>
              <div className="uploaded-file-info">
                <strong title={file.originalName}>{file.originalName}</strong>
                <small>{fileType(file.originalName)} - {formatBytes(file.sizeBytes)} - {scopeLabel(file.accessScope)}{file.createdAt ? ` - ${formatDate(file.createdAt)}` : ''}</small>
                {file.description && <p>{file.description}</p>}
              </div>
              <div className="uploaded-file-actions">
                <button className="btn ghost table-btn" type="button" disabled={busyFileId === file.id} onClick={() => void handleDownload(file)} title="Tải xuống" aria-label={`Tải xuống ${file.originalName}`}><Download aria-hidden="true" /></button>
                {canDelete && <button className="btn ghost table-btn danger-text" type="button" disabled={busyFileId === file.id} onClick={() => void handleDelete(file)} title="Xóa file" aria-label={`Xóa ${file.originalName}`}><Trash2 aria-hidden="true" /></button>}
              </div>
            </article>
          ))}
        </div> : <div className="files-list-empty"><FolderOpen aria-hidden="true" /><strong>Chưa có file đã tải lên</strong><span>File tải lên thành công sẽ xuất hiện tại đây.</span></div>}
      </section>
    </div>
  )
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function scopeLabel(scope: string) {
  return scopeOptions.find((option) => option.value === scope)?.label ?? scope
}

function ScopeIcon({ scope }: { scope: FileAccessScope }) {
  if (scope === 'LAB') return <UsersRound aria-hidden="true" />
  if (scope === 'PUBLIC') return <Globe2 aria-hidden="true" />
  return <LockKeyhole aria-hidden="true" />
}

function fileType(originalName: string) {
  const extension = originalName.split('.').pop()?.trim()
  return extension ? extension.toUpperCase() : 'FILE'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

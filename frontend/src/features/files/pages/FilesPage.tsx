import {
  AlertCircle,
  Download,
  FileArchive,
  FileImage,
  FileSpreadsheet,
  FileText,
  FolderKanban,
  FolderOpen,
  Globe2,
  LockKeyhole,
  RotateCcw,
  Search,
  Trash2,
  Upload,
  UsersRound,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import type { PopupSelectOption } from '../../../shared/ui/PopupSelect'
import { useAuth } from '../../auth/authContext'
import type { FileResponse } from '../../../shared/types/api'
import { D2_UPLOAD_ACCEPT, deleteFile, downloadFile, listOwnFiles, uploadFile } from '../api'
import type { FileAccessScope } from '../api'
import './FilesPage.css'

const MAX_FILE_SIZE = 25 * 1024 * 1024

const scopeOptions: PopupSelectOption[] = [
  { value: 'PRIVATE', label: 'Riêng tư', description: 'Chỉ bạn và quản trị viên có thể tải xuống.' },
  { value: 'LAB', label: 'Nội bộ Lab', description: 'Mọi thành viên đã đăng nhập có thể tải xuống.' },
  { value: 'PUBLIC', label: 'Công khai', description: 'Có thể tải xuống mà không cần đăng nhập.' },
]

const filterScopeOptions: PopupSelectOption[] = [
  { value: 'ALL', label: 'Tất cả phạm vi' },
  { value: 'PRIVATE', label: 'Riêng tư' },
  { value: 'LAB', label: 'Nội bộ Lab' },
  { value: 'PROJECT', label: 'Trong dự án' },
  { value: 'PUBLIC', label: 'Công khai' },
]

const sortOptions: PopupSelectOption[] = [
  { value: 'newest', label: 'Mới nhất trước' },
  { value: 'oldest', label: 'Cũ nhất trước' },
  { value: 'name-asc', label: 'Tên file A - Z' },
  { value: 'size-desc', label: 'Dung lượng giảm dần' },
]

export function FilesPage() {
  const { token, profile } = useAuth()
  const toast = useToast()
  const inputRef = useRef<HTMLInputElement>(null)
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

  // Search, Filter & Sort State
  const [searchQuery, setSearchQuery] = useState('')
  const [scopeFilter, setScopeFilter] = useState<'ALL' | FileAccessScope>('ALL')
  const [sortBy, setSortBy] = useState<'newest' | 'oldest' | 'name-asc' | 'size-desc'>('newest')

  const canDelete = profile?.permissions.includes('FILE_DELETE') ?? false
  const selectedScope = scopeOptions.find((option) => option.value === accessScope)

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

    return () => {
      cancelled = true
    }
  }, [token])

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
      toast.success('Đã tải file lên', `"${uploaded.originalName}" đã được thêm vào kho lưu trữ.`)
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
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (reason: unknown) {
      toast.error('Không thể tải file xuống', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setBusyFileId(null)
    }
  }

  const handleDelete = async (file: FileResponse) => {
    if (!token || !canDelete || !window.confirm(`Chuyển "${file.originalName}" vào thùng rác?`)) return
    setBusyFileId(file.id)
    try {
      await deleteFile(token, file.id)
      setUploadedFiles((current) => current.filter((item) => item.id !== file.id))
      toast.success('Đã chuyển file vào thùng rác', `"${file.originalName}" đã được gỡ khỏi danh sách.`)
    } catch (reason: unknown) {
      toast.error('Không thể xóa file', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setBusyFileId(null)
    }
  }

  // Filter and sort files in memory
  const filteredFiles = useMemo(() => {
    let result = uploadedFiles

    // Scope filtering
    if (scopeFilter !== 'ALL') {
      result = result.filter((file) => file.accessScope === scopeFilter)
    }

    // Search query
    const q = searchQuery.trim().toLowerCase()
    if (q) {
      result = result.filter(
        (file) =>
          file.originalName.toLowerCase().includes(q) ||
          (file.description && file.description.toLowerCase().includes(q)),
      )
    }

    // Sorting
    result = [...result].sort((a, b) => {
      if (sortBy === 'oldest') {
        return (a.createdAt || '').localeCompare(b.createdAt || '')
      }
      if (sortBy === 'name-asc') {
        return a.originalName.localeCompare(b.originalName, 'vi')
      }
      if (sortBy === 'size-desc') {
        return b.sizeBytes - a.sizeBytes
      }
      // Default: newest
      return (b.createdAt || '').localeCompare(a.createdAt || '')
    })

    return result
  }, [uploadedFiles, scopeFilter, searchQuery, sortBy])

  const isFiltered = searchQuery.trim().length > 0 || scopeFilter !== 'ALL'

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
          <span className="files-surface-limit-badge">Tối đa 25 MB</span>
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
          onDragEnter={(event) => {
            event.preventDefault()
            setDragging(true)
          }}
          onDragOver={(event) => event.preventDefault()}
          onDragLeave={(event) => {
            event.preventDefault()
            setDragging(false)
          }}
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
          <span className="file-dropzone-icon" aria-hidden="true">
            {selectedFile ? <FileText /> : <Upload />}
          </span>
          <span className="file-dropzone-copy">
            <strong title={selectedFile?.name}>
              {selectedFile?.name ?? 'Kéo thả file vào đây hoặc chọn từ máy tính'}
            </strong>
            <small id="file-selection-help">
              {selectedFile
                ? `${formatBytes(selectedFile.size)} - Nhấn để chọn file khác`
                : 'Hỗ trợ ảnh, PDF, Office và ZIP. Tối đa 25 MB.'}
            </small>
          </span>
        </div>
        {validationError && (
          <p className="file-selection-error" id="file-selection-error" role="alert">
            <AlertCircle aria-hidden="true" />
            {validationError}
          </p>
        )}

        <div className="files-upload-fields">
          <div className="field files-scope-field">
            <span id="files-scope-label" className="files-field-label">
              Phạm vi truy cập
            </span>
            <PopupSelect
              ariaLabel="Chọn phạm vi truy cập file"
              value={accessScope}
              options={scopeOptions}
              onChange={(val) => setAccessScope(val as FileAccessScope)}
              className="files-scope-popup-select"
            />
            <small className="muted">{selectedScope?.description}</small>
          </div>

          <label className="field files-description-field">
            <span className="files-field-label">
              Mô tả <small>(không bắt buộc)</small>
            </span>
            <textarea
              className="textarea"
              rows={2}
              maxLength={5000}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Nội dung hoặc mục đích sử dụng của file..."
            />
          </label>
        </div>

        <div className="files-upload-actions">
          <button className="btn primary" type="submit" disabled={uploading || !selectedFile}>
            <Upload size={16} aria-hidden="true" />
            {uploading ? 'Đang tải lên...' : 'Tải file lên'}
          </button>
        </div>
      </form>

      <section className="files-list-surface" aria-labelledby="files-list-heading">
        <div className="files-list-head">
          <div>
            <h2 id="files-list-heading">File của bạn</h2>
            <p>
              {isFiltered
                ? `Hiển thị ${filteredFiles.length} trong tổng số ${uploadedFiles.length} file`
                : 'Tất cả file bạn đã tải lên Google Drive của SmartLab.'}
            </p>
          </div>
          <span className="files-count-badge">
            {filteredFiles.length} {filteredFiles.length === 1 ? 'file' : 'files'}
          </span>
        </div>

        {/* Filter & Search Toolbar */}
        <div className="files-filter-toolbar" role="search" aria-label="Tìm kiếm và lọc file">
          <div className="files-search-box">
            <Search size={16} className="files-search-icon" aria-hidden="true" />
            <input
              type="text"
              className="input files-search-input"
              placeholder="Tìm theo tên file hoặc mô tả..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Tìm kiếm file"
            />
            {searchQuery && (
              <button
                type="button"
                className="files-search-clear"
                onClick={() => setSearchQuery('')}
                aria-label="Xóa từ khóa tìm kiếm"
              >
                <X size={14} aria-hidden="true" />
              </button>
            )}
          </div>

          <div className="files-toolbar-controls">
            <div className="files-scope-filter">
              <PopupSelect
                ariaLabel="Lọc theo phạm vi truy cập"
                value={scopeFilter}
                options={filterScopeOptions}
                onChange={(val) => setScopeFilter(val as 'ALL' | FileAccessScope)}
                className="files-filter-select"
              />
            </div>

            <div className="files-sort-control">
              <PopupSelect
                ariaLabel="Sắp xếp danh sách file"
                value={sortBy}
                options={sortOptions}
                onChange={(val) => setSortBy(val as 'newest' | 'oldest' | 'name-asc' | 'size-desc')}
                className="files-sort-select"
              />
            </div>

            {isFiltered && (
              <button
                type="button"
                className="btn ghost files-reset-filters-btn"
                onClick={() => {
                  setSearchQuery('')
                  setScopeFilter('ALL')
                }}
              >
                <RotateCcw size={13} aria-hidden="true" />
                Đặt lại
              </button>
            )}
          </div>
        </div>

        {/* File List Content */}
        {loadingFiles ? (
          <div className="files-list-loading" role="status">
            <FolderOpen aria-hidden="true" />
            <span>Đang tải danh sách file...</span>
          </div>
        ) : uploadedFiles.length === 0 ? (
          <div className="files-list-empty">
            <FolderOpen aria-hidden="true" />
            <strong>Chưa có file đã tải lên</strong>
            <span>File tải lên thành công sẽ xuất hiện tại đây. Hãy kéo thả file vào khung phía trên để bắt đầu.</span>
          </div>
        ) : filteredFiles.length === 0 ? (
          <div className="files-list-empty">
            <Search aria-hidden="true" />
            <strong>Không tìm thấy file phù hợp</strong>
            <span>Không có file nào khớp với từ khóa tìm kiếm hoặc bộ lọc đã chọn.</span>
            <button
              type="button"
              className="btn secondary"
              onClick={() => {
                setSearchQuery('')
                setScopeFilter('ALL')
              }}
            >
              <RotateCcw size={14} aria-hidden="true" />
              Xóa bộ lọc tìm kiếm
            </button>
          </div>
        ) : (
          <div className="uploaded-file-list" role="list">
            {filteredFiles.map((file) => {
              const { icon, className } = getFileIcon(file.originalName)
              return (
                <article className="uploaded-file-row" key={file.id} role="listitem">
                  <span className={`uploaded-file-icon ${className}`} aria-hidden="true">
                    {icon}
                  </span>
                  <div className="uploaded-file-info">
                    <div className="uploaded-file-title-row">
                      <span className="uploaded-file-name" title={file.originalName}>
                        {file.originalName}
                      </span>
                      <ScopeBadge scope={file.accessScope} />
                    </div>
                    <div className="uploaded-file-meta">
                      <span>{fileType(file.originalName)}</span>
                      <span>{formatBytes(file.sizeBytes)}</span>
                      {file.createdAt && <span>{formatDate(file.createdAt)}</span>}
                    </div>
                    {file.description && <p className="uploaded-file-desc">{file.description}</p>}
                  </div>
                  <div className="uploaded-file-actions">
                    <button
                      className="btn ghost table-btn"
                      type="button"
                      disabled={busyFileId === file.id}
                      onClick={() => void handleDownload(file)}
                      title="Tải xuống"
                      aria-label={`Tải xuống ${file.originalName}`}
                    >
                      <Download aria-hidden="true" />
                    </button>
                    {canDelete && (
                      <button
                        className="btn ghost table-btn danger-text"
                        type="button"
                        disabled={busyFileId === file.id}
                        onClick={() => void handleDelete(file)}
                        title="Xóa file"
                        aria-label={`Xóa ${file.originalName}`}
                      >
                        <Trash2 aria-hidden="true" />
                      </button>
                    )}
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function ScopeBadge({ scope }: { scope: string }) {
  if (scope === 'PROJECT') {
    return (
      <span className="uploaded-file-scope-badge scope-project">
        <FolderKanban size={11} aria-hidden="true" />
        Trong dự án
      </span>
    )
  }
  if (scope === 'LAB') {
    return (
      <span className="uploaded-file-scope-badge scope-lab">
        <UsersRound size={11} aria-hidden="true" />
        Nội bộ Lab
      </span>
    )
  }
  if (scope === 'PUBLIC') {
    return (
      <span className="uploaded-file-scope-badge scope-public">
        <Globe2 size={11} aria-hidden="true" />
        Công khai
      </span>
    )
  }
  return (
    <span className="uploaded-file-scope-badge scope-private">
      <LockKeyhole size={11} aria-hidden="true" />
      Riêng tư
    </span>
  )
}

function getFileIcon(filename: string) {
  const ext = filename.split('.').pop()?.toLowerCase() ?? ''
  if (['jpg', 'jpeg', 'png', 'webp', 'gif', 'svg'].includes(ext)) {
    return { icon: <FileImage size={20} aria-hidden="true" />, className: 'icon-image' }
  }
  if (ext === 'pdf') {
    return { icon: <FileText size={20} aria-hidden="true" />, className: 'icon-pdf' }
  }
  if (['doc', 'docx', 'txt'].includes(ext)) {
    return { icon: <FileText size={20} aria-hidden="true" />, className: 'icon-doc' }
  }
  if (['csv', 'xls', 'xlsx'].includes(ext)) {
    return { icon: <FileSpreadsheet size={20} aria-hidden="true" />, className: 'icon-sheet' }
  }
  if (['zip', 'rar', 'tar', 'gz', '7z'].includes(ext)) {
    return { icon: <FileArchive size={20} aria-hidden="true" />, className: 'icon-archive' }
  }
  return { icon: <FileText size={20} aria-hidden="true" />, className: '' }
}

function fileType(originalName: string) {
  const extension = originalName.split('.').pop()?.trim()
  return extension ? extension.toUpperCase() : 'FILE'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

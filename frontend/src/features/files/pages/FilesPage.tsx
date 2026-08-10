import { Check, Download, FileText, FolderOpen, Trash2, Upload, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { useAuth } from '../../auth/authContext'
import type { FileResponse } from '../../../shared/types/api'
import { deleteFile, downloadFile, uploadFile } from '../api'
import type { FileAccessScope } from '../api'

const MAX_FILE_SIZE = 25 * 1024 * 1024
const ACCEPTED_TYPES = [
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

const scopeOptions: Array<{ value: FileAccessScope; label: string; description: string }> = [
  { value: 'PRIVATE', label: 'Riêng tư', description: 'Chỉ bạn và quản trị viên có thể tải xuống.' },
  { value: 'LAB', label: 'Nội bộ Lab', description: 'Mọi thành viên đã đăng nhập có thể tải xuống.' },
  { value: 'PUBLIC', label: 'Công khai', description: 'Có thể tải xuống mà không cần đăng nhập.' },
]

export function FilesPage() {
  const { token, profile } = useAuth()
  const inputRef = useRef<HTMLInputElement>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [accessScope, setAccessScope] = useState<FileAccessScope>('PRIVATE')
  const [description, setDescription] = useState('')
  const [recentFiles, setRecentFiles] = useState<FileResponse[]>([])
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [busyFileId, setBusyFileId] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const canDelete = profile?.permissions.includes('FILE_DELETE') ?? false

  const chooseFile = (file?: File) => {
    setMessage(null)
    setError(null)
    if (!file) {
      setSelectedFile(null)
      return
    }
    if (file.size === 0) {
      setError('File không được để trống.')
      setSelectedFile(null)
      return
    }
    if (file.size > MAX_FILE_SIZE) {
      setError('File vượt quá giới hạn 25 MB.')
      setSelectedFile(null)
      return
    }
    setSelectedFile(file)
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !selectedFile) {
      setError('Vui lòng chọn file cần tải lên.')
      return
    }

    setUploading(true)
    setMessage(null)
    setError(null)
    try {
      const uploaded = await uploadFile(token, selectedFile, accessScope, description)
      setRecentFiles((current) => [uploaded, ...current.filter((file) => file.id !== uploaded.id)])
      setSelectedFile(null)
      setDescription('')
      if (inputRef.current) inputRef.current.value = ''
      setMessage(`Đã tải lên “${uploaded.originalName}”.`)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể tải file lên.')
    } finally {
      setUploading(false)
    }
  }

  const handleDownload = async (file: FileResponse) => {
    if (!token) return
    setBusyFileId(file.id)
    setError(null)
    try {
      const blob = await downloadFile(token, file.id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = file.originalName
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể tải file xuống.')
    } finally {
      setBusyFileId(null)
    }
  }

  const handleDelete = async (file: FileResponse) => {
    if (!token || !canDelete || !window.confirm(`Chuyển “${file.originalName}” vào thùng rác?`)) return
    setBusyFileId(file.id)
    setMessage(null)
    setError(null)
    try {
      await deleteFile(token, file.id)
      setRecentFiles((current) => current.filter((item) => item.id !== file.id))
      setMessage(`Đã chuyển “${file.originalName}” vào thùng rác.`)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể xóa file.')
    } finally {
      setBusyFileId(null)
    }
  }

  return (
    <div className="files-page">
      <div className="page-title">
        <div>
          <span className="eyebrow">D2 · Google Drive storage</span>
          <h1>Tệp của tôi</h1>
          <p>Tải tài liệu lên kho lưu trữ của Smart Lab và kiểm soát phạm vi truy cập.</p>
        </div>
        <span className="badge info">Tối đa 25 MB / file</span>
      </div>

      {error && <div className="alert error"><X />{error}</div>}
      {message && <div className="alert"><Check />{message}</div>}

      <div className="panel-grid files-grid">
        <form className="panel" onSubmit={handleSubmit}>
          <div className="panel-head">
            <div><h2>Tải file mới</h2><p>Hỗ trợ ảnh, PDF, văn bản, ZIP và tài liệu Office.</p></div>
            <Upload size={20} />
          </div>

          <div className="form-stack">
            <div
              className={`file-dropzone ${dragging ? 'dragging' : ''} ${selectedFile ? 'has-file' : ''}`}
              role="button"
              tabIndex={0}
              onClick={() => inputRef.current?.click()}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') inputRef.current?.click()
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
                accept={ACCEPTED_TYPES}
                onChange={(event) => chooseFile(event.target.files?.[0])}
              />
              {selectedFile ? (
                <>
                  <span className="file-dropzone-icon"><FileText /></span>
                  <strong>{selectedFile.name}</strong>
                  <small>{formatBytes(selectedFile.size)} · Nhấn để chọn file khác</small>
                </>
              ) : (
                <>
                  <span className="file-dropzone-icon"><Upload /></span>
                  <strong>Kéo thả file vào đây</strong>
                  <small>hoặc nhấn để chọn từ máy tính</small>
                </>
              )}
            </div>

            <label className="field">
              <span>Phạm vi truy cập</span>
              <select className="select" value={accessScope} onChange={(event) => setAccessScope(event.target.value as FileAccessScope)}>
                {scopeOptions.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </select>
              <small className="muted">{scopeOptions.find((option) => option.value === accessScope)?.description}</small>
            </label>

            <label className="field">
              <span>Mô tả</span>
              <textarea className="textarea" rows={4} maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Nội dung hoặc mục đích sử dụng của file..." />
            </label>

            <button className="btn primary" type="submit" disabled={uploading || !selectedFile}>
              <Upload size={16} />{uploading ? 'Đang tải lên Drive...' : 'Tải file lên'}
            </button>
          </div>
        </form>

        <section className="panel">
          <div className="panel-head">
            <div><h2>Vừa tải lên</h2><p>Các file được tải lên trong phiên làm việc hiện tại.</p></div>
            <FolderOpen size={20} />
          </div>

          {recentFiles.length ? <div className="uploaded-file-list">
            {recentFiles.map((file) => (
              <article className="uploaded-file-row" key={file.id}>
                <span className="uploaded-file-icon"><FileText /></span>
                <div className="uploaded-file-info">
                  <strong>{file.originalName}</strong>
                  <small>{formatBytes(file.sizeBytes)} · {scopeLabel(file.accessScope)}{file.createdAt ? ` · ${formatDate(file.createdAt)}` : ''}</small>
                  {file.description && <p>{file.description}</p>}
                </div>
                <div className="uploaded-file-actions">
                  <button className="btn ghost table-btn" type="button" disabled={busyFileId === file.id} onClick={() => void handleDownload(file)} title="Tải xuống"><Download /></button>
                  {canDelete && <button className="btn ghost table-btn danger-text" type="button" disabled={busyFileId === file.id} onClick={() => void handleDelete(file)} title="Xóa file"><Trash2 /></button>}
                </div>
              </article>
            ))}
          </div> : <div className="empty tight"><FolderOpen /><strong>Chưa có file trong phiên này</strong><span>File tải lên thành công sẽ xuất hiện tại đây.</span></div>}
        </section>
      </div>
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

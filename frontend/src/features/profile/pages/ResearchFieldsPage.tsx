import { Edit3, FlaskConical, Image as ImageIcon, Plus, Save, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { apiClient } from '../../../lib/apiClient'
import { Feedback } from '../../../shared/components/Feedback'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'
import type { ResearchField } from '../../../shared/types/api'
import { useToast } from '../../../shared/toast/useToast'
import { useAuth } from '../../auth/authContext'
import { uploadPublicImage } from '../api'
import '../../content/adminContent.css'

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
const publicFileUrl = (fileId: number) => `${baseUrl}/files/${fileId}`
const RESEARCH_COVER_ACCEPT = 'image/jpeg,image/png,image/webp'
const RESEARCH_COVER_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
// Public backend default: smartlab.file.max-size-bytes=26214400. Runtime overrides remain backend-authoritative.
const RESEARCH_COVER_MAX_BYTES = 25 * 1024 * 1024

type CoverRetry = {
  fieldId: number
  fieldName: string
  file: File
  uploadedFileId?: number
}

function validateCoverFile(file: File | null) {
  if (!file) return null
  if (file.size <= 0) return 'Ảnh đại diện không được rỗng.'
  if (!RESEARCH_COVER_TYPES.has(file.type)) return 'Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP.'
  if (file.size > RESEARCH_COVER_MAX_BYTES) return 'Ảnh đại diện không được vượt quá 25 MiB.'
  return null
}

export function ResearchFieldsPage() {
  const { token, profile } = useAuth()
  const toast = useToast()
  const [fields, setFields] = useState<ResearchField[]>([])
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [coverFile, setCoverFile] = useState<File | null>(null)
  const [coverPreviewUrl, setCoverPreviewUrl] = useState<string | null>(null)
  const [coverPreviewByField, setCoverPreviewByField] = useState<Record<number, string>>({})
  const [editingFieldId, setEditingFieldId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [uploadingCoverId, setUploadingCoverId] = useState<number | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [coverErrors, setCoverErrors] = useState<Record<number, string>>({})
  const [coverRetry, setCoverRetry] = useState<CoverRetry | null>(null)
  const [brokenCoverIds, setBrokenCoverIds] = useState<Set<number>>(() => new Set())

  const hasFileUpload = profile?.permissions.includes('FILE_UPLOAD') ?? false

  const loadFields = useCallback(async () => {
    if (!token) return
    setLoadError(null)
    const nextFields = await apiClient<ResearchField[]>('/admin/research-fields', { token })
    setFields(nextFields)
  }, [token])

  useEffect(() => {
    void loadFields()
      .catch((reason: unknown) => setLoadError(reason instanceof Error ? reason.message : 'Không tải được lĩnh vực.'))
      .finally(() => setLoading(false))
  }, [loadFields])

  useEffect(() => () => {
    if (coverPreviewUrl) URL.revokeObjectURL(coverPreviewUrl)
  }, [coverPreviewUrl])

  useEffect(() => () => {
    Object.values(coverPreviewByField).forEach((previewUrl) => URL.revokeObjectURL(previewUrl))
  }, [coverPreviewByField])

  function resetForm() {
    setEditingFieldId(null)
    setCode('')
    setName('')
    setDescription('')
    setCoverFile(null)
    setCoverPreviewUrl(null)
    setValidationError(null)
  }

  function handleCoverSelection(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null
    event.target.value = ''
    const error = validateCoverFile(file)
    if (error) {
      setCoverFile(null)
      setCoverPreviewUrl(null)
      setValidationError(error)
      return
    }
    setValidationError(null)
    setCoverFile(file)
    setCoverPreviewUrl(file ? URL.createObjectURL(file) : null)
  }

  function beginEdit(field: ResearchField) {
    setEditingFieldId(field.id)
    setName(field.name)
    setDescription(field.description ?? '')
    setCode(field.code)
    setCoverFile(null)
    setCoverPreviewUrl(null)
    setValidationError(null)
    window.requestAnimationFrame(() => document.getElementById('research-field-name')?.focus())
  }

  async function attachCover(fieldId: number, fieldName: string, file: File, uploadedFileId?: number) {
    if (!token || !hasFileUpload) return false
    setUploadingCoverId(fieldId)
    setCoverErrors((previous) => ({ ...previous, [fieldId]: '' }))
    let fileId = uploadedFileId
    try {
      if (!fileId) {
        fileId = (await uploadPublicImage(token, file, `Ảnh đại diện ${fieldName}`)).id
      }
      await apiClient<ResearchField>(`/admin/research-fields/${fieldId}`, {
        method: 'PATCH',
        token,
        body: JSON.stringify({ coverFileId: fileId }),
      })
      setCoverRetry(null)
      setCoverErrors((previous) => ({ ...previous, [fieldId]: '' }))
      setCoverPreviewByField((previous) => {
        if (!previous[fieldId]) return previous
        const next = { ...previous }
        delete next[fieldId]
        return next
      })
      await loadFields()
      toast.success('Đã cập nhật ảnh đại diện')
      return true
    } catch (reason: unknown) {
      const message = reason instanceof Error ? reason.message : 'Vui lòng thử lại.'
      setCoverRetry({ fieldId, fieldName, file, ...(fileId ? { uploadedFileId: fileId } : {}) })
      setCoverErrors((previous) => ({
        ...previous,
        [fieldId]: `Ảnh chưa được gắn vào lĩnh vực. ${message} Bạn có thể thử lại.`,
      }))
      toast.error('Không thể cập nhật ảnh', `Lĩnh vực vẫn được giữ nguyên. ${message}`)
      return false
    } finally {
      setUploadingCoverId(null)
    }
  }

  async function saveField(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || saving) return
    setSaving(true)
    setValidationError(null)
    const trimmedName = name.trim()
    const trimmedDescription = description.trim()
    try {
      if (!trimmedName) {
        setValidationError('Vui lòng nhập tên lĩnh vực.')
        return
      }
      if (trimmedName.length > 150) {
        setValidationError('Tên lĩnh vực không được vượt quá 150 ký tự.')
        return
      }
      if (trimmedDescription.length > 5000) {
        setValidationError('Mô tả không được vượt quá 5000 ký tự.')
        return
      }
      if (editingFieldId !== null) {
        await apiClient<ResearchField>(`/admin/research-fields/${editingFieldId}`, {
          method: 'PATCH',
          token,
          body: JSON.stringify({ name: trimmedName, description: trimmedDescription }),
        })
        await loadFields()
        resetForm()
        toast.success('Đã cập nhật lĩnh vực nghiên cứu')
        return
      }

      const normalizedCode = code
        .trim()
        .toUpperCase()
        .replace(/[^A-Z0-9]+/g, '_')
        .replace(/^_+|_+$/g, '')
      if (!/^[A-Z][A-Z0-9_]{0,79}$/.test(normalizedCode)) {
        setValidationError('Code phải bắt đầu bằng chữ cái và chỉ gồm chữ, số hoặc dấu gạch dưới.')
        return
      }

      // Create the field first. A cover is attached only after the field has a stable id.
      const created = await apiClient<ResearchField>('/admin/research-fields', {
        method: 'POST',
        token,
        body: JSON.stringify({ code: normalizedCode, name: trimmedName, description: trimmedDescription }),
      })
      const selectedCover = coverFile
      resetForm()
      toast.success('Đã tạo lĩnh vực nghiên cứu')

      if (selectedCover) {
        const attached = await attachCover(created.id, created.name, selectedCover)
        if (!attached) await loadFields().catch(() => undefined)
      } else {
        await loadFields()
      }
    } catch (reason: unknown) {
      toast.error('Không thể lưu lĩnh vực', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setSaving(false)
    }
  }

  async function toggleField(field: ResearchField) {
    if (!token || deletingId === field.id) return
    setDeletingId(field.id)
    try {
      await apiClient<ResearchField>(`/admin/research-fields/${field.id}`, {
        method: 'PATCH',
        token,
        body: JSON.stringify({ isActive: !field.isActive }),
      })
      await loadFields()
    } catch (reason: unknown) {
      toast.error('Không thể cập nhật lĩnh vực', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setDeletingId(null)
    }
  }

  async function deleteField(field: ResearchField) {
    if (!token || !field.isActive || deletingId === field.id) return
    if (!await confirmDialog({
      title: 'Tắt lĩnh vực?',
      description: `“${field.name}” sẽ không còn được sử dụng. Dữ liệu lịch sử vẫn được giữ lại.`,
      confirmLabel: 'Tắt lĩnh vực',
      destructive: true,
    })) return
    setDeletingId(field.id)
    try {
      await apiClient<void>(`/admin/research-fields/${field.id}`, { method: 'DELETE', token })
      await loadFields()
      toast.success('Đã tắt lĩnh vực', `“${field.name}” không còn được sử dụng.`)
    } catch (reason: unknown) {
      toast.error('Không thể tắt lĩnh vực', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setDeletingId(null)
    }
  }

  async function updateCover(field: ResearchField, event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null
    event.target.value = ''
    const validationMessage = validateCoverFile(file)
    if (validationMessage || !file) {
      if (validationMessage) {
        setCoverErrors((previous) => ({ ...previous, [field.id]: validationMessage }))
        toast.error('Ảnh không hợp lệ', validationMessage)
      }
      return
    }
    setCoverPreviewByField((previous) => ({ ...previous, [field.id]: URL.createObjectURL(file) }))
    await attachCover(field.id, field.name, file)
  }

  async function retryCoverUpload() {
    if (!coverRetry) return
    await attachCover(coverRetry.fieldId, coverRetry.fieldName, coverRetry.file, coverRetry.uploadedFileId)
  }

  async function removeCover(field: ResearchField) {
    if (!token || uploadingCoverId === field.id) return
    if (!await confirmDialog({
      title: 'Gỡ ảnh đại diện?',
      description: `Ảnh đại diện của “${field.name}” sẽ được gỡ khỏi lĩnh vực.`,
      confirmLabel: 'Gỡ ảnh',
      destructive: true,
    })) return
    setUploadingCoverId(field.id)
    try {
      await apiClient<ResearchField>(`/admin/research-fields/${field.id}`, {
        method: 'PATCH',
        token,
        body: JSON.stringify({ removeCover: true }),
      })
      await loadFields()
      toast.success('Đã gỡ ảnh đại diện')
    } catch (reason: unknown) {
      toast.error('Không thể gỡ ảnh', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setUploadingCoverId(null)
    }
  }

  return (
    <div>
      <div className="page-title">
        <div><span className="eyebrow">Research fields</span><h1>Lĩnh vực nghiên cứu</h1><p>Quản lý các lĩnh vực để thành viên và dự án có thể gắn nhãn.</p></div>
      </div>
      <Feedback error={loadError ?? undefined} />
      {loadError && <button className="btn ghost" type="button" onClick={() => void loadFields()}>Thử tải lại</button>}
      <Feedback error={validationError ?? undefined} />
      {coverRetry && (
        <div className="admin-inline-alert" role="alert">
          <span>Ảnh đại diện của “{coverRetry.fieldName}” chưa được gắn. File đã tải lên sẽ được dùng lại khi có thể.</span>
          <button className="btn ghost small" type="button" onClick={() => void retryCoverUpload()} disabled={uploadingCoverId === coverRetry.fieldId}>
            {uploadingCoverId === coverRetry.fieldId ? 'Đang thử lại...' : 'Thử lại gắn ảnh'}
          </button>
        </div>
      )}
      <div className="panel-grid">
        <form className="panel" onSubmit={(event) => void saveField(event)}>
          <div className="panel-head">
            <div><h2>{editingFieldId === null ? 'Thêm lĩnh vực' : 'Sửa lĩnh vực'}</h2><p>{editingFieldId === null ? 'Code sẽ được chuẩn hóa thành chữ hoa.' : 'Chỉ tên và mô tả được cập nhật trong biểu mẫu này.'}</p></div>
            {editingFieldId === null ? <Plus size={20} aria-hidden="true" /> : <Edit3 size={20} aria-hidden="true" />}
          </div>
          <div className="form-stack">
            {editingFieldId === null ? (
              <label className="field"><span>Code</span><input className="input" required maxLength={80} value={code} onChange={(event) => setCode(event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_'))} placeholder="AI" pattern="[A-Z][A-Z0-9_]*" title="Bắt đầu bằng chữ cái; chỉ dùng chữ, số hoặc dấu gạch dưới" /></label>
            ) : (
              <label className="field"><span>Code</span><input className="input" value={code} readOnly /></label>
            )}
            <label className="field"><span>Tên lĩnh vực</span><input id="research-field-name" className="input" required maxLength={150} value={name} onChange={(event) => setName(event.target.value)} placeholder="Artificial Intelligence" /></label>
            <label className="field"><span>Mô tả</span><textarea className="textarea" rows={5} maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
            {editingFieldId === null && (
              <>
                <label className="field">
                  <span>Ảnh đại diện (Tùy chọn)</span>
                  {hasFileUpload ? <input className="input" type="file" accept={RESEARCH_COVER_ACCEPT} onChange={handleCoverSelection} /> : <p className="hint">Bạn không có quyền tải ảnh lên. Có thể tạo lĩnh vực trước rồi nhờ người có quyền FILE_UPLOAD gắn ảnh.</p>}
                  <p className="hint">JPEG, PNG hoặc WebP, tối đa 25 MiB.</p>
                </label>
                {coverPreviewUrl && <div className="research-field-cover-preview-wrap"><img className="research-field-cover-preview" src={coverPreviewUrl} alt="Xem trước ảnh đại diện" /></div>}
              </>
            )}
            <div className="research-field-form-actions">
              <button className="btn primary" type="submit" disabled={saving}>{saving ? 'Đang lưu...' : editingFieldId === null ? <><Save size={16} />Tạo lĩnh vực</> : <><Save size={16} />Lưu thay đổi</>}</button>
              {editingFieldId !== null && <button className="btn ghost" type="button" onClick={resetForm} disabled={saving}><X size={16} />Hủy sửa</button>}
            </div>
          </div>
        </form>
        <section className="panel">
          <div className="panel-head"><div><h2>Danh sách hiện tại</h2><p>{fields.length} lĩnh vực trong hệ thống.</p></div><FlaskConical size={20} aria-hidden="true" /></div>
          {loading ? <div className="empty tight">Đang tải...</div> : <div className="field-admin-list">
            {fields.map((field) => {
              const imageIsBroken = field.coverFileId ? brokenCoverIds.has(field.coverFileId) : false
              return (
                <div className="research-field-admin-row" key={field.id}>
                  <div className="research-field-admin-row-head">
                    <div><strong>{field.name}</strong><span>{field.code}</span></div>
                    <span className="field-admin-actions">
                      <button className={`badge ${field.isActive ? 'success' : 'danger'}`} type="button" onClick={() => void toggleField(field)} disabled={deletingId === field.id}>
                        {field.isActive ? 'Đang dùng' : 'Đã tắt'}
                      </button>
                      <button className="btn ghost table-btn" type="button" title="Sửa lĩnh vực" aria-label={`Sửa lĩnh vực ${field.name}`} onClick={() => beginEdit(field)}>
                        <Edit3 aria-hidden="true" />
                      </button>
                      <button className="btn ghost table-btn danger-text" type="button" title="Tắt lĩnh vực" aria-label={`Tắt lĩnh vực ${field.name}`} disabled={!field.isActive || deletingId === field.id} onClick={() => void deleteField(field)}>
                        <Trash2 aria-hidden="true" />
                      </button>
                    </span>
                  </div>
                  <div className="research-field-cover-row">
                    {coverPreviewByField[field.id] ? (
                      <img className="research-field-cover-preview" src={coverPreviewByField[field.id]} alt={`Xem trước ảnh đại diện ${field.name}`} />
                    ) : field.coverFileId && !imageIsBroken ? (
                      <img className="research-field-cover-preview" src={publicFileUrl(field.coverFileId)} alt={`Ảnh đại diện ${field.name}`} onError={() => setBrokenCoverIds((previous) => new Set(previous).add(field.coverFileId as number))} />
                    ) : (
                      <div className="research-field-cover-placeholder" role="img" aria-label={`Chưa có ảnh đại diện khả dụng cho ${field.name}`}>
                        <ImageIcon size={20} aria-hidden="true" /><span>{imageIsBroken ? 'Không tải được ảnh' : 'Chưa có ảnh'}</span>
                      </div>
                    )}
                    <div className="research-field-cover-actions">
                      {hasFileUpload ? (
                        <label className={`btn outline small ${uploadingCoverId === field.id ? 'is-disabled' : ''}`}>
                          {uploadingCoverId === field.id ? 'Đang tải...' : field.coverFileId ? 'Đổi ảnh' : 'Chọn ảnh'}
                          <input className="research-field-cover-input" type="file" accept={RESEARCH_COVER_ACCEPT} disabled={uploadingCoverId === field.id} onChange={(event) => void updateCover(field, event)} />
                        </label>
                      ) : (
                        <p className="research-field-cover-permission">Thiếu quyền FILE_UPLOAD để chọn hoặc đổi ảnh.</p>
                      )}
                      {field.coverFileId && <button type="button" className="btn ghost small danger-text" disabled={uploadingCoverId === field.id} onClick={() => void removeCover(field)}>Gỡ ảnh</button>}
                      {coverErrors[field.id] && <p className="research-field-cover-error" role="alert">{coverErrors[field.id]}</p>}
                    </div>
                  </div>
                </div>
              )
            })}
            {!fields.length && <div className="empty tight">Chưa có lĩnh vực.</div>}
          </div>}
        </section>
      </div>
    </div>
  )
}

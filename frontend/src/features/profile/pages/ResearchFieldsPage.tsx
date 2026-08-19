import { FlaskConical, Plus, Save, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { useAuth } from '../../auth/authContext'
import { apiClient } from '../../../lib/apiClient'
import type { ResearchField } from '../../../shared/types/api'

export function ResearchFieldsPage() {
  const { token } = useAuth()
  const toast = useToast()
  const [fields, setFields] = useState<ResearchField[]>([])
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)

  const loadFields = useCallback(() => {
    if (!token) return Promise.resolve()
    return apiClient<ResearchField[]>('/admin/research-fields', { token }).then(setFields)
  }, [token])

  useEffect(() => {
    loadFields()
      .catch((reason: unknown) => setLoadError(reason instanceof Error ? reason.message : 'Không tải được lĩnh vực.'))
      .finally(() => setLoading(false))
  }, [loadFields])

  const createField = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    setSaving(true)
    setValidationError(null)
    try {
      const normalizedCode = code
        .trim()
        .toUpperCase()
        .replace(/[^A-Z0-9]+/g, '_')
        .replace(/^_+|_+$/g, '')
      if (!/^[A-Z][A-Z0-9_]{0,79}$/.test(normalizedCode)) {
        setValidationError('Code phải bắt đầu bằng chữ cái và chỉ gồm chữ, số hoặc dấu gạch dưới.')
        return
      }
      await apiClient<ResearchField>('/admin/research-fields', {
        method: 'POST',
        token,
        body: JSON.stringify({ code: normalizedCode, name: name.trim(), description: description.trim() }),
      })
      setCode('')
      setName('')
      setDescription('')
      await loadFields()
      toast.success('Đã tạo lĩnh vực nghiên cứu')
    } catch (reason: unknown) {
      toast.error('Không thể tạo lĩnh vực', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setSaving(false)
    }
  }

  const toggleField = async (field: ResearchField) => {
    if (!token) return
    try {
      await apiClient<ResearchField>(`/admin/research-fields/${field.id}`, {
        method: 'PATCH',
        token,
        body: JSON.stringify({ isActive: !field.isActive }),
      })
      await loadFields()
    } catch (reason: unknown) {
      toast.error('Không thể cập nhật lĩnh vực', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    }
  }

  const deleteField = async (field: ResearchField) => {
    if (!token || !window.confirm(`Tắt lĩnh vực “${field.name}”?`)) return
    setDeletingId(field.id)
    try {
      await apiClient<void>(`/admin/research-fields/${field.id}`, {
        method: 'DELETE',
        token,
      })
      await loadFields()
      toast.success('Đã tắt lĩnh vực', `“${field.name}” không còn được sử dụng.`)
    } catch (reason: unknown) {
      toast.error('Không thể tắt lĩnh vực', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div>
      <div className="page-title">
        <div><span className="eyebrow">Research fields</span><h1>Lĩnh vực nghiên cứu</h1><p>Quản lý các lĩnh vực để thành viên và dự án có thể gắn nhãn.</p></div>
      </div>
      <Feedback error={loadError ?? undefined} />
      <Feedback error={validationError ?? undefined} />
      <div className="panel-grid">
        <form className="panel" onSubmit={createField}>
          <div className="panel-head"><div><h2>Thêm lĩnh vực</h2><p>Code sẽ được chuẩn hóa thành chữ hoa.</p></div><Plus size={20} /></div>
          <div className="form-stack">
            <label className="field"><span>Code</span><input className="input" required maxLength={80} value={code} onChange={(event) => setCode(event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_'))} placeholder="AI" pattern="[A-Z][A-Z0-9_]*" title="Bắt đầu bằng chữ cái; chỉ dùng chữ, số hoặc dấu gạch dưới" /></label>
            <label className="field"><span>Tên lĩnh vực</span><input className="input" required value={name} onChange={(event) => setName(event.target.value)} placeholder="Artificial Intelligence" /></label>
            <label className="field"><span>Mô tả</span><textarea className="textarea" rows={5} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
            <button className="btn primary" type="submit" disabled={saving}><Save size={16} />{saving ? 'Đang tạo...' : 'Tạo lĩnh vực'}</button>
          </div>
        </form>
        <section className="panel">
          <div className="panel-head"><div><h2>Danh sách hiện tại</h2><p>{fields.length} lĩnh vực trong hệ thống.</p></div><FlaskConical size={20} /></div>
          {loading ? <div className="empty tight">Đang tải...</div> : <div className="field-admin-list">
            {fields.map((field) => <div className="field-admin-row" key={field.id}><div><strong>{field.name}</strong><span>{field.code}</span></div><span className="field-admin-actions"><button className={`badge ${field.isActive ? 'success' : 'danger'}`} type="button" onClick={() => void toggleField(field)}>{field.isActive ? 'Đang dùng' : 'Đã tắt'}</button><button className="btn ghost table-btn danger-text" type="button" title="Tắt lĩnh vực" disabled={!field.isActive || deletingId === field.id} onClick={() => void deleteField(field)}><Trash2 /></button></span></div>)}
            {!fields.length && <div className="empty tight">Chưa có lĩnh vực.</div>}
          </div>}
        </section>
      </div>
    </div>
  )
}

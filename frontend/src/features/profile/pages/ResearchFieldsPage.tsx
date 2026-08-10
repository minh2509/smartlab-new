import { Check, FlaskConical, Plus, Save, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../auth/authContext'
import { apiClient } from '../../../lib/apiClient'
import type { ResearchField } from '../../../shared/types/api'

export function ResearchFieldsPage() {
  const { token } = useAuth()
  const [fields, setFields] = useState<ResearchField[]>([])
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadFields = useCallback(() => {
    if (!token) return Promise.resolve()
    return apiClient<ResearchField[]>('/admin/research-fields', { token }).then(setFields)
  }, [token])

  useEffect(() => {
    loadFields()
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Không tải được lĩnh vực.'))
      .finally(() => setLoading(false))
  }, [loadFields])

  const createField = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    setSaving(true)
    setMessage(null)
    setError(null)
    try {
      await apiClient<ResearchField>('/admin/research-fields', {
        method: 'POST',
        token,
        body: JSON.stringify({ code, name, description }),
      })
      setCode('')
      setName('')
      setDescription('')
      await loadFields()
      setMessage('Đã tạo lĩnh vực nghiên cứu.')
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể tạo lĩnh vực.')
    } finally {
      setSaving(false)
    }
  }

  const toggleField = async (field: ResearchField) => {
    if (!token) return
    setError(null)
    try {
      await apiClient<ResearchField>(`/admin/research-fields/${field.id}`, {
        method: 'PATCH',
        token,
        body: JSON.stringify({ isActive: !field.isActive }),
      })
      await loadFields()
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể cập nhật lĩnh vực.')
    }
  }

  const deleteField = async (field: ResearchField) => {
    if (!token || !window.confirm(`Tắt lĩnh vực “${field.name}”?`)) return
    setDeletingId(field.id)
    setMessage(null)
    setError(null)
    try {
      await apiClient<void>(`/admin/research-fields/${field.id}`, {
        method: 'DELETE',
        token,
      })
      await loadFields()
      setMessage(`Đã tắt lĩnh vực “${field.name}”.`)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể tắt lĩnh vực.')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div>
      <div className="page-title">
        <div><span className="eyebrow">D2 · Research fields</span><h1>Lĩnh vực nghiên cứu</h1><p>Quản lý các lĩnh vực để thành viên và dự án có thể gắn nhãn.</p></div>
      </div>
      {error && <div className="alert error">{error}</div>}
      {message && <div className="alert"><Check />{message}</div>}
      <div className="panel-grid">
        <form className="panel" onSubmit={createField}>
          <div className="panel-head"><div><h2>Thêm lĩnh vực</h2><p>Code sẽ được chuẩn hóa thành chữ hoa.</p></div><Plus size={20} /></div>
          <div className="form-stack">
            <label className="field"><span>Code</span><input className="input" required value={code} onChange={(event) => setCode(event.target.value)} placeholder="AI" /></label>
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

import { Check, Save, UserCog } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/authContext'
import { apiClient } from '../../../lib/apiClient'
import { getResearchFields } from '../api'
import type { MemberProfile, ResearchField } from '../../../shared/types/api'

type AdminForm = {
  bio: string
  publicEmail: string
  phone: string
  activeStatus: string
  isFeatured: boolean
  featuredOrder: string
  researchFieldIds: number[]
}

const emptyForm: AdminForm = {
  bio: '',
  publicEmail: '',
  phone: '',
  activeStatus: 'ACTIVE',
  isFeatured: false,
  featuredOrder: '',
  researchFieldIds: [],
}

const phonePattern = /^(|0[35789]\d{8}|\+84[35789]\d{8})$/

export function AdminMembersPage() {
  const { token } = useAuth()
  const [members, setMembers] = useState<MemberProfile[]>([])
  const [fields, setFields] = useState<ResearchField[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [form, setForm] = useState<AdminForm>(emptyForm)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) return
    Promise.all([
      apiClient<MemberProfile[]>('/admin/members', { token }),
      getResearchFields(),
    ])
      .then(([memberData, fieldData]) => {
        setMembers(memberData)
        setFields(fieldData)
        if (memberData[0]) selectMember(memberData[0])
      })
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Không tải được thành viên.'))
      .finally(() => setLoading(false))
  }, [token])

  const selectMember = (member: MemberProfile) => {
    setSelectedId(member.userId)
    setForm({
      bio: member.bio ?? '',
      publicEmail: member.publicEmail ?? '',
      phone: member.phone ?? '',
      activeStatus: member.activeStatus,
      isFeatured: member.isFeatured,
      featuredOrder: member.featuredOrder === undefined ? '' : String(member.featuredOrder),
      researchFieldIds: member.researchFields.map((field) => field.id),
    })
    setMessage(null)
  }

  const toggleField = (id: number) => {
    setForm((current) => ({
      ...current,
      researchFieldIds: current.researchFieldIds.includes(id)
        ? current.researchFieldIds.filter((fieldId) => fieldId !== id)
        : [...current.researchFieldIds, id],
    }))
  }

  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !selectedId) return
    if (!phonePattern.test(form.phone.trim())) {
      setError('Số điện thoại phải có dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.')
      return
    }
    setSaving(true)
    setMessage(null)
    setError(null)
    try {
      const updated = await apiClient<MemberProfile>(`/admin/members/${selectedId}`, {
        method: 'PATCH',
        token,
        body: JSON.stringify({
          ...form,
          featuredOrder: form.featuredOrder ? Number(form.featuredOrder) : null,
          clearFeaturedOrder: !form.featuredOrder,
        }),
      })
      setMembers((current) => current.map((member) => member.userId === updated.userId ? updated : member))
      setMessage('Đã cập nhật hồ sơ thành viên.')
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể cập nhật thành viên.')
    } finally {
      setSaving(false)
    }
  }

  const selectedMember = members.find((member) => member.userId === selectedId)

  return (
    <div>
      <div className="page-title"><div><span className="eyebrow">Member profiles</span><h1>Hồ sơ thành viên</h1><p>Quản trị trạng thái, thông tin công khai và lĩnh vực của từng thành viên.</p></div></div>
      {error && <div className="alert error">{error}</div>}
      {message && <div className="alert"><Check />{message}</div>}
      {loading ? <div className="empty">Đang tải thành viên...</div> : <div className="panel-grid">
        <section className="panel">
          <div className="panel-head"><div><h2>Danh sách</h2><p>{members.length} thành viên</p></div><UserCog size={20} /></div>
          <div className="field-admin-list">
            {members.map((member) => <button className={`member-select-row ${selectedId === member.userId ? 'selected' : ''}`} type="button" key={member.userId} onClick={() => selectMember(member)}><span className="member-avatar">{member.name.split(' ').filter(Boolean).slice(-2).map((part) => part[0]).join('').toUpperCase()}</span><span><strong>{member.name}</strong><small>{member.email}</small></span><span className={`badge ${member.activeStatus === 'ACTIVE' ? 'success' : 'danger'}`}>{member.activeStatus}</span></button>)}
            {!members.length && <div className="empty tight">Chưa có hồ sơ thành viên.</div>}
          </div>
        </section>
        <form className="panel" onSubmit={save}>
          <div className="panel-head"><div><h2>{selectedMember?.name ?? 'Chọn thành viên'}</h2><p>Thay đổi sẽ được ghi vào member profile.</p></div></div>
          {selectedMember ? <div className="form-stack">
            <label className="field"><span>Email công khai</span><input className="input" type="email" value={form.publicEmail} onChange={(event) => setForm({ ...form, publicEmail: event.target.value })} /></label>
            <label className="field"><span>Số điện thoại</span><input className="input" type="tel" inputMode="tel" placeholder="0901234567" value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label>
            <label className="field"><span>Trạng thái</span><select className="select" value={form.activeStatus} onChange={(event) => setForm({ ...form, activeStatus: event.target.value })}><option value="ACTIVE">ACTIVE</option><option value="INACTIVE">INACTIVE</option><option value="ALUMNI">ALUMNI</option></select></label>
            <label className="field"><span>Giới thiệu</span><textarea className="textarea" rows={5} value={form.bio} onChange={(event) => setForm({ ...form, bio: event.target.value })} /></label>
            <label className="field-option selected"><input type="checkbox" checked={form.isFeatured} onChange={(event) => setForm({ ...form, isFeatured: event.target.checked })} /><span><strong>Thành viên nổi bật</strong><small>Hiển thị ưu tiên trên public page.</small></span></label>
            <label className="field"><span>Thứ tự nổi bật</span><input className="input" type="number" min="0" value={form.featuredOrder} onChange={(event) => setForm({ ...form, featuredOrder: event.target.value })} /></label>
            <div className="field-options"><span className="field-label">Lĩnh vực nghiên cứu</span>{fields.map((field) => <label className="field-option" key={field.id}><input type="checkbox" checked={form.researchFieldIds.includes(field.id)} onChange={() => toggleField(field.id)} /><span><strong>{field.name}</strong><small>{field.code}</small></span></label>)}</div>
            <button className="btn primary" type="submit" disabled={saving}><Save size={16} />{saving ? 'Đang lưu...' : 'Lưu thay đổi'}</button>
          </div> : <div className="empty tight">Chọn một thành viên để chỉnh sửa.</div>}
        </form>
      </div>}
    </div>
  )
}

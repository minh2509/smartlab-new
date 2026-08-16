import { Camera, Check, CircleUserRound, Save, Trash2, UserRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../auth/authContext'
import { downloadFile, getMyMemberProfile, getResearchFields, updateMyMemberProfile, uploadAvatar } from '../api'
import type { MemberProfile, ResearchField } from '../../../shared/types/api'

type FormState = {
  phone: string
  publicEmail: string
  bio: string
  researchFieldIds: number[]
  avatarFileId?: number
}

export function ProfilePage() {
  const { token, profile: account } = useAuth()
  const [member, setMember] = useState<MemberProfile | null>(null)
  const [fields, setFields] = useState<ResearchField[]>([])
  const [form, setForm] = useState<FormState>({ phone: '', publicEmail: '', bio: '', researchFieldIds: [] })
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const phonePattern = /^(|0[35789]\d{8}|\+84[35789]\d{8})$/

  useEffect(() => {
    if (!token) return
    let active = true
    setLoading(true)
    Promise.all([getMyMemberProfile(token), getResearchFields()])
      .then(([profileData, fieldData]) => {
        if (!active) return
        setMember(profileData)
        setFields(fieldData)
        setForm({
          phone: profileData.phone ?? '',
          publicEmail: profileData.publicEmail ?? '',
          bio: profileData.bio ?? '',
          researchFieldIds: profileData.researchFields.map((field) => field.id),
          avatarFileId: profileData.avatar?.id,
        })
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : 'Không tải được hồ sơ.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [token])

  useEffect(() => {
    if (!token || !member?.avatar?.id) {
      setAvatarUrl(null)
      return
    }
    let active = true
    let objectUrl: string | null = null
    downloadFile(token, member.avatar.id)
      .then((blob) => {
        if (!active) return
        objectUrl = URL.createObjectURL(blob)
        setAvatarUrl(objectUrl)
      })
      .catch(() => { if (active) setAvatarUrl(null) })
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [member?.avatar?.id, token])

  const initials = useMemo(() => {
    const name = member?.name ?? account?.name ?? 'Smart Lab'
    return name.split(' ').filter(Boolean).slice(-2).map((part) => part[0]).join('').toUpperCase()
  }, [account?.name, member?.name])

  const joinedLabDate = useMemo(() => {
    if (!member?.joinedLabAt) return 'Chưa có thông tin'
    const [year, month, day] = member.joinedLabAt.split('-').map(Number)
    if (!year || !month || !day) return member.joinedLabAt
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day))
  }, [member?.joinedLabAt])

  const toggleField = (fieldId: number) => {
    setForm((current) => ({
      ...current,
      researchFieldIds: current.researchFieldIds.includes(fieldId)
        ? current.researchFieldIds.filter((id) => id !== fieldId)
        : [...current.researchFieldIds, fieldId],
    }))
  }

  const handleSave = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    if (!phonePattern.test(form.phone.trim())) {
      setError('Số điện thoại phải có dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.')
      return
    }
    setSaving(true)
    setError(null)
    setMessage(null)
    try {
      const updated = await updateMyMemberProfile(token, form)
      setMember(updated)
      setMessage('Đã lưu hồ sơ thành viên.')
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể lưu hồ sơ.')
    } finally {
      setSaving(false)
    }
  }

  const handleAvatar = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file || !token) return
    setUploading(true)
    setError(null)
    setMessage(null)
    try {
      const uploaded = await uploadAvatar(token, file)
      const updated = await updateMyMemberProfile(token, { ...form, avatarFileId: uploaded.id })
      setMember(updated)
      setForm((current) => ({ ...current, avatarFileId: uploaded.id }))
      setMessage('Đã cập nhật ảnh đại diện.')
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể tải ảnh đại diện.')
    } finally {
      setUploading(false)
      event.target.value = ''
    }
  }

  const removeAvatar = async () => {
    if (!token || !member?.avatar) return
    setSaving(true)
    setError(null)
    setMessage(null)
    try {
      const updated = await updateMyMemberProfile(token, { ...form, avatarFileId: undefined, removeAvatar: true })
      setMember(updated)
      setForm((current) => ({ ...current, avatarFileId: undefined }))
      setMessage('Đã gỡ ảnh đại diện khỏi hồ sơ.')
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể gỡ ảnh đại diện.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="empty">Đang tải hồ sơ thành viên...</div>
  if (error && !member) return <div className="alert error"><CircleUserRound />{error}</div>

  return (
    <div className="admin-profile">
      <div className="page-title">
        <div>
          <span className="eyebrow">Member profile</span>
          <h1>Hồ sơ của tôi</h1>
          <p>Thông tin này được dùng trong danh sách thành viên và các hoạt động của Smart Lab.</p>
        </div>
        <span className="badge success"><Check size={14} /> {member?.activeStatus ?? 'ACTIVE'}</span>
      </div>

      {error && <div className="alert error"><CircleUserRound />{error}</div>}
      {message && <div className="alert"><Check />{message}</div>}

      <section className="admin-profile-hero">
        <div className="admin-profile-main">
          <div className="profile-avatar-wrap">
            {avatarUrl ? <img className="profile-avatar-image" src={avatarUrl} alt="Ảnh đại diện" /> : <span className="admin-profile-avatar">{initials}</span>}
            <label className="avatar-upload" title="Đổi ảnh đại diện">
              <Camera size={15} />
              <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" onChange={handleAvatar} disabled={uploading} />
            </label>
          </div>
          <div>
            <span className="eyebrow">Smart Lab member</span>
            <h2>{member?.name ?? account?.name}</h2>
            <p>{member?.email ?? account?.email}</p>
          </div>
          {member?.avatar && <button className="btn ghost" type="button" onClick={() => void removeAvatar()} disabled={saving || uploading}><Trash2 size={15} /> Gỡ ảnh</button>}
        </div>
        <div className="admin-profile-status">
          {member?.researchFields.map((field) => <span className="badge info" key={field.id}>{field.name}</span>)}
        </div>
      </section>

      <form className="panel-grid" onSubmit={handleSave}>
        <section className="panel">
          <div className="panel-head">
            <div><h2>Thông tin cá nhân</h2><p>Chỉ email công khai mới xuất hiện trên trang thành viên.</p></div>
            <UserRound size={20} />
          </div>
          <div className="form-stack">
            <label className="field"><span>Số điện thoại</span><input className="input" type="tel" inputMode="tel" placeholder="0901234567" value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /><small className="muted">Để trống nếu không muốn khai báo. Ví dụ: 0901234567 hoặc +84901234567.</small></label>
            <label className="field"><span>Email công khai</span><input className="input" type="email" value={form.publicEmail} onChange={(event) => setForm({ ...form, publicEmail: event.target.value })} /></label>
            <div className="field"><span>Ngày tham gia Lab</span><strong className="input">{joinedLabDate}</strong><small className="muted">Ngày tham gia do hệ thống ghi nhận và không thể chỉnh sửa.</small></div>
            <label className="field"><span>Giới thiệu</span><textarea className="textarea" rows={6} value={form.bio} onChange={(event) => setForm({ ...form, bio: event.target.value })} /></label>
          </div>
        </section>

        <section className="panel">
          <div className="panel-head"><div><h2>Lĩnh vực nghiên cứu</h2><p>Chọn một hoặc nhiều lĩnh vực đang tham gia.</p></div></div>
          <div className="field-options">
            {fields.map((field) => (
              <label className={`field-option ${form.researchFieldIds.includes(field.id) ? 'selected' : ''}`} key={field.id}>
                <input type="checkbox" checked={form.researchFieldIds.includes(field.id)} onChange={() => toggleField(field.id)} />
                <span><strong>{field.name}</strong><small>{field.code}</small></span>
                {form.researchFieldIds.includes(field.id) && <Check size={17} />}
              </label>
            ))}
            {!fields.length && <div className="empty tight">Chưa có lĩnh vực active.</div>}
          </div>
          <div className="form-actions profile-save-actions">
            <button className="btn primary" type="submit" disabled={saving || uploading}><Save size={16} />{saving ? 'Đang lưu...' : 'Lưu hồ sơ'}</button>
            {uploading && <span className="muted">Đang tải ảnh lên Drive...</span>}
          </div>
        </section>
      </form>
    </div>
  )
}

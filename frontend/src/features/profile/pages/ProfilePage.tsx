import { AlertCircle, Camera, Check, Save, Trash2, UserRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
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
  const toast = useToast()
  const canUpdate = account?.permissions.includes('PROFILE_UPDATE') ?? false
  const [member, setMember] = useState<MemberProfile | null>(null)
  const [fields, setFields] = useState<ResearchField[]>([])
  const [form, setForm] = useState<FormState>({ phone: '', publicEmail: '', bio: '', researchFieldIds: [] })
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const phonePattern = /^(|0[35789]\d{8}|\+84[35789]\d{8})$/

  useEffect(() => {
    if (!token) return
    let active = true
    setLoading(true)
    setLoadError(null)
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
        if (active) setLoadError(reason instanceof Error ? reason.message : 'Không tải được hồ sơ.')
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
    if (!canUpdate) return
    setForm((current) => ({
      ...current,
      researchFieldIds: current.researchFieldIds.includes(fieldId)
        ? current.researchFieldIds.filter((id) => id !== fieldId)
        : [...current.researchFieldIds, fieldId],
    }))
  }

  const handleSave = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !canUpdate) return
    if (!phonePattern.test(form.phone.trim())) {
      setValidationError('Số điện thoại phải có dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.')
      return
    }
    setSaving(true)
    setValidationError(null)
    try {
      const updated = await updateMyMemberProfile(token, form)
      setMember(updated)
      toast.success('Đã lưu hồ sơ', 'Thông tin thành viên đã được cập nhật.')
    } catch (reason: unknown) {
      toast.error('Không thể lưu hồ sơ', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setSaving(false)
    }
  }

  const handleAvatar = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file || !token || !canUpdate) return
    setUploading(true)
    try {
      const uploaded = await uploadAvatar(token, file)
      const updated = await updateMyMemberProfile(token, { ...form, avatarFileId: uploaded.id })
      setMember(updated)
      setForm((current) => ({ ...current, avatarFileId: uploaded.id }))
      toast.success('Đã cập nhật ảnh đại diện')
    } catch (reason: unknown) {
      toast.error('Không thể tải ảnh đại diện', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setUploading(false)
      event.target.value = ''
    }
  }

  const removeAvatar = async () => {
    if (!token || !member?.avatar || !canUpdate) return
    setSaving(true)
    try {
      const updated = await updateMyMemberProfile(token, { ...form, avatarFileId: undefined, removeAvatar: true })
      setMember(updated)
      setForm((current) => ({ ...current, avatarFileId: undefined }))
      toast.success('Đã gỡ ảnh đại diện')
    } catch (reason: unknown) {
      toast.error('Không thể gỡ ảnh đại diện', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="empty">Đang tải hồ sơ thành viên...</div>
  if (loadError && !member) return <Feedback error={loadError} />

  return (
    <div className="admin-profile">
      <div className="profile-page-intro">
        <div>
          <h1>Hồ sơ cá nhân <span>Member Profile</span></h1>
          <p>Quản lý thông tin hiển thị trong danh sách thành viên và hoạt động của Smart Lab.</p>
        </div>
        <span className="profile-status"><Check size={14} aria-hidden="true" /> {member?.activeStatus ?? 'ACTIVE'}</span>
      </div>

      {!canUpdate && <Feedback message="Bạn chỉ có quyền xem hồ sơ. Liên hệ quản trị viên nếu cần cập nhật thông tin." />}

      <section className="profile-identity-surface" aria-label="Thông tin định danh thành viên">
        <div className="profile-identity-main">
          <div className="profile-avatar-wrap profile-avatar-wrap-large">
            {avatarUrl ? <img className="profile-avatar-image" src={avatarUrl} alt="Ảnh đại diện" /> : <span className="admin-profile-avatar">{initials}</span>}
            {canUpdate && <label className="avatar-upload" title="Đổi ảnh đại diện">
              <Camera size={16} aria-hidden="true" />
              <span className="sr-only">Đổi ảnh đại diện</span>
              <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" onChange={handleAvatar} disabled={uploading} />
            </label>}
          </div>
          <div className="profile-identity-copy">
            <span className="profile-identity-type">Smart Lab Member</span>
            <h2>{member?.name ?? account?.name}</h2>
            <p>{member?.email ?? account?.email}</p>
          </div>
        </div>
        <div className="profile-identity-actions">
          <div className="profile-avatar-action-row">
            {canUpdate && <label className="btn ghost profile-avatar-action" title="Đổi ảnh đại diện">
              <Camera size={16} aria-hidden="true" /> Đổi ảnh
              <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" onChange={handleAvatar} disabled={uploading} />
            </label>}
            {member?.avatar && canUpdate && <button className="btn ghost profile-remove-avatar" type="button" onClick={() => void removeAvatar()} disabled={saving || uploading}><Trash2 size={16} aria-hidden="true" /> Gỡ ảnh</button>}
          </div>
          {uploading && <span className="profile-avatar-feedback" role="status">Đang tải ảnh lên...</span>}
        </div>
      </section>

      <form className="profile-form-layout" onSubmit={handleSave}>
        <section className="panel profile-personal-panel">
          <div className="panel-head">
            <div><h2>Thông tin cá nhân</h2><p>Chỉ email công khai được dùng trên trang thành viên.</p></div>
            <UserRound size={20} aria-hidden="true" />
          </div>
          <div className="form-stack">
            <label className="field"><span>Số điện thoại</span><input className="input" type="tel" inputMode="tel" placeholder="0901234567" value={form.phone} onChange={(event) => { setForm({ ...form, phone: event.target.value }); setValidationError(null) }} disabled={!canUpdate} aria-invalid={Boolean(validationError)} aria-describedby={validationError ? 'profile-phone-error' : undefined} /><small className="muted">Để trống nếu không muốn khai báo. Ví dụ: 0901234567 hoặc +84901234567.</small>{validationError && <small className="field-error profile-phone-validation" id="profile-phone-error" role="alert"><AlertCircle aria-hidden="true" />{validationError}</small>}</label>
            <label className="field"><span>Email công khai</span><input className="input" type="email" value={form.publicEmail} onChange={(event) => setForm({ ...form, publicEmail: event.target.value })} disabled={!canUpdate} /></label>
            <label className="field"><span>Giới thiệu</span><textarea className="textarea" rows={6} value={form.bio} onChange={(event) => setForm({ ...form, bio: event.target.value })} disabled={!canUpdate} /></label>
          </div>
        </section>

        <section className="panel profile-lab-panel">
          <div className="panel-head"><div><h2>Lab information</h2><p>Thông tin thành viên và lĩnh vực nghiên cứu.</p></div></div>
          <div className="profile-readonly-meta">
            <span>Ngày tham gia Lab</span>
            <strong>{joinedLabDate}</strong>
            <small>Do hệ thống ghi nhận, không thể chỉnh sửa.</small>
          </div>
          <div className="profile-fields-head">
            <h3>Lĩnh vực nghiên cứu</h3>
            <p>Chọn một hoặc nhiều lĩnh vực đang tham gia.</p>
          </div>
          <div className="field-options">
            {fields.map((field) => (
              <label className={`field-option ${form.researchFieldIds.includes(field.id) ? 'selected' : ''}`} key={field.id}>
                <input type="checkbox" checked={form.researchFieldIds.includes(field.id)} onChange={() => toggleField(field.id)} disabled={!canUpdate} />
                <span><strong>{field.name}</strong><small>{field.code}</small></span>
                {form.researchFieldIds.includes(field.id) && <Check size={17} />}
              </label>
            ))}
            {!fields.length && <div className="profile-fields-empty">Chưa có lĩnh vực nghiên cứu đang hoạt động.</div>}
          </div>
        </section>
        <div className="form-actions profile-save-actions">
          {canUpdate && <button className="btn primary" type="submit" disabled={saving || uploading}><Save size={16} aria-hidden="true" />{saving ? 'Đang lưu hồ sơ...' : 'Lưu thay đổi'}</button>}
          {!canUpdate && <span className="profile-readonly-note">Hồ sơ đang ở chế độ chỉ xem.</span>}
        </div>
      </form>
    </div>
  )
}

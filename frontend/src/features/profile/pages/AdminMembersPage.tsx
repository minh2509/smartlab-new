import { ArrowLeft, ChevronLeft, ChevronRight, Save, Search, UserCog, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { apiClient } from '../../../lib/apiClient'
import type { MemberProfile, ResearchField } from '../../../shared/types/api'
import { useAuth } from '../../auth/authContext'
import { getResearchFields } from '../api'
import './AdminMembersPage.css'

type AdminForm = {
  bio: string
  publicEmail: string
  phone: string
  activeStatus: string
  isFeatured: boolean
  featuredOrder: string
  joinedLabAt: string
  researchFieldIds: number[]
}

const PAGE_SIZE = 20
const phonePattern = /^(|0[35789]\d{8}|\+84[35789]\d{8})$/
const statusOptions = [
  { value: 'ACTIVE', label: 'Đang hoạt động' },
  { value: 'INACTIVE', label: 'Không hoạt động' },
  { value: 'ALUMNI', label: 'Cựu thành viên' },
]
const statusFilterOptions = [{ value: 'ALL', label: 'Tất cả trạng thái' }, ...statusOptions]
const sortOptions = [
  { value: 'NAME_ASC', label: 'Tên A–Z' },
  { value: 'NAME_DESC', label: 'Tên Z–A' },
  { value: 'JOINED_NEWEST', label: 'Mới tham gia' },
  { value: 'JOINED_OLDEST', label: 'Tham gia lâu nhất' },
]

function formFromMember(member: MemberProfile): AdminForm {
  return {
    bio: member.bio ?? '',
    publicEmail: member.publicEmail ?? '',
    phone: member.phone ?? '',
    activeStatus: member.activeStatus,
    isFeatured: member.isFeatured,
    featuredOrder: member.featuredOrder === undefined ? '' : String(member.featuredOrder),
    joinedLabAt: member.joinedLabAt?.slice(0, 10) ?? '',
    researchFieldIds: member.researchFields.map((field) => field.id).sort((a, b) => a - b),
  }
}

function formsMatch(left: AdminForm, right: AdminForm) {
  return left.bio.trim() === right.bio.trim()
    && left.publicEmail.trim() === right.publicEmail.trim()
    && left.phone.trim() === right.phone.trim()
    && left.activeStatus === right.activeStatus
    && left.isFeatured === right.isFeatured
    && left.featuredOrder.trim() === right.featuredOrder.trim()
    && left.joinedLabAt === right.joinedLabAt
    && left.researchFieldIds.slice().sort((a, b) => a - b).join(',') === right.researchFieldIds.slice().sort((a, b) => a - b).join(',')
}

function statusLabel(status: string) {
  return statusOptions.find((option) => option.value === status)?.label ?? status
}

function initials(name: string) {
  return name.split(' ').filter(Boolean).slice(-2).map((part) => part[0]).join('').toUpperCase() || '?'
}

export function AdminMembersPage() {
  const { token } = useAuth()
  const toast = useToast()
  const [members, setMembers] = useState<MemberProfile[]>([])
  const [fields, setFields] = useState<ResearchField[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [form, setForm] = useState<AdminForm>(() => formFromMember({ userId: '', name: '', activeStatus: 'ACTIVE', isFeatured: false, researchFields: [] }))
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [sort, setSort] = useState('NAME_ASC')
  const [page, setPage] = useState(1)
  const [fieldSearch, setFieldSearch] = useState('')
  const [pendingMemberId, setPendingMemberId] = useState<string | null>(null)
  const [mobileDetailOpen, setMobileDetailOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) return
    Promise.all([apiClient<MemberProfile[]>('/admin/members', { token }), getResearchFields()])
      .then(([memberData, fieldData]) => {
        setMembers(memberData)
        setFields(fieldData)
        if (memberData[0]) {
          setSelectedId(memberData[0].userId)
          setForm(formFromMember(memberData[0]))
        }
      })
      .catch((reason: unknown) => setLoadError(reason instanceof Error ? reason.message : 'Không tải được thành viên.'))
      .finally(() => setLoading(false))
  }, [token])

  const selectedMember = members.find((member) => member.userId === selectedId)
  const isDirty = selectedMember ? !formsMatch(form, formFromMember(selectedMember)) : false
  const filteredMembers = useMemo(() => {
    const normalizedSearch = search.trim().toLocaleLowerCase('vi')
    const collator = new Intl.Collator('vi', { sensitivity: 'base' })
    return members
      .filter((member) => {
        const matchesSearch = !normalizedSearch || [member.name, member.email, member.publicEmail]
          .filter((value): value is string => Boolean(value))
          .some((value) => value.toLocaleLowerCase('vi').includes(normalizedSearch))
        return matchesSearch && (statusFilter === 'ALL' || member.activeStatus === statusFilter)
      })
      .sort((left, right) => {
        if (sort === 'NAME_ASC') return collator.compare(left.name, right.name)
        if (sort === 'NAME_DESC') return collator.compare(right.name, left.name)
        const leftDate = left.joinedLabAt ? Date.parse(left.joinedLabAt) : Number.NaN
        const rightDate = right.joinedLabAt ? Date.parse(right.joinedLabAt) : Number.NaN
        const leftMissing = Number.isNaN(leftDate)
        const rightMissing = Number.isNaN(rightDate)
        if (leftMissing || rightMissing) return leftMissing === rightMissing ? collator.compare(left.name, right.name) : leftMissing ? 1 : -1
        return sort === 'JOINED_NEWEST' ? rightDate - leftDate : leftDate - rightDate
      })
  }, [members, search, sort, statusFilter])
  const pageCount = Math.max(1, Math.ceil(filteredMembers.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)
  const pageMembers = filteredMembers.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)
  const visibleStart = filteredMembers.length ? (currentPage - 1) * PAGE_SIZE + 1 : 0
  const visibleEnd = Math.min(currentPage * PAGE_SIZE, filteredMembers.length)
  const filteredFields = useMemo(() => {
    const query = fieldSearch.trim().toLocaleLowerCase('vi')
    return !query ? fields : fields.filter((field) => `${field.name} ${field.code}`.toLocaleLowerCase('vi').includes(query))
  }, [fieldSearch, fields])

  useEffect(() => { if (page > pageCount) setPage(pageCount) }, [page, pageCount])

  const updateDirectory = (nextSearch?: string, nextStatus?: string, nextSort?: string) => {
    if (nextSearch !== undefined) setSearch(nextSearch)
    if (nextStatus !== undefined) setStatusFilter(nextStatus)
    if (nextSort !== undefined) setSort(nextSort)
    setPage(1)
  }

  const chooseMember = (member: MemberProfile) => {
    if (member.userId === selectedId) { setMobileDetailOpen(true); return }
    if (isDirty) { setPendingMemberId(member.userId); return }
    setSelectedId(member.userId)
    setForm(formFromMember(member))
    setValidationError(null)
    setMobileDetailOpen(true)
  }

  const discardAndSelect = () => {
    const target = members.find((member) => member.userId === pendingMemberId)
    if (target) {
      setSelectedId(target.userId)
      setForm(formFromMember(target))
      setValidationError(null)
      setMobileDetailOpen(true)
    }
    setPendingMemberId(null)
  }

  const undo = () => {
    if (!selectedMember) return
    setForm(formFromMember(selectedMember))
    setValidationError(null)
  }

  const toggleField = (id: number) => setForm((current) => ({
    ...current,
    researchFieldIds: current.researchFieldIds.includes(id)
      ? current.researchFieldIds.filter((fieldId) => fieldId !== id)
      : [...current.researchFieldIds, id].sort((a, b) => a - b),
  }))

  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token || !selectedId || !isDirty) return
    if (!phonePattern.test(form.phone.trim())) {
      setValidationError('Số điện thoại phải có dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.')
      return
    }
    setSaving(true)
    setValidationError(null)
    try {
      const updated = await apiClient<MemberProfile>(`/admin/members/${selectedId}`, {
        method: 'PATCH', token,
        body: JSON.stringify({
          ...form,
          joinedLabAt: form.joinedLabAt || null,
          featuredOrder: form.featuredOrder ? Number(form.featuredOrder) : null,
          clearFeaturedOrder: !form.featuredOrder,
        }),
      })
      setMembers((current) => current.map((member) => member.userId === updated.userId ? updated : member))
      setForm(formFromMember(updated))
      toast.success('Đã cập nhật hồ sơ thành viên')
    } catch (reason: unknown) {
      toast.error('Không thể cập nhật thành viên', reason instanceof Error ? reason.message : 'Vui lòng thử lại.')
    } finally { setSaving(false) }
  }

  return <div className="member-directory-page">
    <div className="page-title"><div><span className="eyebrow">Member profiles</span><h1>Hồ sơ thành viên</h1><p>Quản trị trạng thái, thông tin công khai và lĩnh vực của từng thành viên.</p></div></div>
    <Feedback error={loadError ?? undefined} />
    {loading ? <div className="member-directory-loading" aria-live="polite">Đang tải thành viên...</div> : <div className={`member-workspace ${mobileDetailOpen ? 'has-mobile-detail' : ''}`}>
      <section className="member-directory-pane" aria-label="Danh sách thành viên">
        <header className="member-directory-controls">
          <div className="member-directory-heading"><div><h2>Danh sách thành viên</h2><p>{members.length} thành viên</p></div><UserCog size={20} aria-hidden="true" /></div>
          <label className="member-search"><span className="sr-only">Tìm thành viên</span><Search size={17} aria-hidden="true" /><input value={search} onChange={(event) => updateDirectory(event.target.value)} placeholder="Tìm theo tên hoặc email..." /></label>
          <div className="member-directory-filters"><PopupSelect value={statusFilter} options={statusFilterOptions} onChange={(value) => updateDirectory(undefined, value)} ariaLabel="Lọc theo trạng thái" /><PopupSelect value={sort} options={sortOptions} onChange={(value) => updateDirectory(undefined, undefined, value)} ariaLabel="Sắp xếp thành viên" /></div>
        </header>
        <div className="member-directory-scroll">
          {pageMembers.map((member) => <button className={`member-directory-row ${selectedId === member.userId ? 'is-selected' : ''}`} type="button" key={member.userId} onClick={() => chooseMember(member)} aria-pressed={selectedId === member.userId}>
            <span className="member-directory-avatar" aria-hidden="true">{initials(member.name)}</span><span className="member-directory-row-copy"><strong>{member.name}</strong><small>{member.email || member.publicEmail || 'Chưa có email'}</small></span><span className="member-directory-status">{statusLabel(member.activeStatus)}</span>
          </button>)}
          {!members.length && <div className="member-directory-empty">Chưa có hồ sơ thành viên.</div>}
          {members.length > 0 && !pageMembers.length && <div className="member-directory-empty">Không tìm thấy thành viên phù hợp.</div>}
        </div>
        <footer className="member-directory-pagination"><span>Hiển thị {visibleStart}–{visibleEnd} / {filteredMembers.length} thành viên</span><div><button type="button" className="btn ghost table-btn" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)} aria-label="Trang trước"><ChevronLeft size={17} /></button><span>Trang {currentPage} / {pageCount}</span><button type="button" className="btn ghost table-btn" disabled={currentPage === pageCount} onClick={() => setPage(currentPage + 1)} aria-label="Trang sau"><ChevronRight size={17} /></button></div></footer>
      </section>
      <form className="member-profile-pane" onSubmit={save}>
        {selectedMember ? <>
          <header className="member-profile-header"><button className="member-mobile-back" type="button" onClick={() => setMobileDetailOpen(false)}><ArrowLeft size={17} />Quay lại danh sách</button><div><h2>{selectedMember.name}</h2><p>{selectedMember.email || selectedMember.publicEmail || 'Chưa có email'}</p></div><span className="member-profile-status">{statusLabel(form.activeStatus)}</span></header>
          <div className="member-profile-scroll">
            <section className="member-form-section"><h3>Thông tin công khai</h3><div className="member-form-grid"><label className="field"><span>Email công khai</span><input className="input" type="email" value={form.publicEmail} onChange={(event) => setForm({ ...form, publicEmail: event.target.value })} disabled={saving} /></label><label className="field"><span>Số điện thoại</span><input className={`input ${validationError ? 'invalid' : ''}`} type="tel" inputMode="tel" placeholder="0901234567" value={form.phone} aria-invalid={Boolean(validationError)} aria-describedby={validationError ? 'member-phone-error' : undefined} onChange={(event) => { setForm({ ...form, phone: event.target.value }); setValidationError(null) }} disabled={saving} />{validationError && <small id="member-phone-error" className="field-error">{validationError}</small>}</label><label className="field"><span>Trạng thái</span><PopupSelect value={form.activeStatus} options={statusOptions} onChange={(value) => setForm({ ...form, activeStatus: value })} ariaLabel="Trạng thái thành viên" disabled={saving} /></label><label className="field"><span>Ngày tham gia Lab</span><input className="input" type="date" max={new Date().toISOString().slice(0, 10)} value={form.joinedLabAt} onChange={(event) => setForm({ ...form, joinedLabAt: event.target.value })} disabled={saving} /></label></div></section>
            <section className="member-form-section"><h3>Giới thiệu</h3><label className="field"><span className="sr-only">Giới thiệu</span><textarea className="textarea" rows={5} value={form.bio} onChange={(event) => setForm({ ...form, bio: event.target.value })} disabled={saving} /></label></section>
            <section className="member-form-section"><h3>Hiển thị công khai</h3><label className={`member-featured-toggle ${form.isFeatured ? 'is-enabled' : ''}`}><span><strong>Thành viên nổi bật</strong><small>Hiển thị ưu tiên trên trang công khai.</small></span><input type="checkbox" checked={form.isFeatured} onChange={(event) => setForm({ ...form, isFeatured: event.target.checked })} disabled={saving} /></label><label className={`field member-featured-order ${form.isFeatured ? '' : 'is-muted'}`}><span>Thứ tự nổi bật</span><input className="input" type="number" min="0" value={form.featuredOrder} onChange={(event) => setForm({ ...form, featuredOrder: event.target.value })} disabled={saving || !form.isFeatured} /></label></section>
            <section className="member-form-section"><h3>Lĩnh vực nghiên cứu</h3>{fields.length > 8 && <label className="member-field-search"><span className="sr-only">Tìm lĩnh vực nghiên cứu</span><Search size={16} aria-hidden="true" /><input value={fieldSearch} onChange={(event) => setFieldSearch(event.target.value)} placeholder="Tìm lĩnh vực..." disabled={saving} /></label>}<div className="member-field-checklist">{filteredFields.map((field) => <label className="member-field-check" key={field.id}><input type="checkbox" checked={form.researchFieldIds.includes(field.id)} onChange={() => toggleField(field.id)} disabled={saving} /><span><strong>{field.name}</strong><small>{field.code}</small></span></label>)}{!filteredFields.length && <p className="member-fields-empty">Không tìm thấy lĩnh vực phù hợp.</p>}</div></section>
          </div>
          <footer className="member-profile-footer"><span>{isDirty ? 'Có thay đổi chưa lưu' : 'Không có thay đổi chưa lưu'}</span><div><button className="btn ghost" type="button" onClick={undo} disabled={!isDirty || saving}><X size={16} />Hoàn tác</button><button className="btn primary" type="submit" disabled={!isDirty || saving}><Save size={16} />{saving ? 'Đang lưu...' : 'Lưu thay đổi'}</button></div></footer>
        </> : <div className="member-directory-empty">Chọn một thành viên để chỉnh sửa.</div>}
      </form>
    </div>}
    {pendingMemberId && selectedMember && <ConfirmDialog title="Bỏ thay đổi chưa lưu?" description={`Các thay đổi của ${selectedMember.name} chưa được lưu.`} cancelLabel="Tiếp tục chỉnh sửa" confirmLabel="Bỏ thay đổi" destructive onClose={(confirmed) => confirmed ? discardAndSelect() : setPendingMemberId(null)} />}
  </div>
}

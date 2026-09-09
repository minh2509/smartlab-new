import { Pencil, RefreshCw, Trash2, Upload, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../../auth/authContext'
import { ApiClientError } from '../../../lib/apiClient'
import { listPublicEvents } from '../../events/api'
import type { LabEvent } from '../../events/types'
import { listPublicProjects } from '../../projects/api'
import type { PublicProjectSummary } from '../../projects/types'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Pagination } from '../../../shared/components/Pagination'
import { useToast } from '../../../shared/toast/useToast'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'
import { listAdminGallery, createAdminGallery, deleteAdminGallery, publishAdminGallery, unpublishAdminGallery, updateAdminGallery } from '../galleryApi'
import type { AdminGalleryItem, GalleryMetadataPayload, GalleryStatus } from '../galleryTypes'
import { GALLERY_CATEGORIES, GALLERY_SORTS, type GalleryCategory, type GallerySort } from '../../public/galleryTypes'
import { publicGalleryFileUrl } from '../../public/galleryApi'
import '../../content/adminContent.css'
import './AdminGalleryPage.css'
import fallbackImage from '../../../assets/fields/ai-research.webp'

const PAGE_SIZE = 24
const CATEGORY_LABELS: Record<GalleryCategory, string> = { ALL: 'Tất cả danh mục', WORKSHOP: 'Workshop', PROJECT_DEMO: 'Demo dự án', EVENT: 'Sự kiện', LAB_ACTIVITY: 'Hoạt động Lab', OTHER: 'Khác' }
type UploadRow = { file: File; preview: string; title: string; altText: string; state: 'queued' | 'uploading' | 'success' | 'failed'; error?: string }

export function AdminGalleryPage() {
  const { token } = useAuth()
  const toast = useToast()
  const [items, setItems] = useState<AdminGalleryItem[]>([])
  const [projects, setProjects] = useState<PublicProjectSummary[]>([])
  const [events, setEvents] = useState<LabEvent[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<GalleryStatus | ''>('')
  const [category, setCategory] = useState<GalleryCategory>('ALL')
  const [sort, setSort] = useState<GallerySort>('LATEST')
  const [projectFilter, setProjectFilter] = useState('')
  const [yearFilter, setYearFilter] = useState('')
  const [rows, setRows] = useState<UploadRow[]>([])
  const [common, setCommon] = useState({ caption: '', category: 'OTHER' as Exclude<GalleryCategory, 'ALL'>, projectId: '', eventId: '', capturedAt: '', isFeatured: false })
  const [editing, setEditing] = useState<AdminGalleryItem | null>(null)
  const [busy, setBusy] = useState(false)
  const rowsRef = useRef<UploadRow[]>([])

  const load = useCallback(async (signal?: AbortSignal) => {
    if (!token) return
    setLoading(true)
    try {
      const response = await listAdminGallery(token, { page, size: PAGE_SIZE, q: query, status: status || undefined, category, projectId: projectFilter ? Number(projectFilter) : undefined, year: yearFilter ? Number(yearFilter) : undefined, sort }, signal)
      if (!signal?.aborted) { setItems(response.items); setTotal(response.totalElements); setTotalPages(response.totalPages); setError('') }
    } catch (reason: unknown) { if (!signal?.aborted) setError(galleryError(reason, 'Không thể tải thư viện ảnh quản trị.')) }
    finally { if (!signal?.aborted) setLoading(false) }
  }, [category, page, projectFilter, query, sort, status, token, yearFilter])
  useEffect(() => { const controller = new AbortController(); void load(controller.signal); return () => controller.abort() }, [load])
  useEffect(() => { const controller = new AbortController(); void Promise.all([listPublicProjects(0, 48, {}, controller.signal), listPublicEvents({}, controller.signal)]).then(([projectPage, publicEvents]) => { if (!controller.signal.aborted) { setProjects(projectPage.items); setEvents(publicEvents) } }).catch(() => undefined); return () => controller.abort() }, [])
  useEffect(() => { rowsRef.current = rows }, [rows])
  useEffect(() => () => rowsRef.current.forEach((row) => URL.revokeObjectURL(row.preview)), [])

  function chooseFiles(event: React.ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(event.target.files ?? [])
    setRows((current) => [...current, ...selected.map((file) => ({ file, preview: URL.createObjectURL(file), title: file.name.replace(/\.[^.]+$/, ''), altText: file.name.replace(/\.[^.]+$/, ''), state: 'queued' as const }))])
    event.target.value = ''
  }
  function removeRow(index: number) { setRows((current) => { const next = [...current]; URL.revokeObjectURL(next[index].preview); next.splice(index, 1); return next }) }
  async function uploadQueue() {
    if (!token || busy || rows.every((row) => row.state === 'success')) return
    setBusy(true)
    for (let index = 0; index < rows.length; index += 1) {
      const row = rows[index]
      if (row.state === 'success') continue
      setRows((current) => current.map((value, i) => i === index ? { ...value, state: 'uploading', error: undefined } : value))
      try {
        const payload: GalleryMetadataPayload = { title: row.title.trim(), altText: row.altText.trim(), caption: common.caption || null, category: common.category, projectId: common.projectId ? Number(common.projectId) : null, eventId: common.eventId ? Number(common.eventId) : null, capturedAt: common.capturedAt || null, isFeatured: common.isFeatured }
        await createAdminGallery(token, row.file, payload)
        setRows((current) => current.map((value, i) => i === index ? { ...value, state: 'success' } : value))
      } catch (reason: unknown) { setRows((current) => current.map((value, i) => i === index ? { ...value, state: 'failed', error: galleryError(reason, 'Tải ảnh thất bại.') } : value)) }
    }
    setBusy(false); toast.success('Đã xử lý hàng đợi ảnh'); void load()
  }
  async function changeStatus(item: AdminGalleryItem) {
    if (!token || busy) return
    setBusy(true); setError('')
    try { if (item.status === 'PUBLISHED') await unpublishAdminGallery(token, item.id); else await publishAdminGallery(token, item.id); toast.success(item.status === 'PUBLISHED' ? 'Đã gỡ ảnh khỏi công khai' : 'Đã xuất bản ảnh'); await load() }
    catch (reason: unknown) { setError(galleryError(reason, 'Không thể cập nhật trạng thái ảnh.')) }
    finally { setBusy(false) }
  }
  async function remove(item: AdminGalleryItem) {
    if (!token || busy) return
    if (!await confirmDialog({ title: 'Xóa ảnh?', description: `“${item.title}” sẽ được gỡ khỏi thư viện.`, confirmLabel: 'Xóa ảnh', destructive: true })) return
    setBusy(true); try { await deleteAdminGallery(token, item.id); toast.success('Đã xóa ảnh'); if (items.length === 1 && page > 0) setPage((value) => value - 1); else await load() } catch (reason: unknown) { setError(galleryError(reason, 'Không thể xóa ảnh.')) } finally { setBusy(false) }
  }
  async function saveEdit(payload: GalleryMetadataPayload) { if (!token || !editing) return; setBusy(true); try { await updateAdminGallery(token, editing.id, payload); setEditing(null); toast.success('Đã cập nhật thông tin ảnh'); await load() } catch (reason: unknown) { setError(galleryError(reason, 'Không thể cập nhật ảnh.')) } finally { setBusy(false) } }

  return <div className="admin-content-page admin-gallery-page"><div className="page-title"><div><span className="eyebrow">Nội dung</span><h1>Thư viện ảnh</h1><p>Quản lý ảnh riêng tư ở dạng nháp và xuất bản những ảnh được phép chia sẻ công khai.</p></div><button className="btn" type="button" onClick={() => void load()} disabled={loading || busy}><RefreshCw size={15} aria-hidden="true" /> Tải lại</button></div>
    <section className="panel page-section admin-gallery-uploader"><div className="panel-head"><div><h2>Thêm ảnh</h2><p className="muted">Ảnh mới luôn được tạo ở trạng thái nháp và tệp PRIVATE.</p></div><label className="btn primary"><Upload size={16} aria-hidden="true" /> Chọn ảnh<input className="sr-only" type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple onChange={chooseFiles} /></label></div>
      {rows.length > 0 ? <><div className="admin-gallery-queue">{rows.map((row, index) => <div className="admin-gallery-queue-row" key={`${row.file.name}-${index}`}><img src={row.preview} alt="" /><label><span>Tiêu đề</span><input className="input" value={row.title} onChange={(event) => setRows((current) => current.map((value, i) => i === index ? { ...value, title: event.target.value } : value))} /></label><label><span>Alt text</span><input className="input" value={row.altText} onChange={(event) => setRows((current) => current.map((value, i) => i === index ? { ...value, altText: event.target.value } : value))} /></label><span className={`badge ${row.state === 'success' ? 'success' : row.state === 'failed' ? 'danger' : 'info'}`}>{row.state === 'success' ? 'Đã tải' : row.state === 'failed' ? row.error || 'Lỗi' : row.state === 'uploading' ? 'Đang tải' : 'Chờ tải'}</span><button className="icon-btn" type="button" onClick={() => removeRow(index)} disabled={busy || row.state === 'uploading'} aria-label={`Bỏ ${row.file.name}`}><X size={16} aria-hidden="true" /></button></div>)}</div><div className="admin-gallery-common"><label><span>Mô tả chung</span><textarea className="textarea" value={common.caption} onChange={(event) => setCommon({ ...common, caption: event.target.value })} /></label><label><span>Danh mục</span><select className="input" value={common.category} onChange={(event) => setCommon({ ...common, category: event.target.value as Exclude<GalleryCategory, 'ALL'> })}>{GALLERY_CATEGORIES.filter((value) => value !== 'ALL').map((value) => <option value={value} key={value}>{CATEGORY_LABELS[value]}</option>)}</select></label><label><span>Dự án công khai</span><select className="input" value={common.projectId} onChange={(event) => setCommon({ ...common, projectId: event.target.value })}><option value="">Không gắn dự án</option>{projects.map((project) => <option value={project.id} key={project.id}>{project.name}</option>)}</select></label><label><span>Sự kiện công khai</span><select className="input" value={common.eventId} onChange={(event) => setCommon({ ...common, eventId: event.target.value })}><option value="">Không gắn sự kiện</option>{events.filter((event) => event.visibility === 'PUBLIC').map((event) => <option value={event.id} key={event.id}>{event.title}</option>)}</select></label><label><span>Ngày chụp</span><input className="input" type="datetime-local" value={common.capturedAt} onChange={(event) => setCommon({ ...common, capturedAt: event.target.value })} /></label><label className="checkbox-line"><input type="checkbox" checked={common.isFeatured} onChange={(event) => setCommon({ ...common, isFeatured: event.target.checked })} /> Nổi bật</label></div><button className="btn primary" type="button" onClick={() => void uploadQueue()} disabled={busy || rows.every((row) => row.state === 'success')}><Upload size={15} aria-hidden="true" /> {busy ? 'Đang xử lý...' : 'Tải các ảnh đã chọn'}</button></> : <p className="muted">Chọn một hoặc nhiều ảnh JPEG, PNG, WebP hoặc GIF để bắt đầu.</p>}
    </section>
    <section className="panel page-section"><div className="admin-gallery-filters"><label><span>Tìm kiếm</span><input className="input" value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }} placeholder="Tìm tiêu đề..." /></label><label><span>Trạng thái</span><select className="input" value={status} onChange={(event) => { setStatus(event.target.value as GalleryStatus | ''); setPage(0) }}><option value="">Tất cả</option><option value="DRAFT">Bản nháp</option><option value="PUBLISHED">Đã xuất bản</option></select></label><label><span>Danh mục</span><select className="input" value={category} onChange={(event) => { setCategory(event.target.value as GalleryCategory); setPage(0) }}>{GALLERY_CATEGORIES.map((value) => <option value={value} key={value}>{CATEGORY_LABELS[value]}</option>)}</select></label><label><span>Dự án</span><select className="input" value={projectFilter} onChange={(event) => { setProjectFilter(event.target.value); setPage(0) }}><option value="">Tất cả dự án</option>{projects.map((project) => <option value={project.id} key={project.id}>{project.name}</option>)}</select></label><label><span>Năm</span><input className="input" type="number" min="1900" max="2100" value={yearFilter} onChange={(event) => { setYearFilter(event.target.value); setPage(0) }} placeholder="Tất cả năm" /></label><label><span>Sắp xếp</span><select className="input" value={sort} onChange={(event) => { setSort(event.target.value as GallerySort); setPage(0) }}>{GALLERY_SORTS.map((value) => <option value={value} key={value}>{value === 'LATEST' ? 'Mới cập nhật' : 'Cũ nhất'}</option>)}</select></label></div>{error ? <p className="alert error" role="alert">{error}</p> : null}{loading ? <div className="empty">Đang tải ảnh...</div> : items.length === 0 ? <EmptyState title="Chưa có ảnh" description="Chọn ảnh ở khu vực phía trên để tạo bản ghi Gallery." /> : <><p className="muted">{total} ảnh</p><div className="admin-gallery-grid">{items.map((item) => <article className="admin-gallery-card" key={item.id}><img src={item.fileId && item.status === 'PUBLISHED' ? publicGalleryFileUrl(item.fileId) : fallbackImage} alt={item.altText} onError={(event) => { event.currentTarget.src = fallbackImage }} /><div className="admin-gallery-card-body"><div className="admin-gallery-card-head"><span className={`badge ${item.status === 'PUBLISHED' ? 'success' : 'info'}`}>{item.status === 'PUBLISHED' ? 'Công khai' : 'Bản nháp'}</span><span className="muted">{CATEGORY_LABELS[item.category]}</span></div><h3>{item.title}</h3><p className="muted">{item.originalFileName || 'Không rõ tên tệp'}</p><div className="admin-gallery-card-actions"><button className="btn" type="button" onClick={() => setEditing(item)} disabled={busy}><Pencil size={14} aria-hidden="true" /> Sửa</button><button className="btn" type="button" onClick={() => void changeStatus(item)} disabled={busy}>{item.status === 'PUBLISHED' ? 'Gỡ công khai' : 'Xuất bản'}</button><button className="icon-btn danger" type="button" onClick={() => void remove(item)} disabled={busy} aria-label={`Xóa ${item.title}`}><Trash2 size={15} aria-hidden="true" /></button></div></div></article>)}</div><Pagination page={page + 1} totalPages={totalPages} onChange={(value) => setPage(value - 1)} /></>}</section>
    {editing ? <GalleryEditDialog item={editing} projects={projects} events={events} busy={busy} onClose={() => setEditing(null)} onSave={(payload) => void saveEdit(payload)} /> : null}
  </div>
}

function GalleryEditDialog({ item, projects, events, busy, onClose, onSave }: { item: AdminGalleryItem; projects: PublicProjectSummary[]; events: LabEvent[]; busy: boolean; onClose: () => void; onSave: (payload: GalleryMetadataPayload) => void }) {
  const [form, setForm] = useState<GalleryMetadataPayload>({ title: item.title, caption: item.caption, altText: item.altText, category: item.category, projectId: item.projectId, eventId: item.eventId, capturedAt: item.capturedAt, isFeatured: item.isFeatured })
  return <div className="admin-gallery-dialog-backdrop"><div className="admin-gallery-dialog" role="dialog" aria-modal="true" aria-labelledby="gallery-edit-title"><div className="panel-head"><h2 id="gallery-edit-title">Sửa thông tin ảnh</h2><button className="icon-btn" type="button" onClick={onClose} aria-label="Đóng"><X size={16} aria-hidden="true" /></button></div><div className="form-stack"><label><span>Tiêu đề</span><input className="input" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} /></label><label><span>Alt text</span><input className="input" value={form.altText} onChange={(event) => setForm({ ...form, altText: event.target.value })} /></label><label><span>Mô tả</span><textarea className="textarea" value={form.caption ?? ''} onChange={(event) => setForm({ ...form, caption: event.target.value })} /></label><label><span>Danh mục</span><select className="input" value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value as Exclude<GalleryCategory, 'ALL'> })}>{GALLERY_CATEGORIES.filter((value) => value !== 'ALL').map((value) => <option value={value} key={value}>{CATEGORY_LABELS[value]}</option>)}</select></label><label><span>Dự án công khai</span><select className="input" value={form.projectId ?? ''} onChange={(event) => setForm({ ...form, projectId: event.target.value ? Number(event.target.value) : null })}><option value="">Không gắn dự án</option>{projects.map((project) => <option value={project.id} key={project.id}>{project.name}</option>)}</select></label><label><span>Sự kiện công khai</span><select className="input" value={form.eventId ?? ''} onChange={(event) => setForm({ ...form, eventId: event.target.value ? Number(event.target.value) : null })}><option value="">Không gắn sự kiện</option>{events.filter((event) => event.visibility === 'PUBLIC').map((event) => <option value={event.id} key={event.id}>{event.title}</option>)}</select></label><label><span>Ngày chụp</span><input className="input" type="datetime-local" value={form.capturedAt ? form.capturedAt.slice(0, 16) : ''} onChange={(event) => setForm({ ...form, capturedAt: event.target.value || null })} /></label><label className="checkbox-line"><input type="checkbox" checked={Boolean(form.isFeatured)} onChange={(event) => setForm({ ...form, isFeatured: event.target.checked })} /> Nổi bật</label></div><div className="admin-gallery-dialog-actions"><button className="btn" type="button" onClick={onClose} disabled={busy}>Hủy</button><button className="btn primary" type="button" onClick={() => onSave(form)} disabled={busy}>Lưu thay đổi</button></div></div></div>
}

function galleryError(reason: unknown, fallback: string) {
  if (!(reason instanceof ApiClientError)) return fallback
  if (reason.status === 400) return 'Thông tin ảnh chưa hợp lệ.'
  if (reason.status === 403) return 'Bạn không có quyền thực hiện thao tác này.'
  if (reason.status === 404) return 'Không tìm thấy ảnh.'
  if (reason.status === 409) return 'Ảnh đang được sử dụng hoặc có trạng thái không hợp lệ.'
  if (reason.status === 413) return 'Ảnh vượt quá dung lượng cho phép.'
  if (reason.status === 415) return 'Định dạng ảnh chưa được hỗ trợ.'
  if (reason.status >= 500) return 'Máy chủ đang gặp sự cố. Vui lòng thử lại.'
  return fallback
}

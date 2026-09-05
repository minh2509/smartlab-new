import { ExternalLink, Newspaper, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '../../auth/authContext'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { useToast } from '../../../shared/toast/useToast'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'
import { AdminContentDialog } from '../components/AdminContentDialog'
import { createAdminNews, deleteAdminNews, listAdminNews, updateAdminNews } from '../newsAdminApi'
import type { AdminLabNewsArticle, CreateAdminNewsPayload, UpdateAdminNewsPayload } from '../newsAdminTypes'
import { formatAdminDate, toDateTimeLocal, toInstant, validateHttpUrl } from '../adminContentUtils'
import '../adminContent.css'

type NewsForm = {
  title: string
  excerpt: string
  sourceName: string
  sourceUrl: string
  publishedAt: string
  isPublic: boolean
}

const PAGE_SIZE = 20

function emptyNewsForm(): NewsForm {
  return {
    title: '',
    excerpt: '',
    sourceName: '',
    sourceUrl: '',
    publishedAt: '',
    isPublic: false,
  }
}

function NewsVisibilityBadge({ isPublic }: { isPublic: boolean }) {
  return <span className={`badge ${isPublic ? 'success' : 'info'}`}>{isPublic ? 'Công khai' : 'Riêng tư'}</span>
}

export function AdminNewsPage() {
  const { token } = useAuth()
  const toast = useToast()
  const triggerRef = useRef<HTMLButtonElement>(null)
  const [items, setItems] = useState<AdminLabNewsArticle[]>([])
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<NewsForm>(emptyNewsForm)
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  const loadNews = useCallback(async (signal?: AbortSignal) => {
    if (!token) return
    setLoading(true)
    try {
      const response = await listAdminNews(token, page, PAGE_SIZE, signal)
      if (signal?.aborted) return
      setItems(response.items)
      setTotalElements(response.totalElements)
      setTotalPages(response.totalPages)
      setError('')
    } catch (reason: unknown) {
      if (signal?.aborted) return
      setError(reason instanceof Error ? reason.message : 'Không thể tải danh sách tin tức.')
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [page, token])

  useEffect(() => {
    const controller = new AbortController()
    void loadNews(controller.signal)
    return () => controller.abort()
  }, [loadNews])

  function openCreate() {
    setEditingId(null)
    setForm(emptyNewsForm())
    setFormError('')
    setEditorOpen(true)
  }

  function openEdit(item: AdminLabNewsArticle) {
    setEditingId(item.id)
    setForm({
      title: item.title,
      excerpt: item.excerpt ?? '',
      sourceName: item.sourceName,
      sourceUrl: item.sourceUrl,
      publishedAt: toDateTimeLocal(item.publishedAt),
      isPublic: item.isPublic,
    })
    setFormError('')
    setEditorOpen(true)
  }

  function closeEditor() {
    if (busy) return
    setEditorOpen(false)
    setFormError('')
  }

  async function handleDelete(item: AdminLabNewsArticle) {
    if (!token || busy) return
    const confirmed = await confirmDialog({
      title: 'Xóa tin tức?',
      description: `“${item.title}” sẽ được gỡ khỏi danh sách quản trị và các khu vực công khai. Dữ liệu lịch sử vẫn được giữ lại.`,
      confirmLabel: 'Xóa tin tức',
      destructive: true,
    })
    if (!confirmed) return

    setBusy(true)
    setError('')
    try {
      await deleteAdminNews(token, item.id)
      toast.success('Đã xóa tin tức', item.title)
      if (items.length === 1 && page > 0) setPage((current) => current - 1)
      else await loadNews()
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể xóa tin tức.')
    } finally {
      setBusy(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || busy) return

    const title = form.title.trim()
    const excerpt = form.excerpt.trim()
    const sourceName = form.sourceName.trim()
    const sourceUrl = form.sourceUrl.trim()
    const publishedAt = toInstant(form.publishedAt)

    if (!title) return setFormError('Vui lòng nhập tiêu đề tin tức.')
    if (title.length > 500) return setFormError('Tiêu đề không được vượt quá 500 ký tự.')
    if (excerpt.length > 20000) return setFormError('Mô tả không được vượt quá 20.000 ký tự.')
    if (!sourceName) return setFormError('Vui lòng nhập tên nguồn.')
    if (sourceName.length > 255) return setFormError('Tên nguồn không được vượt quá 255 ký tự.')
    if (sourceUrl.length > 2048 || !validateHttpUrl(sourceUrl)) return setFormError('URL nguồn phải là địa chỉ HTTP(S) hợp lệ và có hostname.')
    if (!publishedAt) return setFormError('Vui lòng nhập thời điểm xuất bản hợp lệ.')

    const payload = {
      title,
      excerpt: excerpt || null,
      sourceName,
      sourceUrl,
      publishedAt,
      isPublic: form.isPublic,
    }

    setBusy(true)
    setFormError('')
    try {
      if (editingId === null) {
        await createAdminNews(token, payload satisfies CreateAdminNewsPayload)
        toast.success('Đã tạo tin tức', form.isPublic ? 'Tin tức đã được công khai.' : 'Tin tức đang ở trạng thái riêng tư.')
      } else {
        await updateAdminNews(token, editingId, payload satisfies UpdateAdminNewsPayload)
        toast.success('Đã cập nhật tin tức')
      }
      setEditorOpen(false)
      await loadNews()
    } catch (reason: unknown) {
      setFormError(reason instanceof Error ? reason.message : 'Không thể lưu tin tức.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="admin-content-page">
      <div className="page-title">
        <div>
          <span className="eyebrow">Nội dung</span>
          <h1>Quản lý tin tức</h1>
          <p>Quản lý các bài đăng truyền thông bên ngoài và trạng thái hiển thị trên Smart Lab.</p>
        </div>
        <div className="project-page-actions">
          <button ref={triggerRef} className="btn primary" type="button" onClick={openCreate} disabled={busy}>
            <Plus aria-hidden="true" /> Tạo tin tức
          </button>
          <button className="btn" type="button" onClick={() => void loadNews()} disabled={loading || busy}>
            <RefreshCw aria-hidden="true" /> {loading ? 'Đang tải...' : 'Tải lại'}
          </button>
        </div>
      </div>

      <section className="panel page-section" aria-busy={loading}>
        <div className="panel-head">
          <div>
            <h2>Danh sách tin tức</h2>
            <p>{totalElements.toLocaleString('vi-VN')} tin tức đang hoạt động trong hệ thống.</p>
          </div>
          <Newspaper aria-hidden="true" size={20} />
        </div>

        <Feedback error={error} />
        {loading ? <div className="empty">Đang tải tin tức...</div> : items.length === 0 ? (
          <EmptyState title="Chưa có tin tức" description="Tạo tin tức đầu tiên để quản lý nguồn truyền thông của Smart Lab." />
        ) : (
          <>
            <div className="admin-content-table-wrap">
              <table className="admin-content-table">
                <caption className="sr-only">Danh sách tin tức</caption>
                <thead>
                  <tr>
                    <th scope="col">Tiêu đề</th>
                    <th scope="col">Nguồn</th>
                    <th scope="col">Xuất bản</th>
                    <th scope="col">Hiển thị</th>
                    <th scope="col">Cập nhật</th>
                    <th scope="col"><span className="sr-only">Thao tác</span></th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td className="admin-content-title-cell">{item.title}</td>
                      <td>
                        <a className="admin-content-source-link" href={item.sourceUrl} target="_blank" rel="noopener noreferrer">
                          {item.sourceName} <ExternalLink aria-hidden="true" size={13} />
                        </a>
                      </td>
                      <td className="admin-content-muted-cell">{formatAdminDate(item.publishedAt)}</td>
                      <td><NewsVisibilityBadge isPublic={item.isPublic} /></td>
                      <td className="admin-content-muted-cell">{formatAdminDate(item.updatedAt)}</td>
                      <td>
                        <div className="admin-content-actions">
                          <button className="btn ghost sm" type="button" onClick={() => openEdit(item)} disabled={busy}><Pencil aria-hidden="true" /> Sửa</button>
                          <button className="btn ghost sm danger-text" type="button" onClick={() => void handleDelete(item)} disabled={busy}><Trash2 aria-hidden="true" /> Xóa</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="admin-content-mobile-list">
              {items.map((item) => (
                <article className="admin-content-mobile-card" key={item.id}>
                  <div className="admin-content-mobile-card-head">
                    <h3>{item.title}</h3>
                    <NewsVisibilityBadge isPublic={item.isPublic} />
                  </div>
                  <a className="admin-content-source-link" href={item.sourceUrl} target="_blank" rel="noopener noreferrer">
                    {item.sourceName} <ExternalLink aria-hidden="true" size={13} />
                  </a>
                  <p className="admin-content-mobile-meta">Xuất bản: {formatAdminDate(item.publishedAt)} · Cập nhật: {formatAdminDate(item.updatedAt)}</p>
                  <div className="admin-content-actions">
                    <button className="btn ghost sm" type="button" onClick={() => openEdit(item)} disabled={busy}><Pencil aria-hidden="true" /> Sửa</button>
                    <button className="btn ghost sm danger-text" type="button" onClick={() => void handleDelete(item)} disabled={busy}><Trash2 aria-hidden="true" /> Xóa</button>
                  </div>
                </article>
              ))}
            </div>
            <Pagination page={page + 1} totalPages={totalPages} onChange={(nextPage) => setPage(nextPage - 1)} />
          </>
        )}
      </section>

      <AdminContentDialog
        open={editorOpen}
        title={editingId === null ? 'Tạo tin tức mới' : 'Sửa tin tức'}
        description="Tin tức lưu nguồn bên ngoài và có thể được hiển thị hoặc giữ riêng tư."
        submitLabel={editingId === null ? 'Tạo tin tức' : 'Lưu thay đổi'}
        busy={busy}
        triggerRef={triggerRef}
        onClose={closeEditor}
        onSubmit={(event) => void handleSubmit(event)}
      >
        {formError ? <Feedback error={formError} /> : null}
        <div className="admin-content-form-grid">
          <label className="field admin-content-field-full" htmlFor="news-title">
            <span>Tiêu đề *</span>
            <input id="news-title" className="input" type="text" maxLength={500} value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} autoFocus required aria-invalid={Boolean(formError)} />
          </label>
          <label className="field" htmlFor="news-source-name">
            <span>Tên nguồn *</span>
            <input id="news-source-name" className="input" type="text" maxLength={255} value={form.sourceName} onChange={(event) => setForm({ ...form, sourceName: event.target.value })} placeholder="Ví dụ: VnExpress" required />
          </label>
          <label className="field" htmlFor="news-published-at">
            <span>Thời điểm xuất bản *</span>
            <input id="news-published-at" className="input" type="datetime-local" value={form.publishedAt} onChange={(event) => setForm({ ...form, publishedAt: event.target.value })} required />
          </label>
          <label className="field admin-content-field-full" htmlFor="news-source-url">
            <span>URL nguồn *</span>
            <input id="news-source-url" className="input" type="url" maxLength={2048} value={form.sourceUrl} onChange={(event) => setForm({ ...form, sourceUrl: event.target.value })} placeholder="https://example.com/bai-viet" required aria-describedby="news-source-url-help" />
            <small id="news-source-url-help" className="hint">Phải là URL HTTP(S) có hostname hợp lệ.</small>
          </label>
          <label className="field admin-content-field-full" htmlFor="news-excerpt">
            <span>Mô tả ngắn</span>
            <textarea id="news-excerpt" className="textarea" rows={5} maxLength={20000} value={form.excerpt} onChange={(event) => setForm({ ...form, excerpt: event.target.value })} />
          </label>
          <label className="admin-content-checkbox-row admin-content-field-full" htmlFor="news-public">
            <input id="news-public" type="checkbox" checked={form.isPublic} onChange={(event) => setForm({ ...form, isPublic: event.target.checked })} />
            <span>
              <strong>Công khai</strong>
              <small>Tin tức xuất hiện trên trang chủ và kho tin tức công khai.</small>
            </span>
          </label>
        </div>
      </AdminContentDialog>
    </div>
  )
}

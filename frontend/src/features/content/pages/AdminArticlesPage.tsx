import { BookOpenText, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '../../auth/authContext'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { useToast } from '../../../shared/toast/useToast'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'
import { AdminContentDialog } from '../components/AdminContentDialog'
import { createAdminArticle, deleteAdminArticle, listAdminArticles, updateAdminArticle } from '../articleAdminApi'
import type {
  AdminLabArticle,
  ArticleContentDocument,
  ArticleContentFileReference,
  CreateAdminArticlePayload,
  LabArticleStatus,
  UpdateAdminArticlePayload,
} from '../articleAdminTypes'
import { formatAdminDate, toDateTimeLocal, toInstant } from '../adminContentUtils'
import '../adminContent.css'

type ArticleForm = {
  title: string
  slug: string
  excerpt: string
  body: string
  status: LabArticleStatus
  publishedAt: string
  files: ArticleContentFileReference[]
}

const PAGE_SIZE = 20

function emptyArticleForm(): ArticleForm {
  return {
    title: '',
    slug: '',
    excerpt: '',
    body: '',
    status: 'DRAFT',
    publishedAt: '',
    files: [],
  }
}

function readArticleContent(content: Record<string, unknown>): ArticleContentDocument {
  const body = typeof content.body === 'string' ? content.body : ''
  const files = Array.isArray(content.files)
    ? content.files.flatMap((value) => {
        if (typeof value !== 'object' || value === null) return []
        const candidate = value as Record<string, unknown>
        const type = candidate.type
        const fileId = candidate.fileId
        if ((type !== 'image' && type !== 'file') || typeof fileId !== 'number') return []
        const normalizedType: ArticleContentFileReference['type'] = type
        return [{
          type: normalizedType,
          fileId,
          alt: typeof candidate.alt === 'string' ? candidate.alt : undefined,
          label: typeof candidate.label === 'string' ? candidate.label : undefined,
        }]
      })
    : []
  return files.length > 0 ? { body, files } : { body }
}

function ArticleStatusBadge({ status }: { status: LabArticleStatus }) {
  return <span className={`badge ${status === 'PUBLISHED' ? 'success' : 'info'}`}>{status === 'PUBLISHED' ? 'Đã xuất bản' : 'Bản nháp'}</span>
}

export function AdminArticlesPage() {
  const { token } = useAuth()
  const toast = useToast()
  const triggerRef = useRef<HTMLButtonElement>(null)
  const [items, setItems] = useState<AdminLabArticle[]>([])
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<ArticleForm>(emptyArticleForm)
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  const loadArticles = useCallback(async (signal?: AbortSignal) => {
    if (!token) return
    setLoading(true)
    try {
      const response = await listAdminArticles(token, page, PAGE_SIZE, signal)
      if (signal?.aborted) return
      setItems(response.items)
      setTotalElements(response.totalElements)
      setTotalPages(response.totalPages)
      setError('')
    } catch (reason: unknown) {
      if (signal?.aborted) return
      setError(reason instanceof Error ? reason.message : 'Không thể tải danh sách bài viết.')
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [page, token])

  useEffect(() => {
    const controller = new AbortController()
    void loadArticles(controller.signal)
    return () => controller.abort()
  }, [loadArticles])

  function openCreate() {
    setEditingId(null)
    setForm(emptyArticleForm())
    setFormError('')
    setEditorOpen(true)
  }

  function openEdit(item: AdminLabArticle) {
    const content = readArticleContent(item.content)
    setEditingId(item.id)
    setForm({
      title: item.title,
      slug: item.slug,
      excerpt: item.excerpt ?? '',
      body: content.body,
      status: item.status,
      publishedAt: toDateTimeLocal(item.publishedAt),
      files: content.files ?? [],
    })
    setFormError('')
    setEditorOpen(true)
  }

  function closeEditor() {
    if (busy) return
    setEditorOpen(false)
    setFormError('')
  }

  async function handleDelete(item: AdminLabArticle) {
    if (!token || busy) return
    const confirmed = await confirmDialog({
      title: 'Xóa bài viết?',
      description: `“${item.title}” sẽ được gỡ khỏi danh sách quản trị và website. Dữ liệu lịch sử vẫn được giữ lại.`,
      confirmLabel: 'Xóa bài viết',
      destructive: true,
    })
    if (!confirmed) return

    setBusy(true)
    setError('')
    try {
      await deleteAdminArticle(token, item.id)
      toast.success('Đã xóa bài viết', item.title)
      if (items.length === 1 && page > 0) setPage((current) => current - 1)
      else await loadArticles()
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể xóa bài viết.')
    } finally {
      setBusy(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || busy) return

    const title = form.title.trim()
    const slug = form.slug.trim()
    const excerpt = form.excerpt.trim()
    const publishedAt = toInstant(form.publishedAt)

    if (!title) return setFormError('Vui lòng nhập tiêu đề bài viết.')
    if (title.length > 500) return setFormError('Tiêu đề không được vượt quá 500 ký tự.')
    if (slug.length > 500) return setFormError('Slug không được vượt quá 500 ký tự.')
    if (excerpt.length > 20000) return setFormError('Mô tả không được vượt quá 20.000 ký tự.')
    if (!form.body.trim()) return setFormError('Vui lòng nhập nội dung bài viết.')
    if (form.publishedAt && !publishedAt) return setFormError('Thời điểm xuất bản không hợp lệ.')

    const content: ArticleContentDocument = form.files.length > 0
      ? { body: form.body, files: form.files }
      : { body: form.body }
    const publishedPayload = form.status === 'PUBLISHED' && publishedAt ? { publishedAt } : {}

    setBusy(true)
    setFormError('')
    try {
      if (editingId === null) {
        const payload: CreateAdminArticlePayload = {
          title,
          slug: slug || undefined,
          excerpt: excerpt || null,
          content,
          status: form.status,
          ...publishedPayload,
        }
        await createAdminArticle(token, payload)
        toast.success('Đã tạo bài viết', form.status === 'PUBLISHED' ? 'Bài viết đã được xuất bản.' : 'Bài viết đã được lưu ở dạng bản nháp.')
      } else {
        const payload: UpdateAdminArticlePayload = {
          title,
          slug: slug || undefined,
          excerpt: excerpt || null,
          content,
          status: form.status,
          ...publishedPayload,
        }
        await updateAdminArticle(token, editingId, payload)
        toast.success('Đã cập nhật bài viết')
      }
      setEditorOpen(false)
      await loadArticles()
    } catch (reason: unknown) {
      setFormError(reason instanceof Error ? reason.message : 'Không thể lưu bài viết.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="admin-content-page">
      <div className="page-title">
        <div>
          <span className="eyebrow">Nội dung</span>
          <h1>Quản lý bài viết</h1>
          <p>Soạn thảo các bài viết chính thức của Smart Lab ở dạng bản nháp hoặc đã xuất bản.</p>
        </div>
        <div className="project-page-actions">
          <button ref={triggerRef} className="btn primary" type="button" onClick={openCreate} disabled={busy}>
            <Plus aria-hidden="true" /> Tạo bài viết
          </button>
          <button className="btn" type="button" onClick={() => void loadArticles()} disabled={loading || busy}>
            <RefreshCw aria-hidden="true" /> {loading ? 'Đang tải...' : 'Tải lại'}
          </button>
        </div>
      </div>

      <section className="panel page-section" aria-busy={loading}>
        <div className="panel-head">
          <div>
            <h2>Danh sách bài viết</h2>
            <p>{totalElements.toLocaleString('vi-VN')} bài viết đang hoạt động trong hệ thống.</p>
          </div>
          <BookOpenText aria-hidden="true" size={20} />
        </div>

        <Feedback error={error} />
        {loading ? <div className="empty">Đang tải bài viết...</div> : items.length === 0 ? (
          <EmptyState title="Chưa có bài viết" description="Tạo bài viết đầu tiên ở dạng bản nháp hoặc xuất bản ngay." />
        ) : (
          <>
            <div className="admin-content-table-wrap">
              <table className="admin-content-table">
                <caption className="sr-only">Danh sách bài viết chính thức</caption>
                <thead>
                  <tr>
                    <th scope="col">Tiêu đề</th>
                    <th scope="col">Slug</th>
                    <th scope="col">Trạng thái</th>
                    <th scope="col">Xuất bản</th>
                    <th scope="col">Cập nhật</th>
                    <th scope="col"><span className="sr-only">Thao tác</span></th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td className="admin-content-title-cell">{item.title}</td>
                      <td className="admin-content-muted-cell">/{item.slug}</td>
                      <td><ArticleStatusBadge status={item.status} /></td>
                      <td className="admin-content-muted-cell">{item.status === 'PUBLISHED' ? formatAdminDate(item.publishedAt) : 'Chưa xuất bản'}</td>
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
                    <ArticleStatusBadge status={item.status} />
                  </div>
                  <p className="admin-content-muted-cell">/{item.slug}</p>
                  <p className="admin-content-mobile-meta">{item.status === 'PUBLISHED' ? `Xuất bản: ${formatAdminDate(item.publishedAt)}` : 'Chưa xuất bản'} · Cập nhật: {formatAdminDate(item.updatedAt)}</p>
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
        title={editingId === null ? 'Tạo bài viết mới' : 'Sửa bài viết'}
        description="Nội dung bài viết dùng trường body đơn giản theo hợp đồng Article hiện tại."
        submitLabel={editingId === null ? 'Tạo bài viết' : 'Lưu thay đổi'}
        busy={busy}
        triggerRef={triggerRef}
        onClose={closeEditor}
        onSubmit={(event) => void handleSubmit(event)}
      >
        {formError ? <Feedback error={formError} /> : null}
        <div className="admin-content-form-grid">
          <label className="field admin-content-field-full" htmlFor="article-title">
            <span>Tiêu đề *</span>
            <input id="article-title" className="input" type="text" maxLength={500} value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} autoFocus required aria-invalid={Boolean(formError)} />
          </label>
          <label className="field" htmlFor="article-slug">
            <span>Slug</span>
            <input id="article-slug" className="input" type="text" maxLength={500} value={form.slug} onChange={(event) => setForm({ ...form, slug: event.target.value })} placeholder="Tự sinh nếu để trống khi tạo mới" />
          </label>
          <label className="field" htmlFor="article-status">
            <span>Trạng thái *</span>
            <select id="article-status" className="select" value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as LabArticleStatus })}>
              <option value="DRAFT">Bản nháp</option>
              <option value="PUBLISHED">Đã xuất bản</option>
            </select>
          </label>
          <label className="field admin-content-field-full" htmlFor="article-published-at">
            <span>Thời điểm xuất bản {form.status === 'PUBLISHED' ? '(tùy chọn)' : '(giữ nguyên khi bỏ trống)'}</span>
            <input id="article-published-at" className="input" type="datetime-local" value={form.publishedAt} onChange={(event) => setForm({ ...form, publishedAt: event.target.value })} />
            <small className="hint">Nếu xuất bản mà bỏ trống, backend sẽ dùng thời điểm hiện tại. Khi chuyển về bản nháp, thời điểm lịch sử được backend giữ lại.</small>
          </label>
          <label className="field admin-content-field-full" htmlFor="article-excerpt">
            <span>Mô tả ngắn</span>
            <textarea id="article-excerpt" className="textarea" rows={3} maxLength={20000} value={form.excerpt} onChange={(event) => setForm({ ...form, excerpt: event.target.value })} />
          </label>
          <label className="field admin-content-field-full" htmlFor="article-body">
            <span>Nội dung body *</span>
            <textarea id="article-body" className="textarea admin-content-body-input" rows={14} value={form.body} onChange={(event) => setForm({ ...form, body: event.target.value })} aria-describedby="article-body-help" required />
            <small id="article-body-help" className="hint">Chỉ nhập văn bản body. Không có block editor hoặc HTML schema trong Gate 2B.</small>
          </label>
        </div>
      </AdminContentDialog>
    </div>
  )
}

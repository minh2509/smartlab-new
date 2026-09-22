import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { Plus, Tags, Trash2, X } from 'lucide-react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { useToast } from '../../../shared/toast/useToast'
import { useAuth } from '../../auth/authContext'
import { createContentCategory, deleteContentCategory, listAdminContentCategories, setContentCategoryActive } from '../api'
import type { ContentCategory } from '../types'
import './PostCategoriesPage.css'

type CategoryForm = {
  code: string
  name: string
  description: string
}

const EMPTY_FORM: CategoryForm = { code: '', name: '', description: '' }
const PAGE_SIZE = 4

export function PostCategoriesPage() {
  const { token } = useAuth()
  const toast = useToast()
  const [categories, setCategories] = useState<ContentCategory[]>([])
  const [form, setForm] = useState<CategoryForm>(EMPTY_FORM)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [page, setPage] = useState(1)
  const [mutatingId, setMutatingId] = useState<number | null>(null)
  const [isCreateOpen, setIsCreateOpen] = useState(false)

  const loadCategories = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError(null)
    try {
      setCategories(await listAdminContentCategories(token))
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể tải danh mục bài viết.')
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => {
    void loadCategories()
  }, [loadCategories])

  const totalPages = Math.max(1, Math.ceil(categories.length / PAGE_SIZE))
  const pagedCategories = useMemo(
    () => categories.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE),
    [categories, page],
  )

  useEffect(() => {
    if (page > totalPages) setPage(totalPages)
  }, [page, totalPages])

  const closeCreateDialog = useCallback(() => {
    if (busy) return
    setIsCreateOpen(false)
    setForm(EMPTY_FORM)
    setFormError(null)
  }, [busy])

  useEffect(() => {
    if (!isCreateOpen) return
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) closeCreateDialog()
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [busy, closeCreateDialog, isCreateOpen])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || busy) return

    const code = form.code.trim().toUpperCase()
    const name = form.name.trim()
    const description = form.description.trim()
    if (!code) return setFormError('Vui lòng nhập mã danh mục.')
    if (!name) return setFormError('Vui lòng nhập tên danh mục.')
    if (code.length > 80) return setFormError('Mã danh mục không được vượt quá 80 ký tự.')
    if (name.length > 150) return setFormError('Tên danh mục không được vượt quá 150 ký tự.')
    if (description.length > 500) return setFormError('Mô tả không được vượt quá 500 ký tự.')

    setBusy(true)
    setFormError(null)
    try {
      const created = await createContentCategory(token, {
        code,
        name,
        description: description || null,
      })
      const nextCategories = [...categories, created]
      setCategories(nextCategories)
      setPage(Math.ceil(nextCategories.length / PAGE_SIZE))
      setForm(EMPTY_FORM)
      setIsCreateOpen(false)
      toast.success('Đã tạo danh mục', created.name)
    } catch (reason: unknown) {
      setFormError(reason instanceof Error ? reason.message : 'Không thể tạo danh mục bài viết.')
    } finally {
      setBusy(false)
    }
  }

  async function handleToggleActive(category: ContentCategory) {
    if (!token || mutatingId !== null) return
    setMutatingId(category.id)
    setError(null)
    try {
      const updated = await setContentCategoryActive(token, category.id, !category.isActive)
      setCategories((current) => current.map((item) => item.id === updated.id ? updated : item))
      toast.success(updated.isActive ? 'Đã kích hoạt danh mục' : 'Đã tắt danh mục', updated.name)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể đổi trạng thái danh mục.')
    } finally {
      setMutatingId(null)
    }
  }

  async function handleDelete(category: ContentCategory) {
    if (!token || mutatingId !== null || !window.confirm(`Xóa vĩnh viễn danh mục “${category.name}”? Các bài viết đang dùng danh mục này sẽ được chuyển về không có danh mục.`)) return
    setMutatingId(category.id)
    setError(null)
    try {
      await deleteContentCategory(token, category.id)
      setCategories((current) => current.filter((item) => item.id !== category.id))
      toast.success('Đã xóa danh mục', category.name)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể xóa danh mục.')
    } finally {
      setMutatingId(null)
    }
  }

  return (
    <section className="post-categories-page">
      <header className="page-title post-categories-title">
        <div>
          <span className="eyebrow">Nội dung nội bộ</span>
          <h1>Danh mục bài viết</h1>
          <p>Tạo và kiểm tra các danh mục đang được dùng trong trình soạn thảo bài viết.</p>
        </div>
        <div className="post-categories-title-actions">
          <span className="post-categories-count">{categories.length} danh mục</span>
          <button className="btn primary" type="button" onClick={() => setIsCreateOpen(true)}>
            <Plus size={16} aria-hidden="true" /> Tạo danh mục
          </button>
        </div>
      </header>

      <section className="panel post-category-list" aria-labelledby="post-category-list-title">
          <div className="panel-head">
            <div>
              <h2 id="post-category-list-title">Danh mục đang quản lý</h2>
              <p>Danh mục đang hoạt động sẽ xuất hiện trong trình soạn thảo bài viết.</p>
            </div>
          </div>
          <Feedback error={error ?? undefined} />
          {loading ? <div className="empty" aria-busy="true">Đang tải danh mục...</div> : null}
          {!loading && !error && categories.length === 0 ? (
            <EmptyState title="Chưa có danh mục" description="Nhấn “Tạo danh mục” để thêm danh mục đầu tiên." />
          ) : null}
          {!loading && categories.length > 0 ? (
            <>
              <div className="post-category-table-wrap">
                <table className="table post-category-table">
                  <thead><tr><th>Tên</th><th>Mã</th><th>Mô tả</th><th>Trạng thái</th><th aria-label="Thao tác" /></tr></thead>
                  <tbody>
                    {pagedCategories.map((category) => (
                      <tr key={category.id}>
                        <td><strong>{category.name}</strong></td>
                        <td><code>{category.code}</code></td>
                        <td>{category.description || <span className="muted">Không có mô tả</span>}</td>
                        <td><span className={`badge ${category.isActive ? 'success' : 'danger'}`}>{category.isActive ? 'Đang hoạt động' : 'Đã tắt'}</span></td>
                        <td>
                          <div className="post-category-actions">
                            <button className="btn ghost table-btn" type="button" disabled={mutatingId !== null} onClick={() => void handleToggleActive(category)}>
                              {category.isActive ? 'Tắt' : 'Kích hoạt'}
                            </button>
                            <button className="icon-btn danger" type="button" disabled={mutatingId !== null} onClick={() => void handleDelete(category)} aria-label={`Xóa danh mục ${category.name}`} title="Xóa vĩnh viễn">
                              <Trash2 size={15} aria-hidden="true" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={page} totalPages={totalPages} onChange={setPage} />
            </>
          ) : null}
      </section>

      {isCreateOpen ? (
        <div className="post-category-dialog-backdrop" onMouseDown={closeCreateDialog}>
          <form
            className="post-category-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="post-category-dialog-title"
            onSubmit={(event) => void handleSubmit(event)}
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="panel-head post-category-dialog-head">
              <div>
                <span className="eyebrow">Danh mục bài viết</span>
                <h2 id="post-category-dialog-title">Tạo danh mục mới</h2>
                <p>Mã được chuẩn hóa thành chữ in hoa và phải là duy nhất.</p>
              </div>
              <button className="icon-btn" type="button" onClick={closeCreateDialog} disabled={busy} aria-label="Đóng">
                <X size={18} aria-hidden="true" />
              </button>
            </div>

            <div className="post-category-dialog-body">
              <label className="field" htmlFor="post-category-code">
                <span>Mã danh mục <strong className="req">*</strong></span>
                <input
                  id="post-category-code"
                  className="input"
                  value={form.code}
                  maxLength={80}
                  disabled={busy}
                  autoFocus
                  placeholder="Ví dụ: LAB_NEWS"
                  onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))}
                />
              </label>

              <label className="field" htmlFor="post-category-name">
                <span>Tên hiển thị <strong className="req">*</strong></span>
                <input
                  id="post-category-name"
                  className="input"
                  value={form.name}
                  maxLength={150}
                  disabled={busy}
                  placeholder="Tin tức phòng Lab"
                  onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                />
              </label>

              <label className="field" htmlFor="post-category-description">
                <span>Mô tả</span>
                <textarea
                  id="post-category-description"
                  className="textarea"
                  value={form.description}
                  maxLength={500}
                  rows={5}
                  disabled={busy}
                  onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
                />
                <small className="hint">{form.description.length}/500 ký tự</small>
              </label>

              <Feedback error={formError ?? undefined} />
            </div>

            <div className="post-category-dialog-actions">
              <button className="btn" type="button" onClick={closeCreateDialog} disabled={busy}>Hủy</button>
              <button className="btn primary" type="submit" disabled={busy}>
                <Tags size={16} aria-hidden="true" /> {busy ? 'Đang tạo...' : 'Tạo danh mục'}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </section>
  )
}

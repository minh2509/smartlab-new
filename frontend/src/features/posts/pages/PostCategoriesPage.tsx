import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { Plus, Tags } from 'lucide-react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { useAuth } from '../../auth/authContext'
import { createContentCategory, listContentCategories } from '../api'
import type { ContentCategory } from '../types'
import './PostCategoriesPage.css'

type CategoryForm = {
  code: string
  name: string
  description: string
}

const EMPTY_FORM: CategoryForm = { code: '', name: '', description: '' }

export function PostCategoriesPage() {
  const { token } = useAuth()
  const toast = useToast()
  const [categories, setCategories] = useState<ContentCategory[]>([])
  const [form, setForm] = useState<CategoryForm>(EMPTY_FORM)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const loadCategories = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError(null)
    try {
      setCategories(await listContentCategories(token))
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể tải danh mục bài viết.')
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => {
    void loadCategories()
  }, [loadCategories])

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
      setCategories((current) => [...current, created])
      setForm(EMPTY_FORM)
      toast.success('Đã tạo danh mục', created.name)
    } catch (reason: unknown) {
      setFormError(reason instanceof Error ? reason.message : 'Không thể tạo danh mục bài viết.')
    } finally {
      setBusy(false)
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
        <span className="post-categories-count">{categories.length} danh mục</span>
      </header>

      <div className="post-categories-grid">
        <form className="panel post-category-form" onSubmit={(event) => void handleSubmit(event)}>
          <div className="panel-head">
            <div>
              <h2>Tạo danh mục</h2>
              <p>Mã được chuẩn hóa thành chữ in hoa và phải là duy nhất.</p>
            </div>
            <Tags aria-hidden="true" />
          </div>

          <label className="field" htmlFor="post-category-code">
            <span>Mã danh mục <strong className="req">*</strong></span>
            <input
              id="post-category-code"
              className="input"
              value={form.code}
              maxLength={80}
              disabled={busy}
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
              rows={4}
              disabled={busy}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
            <small className="hint">{form.description.length}/500 ký tự</small>
          </label>

          <Feedback error={formError ?? undefined} />
          <button className="btn primary" type="submit" disabled={busy}>
            <Plus aria-hidden="true" /> {busy ? 'Đang tạo...' : 'Tạo danh mục'}
          </button>
        </form>

        <section className="panel post-category-list" aria-labelledby="post-category-list-title">
          <div className="panel-head">
            <div>
              <h2 id="post-category-list-title">Danh mục đang hoạt động</h2>
              <p>Chỉ các danh mục trong danh sách này xuất hiện ở trình soạn thảo.</p>
            </div>
          </div>
          <Feedback error={error ?? undefined} />
          {loading ? <div className="empty" aria-busy="true">Đang tải danh mục...</div> : null}
          {!loading && !error && categories.length === 0 ? (
            <EmptyState title="Chưa có danh mục" description="Tạo danh mục đầu tiên bằng biểu mẫu bên cạnh." />
          ) : null}
          {!loading && categories.length > 0 ? (
            <div className="post-category-table-wrap">
              <table className="table post-category-table">
                <thead><tr><th>Tên</th><th>Mã</th><th>Mô tả</th></tr></thead>
                <tbody>
                  {categories.map((category) => (
                    <tr key={category.id}>
                      <td><strong>{category.name}</strong></td>
                      <td><code>{category.code}</code></td>
                      <td>{category.description || <span className="muted">Không có mô tả</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </section>
      </div>
    </section>
  )
}

import {
  ArrowDown,
  ArrowUp,
  Check,
  CheckSquare,
  Edit2,
  Eye,
  EyeOff,
  Layers,
  Plus,
  Search,
  Square,
  X,
} from 'lucide-react'
import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { useToast } from '../../../shared/toast/useToast'
import { useAuth } from '../../auth/authContext'
import {
  assignDocumentsCategory,
  createAdminDocumentCategory,
  listAdminDocumentCategories,
  listDocumentsForAssignment,
  reorderAdminDocumentCategories,
  toggleAdminDocumentCategoryActive,
  updateAdminDocumentCategory,
} from '../adminCategoryApi'
import type {
  AdminDocumentCategory,
  AdminDocumentItem,
  CreateDocumentCategoryPayload,
  UpdateDocumentCategoryPayload,
} from '../adminCategoryTypes'
import './AdminDocumentCategoriesPage.css'

const EMPTY_CREATE_FORM: CreateDocumentCategoryPayload = {
  code: '',
  name: '',
  description: '',
  displayOrder: 0,
  isActive: true,
}

export function AdminDocumentCategoriesPage() {
  const { token } = useAuth()
  const toast = useToast()

  const [categories, setCategories] = useState<AdminDocumentCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Create Modal
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState<CreateDocumentCategoryPayload>(EMPTY_CREATE_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  // Edit Modal
  const [editingCategory, setEditingCategory] = useState<AdminDocumentCategory | null>(null)
  const [editForm, setEditForm] = useState<UpdateDocumentCategoryPayload>({ name: '', description: '', displayOrder: 0, isActive: true })

  // Document Assignment Modal
  const [assigningCategory, setAssigningCategory] = useState<AdminDocumentCategory | null>(null)
  const [documents, setDocuments] = useState<AdminDocumentItem[]>([])
  const [loadingDocs, setLoadingDocs] = useState(false)
  const [selectedDocIds, setSelectedDocIds] = useState<Set<number>>(new Set())
  const [docSearch, setDocSearch] = useState('')
  const [docTab, setDocTab] = useState<'all' | 'in_cat' | 'unassigned'>('all')
  const [assignPage, setAssignPage] = useState(1)
  const ASSIGN_PAGE_SIZE = 8

  const loadCategories = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError(null)
    try {
      const data = await listAdminDocumentCategories(token)
      setCategories(data)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể tải danh sách nhóm tài liệu.')
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => {
    void loadCategories()
  }, [loadCategories])

  // Reset assignment page whenever tab or search changes
  useEffect(() => {
    setAssignPage(1)
  }, [docSearch, docTab])

  // Keyboard handlers for escape
  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) {
        setIsCreateOpen(false)
        setEditingCategory(null)
        setAssigningCategory(null)
      }
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [busy])

  // Move Up / Move Down for Ordering
  async function handleMove(index: number, direction: 'up' | 'down') {
    if (!token || busy) return
    const targetIndex = direction === 'up' ? index - 1 : index + 1
    if (targetIndex < 0 || targetIndex >= categories.length) return

    const updated = [...categories]
    const current = updated[index]
    const neighbor = updated[targetIndex]

    const tempOrder = current.displayOrder
    current.displayOrder = neighbor.displayOrder
    neighbor.displayOrder = tempOrder

    updated[index] = neighbor
    updated[targetIndex] = current

    setCategories(updated)
    setBusy(true)
    try {
      await reorderAdminDocumentCategories(token, {
        items: updated.map((item, idx) => ({ id: item.id, displayOrder: idx + 1 })),
      })
      toast.success('Đã cập nhật thứ tự nhóm tài liệu.')
      void loadCategories()
    } catch {
      toast.error('Không thể cập nhật thứ tự nhóm.')
      void loadCategories()
    } finally {
      setBusy(false)
    }
  }

  // Toggle Visibility / Active status
  async function handleToggleActive(category: AdminDocumentCategory) {
    if (!token || busy) return
    setBusy(true)
    try {
      const res = await toggleAdminDocumentCategoryActive(token, category.id)
      setCategories((prev) => prev.map((item) => (item.id === category.id ? res : item)))
      toast.success(res.isActive ? 'Đã kích hoạt hiển thị nhóm.' : 'Đã ẩn nhóm tài liệu.')
    } catch {
      toast.error('Không thể cập nhật trạng thái nhóm.')
    } finally {
      setBusy(false)
    }
  }

  // Create Category
  async function handleCreateSubmit(event: FormEvent) {
    event.preventDefault()
    if (!token || busy) return

    const code = createForm.code.trim().toUpperCase()
    const name = createForm.name.trim()
    const description = createForm.description?.trim() || null

    if (!code) return setFormError('Vui lòng nhập mã nhóm.')
    if (!name) return setFormError('Vui lòng nhập tên nhóm.')
    if (!/^[a-zA-Z0-9_-]+$/.test(code)) {
      return setFormError('Mã nhóm chỉ chứa chữ cái, số, gạch dưới và gạch ngang.')
    }

    setBusy(true)
    setFormError(null)
    try {
      await createAdminDocumentCategory(token, {
        code,
        name,
        description,
        displayOrder: categories.length + 1,
        isActive: createForm.isActive,
      })
      toast.success('Đã tạo nhóm tài liệu mới thành công.')
      setIsCreateOpen(false)
      setCreateForm(EMPTY_CREATE_FORM)
      void loadCategories()
    } catch (reason: unknown) {
      setFormError(reason instanceof Error ? reason.message : 'Không thể tạo nhóm.')
    } finally {
      setBusy(false)
    }
  }

  // Edit Category
  function openEdit(category: AdminDocumentCategory) {
    setEditingCategory(category)
    setEditForm({
      name: category.name,
      description: category.description ?? '',
      displayOrder: category.displayOrder,
      isActive: category.isActive,
    })
    setFormError(null)
  }

  async function handleEditSubmit(event: FormEvent) {
    event.preventDefault()
    if (!token || busy || !editingCategory) return

    const name = editForm.name.trim()
    const description = editForm.description?.trim() || null

    if (!name) return setFormError('Vui lòng nhập tên nhóm.')

    setBusy(true)
    setFormError(null)
    try {
      const updated = await updateAdminDocumentCategory(token, editingCategory.id, {
        name,
        description,
        displayOrder: editForm.displayOrder,
        isActive: editForm.isActive,
      })
      toast.success('Đã cập nhật thông tin nhóm.')
      setCategories((prev) => prev.map((item) => (item.id === updated.id ? updated : item)))
      setEditingCategory(null)
    } catch (reason: unknown) {
      setFormError(reason instanceof Error ? reason.message : 'Không thể cập nhật nhóm.')
    } finally {
      setBusy(false)
    }
  }

  // Open Document Assignment Modal
  async function openAssignment(category: AdminDocumentCategory) {
    if (!token) return
    setAssigningCategory(category)
    setLoadingDocs(true)
    setDocSearch('')
    setDocTab('all')
    setSelectedDocIds(new Set())
    try {
      const docList = await listDocumentsForAssignment(token)
      setDocuments(docList)
      const initialSelected = new Set(
        docList.filter((d) => d.categoryId === category.id).map((d) => d.id),
      )
      setSelectedDocIds(initialSelected)
    } catch {
      toast.error('Không thể tải danh sách tài liệu để phân loại.')
    } finally {
      setLoadingDocs(false)
    }
  }

  // Filtered documents for assignment modal
  const filteredDocs = useMemo(() => {
    const q = docSearch.trim().toLowerCase()
    return documents.filter((doc) => {
      if (docTab === 'in_cat' && doc.categoryId !== assigningCategory?.id) return false
      if (docTab === 'unassigned' && doc.categoryId !== null) return false
      if (!q) return true
      return (
        doc.title.toLowerCase().includes(q) ||
        doc.projectName.toLowerCase().includes(q) ||
        doc.fileName.toLowerCase().includes(q)
      )
    })
  }, [assigningCategory?.id, docSearch, docTab, documents])

  const assignTotalPages = Math.max(1, Math.ceil(filteredDocs.length / ASSIGN_PAGE_SIZE))
  const pagedFilteredDocs = useMemo(
    () => filteredDocs.slice((assignPage - 1) * ASSIGN_PAGE_SIZE, assignPage * ASSIGN_PAGE_SIZE),
    [assignPage, filteredDocs],
  )

  function toggleSelectDoc(id: number) {
    setSelectedDocIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function handleSelectAllFiltered() {
    setSelectedDocIds((prev) => {
      const next = new Set(prev)
      filteredDocs.forEach((d) => next.add(d.id))
      return next
    })
  }

  function handleDeselectAllFiltered() {
    setSelectedDocIds((prev) => {
      const next = new Set(prev)
      filteredDocs.forEach((d) => next.delete(d.id))
      return next
    })
  }

  // Save Assignment
  async function handleSaveAssignment() {
    if (!token || busy || !assigningCategory) return
    setBusy(true)
    try {
      // Find docs that should be assigned to this category
      const docIdsToAssign = Array.from(selectedDocIds)
      if (docIdsToAssign.length > 0) {
        await assignDocumentsCategory(token, {
          categoryId: assigningCategory.id,
          documentIds: docIdsToAssign,
        })
      }

      // Check previously assigned documents that were deselected -> unassign them
      const previouslyAssigned = documents.filter((d) => d.categoryId === assigningCategory.id)
      const deselected = previouslyAssigned
        .filter((d) => !selectedDocIds.has(d.id))
        .map((d) => d.id)

      if (deselected.length > 0) {
        await assignDocumentsCategory(token, {
          categoryId: null,
          documentIds: deselected,
        })
      }

      toast.success('Đã lưu phân loại tài liệu thành công.')
      setAssigningCategory(null)
      void loadCategories()
    } catch {
      toast.error('Không thể cập nhật phân loại tài liệu.')
    } finally {
      setBusy(false)
    }
  }

  const totalAssignedDocs = categories.reduce((sum, c) => sum + (c.documentCount || 0), 0)

  return (
    <div className="admin-doc-cats-page">
      <header className="page-title admin-doc-cats-header">
        <div>
          <span className="eyebrow">Quản trị nội dung</span>
          <h1>Nhóm tài liệu chuyên môn</h1>
          <p>
            Tổ chức, phân loại, sắp xếp thứ tự và quản lý hiển thị các nhóm tài liệu học thuật.
          </p>
        </div>
        <div className="admin-doc-cats-actions">
          <span className="admin-doc-cats-count-badge">
            {categories.length} nhóm · {totalAssignedDocs} tài liệu đã phân loại
          </span>
          <button
            className="btn primary"
            type="button"
            onClick={() => {
              setCreateForm({ ...EMPTY_CREATE_FORM, displayOrder: categories.length + 1 })
              setFormError(null)
              setIsCreateOpen(true)
            }}
          >
            <Plus size={16} aria-hidden="true" />
            Tạo nhóm mới
          </button>
        </div>
      </header>

      {error ? (
        <div className="empty" role="alert">
          <Feedback error={error} />
          <button className="btn" type="button" onClick={() => void loadCategories()}>
            Thử tải lại
          </button>
        </div>
      ) : null}

      {loading && categories.length === 0 ? (
        <div className="empty" aria-busy="true">
          Đang tải danh sách nhóm tài liệu...
        </div>
      ) : null}

      {!loading && categories.length === 0 ? (
        <EmptyState
          title="Chưa có nhóm tài liệu nào"
          description="Bấm 'Tạo nhóm mới' để thiết lập nhóm tài liệu học thuật đầu tiên."
        />
      ) : null}

      {!loading && categories.length > 0 ? (
        <section className="panel admin-doc-cats-panel" aria-labelledby="admin-doc-cats-table-title">
          <div className="panel-head">
            <div>
              <h2 id="admin-doc-cats-table-title">Danh sách nhóm chuyên môn</h2>
              <p>Các nhóm đang hoạt động sẽ xuất hiện trên trang lưu trữ công khai /tai-lieu.</p>
            </div>
          </div>

          <div className="admin-doc-cats-table-wrap">
            <table className="table admin-doc-cats-table">
              <thead>
                <tr>
                  <th style={{ width: '85px' }}>Thứ tự</th>
                  <th style={{ minWidth: '220px' }}>Tên & Mã nhóm</th>
                  <th>Mô tả chuyên môn</th>
                  <th style={{ width: '120px', textAlign: 'center' }}>Tài liệu</th>
                  <th style={{ width: '140px', textAlign: 'center' }}>Trạng thái</th>
                  <th style={{ width: '230px', textAlign: 'right' }} aria-label="Thao tác" />
                </tr>
              </thead>
              <tbody>
                {categories.map((category, index) => (
                  <tr key={category.id} className={!category.isActive ? 'is-inactive-row' : ''}>
                    <td>
                      <div className="admin-doc-cat-order-cell">
                        <span className="admin-doc-cat-order-num">#{category.displayOrder}</span>
                        <div className="admin-doc-cat-order-arrows">
                          <button
                            type="button"
                            className="admin-doc-cat-order-btn"
                            onClick={() => void handleMove(index, 'up')}
                            disabled={index === 0 || busy}
                            aria-label={`Di chuyển nhóm ${category.name} lên trên`}
                            title="Di chuyển lên trên"
                          >
                            <ArrowUp size={12} aria-hidden="true" />
                          </button>
                          <button
                            type="button"
                            className="admin-doc-cat-order-btn"
                            onClick={() => void handleMove(index, 'down')}
                            disabled={index === categories.length - 1 || busy}
                            aria-label={`Di chuyển nhóm ${category.name} xuống dưới`}
                            title="Di chuyển xuống dưới"
                          >
                            <ArrowDown size={12} aria-hidden="true" />
                          </button>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="admin-doc-cat-name-cell">
                        <strong className="admin-doc-cat-name">{category.name}</strong>
                        <code className="admin-doc-cat-code">{category.code}</code>
                      </div>
                    </td>
                    <td>
                      <span className="admin-doc-cat-desc-cell">
                        {category.description || <span className="muted">Chưa có mô tả</span>}
                      </span>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <span className="badge neutral admin-doc-count-badge">
                        <Layers size={12} aria-hidden="true" />
                        {category.documentCount}
                      </span>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <span className={`badge ${category.isActive ? 'success' : 'neutral'}`}>
                        {category.isActive ? 'Đang hiển thị' : 'Đang ẩn'}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div className="admin-doc-cat-actions-cell">
                        <button
                          className="btn ghost table-btn"
                          type="button"
                          onClick={() => void handleToggleActive(category)}
                          disabled={busy}
                          title={category.isActive ? 'Ẩn nhóm này khỏi trang công khai' : 'Hiện nhóm này'}
                        >
                          {category.isActive ? (
                            <>
                              <EyeOff size={13} aria-hidden="true" /> Ẩn
                            </>
                          ) : (
                            <>
                              <Eye size={13} aria-hidden="true" /> Hiện
                            </>
                          )}
                        </button>
                        <button
                          className="btn ghost table-btn"
                          type="button"
                          onClick={() => openEdit(category)}
                          disabled={busy}
                        >
                          <Edit2 size={13} aria-hidden="true" /> Sửa
                        </button>
                        <button
                          className="btn secondary table-btn"
                          type="button"
                          onClick={() => void openAssignment(category)}
                          disabled={busy}
                        >
                          <Layers size={13} aria-hidden="true" /> Phân loại
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      {/* Create Modal */}
      {isCreateOpen ? (
        <div
          className="admin-doc-cats-modal-backdrop"
          onClick={(e) => {
            if (e.target === e.currentTarget && !busy) setIsCreateOpen(false)
          }}
          role="presentation"
        >
          <div
            className="admin-doc-cats-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-modal-title"
          >
            <div className="admin-doc-cats-modal-header">
              <h2 id="create-modal-title">Tạo nhóm tài liệu mới</h2>
              <button
                type="button"
                className="admin-doc-cats-modal-close"
                onClick={() => setIsCreateOpen(false)}
                aria-label="Đóng"
                disabled={busy}
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>
            <form onSubmit={(e) => void handleCreateSubmit(e)}>
              <div className="admin-doc-cats-modal-body">
                {formError ? <Feedback error={formError} /> : null}

                <div className="admin-doc-cats-form-group">
                  <label htmlFor="create-cat-code">Mã nhóm (duy nhất, không đổi)</label>
                  <input
                    id="create-cat-code"
                    className="input"
                    type="text"
                    required
                    placeholder="VD: NGHIEN-CUU-HOC-THUAT"
                    value={createForm.code}
                    onChange={(e) =>
                      setCreateForm((prev) => ({ ...prev, code: e.target.value.toUpperCase() }))
                    }
                  />
                </div>

                <div className="admin-doc-cats-form-group">
                  <label htmlFor="create-cat-name">Tên nhóm</label>
                  <input
                    id="create-cat-name"
                    className="input"
                    type="text"
                    required
                    placeholder="VD: Nghiên cứu & Học thuật"
                    value={createForm.name}
                    onChange={(e) =>
                      setCreateForm((prev) => ({ ...prev, name: e.target.value }))
                    }
                  />
                </div>

                <div className="admin-doc-cats-form-group">
                  <label htmlFor="create-cat-desc">Mô tả nhóm</label>
                  <textarea
                    id="create-cat-desc"
                    rows={3}
                    placeholder="Mô tả phạm vi nội dung của nhóm tài liệu..."
                    value={createForm.description ?? ''}
                    onChange={(e) =>
                      setCreateForm((prev) => ({ ...prev, description: e.target.value }))
                    }
                  />
                </div>

                <div className="admin-doc-cats-form-group">
                  <label className="admin-doc-cats-checkbox-label">
                    <input
                      type="checkbox"
                      checked={createForm.isActive}
                      onChange={(e) =>
                        setCreateForm((prev) => ({ ...prev, isActive: e.target.checked }))
                      }
                    />
                    <span>Kích hoạt hiển thị công khai ngay</span>
                  </label>
                </div>
              </div>

              <div className="admin-doc-cats-modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setIsCreateOpen(false)}
                  disabled={busy}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  {busy ? 'Đang tạo...' : 'Tạo nhóm'}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {/* Edit Modal */}
      {editingCategory ? (
        <div
          className="admin-doc-cats-modal-backdrop"
          onClick={(e) => {
            if (e.target === e.currentTarget && !busy) setEditingCategory(null)
          }}
          role="presentation"
        >
          <div
            className="admin-doc-cats-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="edit-modal-title"
          >
            <div className="admin-doc-cats-modal-header">
              <h2 id="edit-modal-title">Chỉnh sửa: {editingCategory.name}</h2>
              <button
                type="button"
                className="admin-doc-cats-modal-close"
                onClick={() => setEditingCategory(null)}
                aria-label="Đóng"
                disabled={busy}
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>
            <form onSubmit={(e) => void handleEditSubmit(e)}>
              <div className="admin-doc-cats-modal-body">
                {formError ? <Feedback error={formError} /> : null}

                <div className="admin-doc-cats-form-group">
                  <label htmlFor="edit-cat-code">Mã nhóm</label>
                  <input
                    id="edit-cat-code"
                    className="input"
                    type="text"
                    value={editingCategory.code}
                    disabled
                  />
                </div>

                <div className="admin-doc-cats-form-group">
                  <label htmlFor="edit-cat-name">Tên nhóm</label>
                  <input
                    id="edit-cat-name"
                    className="input"
                    type="text"
                    required
                    value={editForm.name}
                    onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                  />
                </div>

                <div className="admin-doc-cats-form-group">
                  <label htmlFor="edit-cat-desc">Mô tả nhóm</label>
                  <textarea
                    id="edit-cat-desc"
                    rows={3}
                    value={editForm.description ?? ''}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, description: e.target.value }))
                    }
                  />
                </div>

                <div className="admin-doc-cats-form-group">
                  <label className="admin-doc-cats-checkbox-label">
                    <input
                      type="checkbox"
                      checked={editForm.isActive}
                      onChange={(e) =>
                        setEditForm((prev) => ({ ...prev, isActive: e.target.checked }))
                      }
                    />
                    <span>Hiển thị công khai</span>
                  </label>
                </div>
              </div>

              <div className="admin-doc-cats-modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setEditingCategory(null)}
                  disabled={busy}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  {busy ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {/* Document Assignment Modal */}
      {assigningCategory ? (
        <div
          className="admin-doc-cats-modal-backdrop"
          onClick={(e) => {
            if (e.target === e.currentTarget && !busy) setAssigningCategory(null)
          }}
          role="presentation"
        >
          <div
            className="admin-doc-cats-modal is-assignment"
            role="dialog"
            aria-modal="true"
            aria-labelledby="assign-modal-title"
          >
            <div className="admin-doc-cats-modal-header">
              <div>
                <h2 id="assign-modal-title">Phân loại tài liệu vào nhóm: {assigningCategory.name}</h2>
                <p style={{ margin: '4px 0 0 0', fontSize: '0.85rem', color: '#64748b' }}>
                  Đang chọn {selectedDocIds.size} tài liệu thuộc nhóm này.
                </p>
              </div>
              <button
                type="button"
                className="admin-doc-cats-modal-close"
                onClick={() => setAssigningCategory(null)}
                aria-label="Đóng"
                disabled={busy}
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>

            <div className="admin-doc-cats-modal-body">
              <div className="admin-doc-assign-search-row">
                <div className="admin-doc-assign-search">
                  <Search size={15} aria-hidden="true" />
                  <input
                    type="search"
                    className="input"
                    placeholder="Tìm tài liệu theo tên, dự án, tên tệp..."
                    value={docSearch}
                    onChange={(e) => setDocSearch(e.target.value)}
                  />
                </div>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={handleSelectAllFiltered}
                  disabled={filteredDocs.length === 0}
                >
                  <CheckSquare size={14} aria-hidden="true" />
                  Chọn tất cả ({filteredDocs.length})
                </button>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={handleDeselectAllFiltered}
                  disabled={selectedDocIds.size === 0}
                >
                  <Square size={14} aria-hidden="true" />
                  Bỏ chọn tất cả
                </button>
              </div>

              <div className="admin-doc-assign-tabs">
                <button
                  type="button"
                  className={`admin-doc-assign-tab ${docTab === 'all' ? 'is-active' : ''}`}
                  onClick={() => setDocTab('all')}
                >
                  Tất cả tài liệu ({documents.length})
                </button>
                <button
                  type="button"
                  className={`admin-doc-assign-tab ${docTab === 'in_cat' ? 'is-active' : ''}`}
                  onClick={() => setDocTab('in_cat')}
                >
                  Đang trong nhóm này (
                  {documents.filter((d) => d.categoryId === assigningCategory.id).length})
                </button>
                <button
                  type="button"
                  className={`admin-doc-assign-tab ${docTab === 'unassigned' ? 'is-active' : ''}`}
                  onClick={() => setDocTab('unassigned')}
                >
                  Chưa phân loại ({documents.filter((d) => d.categoryId === null).length})
                </button>
              </div>

              {loadingDocs ? (
                <div className="empty tight" aria-busy="true">
                  Đang tải danh sách tài liệu...
                </div>
              ) : null}

              {!loadingDocs && filteredDocs.length === 0 ? (
                <EmptyState
                  title="Không có tài liệu nào"
                  description="Không tìm thấy tài liệu phù hợp với bộ lọc hiện tại."
                />
              ) : null}

              {!loadingDocs && filteredDocs.length > 0 ? (
                <>
                  <div className="admin-doc-assign-docs-list">
                    {pagedFilteredDocs.map((doc) => {
                      const isSelected = selectedDocIds.has(doc.id)
                      const isCurrentlyInThisCat = doc.categoryId === assigningCategory.id

                      return (
                        <div
                          key={doc.id}
                          className={`admin-doc-assign-doc-row ${isSelected ? 'is-selected' : ''}`}
                          onClick={() => toggleSelectDoc(doc.id)}
                          role="checkbox"
                          aria-checked={isSelected}
                          tabIndex={0}
                          onKeyDown={(e) => {
                            if (e.key === ' ' || e.key === 'Enter') {
                              e.preventDefault()
                              toggleSelectDoc(doc.id)
                            }
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center' }}>
                            {isSelected ? (
                              <CheckSquare size={18} color="#f2711c" aria-hidden="true" />
                            ) : (
                              <Square size={18} color="#94a3b8" aria-hidden="true" />
                            )}
                          </div>
                          <div className="admin-doc-assign-doc-info">
                            <strong className="admin-doc-assign-doc-title">{doc.title}</strong>
                            <div className="admin-doc-assign-doc-sub">
                              <span>Dự án: {doc.projectName}</span>
                              <span>•</span>
                              <span>Tệp: {doc.fileName}</span>
                              <span>•</span>
                              <span>Quyền: {doc.accessScope}</span>
                              <span>•</span>
                              <span
                                className={`admin-doc-assign-cat-tag ${isCurrentlyInThisCat ? 'in-target' : ''}`}
                              >
                                {doc.categoryName ? doc.categoryName : 'Chưa phân loại'}
                              </span>
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                  {filteredDocs.length > ASSIGN_PAGE_SIZE ? (
                    <div className="admin-doc-assign-pagination">
                      <Pagination
                        page={assignPage}
                        totalPages={assignTotalPages}
                        onChange={setAssignPage}
                      />
                    </div>
                  ) : null}
                </>
              ) : null}
            </div>

            <div className="admin-doc-cats-modal-footer">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setAssigningCategory(null)}
                disabled={busy}
              >
                Hủy
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => void handleSaveAssignment()}
                disabled={busy}
              >
                <Check size={16} aria-hidden="true" />
                {busy ? 'Đang lưu...' : 'Lưu phân loại tài liệu'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}

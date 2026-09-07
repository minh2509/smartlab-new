import { Download, ExternalLink, FileText, Image, MoreHorizontal, Pencil, Plus, RefreshCw, RotateCcw, Search, Trash2, Trophy, Unlink, Upload, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { useAuth } from '../../auth/authContext'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'
import { OverlayPortalHost } from '../../../shared/ui/OverlayPortalHost'
import { Pagination } from '../../../shared/components/Pagination'
import { downloadFile, D2_UPLOAD_ACCEPT } from '../../files/api'
import { listProjects } from '../../projects/api'
import type { Project } from '../../projects/types'
import {
  createAdminAchievement,
  deleteAdminAchievement,
  listAdminAchievements,
  updateAdminAchievement,
  listAchievementFiles,
  uploadAchievementFile,
  deleteAchievementFile,
} from '../achievementAdminApi'
import { ACHIEVEMENT_TYPE_LABELS, ACHIEVEMENT_TYPES } from '../achievementAdminTypes'
import type {
  AchievementType,
  AdminLabAchievement,
  CreateAdminAchievementPayload,
  UpdateAdminAchievementPayload,
  AdminAchievementAttachment,
} from '../achievementAdminTypes'
import '../adminContent.css'

type AchievementTypeFilter = AchievementType | 'ALL'
type VisibilityFilter = 'ALL' | 'PUBLIC' | 'PRIVATE'

const ACHIEVEMENT_MIN_YEAR = 2023
const currentYear = new Date().getFullYear()
const ACHIEVEMENT_ATTACHMENT_MAX_BYTES = 25 * 1024 * 1024
const ACHIEVEMENT_ATTACHMENT_TYPES = new Set(D2_UPLOAD_ACCEPT.split(','))

type AchievementForm = {
  title: string
  summary: string
  achievementType: AchievementType
  achievementYear: number | ''
  achievementDate: string
  evidenceUrl: string
  relatedProjectId: number | ''
  recognizingOrganization: string
  isPublic: boolean
}

const emptyForm: AchievementForm = {
  title: '',
  summary: '',
  achievementType: 'RESEARCH_RESULT',
  achievementYear: currentYear,
  achievementDate: '',
  evidenceUrl: '',
  relatedProjectId: '',
  recognizingOrganization: '',
  isPublic: true,
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function validateAchievementAttachment(file: File) {
  if (file.size <= 0) return 'Tệp không được rỗng.'
  if (!ACHIEVEMENT_ATTACHMENT_TYPES.has(file.type)) return 'Định dạng tệp chưa được hỗ trợ.'
  if (file.size > ACHIEVEMENT_ATTACHMENT_MAX_BYTES) return 'Tệp không được vượt quá 25 MiB.'
  return null
}

function useAccessibleMenu() {
  const [open, setOpen] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const itemRefs = useRef<Array<HTMLButtonElement | HTMLAnchorElement | null>>([])

  const close = useCallback((restoreFocus = true) => {
    setOpen(false)
    if (restoreFocus) window.requestAnimationFrame(() => triggerRef.current?.focus())
  }, [])

  const handleMenuKeyDown = useCallback((event: KeyboardEvent<HTMLDivElement>) => {
    const items = itemRefs.current.filter((item): item is HTMLButtonElement | HTMLAnchorElement => item !== null)
    const currentIndex = items.indexOf(document.activeElement as HTMLButtonElement | HTMLAnchorElement)
    if (event.key === 'Escape') {
      event.preventDefault()
      close()
      return
    }
    if (!items.length) return
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      const nextIndex = event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? items.length - 1
          : (currentIndex + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
      items[nextIndex]?.focus()
    } else if (event.key === 'Tab') {
      close(false)
    }
  }, [close])

  useEffect(() => {
    if (open) window.requestAnimationFrame(() => itemRefs.current[0]?.focus())
  }, [open])

  useEffect(() => {
    if (!open) return
    function handleClickOutside(event: MouseEvent) {
      const target = event.target as Node
      if (!(target instanceof Node) || !triggerRef.current?.parentElement?.contains(target)) close(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [close, open])

  return { open, setOpen, close, triggerRef, itemRefs, handleMenuKeyDown }
}

function ActionMenu({ item, onEdit, onDelete }: { item: AdminLabAchievement, onEdit: () => void, onDelete: () => void }) {
  const menu = useAccessibleMenu()
  const menuRef = useRef<HTMLDivElement>(null)

  function runAction(action: () => void) {
    menu.close(false)
    menu.triggerRef.current?.focus()
    action()
  }

  return (
    <div className="achievement-action-menu" ref={menuRef}>
      <button
        ref={menu.triggerRef}
        className="btn ghost table-btn"
        type="button"
        onClick={() => menu.setOpen((previous) => !previous)}
        onKeyDown={(event) => { if (event.key === 'ArrowDown') { event.preventDefault(); menu.setOpen(true) } }}
        aria-label="Thao tác"
        aria-haspopup="menu"
        aria-expanded={menu.open}
        title="Thao tác"
      >
        <MoreHorizontal aria-hidden="true" size={20} strokeWidth={2.2} />
      </button>
      {menu.open && (
        <div className="achievement-action-menu-dropdown" role="menu" aria-label={`Thao tác với ${item.title}`} onKeyDown={menu.handleMenuKeyDown}>
          <button
            ref={(element) => { menu.itemRefs.current[0] = element }}
            className="achievement-action-menu-item"
            onClick={() => runAction(onEdit)}
            role="menuitem"
            type="button"
          >
            <Pencil size={15} aria-hidden="true" /> Sửa
          </button>
          {item.evidenceUrl && (
            <a
              ref={(element) => { menu.itemRefs.current[1] = element }}
              className="achievement-action-menu-item"
              href={item.evidenceUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => menu.close()}
              role="menuitem"
            >
              <ExternalLink size={15} aria-hidden="true" /> Mở minh chứng
            </a>
          )}
          <button
            ref={(element) => { menu.itemRefs.current[item.evidenceUrl ? 2 : 1] = element }}
            className="achievement-action-menu-item danger"
            onClick={() => runAction(onDelete)}
            role="menuitem"
            type="button"
          >
            <Trash2 size={15} aria-hidden="true" /> Gỡ thành tựu
          </button>
        </div>
      )}
    </div>
  )
}

function AttachmentMenu({ onDownload, onDetach }: { onDownload: () => void, onDetach: () => void }) {
  const menu = useAccessibleMenu()
  const menuRef = useRef<HTMLDivElement>(null)

  function runAction(action: () => void) {
    menu.close(false)
    menu.triggerRef.current?.focus()
    action()
  }

  return (
    <div className="achievement-attachment-menu" ref={menuRef}>
      <button
        ref={menu.triggerRef}
        className="achievement-attachment-trigger"
        type="button"
        onClick={(e) => { e.stopPropagation(); menu.setOpen((previous) => !previous) }}
        onKeyDown={(event) => { if (event.key === 'ArrowDown') { event.preventDefault(); menu.setOpen(true) } }}
        aria-label="Thao tác với tệp"
        aria-haspopup="menu"
        aria-expanded={menu.open}
      >
        <MoreHorizontal size={20} aria-hidden="true" />
      </button>
      {menu.open && (
        <div className="achievement-attachment-dropdown" role="menu" aria-label="Thao tác với tệp" onKeyDown={menu.handleMenuKeyDown}>
          <button ref={(element) => { menu.itemRefs.current[0] = element }} type="button" role="menuitem" className="achievement-attachment-menu-item" onClick={() => runAction(onDownload)}>
            <Download size={16} aria-hidden="true" /> Tải xuống
          </button>
          <button ref={(element) => { menu.itemRefs.current[1] = element }} type="button" role="menuitem" className="achievement-attachment-menu-item danger" onClick={() => runAction(onDetach)}>
            <Unlink size={16} aria-hidden="true" /> Gỡ khỏi thành tựu
          </button>
        </div>
      )}
    </div>
  )
}

export function AdminAchievementsPage() {
  const { token, profile } = useAuth()
  const toast = useToast()

  const [achievements, setAchievements] = useState<AdminLabAchievement[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [searchDraft, setSearchDraft] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState<AchievementTypeFilter>('ALL')
  const [yearFilter, setYearFilter] = useState<number | ''>('')
  const [visibilityFilter, setVisibilityFilter] = useState<VisibilityFilter>('ALL')
  const [pageSize, setPageSize] = useState(50)
  const [page, setPage] = useState(0)

  const [projects, setProjects] = useState<Project[]>([])
  const [projectsError, setProjectsError] = useState('')

  const [isEditorOpen, setIsEditorOpen] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [form, setForm] = useState<AchievementForm>(emptyForm)
  const [pendingAttachments, setPendingAttachments] = useState<File[]>([])
  const [uploadedAttachments, setUploadedAttachments] = useState<AdminAchievementAttachment[]>([])
  const [loadingAttachments, setLoadingAttachments] = useState(false)
  const [isBusy, setIsBusy] = useState(false)
  const [formError, setFormError] = useState('')

  const dialogRef = useRef<HTMLDialogElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const isAdmin = Boolean(profile?.roles.includes('ADMIN'))
  const hasProjectManage = Boolean(profile?.permissions.includes('PROJECT_MANAGE'))
  const hasFileUpload = Boolean(profile?.permissions.includes('FILE_UPLOAD'))

  const hasFilters = Boolean(
    appliedSearch || typeFilter !== 'ALL' || yearFilter !== '' || visibilityFilter !== 'ALL' || pageSize !== 50
  )

  const loadAchievements = useCallback(async (signal?: AbortSignal) => {
    if (!token || !isAdmin || !hasProjectManage) return

    try {
      const isPublic = visibilityFilter === 'ALL' ? undefined : visibilityFilter === 'PUBLIC'
    if (appliedSearch.length > 200) {
        setError('Từ khóa tìm kiếm không được vượt quá 200 ký tự.')
        setAchievements([])
        setTotalElements(0)
        setTotalPages(0)
        return
      }
      const response = await listAdminAchievements(
        token,
        {
          page,
          size: pageSize,
          year: yearFilter === '' ? undefined : yearFilter,
          type: typeFilter === 'ALL' ? undefined : typeFilter,
          isPublic,
          q: appliedSearch || undefined,
        },
        signal
      )
      setAchievements(response.items)
      setTotalElements(response.totalElements)
      setTotalPages(response.totalPages)
      setError('')
    } catch (reason: unknown) {
      if (signal?.aborted) return
      setError('Không tải được danh sách thành tựu. Vui lòng thử lại.')
      console.error('Không tải được danh sách thành tựu:', reason)
    }
  }, [token, isAdmin, hasProjectManage, page, pageSize, yearFilter, typeFilter, visibilityFilter, appliedSearch])

  const loadProjects = useCallback(async () => {
    if (!token || !isAdmin || !hasProjectManage) return
    setProjectsError('')
    try {
      setProjects(await listProjects(token))
    } catch {
      setProjectsError('Không tải được danh sách dự án liên quan. Bạn vẫn có thể lưu thành tựu không gắn dự án.')
    }
  }, [token, isAdmin, hasProjectManage])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    loadAchievements(controller.signal).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [loadAchievements])

  useEffect(() => {
    void loadProjects()
  }, [loadProjects])

  useEffect(() => {
    setPage(0)
  }, [appliedSearch, typeFilter, yearFilter, visibilityFilter, pageSize])

  useEffect(() => {
    const dialog = dialogRef.current
    if (isEditorOpen && dialog && !dialog.open) dialog.showModal()
  }, [isEditorOpen])

  if (!isAdmin || !hasProjectManage) {
    return <EmptyState title="Không có quyền truy cập" description="Bạn cần quyền quản trị thành tựu để xem trang này." />
  }

  function handleSearchSubmit(e: FormEvent) {
    e.preventDefault()
    setAppliedSearch(searchDraft.trim())
  }

  function resetFilters() {
    setSearchDraft('')
    setAppliedSearch('')
    setTypeFilter('ALL')
    setYearFilter('')
    setVisibilityFilter('ALL')
    setPageSize(50)
    setPage(0)
  }

  function handleCreateClick() {
    setForm(emptyForm)
    setEditId(null)
    setFormError('')
    setPendingAttachments([])
    setUploadedAttachments([])
    setIsEditorOpen(true)
  }

  function handleEditClick(item: AdminLabAchievement) {
    setForm({
      title: item.title,
      summary: item.summary ?? '',
      achievementType: item.achievementType,
      achievementYear: item.achievementYear,
      achievementDate: item.achievementDate ?? '',
      evidenceUrl: item.evidenceUrl ?? '',
      relatedProjectId: item.relatedProjectId ?? '',
      recognizingOrganization: item.recognizingOrganization ?? '',
      isPublic: item.isPublic,
    })
    setEditId(item.id)
    setFormError('')
    setPendingAttachments([])
    setUploadedAttachments([])
    setIsEditorOpen(true)

    if (token) {
      setLoadingAttachments(true)
      listAchievementFiles(token, item.id)
        .then(setUploadedAttachments)
        .catch(() => toast.error('Lỗi tải file', 'Không tải được danh sách file đính kèm'))
        .finally(() => setLoadingAttachments(false))
    }
  }

  function closeEditor() {
    setIsEditorOpen(false)
    if (dialogRef.current?.open) dialogRef.current.close()
    window.requestAnimationFrame(() => triggerRef.current?.focus())
  }

  function handleCloseEditor() {
    if (isBusy) return
    closeEditor()
  }

  async function handleDelete(item: AdminLabAchievement) {
    if (isBusy || !token) return
    if (!await confirmDialog({
      title: 'Gỡ thành tựu?',
      description: `“${item.title}” sẽ không còn xuất hiện trong danh sách quản trị hoặc trên website. Dữ liệu lịch sử vẫn được giữ lại.`,
      confirmLabel: 'Gỡ thành tựu',
      destructive: true,
    })) return

    setIsBusy(true)
    setError('')
    try {
      await deleteAdminAchievement(token, item.id)
      toast.success('Đã gỡ thành tựu', item.title)
      const newPage = (achievements.length === 1 && page > 0) ? page - 1 : page
      if (newPage !== page) setPage(newPage)
      else await loadAchievements()
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Không thể gỡ thành tựu.')
    } finally {
      setIsBusy(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isBusy || !token) return

    if (!form.title.trim()) {
      setFormError('Vui lòng nhập tên thành tựu.')
      return
    }
    if (form.achievementYear === '') {
      setFormError('Vui lòng nhập năm thành tựu.')
      return
    }
    if (!Number.isInteger(form.achievementYear) || form.achievementYear < ACHIEVEMENT_MIN_YEAR || form.achievementYear > currentYear) {
      setFormError('Năm thành tựu phải từ 2023 đến năm hiện tại.')
      return
    }
    if (form.achievementDate) {
      const yearStr = form.achievementDate.split('-')[0]
      if (yearStr && parseInt(yearStr, 10) !== form.achievementYear) {
        setFormError('Năm trong ngày không khớp với năm thành tựu.')
        return
      }
    }
    const evidenceUrl = form.evidenceUrl.trim()
    if (evidenceUrl) {
      try {
        const parsedUrl = new URL(evidenceUrl)
        if ((parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') || !parsedUrl.hostname) {
          setFormError('Đường dẫn minh chứng phải là URL HTTP(S) hợp lệ.')
          return
        }
      } catch {
        setFormError('Đường dẫn minh chứng phải là URL HTTP(S) hợp lệ.')
        return
      }
    }

    setIsBusy(true)
    setFormError('')

    try {
      if (editId) {
        const payload: UpdateAdminAchievementPayload = {
          title: form.title.trim(),
          summary: form.summary.trim() || null,
          achievementType: form.achievementType,
          achievementYear: form.achievementYear as number,
          achievementDate: form.achievementDate || null,
          evidenceUrl: form.evidenceUrl.trim() || null,
          relatedProjectId: form.relatedProjectId === '' ? null : form.relatedProjectId,
          recognizingOrganization: form.recognizingOrganization.trim() || null,
          isPublic: form.isPublic,
        }
        await updateAdminAchievement(token, editId, payload)
        toast.success('Đã cập nhật thành tựu', 'Thông tin đã được lưu lại.')
      } else {
        const payload: CreateAdminAchievementPayload = {
          title: form.title.trim(),
          summary: form.summary.trim() || null,
          achievementType: form.achievementType,
          achievementYear: form.achievementYear as number,
          achievementDate: form.achievementDate || null,
          evidenceUrl: form.evidenceUrl.trim() || null,
          relatedProjectId: form.relatedProjectId === '' ? null : form.relatedProjectId,
          recognizingOrganization: form.recognizingOrganization.trim() || null,
          isPublic: form.isPublic,
        }
        const created = await createAdminAchievement(token, payload)
        if (pendingAttachments.length > 0) {
          const pendingFiles = [...pendingAttachments]
          const results = await Promise.allSettled(
            pendingFiles.map(file => uploadAchievementFile(token, created.id, file))
          )
          const successfulAttachments = results.flatMap((result) => result.status === 'fulfilled' ? [result.value] : [])
          const failedFiles = pendingFiles.filter((_, index) => results[index]?.status === 'rejected')
          if (failedFiles.length > 0) {
            setEditId(created.id)
            setUploadedAttachments(successfulAttachments)
            setPendingAttachments(failedFiles)
            setFormError(`Đã tạo thành tựu, nhưng các tệp sau chưa tải lên được: ${failedFiles.map((file) => file.name).join(', ')}. Tệp thành công đã được giữ lại; hãy thử lại từng tệp lỗi.`)
            toast.error('Lưu một phần', 'Thành tựu đã được tạo; vẫn còn tệp minh chứng cần thử lại.')
            await loadAchievements()
            return
          }
          toast.success('Thành công', 'Đã tạo thành tựu và tải đủ tệp minh chứng')
        } else {
          toast.success('Thành công', 'Đã tạo thành tựu')
        }
      }
      closeEditor()
      await loadAchievements()
    } catch (reason: unknown) {
      setFormError(reason instanceof Error ? reason.message : 'Lưu thất bại.')
    } finally {
      setIsBusy(false)
    }
  }

  async function handleUploadAttachment(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file || !token) return
    const validationError = validateAchievementAttachment(file)
    if (validationError) {
      setFormError(validationError)
      e.target.value = ''
      return
    }

    if (editId) {
      setIsBusy(true)
      try {
        const attachment = await uploadAchievementFile(token, editId, file)
        setUploadedAttachments(prev => [...prev, attachment])
        toast.success('Đã tải lên', 'Tệp minh chứng đã được thêm')
      } catch {
        toast.error('Lỗi tải lên', 'Không thể tải lên tệp minh chứng')
      } finally {
        setIsBusy(false)
        if (fileInputRef.current) fileInputRef.current.value = ''
      }
    } else {
      setPendingAttachments(prev => [...prev, file])
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  async function handleRetryPendingAttachment(index: number) {
    if (!token || !editId || isBusy) return
    const file = pendingAttachments[index]
    if (!file) return
    const validationError = validateAchievementAttachment(file)
    if (validationError) {
      setFormError(`${file.name}: ${validationError}`)
      return
    }
    setIsBusy(true)
    try {
      const attachment = await uploadAchievementFile(token, editId, file)
      setUploadedAttachments((previous) => [...previous, attachment])
      setPendingAttachments((previous) => previous.filter((_, pendingIndex) => pendingIndex !== index))
      setFormError('')
      toast.success('Đã thử lại tệp', file.name)
    } catch (reason: unknown) {
      setFormError(`${file.name}: ${reason instanceof Error ? reason.message : 'Không thể tải lên tệp.'}`)
    } finally {
      setIsBusy(false)
    }
  }

  async function handleDeleteAttachment(attachmentId: number) {
    if (!token || !editId) return
    if (!await confirmDialog({ title: 'Gỡ tệp minh chứng?', description: 'Tệp sẽ được gỡ khỏi thành tựu này. Tệp lưu trữ gốc không bị xóa.', confirmLabel: 'Gỡ khỏi thành tựu', destructive: true })) return

    setIsBusy(true)
    try {
      await deleteAchievementFile(token, editId, attachmentId)
      setUploadedAttachments(prev => prev.filter(a => a.id !== attachmentId))
      toast.success('Thành công', 'Đã gỡ tệp minh chứng')
    } catch {
      toast.error('Lỗi', 'Không thể gỡ tệp minh chứng.')
    } finally {
      setIsBusy(false)
    }
  }

  async function handleDownloadAttachment(attachment: AdminAchievementAttachment) {
    if (!token || isBusy) return
    setIsBusy(true)
    try {
      const blob = await downloadFile(token, attachment.fileId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = attachment.originalName
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error('Lỗi tải tệp', 'Không thể tải xuống tệp minh chứng')
    } finally {
      setIsBusy(false)
    }
  }
  function handleRemovePendingAttachment(index: number) {
    setPendingAttachments(prev => prev.filter((_, i) => i !== index))
  }

  return (
    <div className="admin-achievement-page">
      <div className="page-title">
        <div>
          <span className="eyebrow">Không gian quản trị</span>
          <h1>Quản lý thành tựu</h1>
          <p>Quản lý các kết quả, sản phẩm, giải thưởng và cột mốc đang được công bố bởi Smart Lab.</p>
        </div>
        <div className="project-page-actions">
          <button
            ref={triggerRef}
            className="btn primary"
            type="button"
            onClick={handleCreateClick}
            disabled={isBusy}
          >
            <Plus /> Tạo thành tựu
          </button>
          <button className="btn" type="button" onClick={() => void loadAchievements()} disabled={loading || isBusy}>
            <RefreshCw /> {loading ? 'Đang tải...' : 'Tải lại'}
          </button>
        </div>
      </div>

      <section className="panel page-section achievement-list-panel">
        <div className="panel-head">
          <div>
            <h2>Danh sách thành tựu</h2>
            <p>{totalElements.toLocaleString('vi-VN')} thành tựu trong hệ thống. Dùng tìm kiếm và bộ lọc để thu hẹp dữ liệu.</p>
          </div>
          <Trophy size={20} aria-hidden="true" />
        </div>

        <Feedback error={error} />
        {projectsError && (
          <div className="admin-inline-alert" role="alert">
            <span>{projectsError}</span>
            <button className="btn ghost small" type="button" onClick={() => void loadProjects()}>Thử tải lại dự án</button>
          </div>
        )}

        <div className="achievement-list-toolbar">
          <form className="achievement-list-search" onSubmit={handleSearchSubmit}>
            <Search aria-hidden="true" />
            <input
              className="input"
              type="search"
              value={searchDraft}
              onChange={(e) => setSearchDraft(e.target.value)}
              placeholder="Tìm kiếm thành tựu..."
              aria-label="Tìm kiếm thành tựu"
            />
          </form>
          <PopupSelect
            className="achievement-list-filter achievement-filter-type"
            value={typeFilter}
            onChange={(val) => setTypeFilter(val as AchievementTypeFilter)}
            ariaLabel="Lọc theo loại"
            options={[
              { value: 'ALL', label: 'Tất cả loại' },
              ...ACHIEVEMENT_TYPES.map(t => ({ value: t, label: ACHIEVEMENT_TYPE_LABELS[t] }))
            ]}
          />
          <PopupSelect
            className="achievement-list-filter achievement-filter-year"
            value={yearFilter === '' ? '' : yearFilter.toString()}
            onChange={(val) => setYearFilter(val ? Number(val) : '')}
            ariaLabel="Lọc theo năm"
            options={[
              { value: '', label: 'Tất cả năm' },
              ...Array.from({ length: currentYear - ACHIEVEMENT_MIN_YEAR + 1 }, (_, i) => {
                const y = currentYear - i
                return { value: y.toString(), label: y.toString() }
              })
            ]}
          />
          <PopupSelect
            className="achievement-list-filter achievement-filter-visibility"
            value={visibilityFilter}
            onChange={(val) => setVisibilityFilter(val as VisibilityFilter)}
            ariaLabel="Lọc theo trạng thái"
            options={[
              { value: 'ALL', label: 'Tất cả trạng thái' },
              { value: 'PUBLIC', label: 'Công khai' },
              { value: 'PRIVATE', label: 'Riêng tư' },
            ]}
          />
          <PopupSelect
            className="achievement-list-filter achievement-filter-page-size"
            value={pageSize.toString()}
            onChange={(val) => setPageSize(Number(val))}
            ariaLabel="Số dòng trên trang"
            options={[
              { value: '20', label: '20 / trang' },
              { value: '50', label: '50 / trang' },
              { value: '100', label: '100 / trang' },
            ]}
          />
          {hasFilters && (
            <button className="btn ghost table-btn" type="button" onClick={resetFilters}>
              <RotateCcw aria-hidden="true" /> Đặt lại
            </button>
          )}
        </div>

        {loading ? (
          <div className="empty">Đang tải thành tựu...</div>
        ) : achievements.length ? (
          <>
            <div className="admin-table-wrap">
              <table className="admin-table achievement-admin-table">
                <thead>
                  <tr>
                    <th className="col-title">Thành tựu</th>
                    <th className="col-type">Loại</th>
                    <th className="col-year">Năm</th>
                    <th className="col-project">Dự án</th>
                    <th className="col-visibility">Hiển thị</th>
                    <th className="col-updated">Cập nhật</th>
                    <th className="col-actions"><span className="sr-only">Thao tác</span></th>
                  </tr>
                </thead>
                <tbody>
                  {achievements.map((item) => (
                    <tr key={item.id}>
                      <td className="col-title" title={item.title}>
                        {item.title}
                      </td>
                      <td className="col-type">
                        {ACHIEVEMENT_TYPE_LABELS[item.achievementType]}
                      </td>
                      <td className="col-year">
                        <strong className="achievement-year">{item.achievementYear}</strong>
                        {item.achievementDate && <span className="achievement-date">{item.achievementDate}</span>}
                      </td>
                      <td className="col-project">
                        {item.relatedProjectId ? projects.find(p => p.id === item.relatedProjectId)?.code || `#${item.relatedProjectId}` : '—'}
                      </td>
                      <td className="col-visibility">
                        <span className={`badge ${item.isPublic ? 'success' : 'info'}`}>
                          {item.isPublic ? 'Công khai' : 'Riêng tư'}
                        </span>
                      </td>
                      <td className="col-updated">
                        {new Date(item.updatedAt).toLocaleDateString('vi-VN')}
                      </td>
                      <td className="col-actions">
                        <ActionMenu
                          item={item}
                          onEdit={() => handleEditClick(item)}
                          onDelete={() => void handleDelete(item)}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="achievement-mobile-list">
              {achievements.map((item) => (
                <div className="achievement-mobile-card" key={item.id}>
                  <div className="achievement-mobile-card-header">
                    <span className="achievement-mobile-card-title">{item.title}</span>
                    <ActionMenu
                      item={item}
                      onEdit={() => handleEditClick(item)}
                      onDelete={() => void handleDelete(item)}
                    />
                  </div>
                  <div className="achievement-mobile-card-meta">
                    <span className={`badge ${item.isPublic ? 'success' : 'info'}`}>
                      {item.isPublic ? 'Công khai' : 'Riêng tư'}
                    </span>
                    <span>{ACHIEVEMENT_TYPE_LABELS[item.achievementType]} · {item.achievementYear}</span>
                  </div>
                  <div className="achievement-mobile-card-details">
                    <span>{item.achievementDate ? `Ngày: ${item.achievementDate}` : 'Chưa có ngày cụ thể'}</span>
                    <span>{item.relatedProjectId ? `Dự án: ${projects.find((project) => project.id === item.relatedProjectId)?.code ?? `#${item.relatedProjectId}`}` : 'Không gắn dự án'}</span>
                    {item.evidenceUrl && <span>Minh chứng ngoài: Có</span>}
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : (
          <div className="achievement-empty-state">
            <EmptyState
              title="Không tìm thấy thành tựu"
              description="Chưa có thành tựu nào phù hợp với bộ lọc hiện tại."
            />
          </div>
        )}

        {totalPages > 0 && (
          <nav className="achievement-pagination" aria-label="Phân trang danh sách thành tựu">
            <span>
              {page * pageSize + 1}–{Math.min((page + 1) * pageSize, totalElements)} / {totalElements} thành tựu
            </span>
            <Pagination page={page + 1} totalPages={totalPages} onChange={(nextPage) => setPage(nextPage - 1)} />
          </nav>
        )}
      </section>

      {isEditorOpen && (
        <dialog
          ref={dialogRef}
          className="achievement-editor-dialog"
          aria-labelledby="achievement-editor-title"
          onCancel={(e) => { e.preventDefault(); handleCloseEditor() }}
          onClick={(e) => { if (e.target === e.currentTarget) handleCloseEditor() }}
        >
          <OverlayPortalHost />
          <form className="achievement-editor-form" onSubmit={(e) => void handleSubmit(e)} noValidate>
            <header className="achievement-editor-head">
              <span className="achievement-editor-icon" aria-hidden="true">
                <Trophy />
              </span>
              <div>
                <h2 id="achievement-editor-title">{editId ? 'Sửa thành tựu' : 'Tạo thành tựu mới'}</h2>
                <p>Nhập thông tin chi tiết về thành tựu của Smart Lab.</p>
              </div>
              <button
                className="achievement-close-button"
                type="button"
                onClick={handleCloseEditor}
                disabled={isBusy}
                aria-label="Đóng trình chỉnh sửa thành tựu"
                title="Đóng"
              >
                <X aria-hidden="true" />
              </button>
            </header>
            <div className="achievement-editor-body">
              {formError && <div className="field-full"><Feedback error={formError} /></div>}

              <section className="achievement-editor-section field-full">
                <h3>Thông tin cơ bản</h3>
                <div className="achievement-editor-grid">
                  <label className="field field-full">
                    <span>Tên thành tựu *</span>
                    <input
                      className="input"
                      autoFocus
                      required
                      maxLength={500}
                      value={form.title}
                      onChange={(e) => setForm({ ...form, title: e.target.value })}
                    />
                  </label>
                  <label className="field field-1">
                    <span>Loại *</span>
                    <PopupSelect
                      value={form.achievementType}
                      onChange={(val) => setForm({ ...form, achievementType: val as AchievementType })}
                      ariaLabel="Loại thành tựu"
                      options={ACHIEVEMENT_TYPES.map(t => ({ value: t, label: ACHIEVEMENT_TYPE_LABELS[t] }))}
                    />
                  </label>
                  <label className="field field-1">
                    <span>Năm *</span>
                    <input
                      className="input"
                      type="number"
                      required
                      min={ACHIEVEMENT_MIN_YEAR}
                      max={currentYear}
                      value={form.achievementYear}
                      onChange={(e) => setForm({ ...form, achievementYear: e.target.value ? Number(e.target.value) : '' })}
                    />
                  </label>
                  <label className="field field-1">
                    <span>Ngày</span>
                    <input
                      className="input"
                      type="date"
                      value={form.achievementDate}
                      onChange={(e) => setForm({ ...form, achievementDate: e.target.value })}
                    />
                  </label>
                </div>
              </section>

              <section className="achievement-editor-section field-full">
                <h3>Ngữ cảnh</h3>
                <div className="achievement-editor-grid">
                  <label className="field field-1">
                    <span>Dự án liên quan</span>
                    <PopupSelect
                      value={form.relatedProjectId === '' ? '' : form.relatedProjectId.toString()}
                      onChange={(val) => setForm({ ...form, relatedProjectId: val === '' ? '' : Number(val) })}
                      ariaLabel="Dự án liên quan"
                      options={[
                        { value: '', label: 'Không có' },
                        ...projects.map(p => ({ value: p.id.toString(), label: `${p.code} - ${p.name}` }))
                      ]}
                    />
                  </label>
                  <label className="field field-1">
                    <span>Đơn vị / tổ chức công nhận</span>
                    <input
                      className="input"
                      type="text"
                      maxLength={500}
                      placeholder="Ví dụ: FPT University"
                      value={form.recognizingOrganization}
                      onChange={(e) => setForm({ ...form, recognizingOrganization: e.target.value })}
                    />
                  </label>
                </div>
              </section>

              <section className="achievement-editor-section field-full">
                <h3>Nội dung</h3>
                <label className="field field-full">
                  <span>Mô tả</span>
                  <textarea
                    className="input"
                    rows={4}
                    maxLength={20000}
                    value={form.summary}
                    onChange={(e) => setForm({ ...form, summary: e.target.value })}
                  />
                </label>
              </section>

              <section className="achievement-editor-section field-full">
                <h3>Minh chứng</h3>
                <div className="achievement-editor-grid">
                  <label className="field field-full">
                    <span>Đường dẫn minh chứng bên ngoài</span>
                    <input
                      className="input"
                      type="url"
                      placeholder="https://"
                      value={form.evidenceUrl}
                      onChange={(e) => setForm({ ...form, evidenceUrl: e.target.value })}
                    />
                  </label>
                </div>

                <div className="achievement-attachments field-full">
                  <h4>Tệp minh chứng</h4>
                  {loadingAttachments ? (
                    <div className="empty">Đang tải tệp minh chứng...</div>
                  ) : (
                    <div className="achievement-attachment-list">
                      {uploadedAttachments.map((file) => {
                        const displayPrimary = file.label?.trim() || file.originalName
                        const displaySecondary = (file.label?.trim() && file.label.trim() !== file.originalName) ? file.originalName : null
                        const isImage = file.mimeType.startsWith('image/')
                        return (
                          <div key={file.id} className="attachment-item">
                            {isImage ? <Image size={16} className="attachment-icon" aria-hidden="true" /> : <FileText size={16} className="attachment-icon" aria-hidden="true" />}
                            <div className="attachment-info">
                              <span className="attachment-name" title={displayPrimary}>{displayPrimary}</span>
                              <span className="attachment-meta">
                                {displaySecondary && <span className="attachment-secondary" title={displaySecondary}>{displaySecondary} &bull; </span>}
                                {formatBytes(file.sizeBytes)}
                              </span>
                            </div>
                            <div className="attachment-actions">
                              <AttachmentMenu
                                onDownload={() => void handleDownloadAttachment(file)}
                                onDetach={() => void handleDeleteAttachment(file.id)}
                              />
                            </div>
                          </div>
                        )
                      })}
                      {pendingAttachments.map((file, index) => {
                        const isImage = file.type.startsWith('image/')
                        return (
                          <div key={index} className="attachment-item pending">
                            {isImage ? <Image size={16} className="attachment-icon" aria-hidden="true" /> : <FileText size={16} className="attachment-icon" aria-hidden="true" />}
                            <div className="attachment-info">
                              <span className="attachment-name" title={file.name}>{file.name}</span>
                              <span className="attachment-meta">{formatBytes(file.size)} (Chờ lưu)</span>
                            </div>
                            {editId && <button type="button" className="btn ghost small" onClick={() => void handleRetryPendingAttachment(index)} disabled={isBusy}>Thử lại</button>}
                            <button type="button" className="btn ghost icon-only destructive" title="Xóa" aria-label={`Xóa tệp ${file.name}`} onClick={() => handleRemovePendingAttachment(index)} disabled={isBusy}>
                              <Trash2 size={14} aria-hidden="true" />
                            </button>
                          </div>
                        )
                      })}
                      {uploadedAttachments.length === 0 && pendingAttachments.length === 0 && (
                        <div className="empty">Chưa có tệp minh chứng.</div>
                      )}
                    </div>
                  )}

                  {hasFileUpload && (
                    <label className={`btn ghost upload-btn ${isBusy ? 'disabled' : ''}`}>
                      <Upload size={16} aria-hidden="true" /> Tải tệp mới
                      <input type="file" className="sr-only" ref={fileInputRef} accept={D2_UPLOAD_ACCEPT} onChange={(e) => void handleUploadAttachment(e)} disabled={isBusy} />
                    </label>
                  )}
                </div>
              </section>

              <section className="achievement-editor-section field-full">
                <h3>Hiển thị</h3>
                <div className="achievement-visibility-settings">
                  <label className="achievement-visibility-row">
                    <input
                      type="checkbox"
                      checked={form.isPublic}
                      onChange={(e) => setForm({ ...form, isPublic: e.target.checked })}
                    />
                    <span>
                      <strong>Công khai</strong>
                      <small>Hiển thị thành tựu này trên website Smart Lab.</small>
                    </span>
                  </label>
                </div>
              </section>
            </div>
            <footer className="achievement-editor-actions">
              <button className="btn ghost" type="button" disabled={isBusy} onClick={handleCloseEditor}>
                Hủy
              </button>
              <button className="btn primary" type="submit" disabled={isBusy}>
                {isBusy ? 'Đang lưu...' : 'Lưu thành tựu'}
              </button>
            </footer>
          </form>
        </dialog>
      )}
    </div>
  )
}

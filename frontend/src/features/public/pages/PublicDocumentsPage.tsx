import {
  Archive,
  Download,
  File,
  FileImage,
  FileSpreadsheet,
  FileText,
  Presentation,
  RotateCcw,
  Search,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiClientError } from '../../../lib/apiClient'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { listPublicProjects } from '../../projects/api'
import type { PublicProjectSummary } from '../../projects/types'
import { PublicPageHead } from '../components/PublicPageHead'
import { listPublicDocumentYears, listPublicDocuments, publicDocumentFileUrl } from '../documentApi'
import {
  PUBLIC_DOCUMENT_FILE_TYPES,
  PUBLIC_DOCUMENT_SORTS,
  type PublicDocumentFileType,
  type PublicDocumentSort,
  type PublicDocumentSummary,
} from '../documentTypes'
import './PublicDocumentsPage.css'

const PAGE_SIZE = 12
const MIN_YEAR = 2000

const FILE_TYPE_LABELS: Record<PublicDocumentFileType, string> = {
  ALL: 'Tất cả loại tệp',
  PDF: 'PDF',
  DOCUMENT: 'Văn bản',
  SPREADSHEET: 'Bảng tính',
  PRESENTATION: 'Trình chiếu',
  IMAGE: 'Hình ảnh',
  ARCHIVE: 'Tệp nén',
  OTHER: 'Khác',
}

const SORT_LABELS: Record<PublicDocumentSort, string> = {
  LATEST: 'Mới cập nhật',
  OLDEST: 'Cũ nhất',
  TITLE_ASC: 'Tên A–Z',
  TITLE_DESC: 'Tên Z–A',
}

export function PublicDocumentsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const projectId = parseProjectId(searchParams.get('project'))
  const fileType = parseFileType(searchParams.get('type'))
  const year = parseYear(searchParams.get('year'))
  const sort = parseSort(searchParams.get('sort'))
  const page = parsePage(searchParams.get('page'))

  const [projects, setProjects] = useState<PublicProjectSummary[]>([])
  const [years, setYears] = useState<number[]>([])
  const [items, setItems] = useState<PublicDocumentSummary[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [optionsError, setOptionsError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setOptionsError(false)
    void Promise.all([
      listPublicProjects(0, 48, {}, controller.signal),
      listPublicDocumentYears(controller.signal),
    ])
      .then(([projectPage, availableYears]) => {
        if (!controller.signal.aborted) {
          setProjects(projectPage.items)
          setYears(availableYears)
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setOptionsError(true)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    void listPublicDocuments(
      page - 1,
      PAGE_SIZE,
      {
        query: query.trim() || undefined,
        projectId: projectId ?? undefined,
        fileType,
        year: year ?? undefined,
        sort,
      },
      controller.signal,
    )
      .then((response) => {
        if (controller.signal.aborted) return
        setItems(response.items)
        setTotalElements(response.totalElements)
        setTotalPages(response.totalPages)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setItems([])
          setTotalElements(0)
          setTotalPages(0)
          setError(publicDocumentError(reason))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [fileType, page, projectId, query, reloadKey, sort, year])

  function updateFilter(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value === 'ALL' || value === '') next.delete(key)
    else next.set(key, value)
    next.delete('page')
    setSearchParams(next)
  }

  function resetFilters() {
    setSearchParams(new URLSearchParams())
  }

  function updatePage(newPage: number) {
    const next = new URLSearchParams(searchParams)
    if (newPage <= 1) next.delete('page')
    else next.set('page', String(newPage))
    setSearchParams(next)
  }

  const hasFilters = Boolean(query.trim()) || projectId !== null || fileType !== 'ALL' || year !== null || sort !== 'LATEST'

  const projectOptions = useMemo(
    () => [
      { value: 'ALL', label: optionsError ? 'Không tải được dự án' : 'Tất cả dự án' },
      ...projects.map((p) => ({ value: String(p.id), label: p.name })),
    ],
    [optionsError, projects],
  )

  const fileTypeOptions = useMemo(
    () =>
      PUBLIC_DOCUMENT_FILE_TYPES.map((type) => ({
        value: type,
        label: FILE_TYPE_LABELS[type],
      })),
    [],
  )

  const yearOptions = useMemo(
    () => [
      { value: 'ALL', label: optionsError ? 'Không tải được năm' : 'Tất cả năm' },
      ...years.map((y) => ({ value: String(y), label: String(y) })),
    ],
    [optionsError, years],
  )

  const sortOptions = useMemo(
    () =>
      PUBLIC_DOCUMENT_SORTS.map((s) => ({
        value: s,
        label: SORT_LABELS[s],
      })),
    [],
  )

  return (
    <>
      <PublicPageHead
        title="Tài liệu"
        description="Các tài liệu được Smart Lab công khai từ những dự án công khai."
      />
      <section className="section public-documents-section">
        <div className="wrap">
          <div className="public-documents-intro">
            <div>
              <div className="kicker">TÀI LIỆU SMART LAB</div>
              <h2>Tài liệu</h2>
              <p>Các tài liệu công khai gắn với dự án đang được giới thiệu tới cộng đồng.</p>
            </div>
            <span className="public-archive-page-size">12 tài liệu / trang</span>
          </div>

          <div className="public-documents-toolbar" role="search">
            <label className="public-documents-search">
              <span className="sr-only">Tìm kiếm tài liệu</span>
              <Search aria-hidden="true" />
              <input
                className="input"
                type="search"
                value={query}
                onChange={(event) => updateFilter('q', event.target.value)}
                placeholder="Tìm theo tên tài liệu hoặc nội dung..."
              />
            </label>

            <PopupSelect
              value={projectId ? String(projectId) : 'ALL'}
              options={projectOptions}
              onChange={(value) => updateFilter('project', value)}
              ariaLabel="Lọc theo dự án"
              className="public-documents-filter"
              disabled={optionsError}
            />

            <PopupSelect
              value={fileType}
              options={fileTypeOptions}
              onChange={(value) => updateFilter('type', value)}
              ariaLabel="Lọc theo loại tệp"
              className="public-documents-filter"
            />

            <PopupSelect
              value={year ? String(year) : 'ALL'}
              options={yearOptions}
              onChange={(value) => updateFilter('year', value)}
              ariaLabel="Lọc theo năm"
              className="public-documents-filter"
              disabled={optionsError}
            />

            <PopupSelect
              value={sort}
              options={sortOptions}
              onChange={(value) => updateFilter('sort', value)}
              ariaLabel="Sắp xếp tài liệu"
              className="public-documents-filter"
            />

            {hasFilters ? (
              <button className="public-documents-reset" type="button" onClick={resetFilters}>
                <RotateCcw size={15} aria-hidden="true" />
                Đặt lại
              </button>
            ) : null}
          </div>

          {error ? (
            <div className="public-documents-request-state" role="alert">
              <Feedback error={error} />
              <button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}>
                Thử tải lại
              </button>
            </div>
          ) : null}

          {loading && items.length === 0 ? (
            <div className="public-empty empty tight public-documents-request-state" aria-busy="true">
              Đang tải tài liệu...
            </div>
          ) : null}

          {!loading && !error && items.length === 0 ? (
            <EmptyState
              title={hasFilters ? 'Không tìm thấy tài liệu phù hợp' : 'Chưa có tài liệu công khai'}
              description={
                hasFilters
                  ? 'Thử thay đổi từ khóa hoặc bộ lọc.'
                  : 'Các tài liệu công khai của Smart Lab sẽ xuất hiện tại đây.'
              }
            />
          ) : null}

          {items.length > 0 ? (
            <>
              <div className="public-documents-results-bar">
                <p className="public-documents-summary" aria-live="polite">
                  {formatRange(page, PAGE_SIZE, totalElements)} · {totalElements} tài liệu
                </p>
                {loading ? (
                  <span className="public-documents-refreshing" aria-live="polite">
                    Đang cập nhật...
                  </span>
                ) : null}
              </div>
              <div className="public-documents-grid" aria-busy={loading}>
                {items.map((document) => (
                  <PublicDocumentCard document={document} key={document.id} />
                ))}
              </div>
              <Pagination page={page} totalPages={totalPages} onChange={updatePage} />
            </>
          ) : null}
        </div>
      </section>
    </>
  )
}

function PublicDocumentCard({ document }: { document: PublicDocumentSummary }) {
  const Icon = fileIcon(document.mimeType, document.originalFileName)
  return (
    <article className="public-document-card">
      <div className="public-document-card-topline">
        <span className="public-document-icon">
          <Icon aria-hidden="true" />
        </span>
        <span className="public-document-project">{document.projectName}</span>
        <time dateTime={document.updatedAt}>{formatDate(document.updatedAt)}</time>
      </div>
      <h3>{document.title}</h3>
      {document.description ? <p className="public-document-description">{document.description}</p> : null}
      <p className="public-document-file">
        <FileText size={15} aria-hidden="true" /> <span title={document.originalFileName}>{document.originalFileName}</span>
      </p>
      <div className="public-document-meta">
        <span>{formatBytes(document.sizeBytes)}</span>
        <span>Phiên bản {document.currentVersionNo}</span>
      </div>
      <a className="public-document-download" href={publicDocumentFileUrl(document.currentFileId)} download={document.originalFileName}>
        <Download size={16} aria-hidden="true" /> Tải tài liệu
      </a>
    </article>
  )
}

function parseProjectId(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

function parseYear(value: string | null) {
  if (!value || !/^\d{4}$/.test(value)) return null
  const parsed = Number(value)
  return parsed >= MIN_YEAR && parsed <= new Date().getFullYear() ? parsed : null
}

function parseFileType(value: string | null): PublicDocumentFileType {
  return value && PUBLIC_DOCUMENT_FILE_TYPES.includes(value as PublicDocumentFileType)
    ? (value as PublicDocumentFileType)
    : 'ALL'
}

function parseSort(value: string | null): PublicDocumentSort {
  return value && PUBLIC_DOCUMENT_SORTS.includes(value as PublicDocumentSort)
    ? (value as PublicDocumentSort)
    : 'LATEST'
}

function parsePage(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 1
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : 1
}

function formatRange(page: number, size: number, total: number) {
  if (total === 0) return '0 / 0'
  return `${(page - 1) * size + 1}–${Math.min(page * size, total)} / ${total}`
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(date)
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value < 0) return 'Không rõ kích thước'
  if (value < 1024) return `${value} B`
  const units = ['KB', 'MB', 'GB']
  let size = value / 1024
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} ${units[index]}`
}

function fileIcon(mimeType: string, name: string) {
  const mime = mimeType.toLowerCase()
  const lowerName = name.toLowerCase()
  if (mime === 'application/pdf' || lowerName.endsWith('.pdf')) return FileText
  if (mime.startsWith('image/')) return FileImage
  if (mime.includes('spreadsheet') || mime.includes('excel') || mime === 'text/csv') return FileSpreadsheet
  if (mime.includes('presentation') || mime.includes('powerpoint')) return Presentation
  if (mime.includes('zip') || mime.includes('rar') || mime.includes('7z') || /\.(zip|rar|7z)$/.test(lowerName))
    return Archive
  if (mime.startsWith('text/') || mime.includes('word') || mime.includes('document')) return FileText
  return File
}

function publicDocumentError(reason: unknown) {
  return reason instanceof ApiClientError && reason.status >= 500
    ? 'Không thể tải tài liệu lúc này. Vui lòng thử lại.'
    : 'Không thể tải tài liệu. Vui lòng thử lại.'
}

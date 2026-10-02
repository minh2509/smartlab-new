import { ChevronRight, RotateCcw, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { PublicPageHead } from '../components/PublicPageHead'
import { listNewsArchive, listNewsSources, listNewsYears } from '../newsApi'
import type { LabNewsArticle, PublicNewsSort } from '../newsTypes'
import './NewsArchivePage.css'

const PAGE_SIZE = 12
const MIN_YEAR = 2000

const SORT_LABELS: Record<PublicNewsSort, string> = {
  LATEST: 'Mới nhất',
  OLDEST: 'Cũ nhất',
}

export function NewsArchivePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const source = searchParams.get('source') ?? ''
  const year = parseYear(searchParams.get('year'))
  const sort = parseSort(searchParams.get('sort'))
  const page = parsePage(searchParams.get('page'))

  const [sources, setSources] = useState<string[]>([])
  const [years, setYears] = useState<number[]>([])
  const [items, setItems] = useState<LabNewsArticle[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [optionsError, setOptionsError] = useState(false)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setOptionsError(false)
    void Promise.all([listNewsSources(controller.signal), listNewsYears(controller.signal)])
      .then(([sourceList, yearList]) => {
        if (!controller.signal.aborted) { setSources(uniqueSources(sourceList)); setYears(yearList) }
      })
      .catch(() => { if (!controller.signal.aborted) setOptionsError(true) })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError(null)
    void listNewsArchive({ q: query.trim() || undefined, source: source || undefined, year: year ?? undefined, sort, page: page - 1, size: PAGE_SIZE, signal: controller.signal })
      .then((response) => {
        if (!controller.signal.aborted) { setItems(response.items); setTotalPages(response.totalPages); setTotalElements(response.totalElements) }
      })
      .catch(() => { if (!controller.signal.aborted) { setItems([]); setTotalElements(0); setTotalPages(0); setError('Không thể tải tin tức.') } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [page, query, reloadKey, sort, source, year])

  function updateFilter(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value === 'ALL' || value === '') next.delete(key)
    else next.set(key, value)
    next.delete('page')
    setSearchParams(next)
  }

  function updatePage(newPage: number) {
    const next = new URLSearchParams(searchParams)
    if (newPage <= 1) next.delete('page')
    else next.set('page', String(newPage))
    setSearchParams(next)
  }

  const hasFilters = Boolean(query.trim()) || Boolean(source) || year !== null || sort !== 'LATEST'

  const sourceOptions = useMemo(() => {
    const base = sources.map((s) => ({ value: s, label: s }))
    if (source && !sources.some((s) => s.toLocaleLowerCase('vi') === source.toLocaleLowerCase('vi'))) base.unshift({ value: source, label: source })
    return [{ value: 'ALL', label: optionsError ? 'Không tải được' : 'Tất cả nguồn' }, ...base]
  }, [optionsError, source, sources])

  const yearOptions = useMemo(() => [
    { value: 'ALL', label: optionsError ? 'Không tải được' : 'Tất cả năm' },
    ...years.map((y) => ({ value: String(y), label: String(y) }))
  ], [optionsError, years])

  const sortOptions = useMemo(() => [
    { value: 'LATEST', label: SORT_LABELS.LATEST },
    { value: 'OLDEST', label: SORT_LABELS.OLDEST }
  ], [])

  return (
    <>
      <PublicPageHead title="Tin tức" description="Tin tức truyền thông về Smart Lab." />
      <section className="news-archive-section">
        <div className="news-archive-header">
          <div className="news-archive-intro">
            <div>
              <div className="kicker">TIN TỨC</div>
              <h2>Tin tức &amp; truyền thông</h2>
              <p>Thông tin truyền thông về Smart Lab từ các nguồn bên ngoài.</p>
            </div>
            <span className="public-archive-page-size">{PAGE_SIZE} tin tức / trang</span>
          </div>

          <div className="news-archive-toolbar">
            <div className="news-archive-search">
              <Search size={16} />
              <input className="input" type="search" value={query} onChange={(e) => updateFilter('q', e.target.value)} placeholder="Tìm tin tức..." />
            </div>
            <PopupSelect value={source || 'ALL'} options={sourceOptions} onChange={(v) => updateFilter('source', v)} ariaLabel="Lọc theo nguồn" className="news-archive-filter" disabled={optionsError} />
            <PopupSelect value={year ? String(year) : 'ALL'} options={yearOptions} onChange={(v) => updateFilter('year', v)} ariaLabel="Lọc theo năm" className="news-archive-filter" disabled={optionsError} />
            <PopupSelect value={sort} options={sortOptions} onChange={(v) => updateFilter('sort', v)} ariaLabel="Sắp xếp" className="news-archive-filter" />
            {hasFilters && <button className="news-archive-reset" type="button" onClick={() => setSearchParams(new URLSearchParams())}><RotateCcw size={14} /> Đặt lại</button>}
          </div>
        </div>

        {error && <div className="news-archive-error" role="alert"><Feedback error={error} /><button className="btn" type="button" onClick={() => setReloadKey((k) => k + 1)}>Thử lại</button></div>}

        {loading && items.length === 0 && <div className="news-archive-empty" aria-busy="true">Đang tải tin tức...</div>}

        {!loading && !error && items.length === 0 && <EmptyState title={hasFilters ? 'Không tìm thấy' : 'Chưa có tin tức công khai'} />}

        {items.length > 0 && (
          <>
            <div className="news-archive-results">
              <p className="news-archive-summary">
                {formatRange(page, PAGE_SIZE, totalElements)} · {totalElements} tin tức
                {loading && <span className="news-archive-refreshing">Đang cập nhật...</span>}
              </p>
              <div className="news-archive-grid">
                {items.map((item) => <NewsCard key={item.id} item={item} />)}
              </div>
            </div>
            <div className="news-archive-pagination">
              <Pagination page={page} totalPages={totalPages} onChange={updatePage} />
            </div>
          </>
        )}
      </section>
    </>
  )
}

function NewsCard({ item }: { item: LabNewsArticle }) {
  return (
    <Link to={`/tin-tuc/${item.id}`} className="news-archive-card">
      <div className="news-archive-card-meta">
        <span className="news-archive-card-source">{item.sourceName}</span>
        {item.publishedAt && <span className="news-archive-card-date">· {formatDate(item.publishedAt)}</span>}
      </div>
      <h3>{item.title}</h3>
      {item.excerpt && <p className="news-archive-card-excerpt">{shorten(item.excerpt, 120)}</p>}
      <span className="news-archive-card-action">Xem chi tiết <ChevronRight size={14} /></span>
    </Link>
  )
}

function parsePage(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 1
  const page = Number(value)
  return Number.isSafeInteger(page) && page > 0 ? page : 1
}

function parseYear(value: string | null) {
  if (!value || !/^\d{4}$/.test(value)) return null
  const year = Number(value)
  return year >= MIN_YEAR && year <= new Date().getFullYear() ? year : null
}

function parseSort(value: string | null): PublicNewsSort {
  return value === 'OLDEST' ? 'OLDEST' : 'LATEST'
}

function uniqueSources(values: string[]) {
  return values.map((v) => v.trim()).filter(Boolean).filter((v, i, a) => a.findIndex((c) => c.toLocaleLowerCase('vi') === v.toLocaleLowerCase('vi')) === i).sort((a, b) => a.localeCompare(b, 'vi'))
}

function formatRange(page: number, size: number, total: number) {
  if (total === 0) return '0 / 0'
  return `${(page - 1) * size + 1}–${Math.min(page * size, total)} / ${total}`
}

function formatDate(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(date)
}

function shorten(value: string | null | undefined, max: number) {
  if (!value) return ''
  return value.length <= max ? value : value.slice(0, max).trim() + '...'
}

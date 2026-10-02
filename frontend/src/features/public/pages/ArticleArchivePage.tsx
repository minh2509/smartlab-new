import { RotateCcw, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { PublicArticleCard } from '../components/PublicDataCards'
import { PublicPageHead } from '../components/PublicPageHead'
import { listArticles, listArticleYears } from '../articleApi'
import type { LabArticleSummary, PublicArticleSort } from '../articleTypes'
import '../landing.css'
import './ArticleArchivePage.css'

const PAGE_SIZE = 12
const MIN_YEAR = 2000

const SORT_LABELS: Record<PublicArticleSort, string> = {
  LATEST: 'Mới nhất',
  OLDEST: 'Cũ nhất',
}

export function ArticleArchivePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const year = parseYear(searchParams.get('year'))
  const sort = parseSort(searchParams.get('sort'))
  const page = parsePage(searchParams.get('page'))

  const [years, setYears] = useState<number[]>([])
  const [items, setItems] = useState<LabArticleSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [yearsError, setYearsError] = useState(false)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setYearsError(false)
    void listArticleYears(controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setYears(result)
      })
      .catch(() => {
        if (!controller.signal.aborted) setYearsError(true)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    void listArticles({
      q: query.trim() || undefined,
      year: year ?? undefined,
      sort,
      page: page - 1,
      size: PAGE_SIZE,
      signal: controller.signal,
    })
      .then((response) => {
        if (controller.signal.aborted) return
        setItems(response.items)
        setTotalPages(response.totalPages)
        setTotalElements(response.totalElements)
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setItems([])
          setTotalElements(0)
          setTotalPages(0)
          setError('Không thể tải danh sách bài viết. Vui lòng thử lại.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, query, reloadKey, sort, year])

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

  const hasFilters = Boolean(query.trim()) || year !== null || sort !== 'LATEST'

  const yearOptions = useMemo(
    () => [
      { value: 'ALL', label: yearsError ? 'Không tải được năm' : 'Tất cả năm' },
      ...years.map((y) => ({ value: String(y), label: String(y) })),
    ],
    [years, yearsError],
  )

  const sortOptions = useMemo(
    () => [
      { value: 'LATEST', label: SORT_LABELS.LATEST },
      { value: 'OLDEST', label: SORT_LABELS.OLDEST },
    ],
    [],
  )

  return (
    <>
      <PublicPageHead
        title="Bài viết"
        description="Các bài viết, chia sẻ và nội dung chuyên môn được Smart Lab công bố."
      />
      <section className="article-archive-section">
        <div className="article-archive-header">
          <div className="article-archive-intro">
            <div>
              <div className="kicker">BÀI VIẾT</div>
              <h2>Bài viết</h2>
              <p>Bài viết, chia sẻ và nội dung chuyên môn của Smart Lab.</p>
            </div>
            <span className="public-archive-page-size">{PAGE_SIZE} bài / trang</span>
          </div>

          <div className="article-archive-toolbar">
            <div className="article-archive-search">
              <Search size={16} />
              <input className="input" type="search" value={query} onChange={(e) => updateFilter('q', e.target.value)} placeholder="Tìm bài viết..." />
            </div>
            <PopupSelect value={year ? String(year) : 'ALL'} options={yearOptions} onChange={(v) => updateFilter('year', v)} ariaLabel="Lọc theo năm" className="article-archive-filter" disabled={yearsError} />
            <PopupSelect value={sort} options={sortOptions} onChange={(v) => updateFilter('sort', v)} ariaLabel="Sắp xếp" className="article-archive-filter" />
            {hasFilters && <button className="article-archive-reset" type="button" onClick={resetFilters}><RotateCcw size={14} /> Đặt lại</button>}
          </div>
        </div>

        {error && <div className="article-archive-error" role="alert"><Feedback error={error} /><button className="btn" type="button" onClick={() => setReloadKey((k) => k + 1)}>Thử lại</button></div>}

        {loading && items.length === 0 && <div className="article-archive-empty" aria-busy="true">Đang tải bài viết...</div>}

        {!loading && !error && items.length === 0 && <EmptyState title={hasFilters ? 'Không tìm thấy' : 'Chưa có bài viết công khai'} />}

        {items.length > 0 && (
          <>
            <div className="article-archive-results">
              <p className="article-archive-summary">{formatRange(page, PAGE_SIZE, totalElements)} · {totalElements} bài viết{loading && <span className="article-archive-refreshing">Đang cập nhật...</span>}</p>
              <div className="article-archive-grid">
                {items.map((item) => <PublicArticleCard key={item.id} article={item} />)}
              </div>
            </div>
            <div className="article-archive-pagination">
              <Pagination page={page} totalPages={totalPages} onChange={updatePage} />
            </div>
          </>
        )}
      </section>
    </>
  )
}

function parsePage(value: string | null) {
  if (value === null || !/^\d+$/.test(value)) return 1
  const page = Number(value)
  return Number.isSafeInteger(page) && page > 0 ? page : 1
}

function parseYear(value: string | null) {
  if (value === null || !/^\d{4}$/.test(value)) return null
  const year = Number(value)
  return year >= MIN_YEAR && year <= new Date().getFullYear() ? year : null
}

function parseSort(value: string | null): PublicArticleSort {
  return value === 'OLDEST' ? 'OLDEST' : 'LATEST'
}

function formatRange(page: number, size: number, total: number) {
  if (total === 0) return '0 / 0'
  return `${(page - 1) * size + 1}–${Math.min(page * size, total)} / ${total}`
}

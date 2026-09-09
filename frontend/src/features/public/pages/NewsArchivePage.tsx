import { ExternalLink, RotateCcw, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { PublicPageHead } from '../components/PublicPageHead'
import { listNewsArchive, listNewsSources, listNewsYears } from '../newsApi'
import type { LabNewsArticle, PublicNewsSort } from '../newsTypes'
import '../landing.css'
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
    void Promise.all([
      listNewsSources(controller.signal),
      listNewsYears(controller.signal),
    ])
      .then(([sourceList, yearList]) => {
        if (!controller.signal.aborted) {
          setSources(uniqueSources(sourceList))
          setYears(yearList)
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
    void listNewsArchive({
      q: query.trim() || undefined,
      source: source || undefined,
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
          setError('Không thể tải danh sách tin tức. Vui lòng thử lại.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, query, reloadKey, sort, source, year])

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

  const hasFilters = Boolean(query.trim()) || Boolean(source) || year !== null || sort !== 'LATEST'

  const sourceOptions = useMemo(() => {
    const base = sources.map((s) => ({ value: s, label: s }))
    if (source && !sources.some((s) => s.toLocaleLowerCase('vi') === source.toLocaleLowerCase('vi'))) {
      base.unshift({ value: source, label: source })
    }
    return [{ value: 'ALL', label: optionsError ? 'Không tải được nguồn' : 'Tất cả nguồn' }, ...base]
  }, [optionsError, source, sources])

  const yearOptions = useMemo(
    () => [
      { value: 'ALL', label: optionsError ? 'Không tải được năm' : 'Tất cả năm' },
      ...years.map((y) => ({ value: String(y), label: String(y) })),
    ],
    [optionsError, years],
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
        title="Tin tức"
        description="Các thông tin truyền thông công khai liên quan đến Smart Lab."
      />
      <section className="section news-archive-section">
        <div className="wrap">
          <div className="news-archive-intro">
            <div>
              <div className="kicker">TIN TỨC &amp; TRUYỀN THÔNG</div>
              <h2>Tin tức</h2>
              <p>Các thông tin truyền thông công khai liên quan đến Smart Lab.</p>
            </div>
            <span className="public-archive-page-size">12 tin tức / trang</span>
          </div>

          <div className="news-archive-toolbar" role="search">
            <label className="news-archive-search">
              <span className="sr-only">Tìm kiếm tin tức</span>
              <Search aria-hidden="true" />
              <input
                className="input"
                type="search"
                value={query}
                onChange={(event) => updateFilter('q', event.target.value)}
                placeholder="Tìm theo tiêu đề tin tức hoặc nguồn..."
              />
            </label>

            <PopupSelect
              value={source ? source : 'ALL'}
              options={sourceOptions}
              onChange={(value) => updateFilter('source', value)}
              ariaLabel="Lọc theo nguồn tin tức"
              className="news-archive-filter"
              disabled={optionsError}
            />

            <PopupSelect
              value={year ? String(year) : 'ALL'}
              options={yearOptions}
              onChange={(value) => updateFilter('year', value)}
              ariaLabel="Lọc theo năm xuất bản"
              className="news-archive-filter"
              disabled={optionsError}
            />

            <PopupSelect
              value={sort}
              options={sortOptions}
              onChange={(value) => updateFilter('sort', value)}
              ariaLabel="Sắp xếp tin tức"
              className="news-archive-filter"
            />

            {hasFilters ? (
              <button className="news-archive-reset" type="button" onClick={resetFilters}>
                <RotateCcw size={15} aria-hidden="true" />
                Đặt lại
              </button>
            ) : null}
          </div>

          {error ? (
            <div className="news-archive-request-state" role="alert">
              <Feedback error={error} />
              <button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}>
                Thử tải lại
              </button>
            </div>
          ) : null}

          {loading && items.length === 0 ? (
            <div className="public-empty empty tight news-archive-request-state" aria-busy="true">
              Đang tải tin tức...
            </div>
          ) : null}

          {!loading && !error && items.length === 0 ? (
            <EmptyState
              title={hasFilters ? 'Không tìm thấy tin tức phù hợp' : 'Chưa có tin tức công khai'}
              description={
                hasFilters
                  ? 'Thử thay đổi từ khóa hoặc bộ lọc.'
                  : 'Các thông tin truyền thông của Smart Lab sẽ xuất hiện tại đây.'
              }
            />
          ) : null}

          {items.length > 0 ? (
            <>
              <div className="news-archive-results-bar">
                <p className="news-archive-summary" aria-live="polite">
                  {formatRange(page, PAGE_SIZE, totalElements)} · {totalElements} tin tức
                </p>
                {loading ? (
                  <span className="news-archive-refreshing" aria-live="polite">
                    Đang cập nhật...
                  </span>
                ) : null}
              </div>
              <div className="landing-media-grid" aria-busy={loading}>
                {items.map((item) => (
                  <NewsCard key={item.id} item={item} />
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

function parsePage(value: string | null) {
  if (value === null || !/^\d+$/.test(value)) return 1
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
  return values
    .map((value) => value.trim())
    .filter(Boolean)
    .filter(
      (value, index, all) =>
        all.findIndex((candidate) => candidate.toLocaleLowerCase('vi') === value.toLocaleLowerCase('vi')) === index,
    )
    .sort((a, b) => a.localeCompare(b, 'vi'))
}

function formatRange(page: number, size: number, total: number) {
  if (total === 0) return '0 / 0'
  return `${(page - 1) * size + 1}–${Math.min(page * size, total)} / ${total}`
}

function NewsCard({ item }: { item: LabNewsArticle }) {
  const content: ReactNode = (
    <div className="landing-media-card-body">
      <div className="landing-media-card-meta">
        <span className="landing-media-card-source">{item.sourceName}</span>
        {item.publishedAt ? <span className="landing-media-card-date">• {formatDate(item.publishedAt)}</span> : null}
      </div>
      <h2 className="landing-media-card-title">{item.title}</h2>
      {item.excerpt ? <p className="landing-media-card-excerpt">{shorten(item.excerpt, 150)}</p> : null}
      <div className="landing-media-card-foot">
        <span className="landing-media-card-action">
          Xem nguồn <ExternalLink size={14} aria-hidden="true" />
        </span>
      </div>
    </div>
  )
  const safeUrl = safeExternalUrl(item.sourceUrl)
  if (!safeUrl) return <article className="landing-media-card is-news">{content}</article>
  return (
    <a
      className="landing-media-card is-news"
      href={safeUrl}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={`Xem ${item.title} trên ${item.sourceName}`}
    >
      {content}
    </a>
  )
}

function safeExternalUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
}

function formatDate(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(date)
}

function shorten(value: string | null | undefined, max: number) {
  if (!value) return ''
  if (value.length <= max) return value
  return value.slice(0, max).trim() + '...'
}

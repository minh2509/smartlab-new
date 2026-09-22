import { Pagination } from "../../../shared/components/Pagination";
import { ArrowLeft, ChevronRight, ExternalLink, RotateCcw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../../shared/components/EmptyState'
import { PublicPageHead } from '../components/PublicPageHead'
import { listAchievementYears, listAchievements } from '../achievementApi'
import { ACHIEVEMENT_TYPE_LABELS } from '../achievementTypes'
import type { Achievement, YearCount } from '../achievementTypes'
import './AchievementArchivePage.css'

const PAGE_SIZE = 10

export function AchievementArchivePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [years, setYears] = useState<YearCount[]>([])
  const [items, setItems] = useState<Achievement[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [yearsLoaded, setYearsLoaded] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const page = parsePage(searchParams.get('page'))
  const requestedYear = parseYear(searchParams.get('year'))
  const selectedYear = yearsLoaded
    ? (requestedYear !== null && years.some((e) => e.year === requestedYear) ? requestedYear : years[0]?.year ?? null)
    : null

  useEffect(() => {
    let active = true
    void listAchievementYears()
      .then((result) => {
        if (!active) return
        setYears(result)
        setYearsLoaded(true)
        const canonicalYear = result.find((e) => e.year === requestedYear)?.year ?? result[0]?.year
        const next = new URLSearchParams(searchParams)
        let changed = false
        if (canonicalYear !== undefined && next.get('year') !== String(canonicalYear)) {
          next.set('year', String(canonicalYear))
          changed = true
        }
        if (next.get('page') === '1') {
          next.delete('page')
          changed = true
        }
        if (changed) setSearchParams(next, { replace: true })
      })
      .catch((reason: unknown) => {
        if (active) { setYearsLoaded(true); setError(reason instanceof Error ? reason.message : 'Lỗi tải năm.') }
      })
    return () => { active = false }
  }, [requestedYear, searchParams, setSearchParams])

  useEffect(() => {
    if (selectedYear === null) {
      setItems([]); setTotalElements(0); setTotalPages(0)
      if (yearsLoaded) setLoading(false)
      return
    }
    let active = true
    setLoading(true); setError(null); setItems([]); setTotalElements(0); setTotalPages(0)
    void listAchievements(selectedYear, page - 1, PAGE_SIZE)
      .then((result) => {
        if (!active) return
        const normalizedPage = result.totalPages > 0 ? Math.min(page, result.totalPages) : 1
        const next = new URLSearchParams(searchParams)
        if (page !== normalizedPage || (page === 1 && next.has('page'))) {
          if (normalizedPage <= 1) next.delete('page')
          else next.set('page', String(normalizedPage))
          setSearchParams(next, { replace: true })
          return
        }
        setItems(result.items); setTotalElements(result.totalElements); setTotalPages(result.totalPages)
      })
      .catch((reason: unknown) => {
        if (active) { setError(reason instanceof Error ? reason.message : 'Lỗi tải thành tựu.') }
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, searchParams, selectedYear, setSearchParams, yearsLoaded])

  function selectYear(value: string) {
    const next = new URLSearchParams(searchParams)
    next.set('year', value)
    next.delete('page')
    setSearchParams(next)
  }

  function updatePage(value: number) {
    const next = new URLSearchParams(searchParams)
    if (value <= 1) next.delete('page')
    else next.set('page', String(value))
    setSearchParams(next)
  }

  return (
    <>
      <PublicPageHead title="Thành tựu" description="Kết quả nghiên cứu, sản phẩm, giải thưởng của Smart Lab." />
      <section className="achievement-archive-section">
        <div className="achievement-archive-header">
          <div className="achievement-archive-intro">
            <div>
              <div className="kicker">THÀNH TỰU</div>
              <h2>Thành tựu &amp; công bố</h2>
              <p>Kết quả nghiên cứu, sản phẩm, giải thưởng và dấu mốc của Smart Lab.</p>
            </div>
            <span className="achievement-archive-page-size">{PAGE_SIZE} mục / trang</span>
          </div>

          <div className="achievement-archive-toolbar">
            <label htmlFor="achievement-year">Năm</label>
            <select id="achievement-year" value={selectedYear ?? ''} onChange={(e) => selectYear(e.target.value)} disabled={years.length === 0}>
              {years.length === 0 ? <option value="">Đang tải...</option> : null}
              {years.map((entry) => <option key={entry.year} value={entry.year}>{entry.year} · {entry.count} mục</option>)}
            </select>
            {selectedYear !== null && <span className="achievement-archive-count">{formatRange(page, PAGE_SIZE, totalElements)} · {totalElements}</span>}
          </div>
        </div>

        <p className="sr-only" role="status" aria-live="polite">
          {loading ? 'Đang tải...' : selectedYear === null ? 'Chưa có năm.' : `Năm ${selectedYear}: ${formatRange(page, PAGE_SIZE, totalElements)} · ${totalElements} mục`}
        </p>

        {error && (
          <div className="achievement-archive-error" role="alert">
            <p>{error}</p>
            <button className="btn" type="button" onClick={() => setSearchParams(new URLSearchParams(searchParams))}>
              <RotateCcw size={15} /> Thử lại
            </button>
          </div>
        )}

        {loading && items.length === 0 && (
          <div className="achievement-archive-empty" aria-busy="true">Đang tải thành tựu...</div>
        )}

        {!loading && !error && items.length === 0 && (
          <EmptyState title={selectedYear ? `Chưa có dữ liệu năm ${selectedYear}` : 'Chưa có thành tựu công khai'} />
        )}

        {items.length > 0 && (
          <>
            <div className="achievement-archive-list" aria-busy={loading}>
              {items.map((item) => <AchievementRow key={item.id} item={item} />)}
            </div>
            <div className="achievement-archive-pagination">
              <Pagination page={page} totalPages={totalPages} onChange={updatePage} />
            </div>
          </>
        )}
      </section>
    </>
  )
}

function AchievementRow({ item }: { item: Achievement }) {
  return (
    <article className="achievement-archive-row">
      <div className="achievement-archive-row-meta">
        <span className="achievement-archive-row-type">{ACHIEVEMENT_TYPE_LABELS[item.achievementType]}</span>
        <time className="achievement-archive-row-date" dateTime={item.achievementDate ?? String(item.achievementYear)}>
          {item.achievementDate ?? item.achievementYear}
        </time>
      </div>

      <Link to={`/thanh-tuu/${item.id}`} className="achievement-archive-row-content">
        <h3>{item.title}</h3>
        {item.summary && <p>{item.summary}</p>}
        <div className="achievement-archive-row-tags">
          {item.recognizingOrganization && <span className="achievement-archive-tag">{item.recognizingOrganization}</span>}
          {item.relatedProject && <span className="achievement-archive-tag">Dự án: {item.relatedProject.code}</span>}
        </div>
        <span className="achievement-archive-row-link-hint">Xem chi tiết <ChevronRight size={14} /></span>
      </Link>

      {item.evidenceUrl && (
        <div className="achievement-archive-row-actions">
          <a href={item.evidenceUrl} target="_blank" rel="noopener noreferrer" className="achievement-archive-evidence" title="Xem minh chứng">
            <ExternalLink size={14} />
          </a>
        </div>
      )}
    </article>
  )
}

function parsePage(value: string | null) {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : 1
}

function formatRange(page: number, size: number, total: number) {
  if (total === 0) return '0 / 0'
  return `${(Math.min(page, Math.ceil(total / size)) - 1) * size + 1}–${Math.min(page * size, total)} / ${total}`
}

function parseYear(value: string | null) {
  if (!value) return null
  const year = Number(value)
  return Number.isInteger(year) ? year : null
}

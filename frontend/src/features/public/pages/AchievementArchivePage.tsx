import { Pagination } from "../../../shared/components/Pagination";

import { ExternalLink, RotateCcw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
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
    ? (requestedYear !== null && years.some((entry) => entry.year === requestedYear)
      ? requestedYear
      : years[0]?.year ?? null)
    : null

  useEffect(() => {
    let active = true
    void listAchievementYears()
      .then((result) => {
        if (!active) return
        setYears(result)
        setYearsLoaded(true)

        const canonicalYear = result.find((entry) => entry.year === requestedYear)?.year ?? result[0]?.year
        const next = new URLSearchParams(searchParams)
        let changed = false

        if (canonicalYear !== undefined && next.get('year') !== String(canonicalYear)) {
          next.set('year', String(canonicalYear))
          changed = true
        }

        const requestedPage = next.get('page')
        if (requestedPage !== null && parsePage(requestedPage) === 1) {
          next.delete('page')
          changed = true
        }

        if (changed) {
          setSearchParams(next, { replace: true })
        }
      })
      .catch((reason: unknown) => {
        if (active) {
          setYearsLoaded(true)
          setError(reason instanceof Error ? reason.message : 'Không tải được danh sách năm.')
        }
      })
    return () => { active = false }
  }, [requestedYear, searchParams, setSearchParams])

  useEffect(() => {
    if (selectedYear === null) {
      setItems([])
      setTotalElements(0)
      setTotalPages(0)
      if (yearsLoaded) setLoading(false)
      return
    }
    let active = true
    setLoading(true)
    setError(null)
    setItems([])
    setTotalElements(0)
    setTotalPages(0)
    void listAchievements(selectedYear, page - 1, PAGE_SIZE)
      .then((result) => {
        if (!active) return

        const normalizedPage = result.totalPages > 0 ? Math.min(page, result.totalPages) : 1
        const next = new URLSearchParams(searchParams)
        const shouldNormalizePage = page !== normalizedPage || (page === 1 && next.has('page'))

        if (shouldNormalizePage) {
          if (normalizedPage <= 1) next.delete('page')
          else next.set('page', String(normalizedPage))
          setSearchParams(next, { replace: true })
          return
        }

        setItems(result.items)
        setTotalElements(result.totalElements)
        setTotalPages(result.totalPages)
      })
      .catch((reason: unknown) => {
        if (active) {
          setItems([])
          setTotalElements(0)
          setTotalPages(0)
          setError(reason instanceof Error ? reason.message : 'Không tải được thành tựu.')
        }
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

  function retry() {
    setSearchParams(new URLSearchParams(searchParams))
  }

  return (
    <>
      <PublicPageHead title="Thành tựu và công bố" description="Các kết quả nghiên cứu, sản phẩm, giải thưởng và dấu mốc công khai của Smart Lab." />
      <section className="section achievement-archive-section">
        <div className="wrap">
          <div className="achievement-archive-intro">
            <div>
              <div className="kicker">ACHIEVEMENT ARCHIVE</div>
              <h2>Thành tựu &amp; công bố</h2>
              <p>Tra cứu các kết quả của Smart Lab theo từng năm. Mỗi trang chỉ tải phần dữ liệu đang được xem.</p>
            </div>
            <span className="achievement-archive-page-size">10 items / trang</span>
          </div>

          <div className="achievement-archive-toolbar">
            <label htmlFor="achievement-year">Năm</label>
            <select id="achievement-year" value={selectedYear ?? ''} onChange={(event) => selectYear(event.target.value)} disabled={years.length === 0}>
              {years.length === 0 ? <option value="">Đang tải năm...</option> : null}
              {years.map((entry) => <option key={entry.year} value={entry.year}>{entry.year} · {entry.count} mục</option>)}
            </select>
            {selectedYear !== null ? <span className="achievement-archive-count">{formatRange(page, PAGE_SIZE, totalElements)} · {totalElements} mục</span> : null}
          </div>

          <p className="sr-only" role="status" aria-live="polite" aria-atomic="true">
            {loading
              ? 'Đang tải thành tựu...'
              : selectedYear === null
                ? 'Chưa có năm thành tựu.'
                : `Năm ${selectedYear}: ${formatRange(page, PAGE_SIZE, totalElements)} · ${totalElements} mục`}
          </p>

          {error ? <div className="achievement-archive-error" role="alert"><p>{error}</p><button className="btn" type="button" onClick={retry}><RotateCcw size={15} aria-hidden="true" /> Thử tải lại</button></div> : null}
          {loading ? <div className="public-empty empty tight" aria-busy="true">Đang tải thành tựu...</div> : null}
          {!loading && !error && items.length === 0 ? <EmptyState title={selectedYear ? `Chưa có dữ liệu trong năm ${selectedYear}` : 'Chưa có thành tựu công khai'} description="Các kết quả được công khai sẽ xuất hiện tại đây." /> : null}

          {items.length > 0 ? <>
            <div className="achievement-archive-list" aria-busy={loading}>
              {items.map((item) => <AchievementRow key={item.id} item={item} />)}
            </div>
            <Pagination page={page} totalPages={totalPages} onChange={updatePage} />
          </> : null}
        </div>
      </section>
    </>
  )
}

function AchievementRow({ item }: { item: Achievement }) {
  return <article className="achievement-archive-row">
    <div className="achievement-archive-row-meta">
      <span>{ACHIEVEMENT_TYPE_LABELS[item.achievementType]}</span>
      <time dateTime={item.achievementDate ?? String(item.achievementYear)}>{item.achievementDate ?? item.achievementYear}</time>
    </div>
    <div className="achievement-archive-row-content">
      <h2>{item.title}</h2>
      {item.summary ? <p>{item.summary}</p> : null}
      {item.recognizingOrganization ? <span className="achievement-archive-organization">{item.recognizingOrganization}</span> : null}
      {item.relatedProject ? <span className="achievement-archive-project">Liên quan: {item.relatedProject.name}</span> : null}
    </div>
    {item.evidenceUrl ? <a className="achievement-archive-evidence" href={item.evidenceUrl} target="_blank" rel="noopener noreferrer">Minh chứng <ExternalLink size={15} aria-hidden="true" /></a> : null}
  </article>
}


function parsePage(value: string | null) {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : 1
}

function formatRange(page: number, size: number, total: number) {
  if (total === 0) return '0 / 0'
  const lastPage = Math.ceil(total / size)
  const safePage = Math.max(1, Math.min(page, lastPage))
  return `${(safePage - 1) * size + 1}–${Math.min(safePage * size, total)} / ${total}`
}

function parseYear(value: string | null) {
  if (!value) return null
  const year = Number(value)
  return Number.isInteger(year) ? year : null
}

import { Pagination } from "../../../shared/components/Pagination";

import { ExternalLink, RotateCcw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PublicPageHead } from '../components/PublicPageHead'
import { listNewsArchive } from '../newsApi'
import type { LabNewsArticle } from '../newsTypes'

const PAGE_SIZE = 12

export function NewsArchivePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [items, setItems] = useState<LabNewsArticle[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const page = parsePage(searchParams.get('page'))

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    setItems([])
    void listNewsArchive(page - 1, PAGE_SIZE, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return
        setItems(response.items)
        setTotalPages(response.totalPages)
        setTotalElements(response.totalElements)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Không thể tải danh sách tin tức.')
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [page, reloadKey])

  function updatePage(value: number) {
    const next = new URLSearchParams(searchParams)
    if (value <= 1) next.delete('page')
    else next.set('page', String(value))
    setSearchParams(next)
  }

  return (
    <>
      <PublicPageHead title="Tin tức" description="Các thông tin truyền thông công khai liên quan đến Smart Lab." />
      <section className="section">
        <div className="wrap">
          <div className="sec-head public-archive-head">
            <div className="kicker">TIN TỨC &amp; TRUYỀN THÔNG</div>
            <h2>Tin tức</h2>
            <p>Các thông tin truyền thông công khai liên quan đến Smart Lab.</p>
            <span className="public-archive-page-size">12 tin tức / trang</span>
          </div>

          {error ? <div role="alert"><div className="alert error">{error}</div><button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}><RotateCcw size={15} aria-hidden="true" /> Thử tải lại</button></div> : null}
          {loading ? <div className="landing-empty-state" aria-busy="true">Đang tải tin tức...</div> : null}
          {!loading && !error && items.length === 0 ? <div className="landing-empty-state">Chưa có tin tức nào được công bố.</div> : null}
          {items.length > 0 ? <>
            <p className="public-archive-summary" aria-live="polite">{formatRange(page, PAGE_SIZE, totalElements)} · {totalElements} tin tức</p>
            <div className="landing-media-grid">
              {items.map((item) => <a key={item.id} className="landing-media-card is-news" href={item.sourceUrl} target="_blank" rel="noopener noreferrer" aria-label={`Xem ${item.title} trên ${item.sourceName}`}>
                <div className="landing-media-card-body">
                  <div className="landing-media-card-meta"><span className="landing-media-card-source">{item.sourceName}</span>{item.publishedAt ? <span className="landing-media-card-date">• {formatDate(item.publishedAt)}</span> : null}</div>
                  <h2 className="landing-media-card-title">{item.title}</h2>
                  {item.excerpt ? <p className="landing-media-card-excerpt">{shorten(item.excerpt, 150)}</p> : null}
                  <div className="landing-media-card-foot"><span className="landing-media-card-action">Xem nguồn <ExternalLink size={14} aria-hidden="true" /></span></div>
                </div>
              </a>)}
            </div>
            <Pagination page={page} totalPages={totalPages} onChange={updatePage} />
          </> : null}
        </div>
      </section>
    </>
  )
}


function parsePage(value: string | null) {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : 1
}

function formatRange(page: number, size: number, total: number) {
  if (total === 0) return '0 / 0'
  return `${(page - 1) * size + 1}–${Math.min(page * size, total)} / ${total}`
}

function formatDate(value: string | null | undefined, opts?: Intl.DateTimeFormatOptions) {
  if (!value) return ''
  return new Intl.DateTimeFormat('vi-VN', opts || { dateStyle: 'medium' }).format(new Date(value))
}

function shorten(value: string | null | undefined, max: number) {
  if (!value) return ''
  if (value.length <= max) return value
  return value.slice(0, max).trim() + '...'
}

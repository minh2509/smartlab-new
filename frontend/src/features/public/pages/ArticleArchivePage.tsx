import { Pagination } from "../../../shared/components/Pagination";

import { RotateCcw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PublicArticleCard } from '../components/PublicDataCards'
import { PublicPageHead } from '../components/PublicPageHead'
import { listArticles } from '../articleApi'
import type { LabArticleSummary } from '../articleTypes'
import '../landing.css'

const PAGE_SIZE = 12

export function ArticleArchivePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [items, setItems] = useState<LabArticleSummary[]>([])
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
    void listArticles(page - 1, PAGE_SIZE, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return
        setItems(response.items)
        setTotalPages(response.totalPages)
        setTotalElements(response.totalElements)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Không thể tải danh sách bài viết.')
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
      <PublicPageHead title="Bài viết" description="Các bài viết, chia sẻ và nội dung chuyên môn được Smart Lab công bố." />
      <section className="section">
        <div className="wrap">
          <div className="sec-head public-archive-head">
            <div className="kicker">BÀI VIẾT SMART LAB</div>
            <h2>Bài viết</h2>
            <p>Các bài viết, chia sẻ và nội dung chuyên môn được Smart Lab công bố.</p>
            <span className="public-archive-page-size">12 bài viết / trang</span>
          </div>

          {error ? <div role="alert"><div className="alert error">{error}</div><button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}><RotateCcw size={15} aria-hidden="true" /> Thử tải lại</button></div> : null}
          {loading ? <div className="public-empty empty tight" aria-busy="true">Đang tải bài viết...</div> : null}
          {!loading && !error && items.length === 0 ? <div className="public-empty empty tight">Chưa có bài viết nào được công bố.</div> : null}
          {items.length > 0 ? <>
            <p className="public-archive-summary" aria-live="polite">{formatRange(page, PAGE_SIZE, totalElements)} · {totalElements} bài viết</p>
            <div className="grid c3 landing-article-grid">{items.map((item) => <PublicArticleCard key={item.id} article={item} />)}</div>
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

import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { listPostReactions } from '../api'
import { relativeTime } from '../relativeTime'
import { REACTIONS } from '../reactions'
import type { PostReactionUser, ReactionCounts, ReactionType } from '../types'

type Props = {
  token: string
  postId: number
  counts: ReactionCounts
  total: number
  onClose: () => void
}

export function PostReactionsDialog({ token, postId, counts, total, onClose }: Props) {
  const panelRef = useRef<HTMLDivElement>(null)
  const [reaction, setReaction] = useState<ReactionType | null>(null)
  const [rows, setRows] = useState<PostReactionUser[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    setRows([])
    setCursor(null)
    void listPostReactions(token, postId, { reaction, limit: 30 })
      .then((page) => { if (active) { setRows(page.items); setCursor(page.nextCursor) } })
      .catch((value: unknown) => { if (active) setError(messageOf(value, 'Không thể tải danh sách cảm xúc.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [postId, reaction, token])

  useEffect(() => {
    panelRef.current?.focus()
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); onClose() }
      if (event.key === 'Tab' && panelRef.current) keepFocusInside(event, panelRef.current)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  async function loadMore() {
    if (!cursor || loadingMore) return
    setLoadingMore(true)
    try {
      const page = await listPostReactions(token, postId, { reaction, cursor, limit: 30 })
      setRows((current) => [...current, ...page.items])
      setCursor(page.nextCursor)
    } catch (value) {
      setError(messageOf(value, 'Không thể tải thêm cảm xúc.'))
    } finally { setLoadingMore(false) }
  }

  const content = <div className="reactions-dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <section className="reactions-dialog" role="dialog" aria-modal="true" aria-labelledby="reactions-dialog-title" ref={panelRef} tabIndex={-1}>
      <header className="reactions-dialog-head">
        <h2 id="reactions-dialog-title">Cảm xúc về bài viết</h2>
        <button type="button" aria-label="Đóng danh sách cảm xúc" onClick={onClose}><X aria-hidden="true" /></button>
      </header>
      <div className="reactions-filter-strip" role="group" aria-label="Lọc cảm xúc">
        <button type="button" className={!reaction ? 'is-selected' : ''} aria-pressed={!reaction} onClick={() => setReaction(null)}>Tất cả {total}</button>
        {REACTIONS.map(({ type, label, Icon }) => <button key={type} type="button" className={reaction === type ? 'is-selected' : ''} aria-pressed={reaction === type} onClick={() => setReaction(type)}>
          <Icon aria-hidden="true" /> {label} {counts[type]}
        </button>)}
      </div>
      <div className="reactions-dialog-body">
        {loading ? <p className="reactions-dialog-state" aria-live="polite">Đang tải cảm xúc...</p> : null}
        {!loading && error ? <p className="reactions-dialog-state is-error" role="alert">{error}</p> : null}
        {!loading && !error && rows.length === 0 ? <p className="reactions-dialog-state">Không có người dùng nào với cảm xúc này.</p> : null}
        {!loading && !error ? <div className="reactor-list">{rows.map((row, index) => {
          const presentation = REACTIONS.find((item) => item.type === row.reaction)!
          const Icon = presentation.Icon
          return <article className="reactor-row" key={`${row.user?.userId ?? 'unknown'}-${row.reaction}-${row.reactedAt}-${index}`}>
            <span className="reactor-avatar" aria-hidden="true">{row.user?.name?.slice(0, 1).toUpperCase() ?? '?'}</span>
            <span><strong>{row.user?.name ?? 'Tài khoản không còn khả dụng'}</strong><small>{relativeTime(row.reactedAt)}</small></span>
            <Icon aria-label={presentation.label} />
          </article>
        })}</div> : null}
        {cursor ? <button className="reactions-load-more" type="button" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? 'Đang tải...' : 'Tải thêm'}</button> : null}
      </div>
    </section>
  </div>
  return createPortal(content, document.body)
}

function messageOf(value: unknown, fallback: string) { return value instanceof Error ? value.message : fallback }

function keepFocusInside(event: KeyboardEvent, container: HTMLElement) {
  const focusable = Array.from(container.querySelectorAll<HTMLElement>('button:not([disabled]), [tabindex]:not([tabindex="-1"])'))
  if (!focusable.length) return
  const first = focusable[0]; const last = focusable.at(-1)!
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}

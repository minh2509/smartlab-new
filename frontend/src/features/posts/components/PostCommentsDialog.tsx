import { useEffect, useRef, useState } from 'react'
import { Edit3, Trash2, X } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import {
  createPostComment,
  deletePostComment,
  listPostComments,
  updatePostComment,
} from '../api'
import { exactDateTime, relativeTime } from '../relativeTime'
import { PostReactionPicker } from './PostReactionPicker'
import { PostContent } from './PostContent'
import { parsePostContent } from '../postContent'
import { reactionPresentation } from '../reactions'
import type { PostComment, PostFeedItem, ReactionState, ReactionType } from '../types'

type Props = {
  post: PostFeedItem
  onClose: () => void
  onCommentCountChange: (count: number) => void
  reactionPending: boolean
  onReaction: (reaction: ReactionType) => Promise<ReactionState>
  onRemoveReaction: () => Promise<ReactionState>
}

export function PostCommentsDialog({
  post,
  onClose,
  onCommentCountChange,
  reactionPending,
  onReaction,
  onRemoveReaction,
}: Props) {
  const { token, profile } = useAuth()
  const panelRef = useRef<HTMLDivElement>(null)
  const composerRef = useRef<HTMLTextAreaElement>(null)
  const [comments, setComments] = useState<PostComment[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const postContent = parsePostContent(post.contentJson)

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panelRef.current?.focus()
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
      if (event.key === 'Tab' && panelRef.current) keepFocusInside(event, panelRef.current)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [onClose])

  useEffect(() => {
    if (!token) return
    setLoading(true)
    void listPostComments(token, post.id)
      .then((page) => { setComments(page.items); setCursor(page.nextCursor) })
      .catch((value: unknown) => setError(messageOf(value, 'Không thể tải bình luận.')))
      .finally(() => setLoading(false))
  }, [post.id, token])

  async function loadMore() {
    if (!token || !cursor || loadingMore) return
    setLoadingMore(true)
    try {
      const page = await listPostComments(token, post.id, cursor)
      setComments((current) => [...current, ...page.items])
      setCursor(page.nextCursor)
    } catch (value) {
      setError(messageOf(value, 'Không thể tải thêm bình luận.'))
    } finally {
      setLoadingMore(false)
    }
  }

  async function submit() {
    const normalized = content.trim()
    if (!token || !normalized || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const created = await createPostComment(token, post.id, normalized)
      setComments((current) => [created, ...current])
      setContent('')
      onCommentCountChange(post.commentCount + 1)
      composerRef.current?.focus()
    } catch (value) {
      setError(messageOf(value, 'Không thể gửi bình luận.'))
    } finally {
      setSubmitting(false)
    }
  }

  async function edit(comment: PostComment) {
    if (!token) return
    const next = window.prompt('Chỉnh sửa bình luận', comment.content)?.trim()
    if (!next || next === comment.content) return
    try {
      const updated = await updatePostComment(token, post.id, comment.id, next)
      setComments((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (value) {
      setError(messageOf(value, 'Không thể chỉnh sửa bình luận.'))
    }
  }

  async function remove(comment: PostComment) {
    if (!token || !window.confirm('Xóa bình luận này?')) return
    try {
      await deletePostComment(token, post.id, comment.id)
      setComments((current) => current.filter((item) => item.id !== comment.id))
      onCommentCountChange(Math.max(0, post.commentCount - 1))
    } catch (value) {
      setError(messageOf(value, 'Không thể xóa bình luận.'))
    }
  }

  return (
    <div className="comments-dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div
        className="comments-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="comments-dialog-title"
        tabIndex={-1}
        ref={panelRef}
      >
        <header className="comments-dialog-head">
          <div>
            <strong id="comments-dialog-title">Bài viết của {post.author?.name ?? 'thành viên Smart Lab'}</strong>
            {post.publishedAt ? <time title={exactDateTime(post.publishedAt)}>{relativeTime(post.publishedAt)}</time> : null}
          </div>
          <button type="button" aria-label="Đóng bình luận" onClick={onClose}><X aria-hidden="true" /></button>
        </header>

        <div className="comments-dialog-scroll">
          <section className="comments-post-content" aria-label="Nội dung bài viết">
            {post.title ? <h2>{post.title}</h2> : null}
            {postContent ? <PostContent contentJson={post.contentJson} slug={post.slug} token={token} parsedContent={postContent} /> : (
              <p>{post.excerpt ?? 'Nội dung này hiện chưa hỗ trợ hiển thị đầy đủ.'}</p>
            )}
          </section>
          <div className="comments-social-strip">
            <span>
              {(Object.entries(post.reactionCounts) as Array<[ReactionType, number]>)
                .filter(([, count]) => count > 0)
                .slice(0, 3)
                .map(([type]) => <span aria-hidden="true" key={type}>{reactionPresentation(type).emoji}</span>)}
              {post.reactionCount > 0 ? <strong>{post.reactionCount}</strong> : <small>Chưa có cảm xúc</small>}
            </span>
            <PostReactionPicker
              selected={post.viewerReaction}
              disabled={reactionPending}
              onSelect={(reaction) => void onReaction(reaction)}
              onRemove={() => void onRemoveReaction()}
            />
          </div>
          <div className="comments-list-head"><strong>Bình luận</strong><span>Mới nhất trước</span></div>
          {loading ? <div className="comments-loading" aria-live="polite">Đang tải bình luận...</div> : null}
          {!loading && comments.length === 0 ? <div className="comments-empty">Chưa có bình luận. Hãy bắt đầu cuộc trao đổi.</div> : null}
          <div className="comments-list">
            {comments.map((comment) => {
              const own = comment.author?.userId === profile?.userId
              return (
                <article className="comment-item" key={comment.id}>
                  <div className="comment-avatar" aria-hidden="true">{comment.author?.name?.slice(0, 1).toUpperCase() ?? '?'}</div>
                  <div className="comment-body">
                    <div className="comment-bubble">
                      <strong>{comment.author?.name ?? 'Tác giả không còn khả dụng'}</strong>
                      <p>{comment.content}</p>
                    </div>
                    <div className="comment-meta">
                      <time title={exactDateTime(comment.createdAt)}>{relativeTime(comment.createdAt)}</time>
                      {own ? <>
                        <span className="comment-meta-separator" aria-hidden="true">·</span>
                        <button type="button" onClick={() => void edit(comment)}><Edit3 aria-hidden="true" /> Sửa</button>
                        <span className="comment-meta-separator" aria-hidden="true">·</span>
                        <button type="button" onClick={() => void remove(comment)}><Trash2 aria-hidden="true" /> Xóa</button>
                      </> : null}
                    </div>
                  </div>
                </article>
              )
            })}
          </div>
          {cursor ? <button className="comments-load-more" type="button" disabled={loadingMore} onClick={() => void loadMore()}>
            {loadingMore ? 'Đang tải...' : 'Tải thêm bình luận'}
          </button> : null}
        </div>

        <footer className="comments-composer">
          {error ? <p role="alert">{error}</p> : null}
          <label htmlFor={`comment-${post.id}`}>Viết bình luận</label>
          <div>
            <textarea
              id={`comment-${post.id}`}
              ref={composerRef}
              value={content}
              maxLength={5000}
              rows={2}
              placeholder="Chia sẻ ý kiến của bạn"
              onChange={(event) => setContent(event.target.value)}
              onKeyDown={(event) => {
                if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') void submit()
              }}
            />
            <button type="button" disabled={!content.trim() || submitting} onClick={() => void submit()}>
              {submitting ? 'Đang gửi' : 'Gửi'}
            </button>
          </div>
        </footer>
      </div>
    </div>
  )
}

function messageOf(value: unknown, fallback: string) {
  return value instanceof Error ? value.message : fallback
}

function keepFocusInside(event: KeyboardEvent, container: HTMLElement) {
  const focusable = Array.from(container.querySelectorAll<HTMLElement>(
    'button:not([disabled]), a[href], textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
  ))
  if (focusable.length === 0) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
